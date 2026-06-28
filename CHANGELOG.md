# Changelog

## 0.1.1 — 2026-06-28

- Fixed a preview startup race where a failed abstract-socket open reset the same ADB transport that was running the scrcpy server.
- Moved the mirror server and video receiver onto independent authenticated ADB transports.
- Increased the preview startup window and made retries non-destructive.
- Captured scrcpy server output and now show the real preview failure instead of silently replacing it with the generic shutter-only screen.
- Added separate Retry preview and Use shutter-only actions.

## 0.1.0 — 2026-06-27

- Added guided Wireless debugging pairing and connection setup.
- Added encrypted ADB host identity storage with Android Keystore.
- Added Camera Key and volume-key shutter methods.
- Added photo timer, cancellation, paced burst, and video command feedback.
- Added real scrcpy 4.0 H.264 preview with three quality profiles.
- Added shutter-only fallback, reconnect, grid, haptics, and keep-awake controls.
- Added adaptive icon, dark camera-console interface, tests, and verification checklist.
