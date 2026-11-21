# Falak — Celestial Prayer-Time Live Wallpaper

An original OpenGL ES 3.0 Android live wallpaper designed for the **Google Pixel 10 Pro** (`1280 × 2856`, 20:9, 495 PPI) that visualizes daily Islamic prayer times as five orbiting planets around a rising star.

## Concept & Visual Design
- **Rising Star**: A half-screen-width incandescent ivory star rises 35% above the bottom edge of the screen against a deep obsidian-grey sky (`#0B0B0D` → `#111214`) with sparse distant starlight.
- **Five Active Prayer Orbits**: Only the five prayer orbits (**Fajr**, **Dhuhr**, **Asr**, **Maghrib**, **Isha**) arc upward across the screen, intersected by a delicate vertical meridian axis and alignment pips.
- **Continuous ~24h Orbits (Amman, Jordan)**: Powered by an offline astronomical solar calculator (`31.9539° N, 35.9106° E`, `Asia/Amman` UTC+3, Jordan Ministry of Awqaf `18° / 18°`, Standard Asr). Each planet interpolates continuously between consecutive days' exact prayer times—adapting smoothly to day-to-day shifts with zero midnight jump—and crosses dead center at its prayer time.
- **LTPO OLED Battery Friendly**: Renders at `1 Hz` while visible (matching Pixel 10 Pro's low-power LTPO mode) and sleeps at `0 Hz` when hidden or screen-off.

## Project Layout
- `model/falak.vert` & `model/falak.frag` — Portable GLSL ES 3.00 analytic 3D shader (Android OpenGL ES 3.0 & WebGL2)
- `model/scene_config.json` — Scene & Amman prayer calculation configuration
- `preview/` — Interactive WebGL2 browser preview and headless snapshot renderer
- `android/` — Pure Android framework Gradle project (`com.falak.livewallpaper`, `minSdk = 29`, `targetSdk = 35`)

## Building

```bash
cd android
gradle assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
