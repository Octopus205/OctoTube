# Octo Mouse

Turn a supported Android phone into a Bluetooth mouse for a paired computer, tablet, or other Bluetooth HID host.

## Current MVP
- Native Android app (Kotlin).
- Bluetooth HID Device profile (Android API 28+).
- Pair a target device in Android Bluetooth settings, then connect from Octo Mouse.
- Touchpad movement and left/right click reports.

## Compatibility
Android exposes the Bluetooth HID Device profile starting at API 28 (Android 9). Actual availability depends on the phone's Bluetooth stack and manufacturer firmware. Bluetooth support alone does not guarantee HID Device support. The app checks whether the HID profile can be opened and registered.

The first version targets Android 9+ and is intended for real-device testing. It does not promise compatibility with every Android phone, computer, tablet, TV, or car system.

## Build
Open this repository in Android Studio and run the `app` configuration, or use:

```bash
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Roadmap
- Smooth pointer acceleration and sensitivity controls.
- Scroll gestures and multi-touch.
- Keyboard and media-control layouts.
- Remember paired hosts and reconnect.
- Better device capability diagnostics and UI polish.

## Technical reference
- [Android BluetoothHidDevice API](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice)
