# Falak — Celestial Prayer-Time Live Wallpaper

An original OpenGL ES 3.0 Android live wallpaper designed for the **Google Pixel 10 Pro** (`1280 × 2856`, 20:9, 495 PPI) that visualizes daily Islamic prayer times as five orbiting planets around a rising star.

<p align="center">
  <img src="renders/falak_asr_preview.gif" alt="Falak Live Wallpaper — Asr Prayer Alignment (Amman)" width="300" />
  <br />
  <sub><em>Asr prayer alignment (15:14, Amman) — planet crossing the center meridian as the starfield gently sprinkles</em></sub>
</p>

## Download
- **[Download Latest APK (v1.0.2)](https://github.com/znajjar/falak/raw/v1.0.2/dist/falak-pixel10pro.apk)** · **[All Releases](https://github.com/znajjar/falak/releases)**

## Concept & Visual Design
- **Rising Star**: A half-screen-width incandescent ivory star rises 35% above the bottom edge of the screen with a lush `1.5×` corona glow against a deep obsidian-grey sky (`#0B0B0D` → `#111214`).
- **Native-Pixel Starfield & Prayer-Time Sprinkle**: Sparse, varied-size pinpoint stars (`0.48–2.75 px`) rendered directly in physical pixels with `1×1` OLED micro-dither. Around each prayer time, the starfield softly sprinkles with twinkling diamond pinpoints and micro-glints.
- **Five Active Prayer Orbits**: Only the five prayer orbits (**Fajr**, **Dhuhr**, **Asr**, **Maghrib**, **Isha**) arc upward across the screen, intersected by a delicate vertical meridian axis, alignment pips, and localized orbital trails.
- **Continuous ~24h Orbits (Amman, Jordan)**: Powered by an offline astronomical solar calculator (`31.9539° N, 35.9106° E`, `Asia/Amman` UTC+3, Jordan Ministry of Awqaf `18° / 18°`, Standard Asr). Each planet interpolates continuously between consecutive days' exact prayer times—adapting smoothly to day-to-day shifts with zero midnight jump—and crosses dead center at its prayer time.
- **Adaptive LTPO OLED Refresh**: Renders at `1 Hz` while idle, ramps smoothly to `30 FPS` during prayer-time sprinkle windows, and sleeps at `0 Hz` when hidden or screen-off.

## Project Layout
- `model/falak.vert` & `model/falak.frag` — Portable GLSL ES 3.00 analytic 3D shader (Android OpenGL ES 3.0 & WebGL2)
- `model/scene_config.json` — Scene & Amman prayer calculation configuration
- `preview/` — Interactive WebGL2 browser preview, snapshot renderer, and Asr GIF generator
- `android/` — Pure Android framework Gradle project (`com.falak.livewallpaper`, `minSdk = 29`, `targetSdk = 35`)

## Building

```bash
cd android
gradle assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
