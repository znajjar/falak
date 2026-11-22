#!/usr/bin/env python3
"""Render a seamless animated GIF showcasing Falak during Asr prayer in Amman.

Captures Asr gliding along Orbit #3 from the left edge into exact center meridian
alignment (15:14 Amman time), pausing gracefully as the starfield sprinkles and
glints, then gliding smoothly out the right edge for an invisible loop seam.
"""

import base64
import json
import math
from io import BytesIO
from pathlib import Path
from PIL import Image
from playwright.sync_api import sync_playwright

ROOT = Path("/home/antigravity/falak-wallpaper")
OUT_GIF = ROOT / "renders" / "falak_asr_preview.gif"


def julian_day(year: int, month: int, day: int) -> float:
    if month <= 2:
        year -= 1
        month += 12
    a = year // 100
    b = 2 - a + a // 4
    return int(365.25 * (year + 4716)) + int(30.6001 * (month + 1)) + day + b - 1524.5


def sun_position(jd: float):
    d = jd - 2451545.0
    g = math.radians((357.529 + 0.98560028 * d) % 360.0)
    q = (280.459 + 0.98564736 * d) % 360.0
    l = math.radians((q + 1.915 * math.sin(g) + 0.020 * math.sin(2.0 * g)) % 360.0)
    e = math.radians(23.439 - 0.00000036 * d)
    ra = (math.degrees(math.atan2(math.cos(e) * math.sin(l), math.cos(l))) / 15.0) % 24.0
    decl = math.asin(math.sin(e) * math.sin(l))
    eqt = q / 15.0 - ra
    if eqt > 12.0:
        eqt -= 24.0
    if eqt < -12.0:
        eqt += 24.0
    return decl, eqt


def hour_angle_for_alt(lat_rad: float, decl: float, alt_deg: float) -> float:
    cos_ha = (math.sin(math.radians(alt_deg)) - math.sin(lat_rad) * math.sin(decl)) / (
        math.cos(lat_rad) * math.cos(decl)
    )
    cos_ha = max(-1.0, min(1.0, cos_ha))
    return math.degrees(math.acos(cos_ha)) / 15.0


def amman_prayers_hours(year: int, month: int, day: int, cfg: dict):
    loc = cfg["defaultLocation"]
    method = cfg["calculationSources"][0]
    lat_r = math.radians(loc["latitudeDeg"])
    lon = loc["longitudeDeg"]
    tz = 3.0
    jd0 = julian_day(year, month, day) - lon / 360.0

    def calc(approx_h, fn):
        decl, eqt = sun_position(jd0 + approx_h / 24.0)
        noon = 12.0 + tz - lon / 15.0 - eqt
        return fn(noon, decl)

    fajr = calc(5.0, lambda n, d: n - hour_angle_for_alt(lat_r, d, -method["fajrAngleDeg"]))
    dhuhr = calc(12.3, lambda n, d: n)
    asr = calc(
        15.3,
        lambda n, d: n
        + hour_angle_for_alt(
            lat_r, d, math.degrees(math.atan(1.0 / (method["asrShadowFactor"] + math.tan(abs(lat_r - d)))))
        ),
    )
    maghrib = calc(17.5, lambda n, d: n + hour_angle_for_alt(lat_r, d, -method["maghribAngleDeg"]))
    isha = calc(19.0, lambda n, d: n + hour_angle_for_alt(lat_r, d, -method["ishaAngleDeg"]))
    return [fajr, dhuhr, asr, maghrib, isha]


def angles_for_hour(hour_of_day: float, today: list):
    angles = []
    for t_p in today:
        diff_h = ((hour_of_day - t_p + 12.0) % 24.0) - 12.0
        angles.append((diff_h / 24.0) * 2.0 * math.pi)
    return angles


