# LensBridge Remote testing

This file separates checks completed on the development machine from checks that require two real phones. A green desktop build is not evidence that a particular Samsung model accepts a key event or encoder profile.

## Automated results

Development environment: Windows, JDK 17.0.19, Android SDK 36, Gradle 8.14.3.

| Gate | Result | Notes |
| --- | --- | --- |
| Kotlin compilation | Pass | Debug and release variants compiled on 2026-06-27 |
| Unit tests | Pass | 14 variant test executions, 0 failures, 0 errors |
| Android lint | Pass | `lint` completed; no errors |
| Debug APK | Pass | 15,637,077 bytes; signed with Android debug certificate |
| Release APK | Pass, unsigned | 3,477,743 bytes; R8 minified and resources shrunk |
| UI launch smoke | Not run | No connected Android device or installed emulator was available |

## Physical verification checklist

These items remain unchecked until they are run on a controller phone and Samsung target phone.

### Pair and connect

- [ ] Pair on Android 11+ using the temporary six-digit code.
- [ ] Confirm the code is not present in SharedPreferences, files, logs, or backups.
- [ ] Connect using the separate connection port.
- [ ] Confirm model, Android version, and battery are fetched.
- [ ] Close and reopen LensBridge; reconnect without pairing again while the target remains trusted.
- [ ] Restart Wireless debugging and confirm the new-port error is readable.

### Harmless shell checks

- [ ] `getprop ro.product.model`
- [ ] `getprop ro.build.version.release`
- [ ] `settings get system screen_brightness`

### Samsung Camera commands

- [ ] Camera Key (`input keyevent 27`) in Photo mode.
- [ ] Volume Up (`input keyevent 24`) with Samsung Camera configured for shutter.
- [ ] Volume Down (`input keyevent 25`) with Samsung Camera configured for shutter.
- [ ] Photo, Portrait, Video, Portrait Video, Pro, Night, and Expert RAW/Astro where available.
- [ ] Confirm LensBridge never replaces or changes Samsung Camera mode.
- [ ] Confirm files appear in the normal Samsung Gallery location.

### Preview

- [ ] scrcpy server pushes and starts from `/data/local/tmp`.
- [ ] Low: 720 px, 15 fps, 1 Mbps.
- [ ] Balanced: 1024 px, 24 fps, 2 Mbps.
- [ ] Smooth: 1024 px, 30 fps, 3 Mbps.
- [ ] Rotation reconfigures the decoder without losing ADB control.
- [ ] Disconnect stops MediaCodec, shell, and abstract-socket streams cleanly.
- [ ] No audio stream is opened.

### Timer, burst, and failure handling

- [ ] 3, 5, and 10-second timers send exactly one command.
- [ ] Timer cancellation sends no command.
- [ ] 3, 5, and 10-shot bursts send the exact count at about 850 ms intervals.
- [ ] Disable Wi-Fi on each phone in turn; LensBridge reports loss without crashing.
- [ ] Reconnect restores controls.
- [ ] Shutter-only mode works when preview is deliberately broken.

### Performance and polish

- [ ] Ten-minute preview session on a weaker Android 8+ controller.
- [ ] No sustained UI jank or unacceptable heat.
- [ ] Small phone, large phone, landscape, and foldable/tablet-width layouts.
- [ ] TalkBack labels and touch target review.
- [ ] Turn off Wireless debugging after the session.

## Exact commands

```powershell
.\gradlew.bat clean
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Artifacts:

- `dist/LensBridge-Remote-0.1.0-debug.apk`
  - SHA-256: `59D85CF7C0FD578029C292611D99E253DCA3B1A522D22641F0CB00E922B3C585`
- `dist/LensBridge-Remote-0.1.0-release-unsigned.apk`
  - SHA-256: `51851088366FE6AFF3CBFEA6FBF2CE29D43909EE8D20F92D12FEAD5AB6D10907`

The development machine repeatedly held generated Gradle resource folders open when switching build variants. Each verification gate passed from a freshly cleared `app/build` directory; this was a Windows filesystem lock, not a source, test, lint, or packaging failure.
