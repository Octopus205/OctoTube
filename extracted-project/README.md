# OctoTube

OctoTube is an Android video discovery and playback app built with Kotlin and Jetpack Compose. The project currently includes multi-source extractors (Piped, Invidious, PeerTube), Media3 playback, subscriptions, downloads, local persistence, and SponsorBlock integration.

## Build locally

1. Open this folder in Android Studio.
2. Allow Gradle sync to finish.
3. Select the `app` configuration and run it on an Android device (Android 7.0/API 24 or newer).
4. To build a debug APK, run `./gradlew assembleDebug`.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

Push to `main` or run the **Android CI** workflow manually. It runs unit tests, builds the debug APK, and uploads `octotube-debug-apk` as a workflow artifact.

## Notes

This is an independent client project, not an official YouTube application. Availability and reliability of third-party extractor instances can change. No API keys or private credentials are included.