def main():
    vert_src = (ROOT / "model/falak.vert").read_text()
    frag_src = (ROOT / "model/falak.frag").read_text()
    cfg = json.loads((ROOT / "model/scene_config.json").read_text())

    today = amman_prayers_hours(2026, 11, 22, cfg)
    asr_h = today[2]
    print(f"Amman Asr time: {int(asr_h):02d}:{int(round((asr_h % 1) * 60)):02d}")

    # Render at 640x1420 (supersampled 2x, 20:9 portrait) and downsample cleanly to 320x710 for README preview
    rw, rh = 640, 1420
    out_w, out_h = 320, 710
    n_frames = 96
    fps = 20
    duration_ms = int(round(1000 / fps))

    # Build timeline around Asr:
    # u in [-1, +1] -> offset in hours from Asr with cubic linger right around center alignment (u=0)
    # Offset span +-0.98h (+-58.8 min = +-14.7 deg) so Asr enters cleanly from left edge,
    # lingers gracefully at exact 0.0 deg while the starfield sprinkles, then exits cleanly right.
    timeline = []
    for k in range(n_frames):
        frac = k / float(n_frames)
        u = 2.0 * frac - 1.0  # [-1, +1)
        # Smooth linger near center: 0.22*u + 0.78*u^3 slows down right at Asr alignment
        offset_h = 0.98 * (0.20 * u + 0.80 * (u ** 3))
        hour = asr_h + offset_h
        t_sec = k / float(fps)
        timeline.append((t_sec, angles_for_hour(hour, today)))

    html = f"""<!DOCTYPE html>
<html>
<body style="margin:0;overflow:hidden;background:#000">
<canvas id="c" width="{rw}" height="{rh}"></canvas>
<script>
const vertSrc = {json.dumps(vert_src)};
const fragSrc = {json.dumps(frag_src)};
const cfg = {json.dumps(cfg)};

const canvas = document.getElementById('c');
const gl = canvas.getContext('webgl2', {{ preserveDrawingBuffer: true, antialias: false }});
if (!gl) throw new Error('WebGL2 unavailable');

function compile(type, src) {{
  const s = gl.createShader(type);
  gl.shaderSource(s, src);
  gl.compileShader(s);
  if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
  return s;
}}
const prog = gl.createProgram();
gl.attachShader(prog, compile(gl.VERTEX_SHADER, vertSrc));
gl.attachShader(prog, compile(gl.FRAGMENT_SHADER, fragSrc));
gl.linkProgram(prog);
if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(prog));

const u = (n) => gl.getUniformLocation(prog, n);
const deg = Math.PI / 180.0;

gl.viewport(0, 0, {rw}, {rh});
gl.useProgram(prog);
gl.uniform2f(u('uResolution'), {rw}, {rh});
gl.uniform3f(u('uCamPos'), cfg.camera.position[0], cfg.camera.position[1], cfg.camera.position[2]);
gl.uniform1f(u('uCamPitch'), cfg.camera.pitchDeg * deg);
gl.uniform1f(u('uCamYaw'), cfg.camera.yawDeg * deg);
gl.uniform1f(u('uFovY'), cfg.camera.fovYDeg * deg);
gl.uniform1f(u('uRingWidth'), cfg.orbits.lineWidthPxAt1024);
gl.uniform1f(u('uRingBrightness'), cfg.orbits.brightness);
gl.uniform1fv(u('uPlanetOrbitR'), new Float32Array(cfg.prayers.map(p => p.orbitRadius)));
gl.uniform1fv(u('uPlanetRadii'), new Float32Array(cfg.prayers.map(p => p.planetRadius)));
gl.uniform1f(u('uPlanetDiffuse'), cfg.planetShading.diffuseStrength);
gl.uniform1f(u('uTerminatorWrap'), cfg.planetShading.terminatorWrap);
gl.uniform1f(u('uRimStrength'), cfg.planetShading.rimStrength);
gl.uniform1f(u('uSunCoreR'), cfg.star.coreRadiusWidthNorm);
gl.uniform1f(u('uSunGlow'), cfg.star.glowIntensity);
gl.uniform1f(u('uGrainStrength'), 0.25);
gl.uniform1f(u('uStarfieldStrength'), cfg.atmosphere.starfieldStrength);

window.renderFrame = (tSec, angles) => {{
  gl.uniform1f(u('uTime'), tSec);
  gl.uniform1fv(u('uPlanetAngles'), new Float32Array(angles));
  gl.drawArrays(gl.TRIANGLES, 0, 3);
  gl.finish();
  return canvas.toDataURL('image/png');
}};
</script>
</body>
</html>"""

    rgb_frames = []
    with sync_playwright() as p:
        browser = p.chromium.launch(
            executable_path="/usr/bin/google-chrome",
            args=[
                "--use-gl=angle",
                "--use-angle=swiftshader",
                "--enable-unsafe-swiftshader",
                "--ignore-gpu-blocklist",
            ],
        )
        page = browser.new_page(viewport={"width": rw, "height": rh})
        page.set_content(html)
        for idx, (t_sec, angles) in enumerate(timeline):
            data_url = page.evaluate("([t, a]) => window.renderFrame(t, a)", [t_sec, angles])
            im = Image.open(BytesIO(base64.b64decode(data_url.split(",", 1)[1]))).convert("RGB")
            im_small = im.resize((out_w, out_h), Image.Resampling.LANCZOS)
            rgb_frames.append(im_small)
            if (idx + 1) % 24 == 0:
                print(f"  Rendered {idx + 1}/{n_frames} frames...")
        browser.close()

    # Build a single shared 256-color optimal palette from the center Asr frame (covers sky, star, glow, planet & stars)
    # so there is zero frame-to-frame palette flicker and compact GIF compression!
    master = rgb_frames[n_frames // 2].quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
    pal_frames = [
        f.quantize(palette=master, dither=Image.Dither.FLOYDSTEINBERG)
        for f in rgb_frames
    ]

    OUT_GIF.parent.mkdir(parents=True, exist_ok=True)
    pal_frames[0].save(
        OUT_GIF,
        save_all=True,
        append_images=pal_frames[1:],
        duration=duration_ms,
        loop=0,
        optimize=True,
        disposal=1,
    )
    size_kb = OUT_GIF.stat().st_size / 1024.0
    print(f"Saved {OUT_GIF} ({out_w}x{out_h}, {n_frames} frames @ {fps} FPS, {size_kb:.1f} KB)")

    # Save 3 key frames as a quick verification strip
    strip = Image.new("RGB", (out_w * 3 + 16, out_h), (12, 13, 16))
    for i, f_idx in enumerate([16, n_frames // 2, n_frames - 17]):
        strip.paste(pal_frames[f_idx].convert("RGB"), (i * (out_w + 8), 0))
    strip.save("/home/antigravity/.gemini/jetski/brain/v1/default/jetski_remote_agents_3p_devbox/ced1fc68-0dfd-4396-b61e-f7dcbce51de5/scratch/asr_gif_keyframes.png")


if __name__ == "__main__":
    main()
