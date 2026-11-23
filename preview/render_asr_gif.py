#!/usr/bin/env python3
"""Render a seamless 1280x640 animated GIF showcasing Falak during Asr prayer in Amman.

Matches the 1280x640 social preview dimensions and obsidian starry-crescent backdrop,
framing the live WebGL2 wallpaper inside a centered matte-black phone silhouette with
balanced bilingual branding (FALAK on the left wing, Classical Mushakkal Thuluth فَلَك
on the right wing) as Asr glides into exact center meridian alignment (15:14 Amman time).
"""

import base64
import json
import math
import sys
import xml.etree.ElementTree as ET
from io import BytesIO
from pathlib import Path
from PIL import Image, ImageDraw
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent
OUT_GIF = ROOT / "renders" / "falak_asr_preview.gif"
WORDMARK_XML = ROOT / "android/app/src/main/res/drawable/ic_falak_wordmark.xml"

CANVAS_W, CANVAS_H = 1280, 640
PHONE_W, PHONE_H = 266, 556
BEZEL = 9
SCREEN_W = PHONE_W - 2 * BEZEL  # 248
SCREEN_H = PHONE_H - 2 * BEZEL  # 538
SCREEN_X = (CANVAS_W - SCREEN_W) // 2  # 516
SCREEN_Y = (CANVAS_H - SCREEN_H) // 2  # 51
SCREEN_RADIUS = 31


def load_wordmark_path() -> str:
    """Load the exact 1A Classical Mushakkal Thuluth (فَلَك) pathData from Android resources."""
    tree = ET.parse(WORDMARK_XML)
    ns = {"android": "http://schemas.android.com/apk/res/android"}
    path_el = tree.getroot().find("path")
    assert path_el is not None, f"Missing <path> in {WORDMARK_XML}"
    return path_el.attrib[f"{{{ns['android']}}}pathData"]


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


