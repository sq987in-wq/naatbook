# NaatBook

An offline-first, privacy-focused Android notebook for writing, reading, recording, and organizing Naat, Hamd, and other devotional poetry.

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Let Gradle sync the project.
4. Run the app on an emulator or physical device.

## Build from CI

The **Android APK Build** workflow (`.github/workflows/build.yml`) builds a debug
APK on every push to `main`, on pull requests, and on manual dispatch. You can
download the resulting `naatbook-debug-apk` artifact from any successful
workflow run, or build it yourself with `./gradlew assembleDebug`.

## Verification

Run the repository verification gate before handing off a P4 build:

```bash
./scripts/verify-p4.sh
```

The script runs the focused JVM/Robolectric suite, builds the debug APK, checks
patch whitespace, and fails if the branch contains changes under
`.github/workflows/`. Individual tests can be run with
`./gradlew testDebugUnitTest`; reports are written beneath
`app/build/reports/tests/testDebugUnitTest/`.
