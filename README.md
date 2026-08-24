# NaatBook

An offline-first, privacy-focused Android notebook for writing, reading, recording, and organizing Naat, Hamd, and other devotional poetry.

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open Android Studio.
2. Select **Open** and choose the directory containing this project.
3. Let Gradle sync the project.
4. Run the app on an emulator or physical device.

## Build variants

P4f keeps the repository's immutable CI contract intact: CI runs `assembleDebug`
and uploads `app-debug.apk`. That artifact is intentionally a **debug-signed,
release-like test APK**:

- R8 code shrinking and optimization are enabled.
- Unused resources are shrunk.
- `debuggable` is disabled.
- It keeps the regular application ID so it exercises the real upgrade/data path.

Use it for release-like device validation:

```bash
./gradlew assembleDebug
```

For ordinary Android Studio work, use the separate unminified `dev` variant. It
uses an `.dev` application-ID suffix so it can coexist with the test build:

```bash
./gradlew assembleDev
```

The production `release` variant uses the same optimizer policy. It is signed
only when a real upload key is supplied outside the repository through
`KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`:

```bash
./gradlew assembleRelease
```

Without that private key, Gradle can still validate R8 and emits an unsigned
release APK. Never commit a keystore, signing password, or R8 mapping file.

## Build from CI

The **Android APK Build** workflow (`.github/workflows/build.yml`) builds the
optimized `debug` APK on every push to `main`, on pull requests, and on manual
dispatch. You can download the resulting `naatbook-debug-apk` artifact from any
successful workflow run.

Depending on the signing certificate available on a CI runner, Android may
require removal of a previously installed CI debug APK before installing a new
one. Export a backup before uninstalling if Android reports a signature mismatch.

## Verification

Run the repository verification gate before handing off a P4 build:

```bash
./scripts/verify-p4.sh
```

The script runs the focused JVM/Robolectric suite, builds both optimized debug
and release APKs, checks that APKs were emitted, checks patch whitespace, and
fails if the branch contains changes under `.github/workflows/`. Individual
tests can be run with `./gradlew testDebugUnitTest`; reports are written beneath
`app/build/reports/tests/testDebugUnitTest/`.
