<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/e8113340-a7d3-4c41-b8fa-ea4b6d3ba0f9

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Run the app on an emulator or physical device

## Build from CI

The **Android APK Build** workflow (`.github/workflows/build.yml`) builds a debug
APK on every push to `main`, on pull requests, and on manual dispatch. You can
download the resulting `naatbook-debug-apk` artifact from any successful
workflow run, or build it yourself with `./gradlew assembleDebug`.
