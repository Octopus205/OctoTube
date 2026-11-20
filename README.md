# Octo Mouse

Turn a supported Android phone into a Bluetooth HID mouse for a computer, tablet, or other Bluetooth host.

## Interface and controls
- Dark, rounded, scrollable mobile-first interface.
- Large left/right click buttons plus middle click.
- Touchpad: one-finger movement, tap-to-left-click, two-finger tap-to-right-click, and two-finger vertical scrolling.
- Adjustable pointer sensitivity, saved between launches.
- Bluetooth connection panel with paired-device list, refresh, Bluetooth settings shortcut, visibility request, connect, and disconnect controls.

## Pairing
1. Enable Bluetooth on the phone.
2. Open **Pair new device** to open Android Bluetooth settings on the phone.
3. On the computer/tablet/target host, open its Bluetooth pairing screen and search for a new device.
4. If the host cannot see the phone, tap **Make visible** in Octo Mouse and approve the Android visibility prompt.
5. Select the phone on the target host and complete pairing.
6. Return to Octo Mouse, refresh the paired-device list, select the target host, and tap **Connect**.

Pairing and HID connection are separate steps. The target must support a standard Bluetooth HID mouse. On Android hosts, availability of a Bluetooth HID host profile may vary by device.

## Compatibility notes
- Android 9 (API 28) or later is required by this implementation.
- Android's Bluetooth HID Device profile availability depends on the phone's Bluetooth stack and manufacturer firmware. Bluetooth support alone does not guarantee HID Device support.
- This app does not promise compatibility with every phone, computer, tablet, TV, or car system. Real-device testing is required.

## Build
Open the repository in Android Studio and run the `app` configuration, or run:

```bash
gradle assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also builds and uploads a debug APK artifact for successful CI runs.

## Technical reference
- [Android BluetoothHidDevice API](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice)
- [Android BluetoothAdapter API](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter)


## Important runtime behavior
Android automatically unregisters a Bluetooth HID Device app when it is no longer in the foreground. Keep Octo Mouse open on screen while controlling the target device. The app requests the HID profile, registers its mouse descriptor, and sends standard mouse input reports after the host has paired with the phone and the HID connection is active. A successful APK build does not prove that a particular phone firmware exposes the HID Device profile; verify this on the intended phone.
