# Contributing to IDSheet Studio

Thanks for helping improve IDSheet Studio! Bug reports, feature ideas, translations and pull
requests are all welcome.

## Getting started

1. Install **Android Studio** (latest stable) or a **JDK 21** plus the Android SDK
   (platform `android-37.0`, build-tools `37.0.0`).
2. Clone the repository and open it in Android Studio, or build from the command line:

   ```bash
   ./gradlew assembleDebug          # debug APK in app/build/outputs/apk/debug/
   ./gradlew testDebugUnitTest      # JVM unit tests
   ./gradlew lintDebug              # Android Lint (must report no errors)
   ./gradlew connectedDebugAndroidTest   # instrumentation tests on a device/emulator
   ```

3. Tests annotated `@RequiresLocalFixtures` (live remove.bg, real update APKs) are skipped in CI.
   To run the live remove.bg test, save your key in the debug app or pass it as a runner
   argument. Never commit it:

   ```bash
   ./gradlew connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.removeBgApiKey="$REMOVE_BG_API_KEY"
   ```

## Project structure

| Package | Responsibility |
|---|---|
| `core` | Units (mm/cm/in, points, pixels) and geometry |
| `domain.model` | Photos, presets, paper sizes, sheet settings |
| `domain.layout` | Packing engine (`LayoutEngine`) and device-independent drawing ops (`SheetComposer`) |
| `pdf` | Dependency-free PDF writer, encoder, read-back inspector, export and printing |
| `image` | Decoding with EXIF orientation, crop math and print rendering |
| `data` | Room database, DataStore settings, Keystore encryption, remove.bg and GitHub clients, updates |
| `ui` | Jetpack Compose screens (MVVM) |

The on-screen preview and the PDF both render the same `SheetOp` list, so anything you change in
`SheetComposer` appears identically in both.

## Guidelines

- Keep all measurements in **millimetres** internally; convert only for display.
- Never stretch photos. Layout changes must keep `LayoutEngine.validate` returning no problems;
  add a unit test for every new packing behaviour.
- Never commit secrets (API keys, keystores, `keystore.properties`, `local.properties`).
- Follow the existing Kotlin style (`kotlin.code.style=official`) and keep lint clean.
- Write clear commit messages in the imperative mood ("Add Letter landscape preset").

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Make your change with tests.
3. Make sure `./gradlew lintDebug testDebugUnitTest assembleDebug` passes. CI runs the same
   commands plus instrumentation tests on an emulator for every push and pull request.
4. Open the pull request with a description of what changed and why. Add screenshots for UI
   changes.

## Releasing (maintainers)

1. Move the `[Unreleased]` notes in `CHANGELOG.md` into a new `## [X.Y.Z] - YYYY-MM-DD` section.
2. Commit, then tag and push: `git tag vX.Y.Z && git push origin vX.Y.Z`.
3. The **Release** workflow creates the GitHub release from the changelog section, builds and
   signs the APK, verifies its signature and version, and attaches the APK and its SHA-256
   checksum. Releases created on github.com are handled the same way.

Version codes are derived from the version (`MAJOR*10000 + MINOR*100 + PATCH`) and the workflow
refuses a release whose version code is not higher than every previous release.

## Code of conduct

Be respectful and constructive. Harassment of any kind is not tolerated.
