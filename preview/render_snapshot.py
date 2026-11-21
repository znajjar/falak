#!/usr/bin/env python3
"""Render Falak 5-planet Amman prayer wallpaper frames via headless Chrome WebGL2."""

import base64
import json
import math
from datetime import date, timedelta
from io import BytesIO
from pathlib import Path
from PIL import Image, ImageDraw
from playwright.sync_api import sync_playwright

ROOT = Path("/home/antigravity/falak-wallpaper")
OUT_DIR = ROOT / "renders"
OUT_DIR.mkdir(parents=True, exist_ok=True)


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
    loc = cfg["location"]
    lat_r = math.radians(loc["latitudeDeg"])
    lon = loc["longitudeDeg"]
    tz = loc["utcOffsetHours"]
    jd0 = julian_day(year, month, day) - lon / 360.0

    def calc(approx_h, fn):
        decl, eqt = sun_position(jd0 + approx_h / 24.0)
        noon = 12.0 + tz - lon / 15.0 - eqt
        return fn(noon, decl)

    fajr = calc(5.0, lambda n, d: n - hour_angle_for_alt(lat_r, d, -loc["fajrAngleDeg"]))
    dhuhr = calc(12.3, lambda n, d: n)
    asr = calc(
        15.3,
        lambda n, d: n
        + hour_angle_for_alt(
            lat_r, d, math.degrees(math.atan(1.0 / (loc["asrShadowFactor"] + math.tan(abs(lat_r - d)))))
        ),
    )
    maghrib = calc(17.5, lambda n, d: n + hour_angle_for_alt(lat_r, d, -0.8333))
    isha = calc(19.0, lambda n, d: n + hour_angle_for_alt(lat_r, d, -loc["ishaAngleDeg"]))
    return [fajr, dhuhr, asr, maghrib, isha]


def compute_angles_for_hour(year: int, month: int, day: int, hour_of_day: float, cfg: dict):
    d_today = date(year, month, day)
    d_prev = d_today - timedelta(days=1)
    d_next = d_today + timedelta(days=1)

    p_prev = [h - 24.0 for h in amman_prayers_hours(d_prev.year, d_prev.month, d_prev.day, cfg)]
    p_today = amman_prayers_hours(d_today.year, d_today.month, d_today.day, cfg)
    p_next = [h + 24.0 for h in amman_prayers_hours(d_next.year, d_next.month, d_next.day, cfg)]

    angles = []
    for i in range(5):
        t0, t1 = (p_prev[i], p_today[i]) if hour_of_day < p_today[i] else (p_today[i], p_next[i])
        phase = (hour_of_day - t0) / (t1 - t0)
        if phase >= 0.5:
            phase -= 1.0
        angles.append(phase * 2.0 * math.pi)
    return angles, p_today


