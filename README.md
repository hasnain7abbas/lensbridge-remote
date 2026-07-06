# LensBridge Remote

LensBridge uses a spare Android phone as a live viewfinder and shutter remote for a Samsung phone on a tripod. Samsung Camera or Expert RAW remains open on the target phone, so its camera modes and image processing are unchanged.

The connection stays on the local Wi-Fi network through Android's user-approved Wireless debugging feature. The app has no account system, cloud service, analytics, or advertising SDK.

> **Current status:** builds, unit tests, and lint pass. Pairing, key events, and live preview still need verification on two physical Android phones. See [TESTING.md](TESTING.md).

## Features

- Guided six-step setup for Android 11+ Wireless debugging
- Pairing-code flow with strict input validation; the temporary code is never saved
- Manual connection-port fallback, reconnect, disconnect, and forget-device controls
- ADB host identity encrypted by a non-exportable Android Keystore AES key
- Camera Key, Volume Down, and Volume Up shutter methods
- Off, 3, 5, and 10-second timers with cancellation
- Paced 3, 5, and 10-shot bursts
- Video command reports when the command is sent without assuming the target started recording
- Real scrcpy 4.0 H.264 screen stream decoded with Android MediaCodec
- Low, Balanced, and Smooth preview profiles
- Shutter-only fallback, local composition grid, haptics, and keep-screen-awake
- A compact dark controller designed for both narrow phones and wide/foldable screens

## First use

1. Put both phones on the same normal Wi-Fi network.
2. On the Samsung phone, enable Developer options and **Wireless debugging**.
3. Tap **Pair device with pairing code** and keep that dialog open.
4. In LensBridge, enter the IP address, pairing port, and current six-digit code.
5. Return to the main Wireless debugging screen. Enter its separate connection port.
6. Open Samsung Camera or Expert RAW yourself and choose the mode you want.
7. Open the remote. If Camera Key does nothing, set Samsung Camera's volume-key action to shutter and select the matching trigger in LensBridge settings.

The pairing port and connection port are different. Android may change the connection port whenever Wireless debugging restarts.

## Preview profiles

| Profile | Long edge | Frame rate | Bitrate |
| --- | ---: | ---: | ---: |
| Low | 720 px | 15 fps | 1 Mbps |
| Balanced | 1024 px | 24 fps | 2 Mbps |
| Smooth | 1024 px | 30 fps | 3 Mbps |

Audio is always disabled. The app bundles the matching, checksum-verified scrcpy 4.0 server and pushes it temporarily to `/data/local/tmp` through ADB for each preview session.

## Troubleshooting

- **Pairing failed:** generate a new code and keep the pairing dialog visible until LensBridge finishes.
- **Connection failed after pairing:** use the port on the main Wireless debugging screen, not the pairing port.
- **Camera did not react:** try Volume Down or Volume Up in Remote settings and confirm the volume-key action inside Samsung Camera.
- **Preview failed:** switch to shutter-only mode. Controls do not depend on the video stream.
- **Hotspot trouble:** try a normal Wi-Fi network first; hotspot isolation varies by Android version and manufacturer.
- **Reconnect stopped working:** Android probably changed its ADB connection port. Forget the saved phone and enter the new endpoint.

## Privacy and security

LensBridge requests network-state, Wi-Fi-state, multicast, internet-socket, and vibration permissions only. “Internet” is Android's permission name for opening network sockets; the app has no remote service configured and uses the endpoint you enter on the local network.

The six-digit pairing code stays in memory only. The long-lived ADB private key is encrypted at rest with Android Keystore and excluded from backup. Forgetting the phone removes its saved endpoint; clearing app data or removing the app removes the host identity. Turn off Wireless debugging when the session is over.

## Build

Requirements: JDK 17 and Android SDK 36.

```powershell
.\gradlew.bat clean
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Ready-to-use debug APK: `dist/LensBridge-Remote-0.1.1-debug.apk` (15.6 MB).

Minified unsigned release APK: `dist/LensBridge-Remote-0.1.1-release-unsigned.apk` (3.5 MB).

The debug APK is signed with the local Android debug certificate and installs normally. The release task produces an unsigned, minified APK unless a signing configuration is supplied locally.

## Architecture

The app is Kotlin + Jetpack Compose with a single state-driven activity. Kadb handles Android-to-Android ADB pairing, shell, sync push, and abstract-socket streams. The mirror client starts the bundled scrcpy 4.0 server with video only, parses its stream/session headers, and queues H.264 packets into MediaCodec without a buffering layer. Camera control remains independent ADB shell input so shutter-only mode continues to work when video is unavailable.

## License

LensBridge Remote is GPL-3.0-or-later because the ADB pairing dependency graph includes `spake2-java`. Kadb and scrcpy are Apache-2.0 projects. Full attributions are in [NOTICE](NOTICE).