def build_backdrop_html(wordmark_d: str) -> str:
    """Build the static 1280x640 obsidian sky, arcs, stars, hilal crescents, side branding & centered phone."""
    # Sparse pinpoint stars positioned cleanly in open sky pockets away from text and calligraphy
    stars = [
        (72, 74, 1.1, 0.38), (186, 52, 0.9, 0.28), (336, 78, 1.3, 0.46),
        (446, 134, 1.1, 0.36), (62, 198, 0.9, 0.26), (452, 344, 1.0, 0.32),
        (74, 484, 1.0, 0.30), (192, 548, 1.1, 0.36), (428, 536, 1.2, 0.40),
        (312, 586, 0.85, 0.26), (464, 58, 0.9, 0.26),
        (824, 68, 1.0, 0.34), (924, 108, 1.25, 0.46), (1054, 56, 0.9, 0.28),
        (1176, 84, 1.2, 0.44), (1222, 184, 0.9, 0.28), (836, 266, 1.1, 0.36),
        (1216, 336, 1.0, 0.32), (834, 442, 0.95, 0.30), (934, 528, 1.15, 0.38),
        (1206, 488, 1.2, 0.40), (1088, 584, 0.9, 0.26), (1182, 574, 0.9, 0.26),
    ]
    star_svg = "\n".join(
        f'<circle cx="{x}" cy="{y}" r="{r}" fill="#FFF7E9" opacity="{op}"/>'
        for x, y, r, op in stars
    )

    # Delicate Islamic micro-crescents (hilal) matching model/falak.frag evalCrescentLayer geometry,
    # placed in open sky pockets above and below both side brand lockups
    crescents = [
        (118, 114, 7.8, -26, 0.62),
        (414,  92, 8.6,  32, 0.72),
        (444, 466, 7.2,  24, 0.56),
        (106, 534, 6.2, -22, 0.44),
        (338, 548, 5.4,  38, 0.36),
        (864, 116, 8.6, -30, 0.72),
        (1152, 128, 7.6,  26, 0.62),
        (848, 496, 7.2, -22, 0.56),
        (1188, 424, 6.0, -36, 0.44),
        (1124, 544, 6.4,  28, 0.48),
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
  @font-face {{
    font-family: 'Roboto';
    src: url('file:///opt/flutter/bin/cache/artifacts/material_fonts/Roboto-Light.ttf') format('truetype');
    font-weight: 300;
  }}
  @font-face {{
    font-family: 'Roboto';
    src: url('file:///opt/flutter/bin/cache/artifacts/material_fonts/Roboto-Regular.ttf') format('truetype');
    font-weight: 400;
  }}
  @font-face {{
    font-family: 'Roboto';
    src: url('file:///opt/flutter/bin/cache/artifacts/material_fonts/Roboto-Medium.ttf') format('truetype');
    font-weight: 500;
  }}
  @font-face {{
    font-family: 'LibMono';
    src: url('file:///usr/share/fonts/truetype/liberation/LiberationMono-Regular.ttf') format('truetype');
    font-weight: 400;
  }}

  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  body {{
    width: {CANVAS_W}px;
    height: {CANVAS_H}px;
    overflow: hidden;
    background: #0B0B0D;
    font-family: 'Roboto', sans-serif;
    color: #FFF7E9;
    position: relative;
    -webkit-font-smoothing: antialiased;
  }}
  .bg-layer {{ position: absolute; inset: 0; }}

  /* Symmetric side brand wings flanking the centered phone */
  .wing {{
    position: absolute;
    top: 0;
    height: {CANVAS_H}px;
    width: {phone_left}px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    text-align: center;
    z-index: 2;
    padding: 0 56px;
  }}
  .wing-left {{ left: 0; padding-top: 18px; }}
  .wing-right {{ right: 0; }}

  .eyebrow {{
    font-family: 'LibMono', monospace;
    font-size: 11.5px;
    letter-spacing: 0.20em;
    color: #848B9D;
    text-transform: uppercase;
    margin-bottom: 14px;
  }}
  .latin-wordmark {{
    font-size: 62px;
    font-weight: 500;
    letter-spacing: 0.16em;
    padding-left: 0.16em;
    line-height: 1.0;
    color: #FFF7E9;
  }}
  .hairline {{
    width: 44px;
    height: 1px;
    background: #2C303D;
    margin: 20px 0 18px;
  }}
  .tagline {{
    font-size: 19px;
    font-weight: 300;
    line-height: 1.42;
    color: #CBD1DF;
    max-width: 300px;
  }}
  .meta-line {{
    font-family: 'LibMono', monospace;
    font-size: 11.5px;
    letter-spacing: 0.08em;
    color: #6E7587;
    margin-top: 20px;
  }}

  .arabic-hero {{
    width: 214px;
    height: 167px;
    display: block;
  }}
  .right-sub {{
    font-family: 'LibMono', monospace;
    font-size: 11.5px;
    letter-spacing: 0.18em;
    padding-left: 0.18em;
    color: #8D94A6;
    text-transform: uppercase;
  }}
  .repo-tag {{
    font-family: 'LibMono', monospace;
    font-size: 12px;
    letter-spacing: 0.05em;
    color: #697082;
    margin-top: 12px;
  }}

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
    z-index: 3;
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

  <!-- Left Wing: English Brand Wordmark & Details -->
  <div class="wing wing-left">
    <div class="eyebrow">Android Live Wallpaper</div>
    <div class="latin-wordmark">FALAK</div>
    <div class="hairline"></div>
    <div class="tagline">Celestial prayer-time<br/>live wallpaper</div>
    <div class="meta-line">OpenGL ES 3.0 · LTPO OLED · Offline</div>
  </div>

  <!-- Right Wing: Classical Mushakkal Thuluth Calligraphy (فَلَك) -->
  <div class="wing wing-right">
    <svg class="arabic-hero" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 50">
      <path fill="#FFF7E9" fill-rule="evenodd" d="{wordmark_d}"/>
    </svg>
    <div class="hairline"></div>
    <div class="right-sub">Five Prayer Orbits</div>
    <div class="repo-tag">github.com/znajjar/falak</div>
  </div>

  <!-- Centered Matte-Black Phone Frame -->
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
    preview_only = "--preview-frame" in sys.argv
    vert_src = (ROOT / "model/falak.vert").read_text()
    frag_src = (ROOT / "model/falak.frag").read_text()
    cfg = json.loads((ROOT / "model/scene_config.json").read_text())
    wordmark_d = load_wordmark_path()

    today = amman_prayers_hours(2026, 11, 22, cfg)
    asr_h = today[2]
    print(f"Amman Asr time: {int(asr_h):02d}:{int(round((asr_h % 1) * 60)):02d}")

    rw, rh = SCREEN_W * 2, SCREEN_H * 2
    n_frames = 96
    fps = 20
    duration_ms = int(round(1000 / fps))

    timeline = []
    for k in range(n_frames):
        frac = k / float(n_frames)
        u = 2.0 * frac - 1.0
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
  gl.uniform1f(u('uPrayerActivity'), -1.0);
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

    render_indices = [n_frames // 2] if preview_only else list(range(n_frames))
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

        # Render static 1280x640 obsidian backdrop + side branding + centered phone bezel at 2x retina
        bg_page = browser.new_page(viewport={"width": CANVAS_W, "height": CANVAS_H}, device_scale_factor=2)
        bg_page.set_content(build_backdrop_html(wordmark_d), wait_until="networkidle")
        bg_page.wait_for_timeout(200)
        bg_raw = bg_page.screenshot()
        backdrop = Image.open(BytesIO(bg_raw)).convert("RGB").resize((CANVAS_W, CANVAS_H), Image.Resampling.LANCZOS)
        bg_page.close()

        # Render live WebGL2 shader frames for the centered phone screen
        gl_page = browser.new_page(viewport={"width": rw, "height": rh})
        gl_page.set_content(gl_html)
        for pos, idx in enumerate(render_indices):
            t_sec, angles = timeline[idx]
            data_url = gl_page.evaluate("([t, a]) => window.renderFrame(t, a)", [t_sec, angles])
            im = Image.open(BytesIO(base64.b64decode(data_url.split(",", 1)[1]))).convert("RGBA")
            im_small = im.resize((SCREEN_W, SCREEN_H), Image.Resampling.LANCZOS)
            im_small = Image.alpha_composite(im_small, punch_overlay)
            screen_frames.append(im_small)
            if not preview_only and (pos + 1) % 24 == 0:
                print(f"  Rendered {pos + 1}/{n_frames} frames...")
        browser.close()

    screen_bg_crop = backdrop.crop((SCREEN_X, SCREEN_Y, SCREEN_X + SCREEN_W, SCREEN_Y + SCREEN_H)).convert("RGBA")

    def composite_screen_tile(screen_rgba: Image.Image) -> Image.Image:
        tile = screen_bg_crop.copy()
        tile.paste(screen_rgba, (0, 0), screen_mask)
        return tile.convert("RGB")

    if preview_only:
        preview_out = Path(sys.argv[sys.argv.index("--preview-frame") + 1])
        full = backdrop.copy()
        full.paste(composite_screen_tile(screen_frames[0]), (SCREEN_X, SCREEN_Y))
        full.save(preview_out, optimize=True)
        print(f"Saved preview frame: {preview_out}")
        return

    center_full = backdrop.copy()
    center_full.paste(composite_screen_tile(screen_frames[n_frames // 2]), (SCREEN_X, SCREEN_Y))
    master_palette = center_full.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
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