def render_batch(vert_src: str, frag_src: str, cfg: dict, jobs: list):
    html = f"""<!DOCTYPE html>
<html>
<body style="margin:0;overflow:hidden;background:#000">
<canvas id="c"></canvas>
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

window.renderOne = (w, h, angles) => {{
  canvas.width = w;
  canvas.height = h;
  gl.viewport(0, 0, w, h);
  gl.useProgram(prog);

  gl.uniform2f(u('uResolution'), w, h);
  gl.uniform1f(u('uTime'), 0.0);

  gl.uniform3f(u('uCamPos'), cfg.camera.position[0], cfg.camera.position[1], cfg.camera.position[2]);
  gl.uniform1f(u('uCamPitch'), cfg.camera.pitchDeg * deg);
  gl.uniform1f(u('uCamYaw'), cfg.camera.yawDeg * deg);
  gl.uniform1f(u('uFovY'), cfg.camera.fovYDeg * deg);

  gl.uniform1f(u('uRingWidth'), cfg.orbits.lineWidthPxAt1024);
  gl.uniform1f(u('uRingBrightness'), cfg.orbits.brightness);

  gl.uniform1fv(u('uPlanetOrbitR'), new Float32Array(cfg.prayers.map(p => p.orbitRadius)));
  gl.uniform1fv(u('uPlanetAngles'), new Float32Array(angles));
  gl.uniform1fv(u('uPlanetRadii'), new Float32Array(cfg.prayers.map(p => p.planetRadius)));

  gl.uniform1f(u('uPlanetDiffuse'), cfg.planetShading.diffuseStrength);
  gl.uniform1f(u('uTerminatorWrap'), cfg.planetShading.terminatorWrap);
  gl.uniform1f(u('uRimStrength'), cfg.planetShading.rimStrength);

  gl.uniform1f(u('uSunCoreR'), cfg.star.coreRadiusNorm);
  gl.uniform1f(u('uSunGlow'), cfg.star.glowIntensity);
  gl.uniform1f(u('uGrainStrength'), cfg.atmosphere.grainStrength);
  gl.uniform1f(u('uStarfieldStrength'), cfg.atmosphere.starfieldStrength);

  gl.drawArrays(gl.TRIANGLES, 0, 3);
  gl.finish();
  return canvas.toDataURL('image/png');
}};
</script>
</body>
</html>"""

    images = []
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
        page = browser.new_page(viewport={"width": 1400, "height": 1400})
        page.set_content(html)
        for w, h, angles in jobs:
            data_url = page.evaluate("([w, h, a]) => window.renderOne(w, h, a)", [w, h, angles])
            png_bytes = base64.b64decode(data_url.split(",", 1)[1])
            images.append(Image.open(BytesIO(png_bytes)).convert("RGB"))
        browser.close()
    return images


def fmt_h(h: float) -> str:
    m = int(round(h * 60))
    return f"{(m // 60) % 24:02d}:{m % 60:02d}"


def main():
    vert_src = (ROOT / "model/falak.vert").read_text()
    frag_src = (ROOT / "model/falak.frag").read_text()
    cfg = json.loads((ROOT / "model/scene_config.json").read_text())

    _, today = compute_angles_for_hour(2026, 11, 21, 12.0, cfg)
    w_dev = cfg["targetDevice"]["widthPx"]
    h_dev = cfg["targetDevice"]["heightPx"]

    sheet_times = [
        (f"Fajr {fmt_h(today[0])}", today[0]),
        (f"Dhuhr {fmt_h(today[1])}", today[1]),
        (f"Asr {fmt_h(today[2])}", today[2]),
        (f"Maghrib {fmt_h(today[3])}", today[3]),
        ("18:15 (Transit)", 18.25),
        (f"Isha {fmt_h(today[4])}", today[4]),
    ]

    jobs = []
    sw, sh = 360, 804
    for _, h_val in sheet_times:
        ang, _ = compute_angles_for_hour(2026, 11, 21, h_val, cfg)
        jobs.append((sw, sh, ang))

    # Native 1280x2856 showcase frame with all 5 planets gracefully staggered across the arches
    jobs.append((w_dev, h_dev, [-0.26, -0.13, 0.0, 0.10, -0.065]))

    imgs = render_batch(vert_src, frag_src, cfg, jobs)
    imgs[-1].save(OUT_DIR / "falak_showcase_1280x2856.png", optimize=True)

    gap = 10
    header = 34
    sheet = Image.new("RGB", (sw * 6 + gap * 5, sh + header), (10, 11, 13))
    draw = ImageDraw.Draw(sheet)
    for idx, ((label, _), im) in enumerate(zip(sheet_times, imgs[:-1])):
        x0 = idx * (sw + gap)
        sheet.paste(im, (x0, header))
        draw.text((x0 + 10, 10), f"Falak · Amman {label}", fill=(215, 218, 226))
    sheet.save(OUT_DIR / "falak_amman_sheet.png")
    print("Saved falak_showcase_1280x2856.png and falak_amman_sheet.png.")


if __name__ == "__main__":
    main()
