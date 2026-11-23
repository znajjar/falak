# Repository Guidelines for Contributors & Agents

## APK Signing & Releases (Important)
- **Never publish or distribute APKs signed with a local/ephemeral `~/.android/debug.keystore`.** Each machine or agent VM generates a different random debug key, which breaks in-place Android updates (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- Official signed APKs are built automatically by [`.github/workflows/build-release.yml`](.github/workflows/build-release.yml) using the repository's `RELEASE_KEYSTORE` GitHub Actions secret:
  - Every push to `main` builds a consistently signed release APK and uploads it as the `falak-release-apk` workflow artifact.
  - Pushing a version tag (`vX.Y.Z`)—or manually triggering the workflow with `release_tag`—builds, verifies, and attaches `falak.apk` directly to the GitHub Release.
- Never commit `.jks`, `.keystore`, `.p12`, or `android/keystore.properties` files.

## Project Invariants
- **Zero Android system/user permissions**: Never request location, internet, or any `android.permission.*` entry (only AndroidX's internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is expected).
- **Android 11–15 / One UI safety**: In `MainActivity.onCreate()`, always configure window/insets controllers **after** `setContent` / attaching the decor view (never before `decorView` exists).
- **Shader sync**: Keep `model/falak.frag`, `android/app/src/main/res/raw/falak_frag.glsl`, and `preview/index.html` in sync whenever editing the GLSL shader.
- **Prayer calibration**: Preserve the two calculation sources (`Jordan Ministry of Awqaf` and `MWL`) and exact Hashemite calendar calibration.
