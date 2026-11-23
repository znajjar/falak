#!/usr/bin/env python3
"""Render a seamless 1280x640 animated GIF showcasing Falak during Asr prayer in Amman.

Matches the 1280x640 social preview dimensions and obsidian starry-crescent backdrop,
framing the live WebGL2 wallpaper inside a centered matte-black phone silhouette as
Asr glides into exact center meridian alignment (15:14 Amman time), lingers while the
star gently pulsates and the starfield & hilal crescents shimmer, then exits smoothly.
"""

import base64
import json
import math
from io import BytesIO
from pathlib import Path
from PIL import Image, ImageDraw
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent
OUT_GIF = ROOT / "renders" / "falak_asr_preview.gif"

CANVAS_W, CANVAS_H = 1280, 640
PHONE_W, PHONE_H = 266, 556
BEZEL = 9
SCREEN_W = PHONE_W - 2 * BEZEL  # 248
SCREEN_H = PHONE_H - 2 * BEZEL  # 538
SCREEN_X = (CANVAS_W - SCREEN_W) // 2  # 516
SCREEN_Y = (CANVAS_H - SCREEN_H) // 2  # 51
SCREEN_RADIUS = 31


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


def build_backdrop_html() -> str:
    """Build the static 1280x640 obsidian sky, concentric arcs, stars, hilal crescents & centered phone shell."""
    # Sparse pinpoint stars balanced symmetrically around the centered phone (x in [507, 773])
    stars = [
        (76, 82, 1.1, 0.40), (168, 52, 0.9, 0.28), (274, 118, 1.3, 0.48),
        (364, 64, 0.9, 0.28), (446, 152, 1.1, 0.36), (112, 238, 0.9, 0.26),
        (216, 294, 1.35, 0.50), (388, 268, 0.9, 0.28), (452, 376, 1.0, 0.32),
        (84, 424, 1.0, 0.30), (192, 488, 1.1, 0.36), (326, 446, 0.9, 0.26),
        (424, 558, 1.2, 0.40), (138, 578, 0.9, 0.25), (308, 588, 0.85, 0.24),
        (838, 72, 1.0, 0.34), (918, 134, 1.25, 0.46), (1026, 58, 0.9, 0.28),
        (1148, 86, 1.2, 0.44), (1216, 174, 0.9, 0.28), (854, 248, 1.3, 0.48),
        (962, 298, 0.9, 0.26), (1094, 242, 1.1, 0.38), (1192, 326, 1.0, 0.32),
        (828, 412, 0.95, 0.30), (936, 466, 1.15, 0.38), (1064, 434, 0.9, 0.26),
        (1214, 462, 1.2, 0.40), (886, 568, 1.0, 0.32), (1042, 584, 0.9, 0.26),
        (1168, 566, 0.9, 0.26),
    ]
    star_svg = "\n".join(
        f'<circle cx="{x}" cy="{y}" r="{r}" fill="#FFF7E9" opacity="{op}"/>'
        for x, y, r, op in stars
    )

    # Delicate Islamic micro-crescents (hilal) matching model/falak.frag evalCrescentLayer geometry
    # Balanced cleanly on left & right obsidian sky wings around the centered phone
    crescents = [
        (142, 148, 8.2, -26, 0.66),
        (338, 174, 9.0,  32, 0.74),
        (256, 374, 6.2, -40, 0.48),
        (414, 462, 7.6,  24, 0.60),
        (104, 514, 6.4, -22, 0.44),
        (312, 542, 5.4,  38, 0.36),
        (932, 162, 9.0, -30, 0.74),
        (1118, 152, 7.8,  26, 0.64),
        (1018, 356, 6.4,  42, 0.48),
        (874, 482, 7.4, -22, 0.58),
        (1176, 392, 5.8, -36, 0.42),
        (1134, 526, 6.8,  28, 0.50),
    ]
    crescent_defs = []
    crescent_uses = []
    for idx, (cx, cy, r, tilt, op) in enumerate(crescents):
        mid = f"hm{idx}"
        r_in = r * 0.96
        dy = -0.32 * r
        crescent_defs.append(
            f'<mask id="{mid}"><rect x="{-r*2:.2f}" y="{-r*2:.2f}" width="{r*4:.2f}" height="{r*4:.2f}" fill="#fff"/>'
            f'<circle cx="0" cy="{dy:.3f}" r="{r_in:.3f}" fill="#000"/></mask>'
        )
        crescent_uses.append(
            f'<g transform="translate({cx},{cy}) rotate({tilt})" opacity="{op}">'
            f'<circle cx="0" cy="0" r="{r:.3f}" fill="#FAF5EC" mask="url(#{mid})"/></g>'
        )

    # Subtle concentric background orbital arcs centered symmetrically below the centered phone
    bg_arcs_svg = "\n".join(
        f'<circle cx="640" cy="760" r="{r}" fill="none" stroke="#FFF7E9" stroke-opacity="{op}" stroke-width="1"/>'
        for r, op in [(310, 0.075), (430, 0.065), (555, 0.055), (685, 0.042), (820, 0.032)]
    )

    phone_left = (CANVAS_W - PHONE_W) // 2
    phone_top = (CANVAS_H - PHONE_H) // 2

    return f"""<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8"/>
<style>
  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  body {{
    width: {CANVAS_W}px;
    height: {CANVAS_H}px;
    overflow: hidden;
    background: #0B0B0D;
    position: relative;
  }}
  .bg-layer {{ position: absolute; inset: 0; }}
  .phone {{
    position: absolute;
    left: {phone_left}px;
    top: {phone_top}px;
    width: {PHONE_W}px;
    height: {PHONE_H}px;
    border-radius: 40px;
    background: #070709;
    border: 1.5px solid #303441;
    padding: {BEZEL - 1}px;
    box-shadow: 0 24px 64px rgba(0, 0, 0, 0.72);
  }}
  .btn-power {{
    position: absolute; right: -3px; top: 134px; width: 2.5px; height: 40px;
    background: #303441; border-radius: 0 2px 2px 0;
  }}
  .btn-vol {{
    position: absolute; right: -3px; top: 194px; width: 2.5px; height: 70px;
    background: #303441; border-radius: 0 2px 2px 0;
  }}
  .screen-well {{
    width: 100%;
    height: 100%;
    border-radius: {SCREEN_RADIUS}px;
    background: #0B0B0D;
    border: 1px solid #181A21;
  }}
</style>
</head>
<body>
  <svg class="bg-layer" width="{CANVAS_W}" height="{CANVAS_H}" viewBox="0 0 {CANVAS_W} {CANVAS_H}" xmlns="http://www.w3.org/2000/svg">
    <defs>{''.join(crescent_defs)}</defs>
    {bg_arcs_svg}
    {star_svg}
    {''.join(crescent_uses)}
  </svg>
  <div class="phone">
    <div class="btn-power"></div>
    <div class="btn-vol"></div>
    <div class="screen-well"></div>
  </div>
</body>
</html>"""


