# Photo Helper

An Android camera coach that measures a live CameraX feed locally, suggests one concrete change, applies supported exposure or device-qualified white-balance changes with one confirmation tap, marks where to tap for supported autofocus, guides physical camera movement, and verifies the result. Optional Qwen visual interpretation through Alibaba Cloud Model Studio is disabled until a user supplies and tests a demo API key.

## Build and test

Requirements: JDK 17 and Android SDK 34.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest # with an API 31+ device or emulator running
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. An Alibaba Cloud Model Studio key is not required for the camera, local coaching, voice, capture, review, or automated tests.

The minified release artifact is intentionally unsigned at `app/build/outputs/apk/release/app-release-unsigned.apk`. Sign it with a user- or CI-owned Android keystore before distribution; no signing key is generated or stored in this repository.

For unattended physical-camera acceptance, first open `test-fixtures/device-stage.html` full-screen on the computer. Position one Android 12/API 31+ phone about 40–80 cm away with its rear camera aimed squarely at the displayed portrait. Connect it by USB, unlock it, enable USB debugging, and approve that connection when Android asks. Keep the normal device lock configured; the runner keeps an already-unlocked phone awake only for the test and turns the screen off afterward. Then run:

```powershell
.\scripts\physical-device-acceptance.ps1
```

The runner ignores emulators and network ADB targets, rejects unauthorized or ambiguous devices and stale APKs, installs the debug/test APKs, and saves a local-only timestamped report under `outputs/qa/physical-device/`. The report fingerprints both APKs but does not retain the raw device serial. Five tests capture/decode from the expected MediaStore album, verify stalled-driver recovery, qualify the active rear lens as standard-wide, enforce the 4 Hz analysis and 10% steady-preview jank gates, require visible apply/reset convergence across five sub-second EV trials, prove chained adjustments reset to the original baseline, require EV/ISO/exposure time from the actual saved-still result, and exercise the complete generic focus recommendation → visible marker → selected preview tap → AF-lock path. They delete the exact test-created MediaStore images and the test package, restore the stay-awake setting, and leave a fresh debug app installed at onboarding.

For safety, the runner stops if Photo Helper is already installed. `-AllowExistingApp` explicitly permits replacement while preserving its private data; the tests can then complete onboarding and leave Camera permission granted.

The stage page starts with the required neutral portrait and hides its controls; number keys 1–6 switch among the synthetic fixtures, and moving the pointer to the top reveals the buttons. The API key is not needed or copied to the device for this hardware gate.

## Verification status

- JVM: all 100 unit tests pass.
- API 34 emulator: all 35 instrumentation tests pass, including MediaStore capture/decode, EV apply/reset, focus metering, focus-target UI, deterministic TalkBack traversal, encrypted key storage, stalled-camera recovery, blocked-camera Retry, atomic Capture Review navigation, microphone error semantics, 512-character key bounding, and 200% font sizing for both Shutter and guidance Cancel.
- API 34 system integration: a clean install exercised the real permission dialog, denial recovery, App Info handoff, permission grant on resume, background/foreground camera recovery, and portrait↔landscape rotation; the shutter returned enabled without a camera-blocked state.
- API 31 AOSP emulator: the same 35 instrumentation tests pass, including strict virtual-camera capture/decode. Redacted XML, hashes, and the lint summary are retained under `outputs/qa/emulator/2026-08-08/`.
- Android lint passes with no errors. Fresh debug/test APKs, a minified unsigned release APK, and an unsigned release bundle build successfully.
- Physical USB camera acceptance is still pending because no authorized phone is visible to ADB. White-balance actions remain hidden until a real camera passes direction and reset qualification.

The repeatable 12-call Qwen contract harness is `scripts/qwen-live-smoke.py`. It reads only `EVALUATION_MODEL_NAME` and `EVALUATION_MODEL_KEY` from `.env`, writes a redacted JSONL report under ignored `outputs/qa/`, and never writes an image or key copy. The 2026-08-07 production-rate run passed 4/12 strict cases; six timed out, one had a network error, and one returned a schema-valid but semantically mismatched face result. Qwen therefore remains optional and fail-closed behind local evidence gates; local coaching, capture, and controls do not depend on it.

## Demo key

Open **Settings**, enter a disposable China (Beijing) Alibaba Cloud Model Studio key, and tap **Test key**. The app stores a non-exportable AES key in Android Keystore and the encrypted API-key ciphertext/IV in private preferences, sends only the comment plus one downscaled live frame or saved photo under review for eligible ambiguous requests, and clears the key on request. Direct client-side API keys are suitable only for this private demo; a distributed build should use a backend proxy.

Face-image inputs and results remain on-device in bundled Google ML Kit. Google separately documents ML Kit SDK collection of device/app information and diagnostic usage metrics; the installed app discloses and links that boundary in Settings.
