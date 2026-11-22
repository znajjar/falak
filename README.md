# Falak — Celestial Prayer-Time Live Wallpaper

An original OpenGL ES 3.0 Android live wallpaper designed for the **Google Pixel 10 Pro** (`1280 × 2856`, 20:9, 495 PPI) that visualizes daily Islamic prayer times anywhere in the world as five orbiting planets around a rising star.

<p align="center">
  <img src="renders/falak_asr_preview.gif" alt="Falak Live Wallpaper — Asr Prayer Alignment (Amman)" width="300" />
  <br />
  <sub><em>Asr prayer alignment (15:14, Amman) — planet crossing the center meridian as the starfield gently sprinkles</em></sub>
</p>

## Download
- **[Download Latest APK (v1.1.0)](https://github.com/znajjar/falak/raw/v1.1.0/dist/falak-pixel10pro.apk)** · **[All Releases](https://github.com/znajjar/falak/releases)**

## Features & Visual Design
- **Worldwide Manual Location Selector (Zero Permissions)**: Pick manually from **145 built-in world cities** (searchable offline) or enter **custom coordinates & IANA timezone** for any location on Earth—with **zero location or internet permissions** requested.
- **Selectable Calculation Sources**: Switch between **Jordan Ministry of Awqaf** (`Fajr 18° / Isha 18°`, Standard Asr) and **Muslim World League (MWL)** (`Fajr 18° / Isha 17°`, Standard Asr). Also accessible directly via Pixel's *Wallpaper & style* settings gear.
- **Continuous ~24h Orbits Anywhere on Earth**: Powered by an offline astronomical solar solver with full IANA timezone/DST support and continuous angle-based high-latitude twilight handling. Each planet interpolates continuously across consecutive days—adapting smoothly with zero midnight jump—and crosses dead center at exact prayer time.
- **Rising Star**: A half-screen-width incandescent ivory star rises 35% above the bottom edge of the screen with a lush `1.5×` corona glow against a deep obsidian-grey sky (`#0B0B0D` → `#111214`).
- **Native-Pixel Starfield & Prayer-Time Sprinkle**: Sparse, varied-size pinpoint stars (`0.48–2.75 px`) rendered directly in physical pixels with `1×1` OLED micro-dither. Around each prayer time, the starfield softly sprinkles with twinkling diamond pinpoints and micro-glints.
- **Five Active Prayer Orbits**: Only the five prayer orbits (**Fajr**, **Dhuhr**, **Asr**, **Maghrib**, **Isha**) arc upward across the screen, intersected by a delicate vertical meridian axis, alignment pips, and localized orbital trails.
- **Adaptive LTPO OLED Refresh**: Renders at `1 Hz` while idle, ramps smoothly to `30 FPS` during prayer-time sprinkle windows, and sleeps at `0 Hz` when hidden or screen-off.

## Project Layout
- `model/falak.vert` & `model/falak.frag` — Portable GLSL ES 3.00 analytic 3D shader (Android OpenGL ES 3.0 & WebGL2)
- `model/scene_config.json` — Scene & calculation source configuration
- `preview/` — Interactive WebGL2 browser preview (with manual location & source selectors), snapshot renderer, and Asr GIF generator
- `android/` — Pure Android framework Gradle project (`com.falak.livewallpaper`, `minSdk = 29`, `targetSdk = 35`, zero external dependencies)

## Building

```bash
cd android
gradle assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