def build_screen_mask(w: int, h: int, radius: int) -> Image.Image:
    """Create a 4x supersampled anti-aliased rounded-rectangle alpha mask for the phone screen."""
    scale = 4
    m = Image.new("L", (w * scale, h * scale), 0)
    draw = ImageDraw.Draw(m)
    draw.rounded_rectangle(
        [0, 0, w * scale - 1, h * scale - 1],
        radius=radius * scale,
        fill=255,
    )
    return m.resize((w, h), Image.Resampling.LANCZOS)


def build_punch_hole_overlay(w: int, h: int) -> Image.Image:
    """Create a crisp 4x supersampled top-center punch-hole camera overlay for the phone screen."""
    scale = 4
    ov = Image.new("RGBA", (w * scale, h * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(ov)
    cx = (w * scale) // 2
    cy = int((13 + 5.5) * scale)
    r_outer = int(6.2 * scale)
    r_inner = int(5.3 * scale)
    draw.ellipse([cx - r_outer, cy - r_outer, cx + r_outer, cy + r_outer], fill=(255, 255, 255, 16))
    draw.ellipse([cx - r_inner, cy - r_inner, cx + r_inner, cy + r_inner], fill=(5, 5, 7, 255))
    return ov.resize((w, h), Image.Resampling.LANCZOS)


def main():
    vert_src = (ROOT / "model/falak.vert").read_text()
    frag_src = (ROOT / "model/falak.frag").read_text()
    cfg = json.loads((ROOT / "model/scene_config.json").read_text())

    today = amman_prayers_hours(2026, 11, 22, cfg)
    asr_h = today[2]
    print(f"Amman Asr time: {int(asr_h):02d}:{int(round((asr_h % 1) * 60)):02d}")

    # Render WebGL2 phone screen at 2x supersampling (496x1076) and downsample cleanly to 248x538
    rw, rh = SCREEN_W * 2, SCREEN_H * 2
    n_frames = 96
    fps = 20
    duration_ms = int(round(1000 / fps))

    timeline = []
    for k in range(n_frames):
        frac = k / float(n_frames)
        u = 2.0 * frac - 1.0  # [-1, +1)
        offset_h = 0.98 * (0.20 * u + 0.80 * (u ** 3))
        hour = asr_h + offset_h
        t_sec = k / float(fps)
        timeline.append((t_sec, angles_for_hour(hour, today)))

    gl_html = f"""<!DOCTYPE html>
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
gl.uniform1f(u('uStarSize'), cfg.atmosphere.starSizeScale);
gl.uniform1f(u('uStarDensity'), cfg.atmosphere.starDensityScale);
gl.uniform1f(u('uGlitterSpeed'), cfg.atmosphere.glitterSpeed);

window.renderFrame = (tSec, angles) => {{
  gl.uniform1f(u('uTime'), tSec);
  gl.uniform1f(u('uPulseTime'), tSec);
  gl.uniform1fv(u('uPlanetAngles'), new Float32Array(angles));
  gl.drawArrays(gl.TRIANGLES, 0, 3);
  gl.finish();
  return canvas.toDataURL('image/png');
}};
</script>
</body>
</html>"""

    screen_mask = build_screen_mask(SCREEN_W, SCREEN_H, SCREEN_RADIUS)
    punch_overlay = build_punch_hole_overlay(SCREEN_W, SCREEN_H)

    screen_frames = []
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

        # 1. Render static 1280x640 obsidian backdrop + centered phone bezel at 2x retina
        bg_page = browser.new_page(viewport={"width": CANVAS_W, "height": CANVAS_H}, device_scale_factor=2)
        bg_page.set_content(build_backdrop_html(), wait_until="networkidle")
        bg_raw = bg_page.screenshot()
        backdrop = Image.open(BytesIO(bg_raw)).convert("RGB").resize((CANVAS_W, CANVAS_H), Image.Resampling.LANCZOS)
        bg_page.close()

        # 2. Render all 96 live WebGL2 shader frames for the centered phone screen
        gl_page = browser.new_page(viewport={"width": rw, "height": rh})
        gl_page.set_content(gl_html)
        for idx, (t_sec, angles) in enumerate(timeline):
            data_url = gl_page.evaluate("([t, a]) => window.renderFrame(t, a)", [t_sec, angles])
            im = Image.open(BytesIO(base64.b64decode(data_url.split(",", 1)[1]))).convert("RGBA")
            im_small = im.resize((SCREEN_W, SCREEN_H), Image.Resampling.LANCZOS)
            im_small = Image.alpha_composite(im_small, punch_overlay)
            screen_frames.append(im_small)
            if (idx + 1) % 24 == 0:
                print(f"  Rendered {idx + 1}/{n_frames} frames...")
        browser.close()

    # Composite the static phone bezel region around the screen so rounded corners anti-alias seamlessly
    screen_bg_crop = backdrop.crop((SCREEN_X, SCREEN_Y, SCREEN_X + SCREEN_W, SCREEN_Y + SCREEN_H)).convert("RGBA")

    def composite_screen_tile(screen_rgba: Image.Image) -> Image.Image:
        tile = screen_bg_crop.copy()
        tile.paste(screen_rgba, (0, 0), screen_mask)
        return tile.convert("RGB")

    # Build ONE shared 256-color optimal palette from the full center Asr frame so background + bezel + screen
    # share identical colors with zero frame-to-frame flicker.
    center_full = backdrop.copy()
    center_full.paste(composite_screen_tile(screen_frames[n_frames // 2]), (SCREEN_X, SCREEN_Y))
    master_palette = center_full.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)

    # Quantize static backdrop ONCE so pixels outside the phone screen are bit-for-bit identical across all frames!
    backdrop_pal = backdrop.quantize(palette=master_palette, dither=Image.Dither.FLOYDSTEINBERG)

    pal_frames = []
    for sf in screen_frames:
        tile_pal = composite_screen_tile(sf).quantize(palette=master_palette, dither=Image.Dither.FLOYDSTEINBERG)
        frame = backdrop_pal.copy()
        frame.paste(tile_pal, (SCREEN_X, SCREEN_Y))
        pal_frames.append(frame)

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
    print(f"Saved {OUT_GIF} ({CANVAS_W}x{CANVAS_H}, {n_frames} frames @ {fps} FPS, {size_kb:.1f} KB)")


if __name__ == "__main__":
    main()
