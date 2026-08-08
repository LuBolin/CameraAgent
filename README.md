# Photo Helper

An Android camera coach that measures a live CameraX feed locally, suggests one concrete change, applies supported exposure, digital-zoom, or device-qualified white-balance changes with one confirmation tap, shows a user-selected tap-to-focus marker, guides physical camera movement, and verifies the result. Generic focus never requires a face. Optional strict Qwen semantic and visual interpretation through Alibaba Cloud Model Studio stays disabled until a user supplies and tests a demo API key.

The **?** control beside **Settings** opens a scrollable, capability-aware guide. It gives example requests, reports which exposure, tap-focus, digital-zoom, and native white-balance controls the active camera exposes, separates guidance from camera settings, and expands into plain-language camera terms. ISO and shutter speed are identified as not adjustable in this version, while exact Kelvin control is represented only by native presets the active camera actually exposes.

## Build and test

Requirements: JDK 17 and Android SDK 34.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest # with an API 31+ device or emulator running
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. An Alibaba Cloud Model Studio key is not required for the camera, local coaching, voice, capture, review, or normal automated test suite; the explicit live Qwen smoke harnesses require one.

The minified release artifact is intentionally unsigned at `app/build/outputs/apk/release/app-release-unsigned.apk`. Sign it with a user- or CI-owned Android keystore before distribution; no signing key is generated or stored in this repository.

For unattended physical-camera acceptance, first open `test-fixtures/device-stage.html` full-screen on the computer. Position one Android 12/API 31+ phone about 40–80 cm away with its rear camera aimed squarely at the displayed portrait. Connect it by USB, unlock it, enable USB debugging, and approve that connection when Android asks. Keep the normal device lock configured; the runner temporarily enables stay-awake over USB, restores the original setting, and preserves the phone's original awake/asleep state afterward. Then run:

```powershell
.\scripts\physical-device-acceptance.ps1
```

The runner ignores emulators and network ADB targets, rejects unauthorized or ambiguous devices and stale APKs, installs the debug/test APKs, and saves a local-only timestamped report under `outputs/qa/physical-device/`. The report fingerprints both APKs but does not retain the raw device serial. Six tests capture/decode from the expected MediaStore album, verify stalled-driver recovery, qualify the active rear lens as standard-wide, enforce the 4 Hz analysis and 10% steady-preview jank gates, require visible apply/reset convergence across five sub-second EV trials, prove chained adjustments reset to the original baseline, require EV/ISO/exposure time from the actual saved-still result, exercise `too zoomed out → 1.00x → Apply 1.25x → verify → Reset 1.00x`, and exercise the generic focus recommendation → visible marker → selected preview tap → AF-lock path without face gating. They delete the exact test-created MediaStore images and the test package, restore the stay-awake setting, and leave a fresh debug app installed at onboarding.

For safety, the runner stops if Photo Helper is already installed. `-AllowExistingApp` explicitly permits replacement while preserving its private data; the tests can then complete onboarding and leave Camera permission granted.

The stage page starts with the required neutral portrait and hides its controls; number keys 1–6 switch among the synthetic fixtures, and moving the pointer to the top reveals the buttons. The API key is not needed or copied to the device for this hardware gate.

## Verification status

- JVM: 131 tests pass with 0 failures, 0 errors, and 0 skips.
- API 34 emulator: all 43 instrumentation tests pass, including strict complaint contracts, MediaStore capture/decode, EV apply/reset, focus metering, focus-target UI, the capability-aware guide's open/close flow, active-camera status reporting, collapsed/expanded technical-detail semantics, deterministic TalkBack traversal, encrypted key storage, stalled-camera recovery, blocked-camera Retry, atomic Capture Review navigation, microphone error semantics, 512-character key bounding, and 200% text usability for Shutter, guidance Cancel, and the guide while controls load.
- API 34 system integration: a clean install exercised the real permission dialog, denial recovery, App Info handoff, permission grant on resume, background/foreground camera recovery, and portrait↔landscape rotation; the shutter returned enabled without a camera-blocked state.
- API 31 AOSP emulator: all 43 instrumentation tests pass, including strict virtual-camera capture/decode and the capability-aware guide coverage above.
- Fresh UI inspection of the guide and existing camera flows on API 31, API 34, and the physical phone found no clipping. Android lint passes with 0 errors and 11 warnings.
- The OnePlus CPH2411/API 35 passed all six physical CameraX tests, including the typed zoom comment → Apply → verify → Reset chain and generic tap-to-focus. Separate qualifications passed five warmer, five cooler, and ten white-balance resets, plus five zoom cycles and adjusted-still telemetry.
- Typed and injected voice-result paths reach the same safe intent chain. The secondary-display speaker also reached the phone microphone, and the phone's captured audio was intelligible and transcribed locally. Android's on-device and standard recognition providers detected the synthetic and human speech samples but returned no hypothesis, so the acoustic on-device speech-to-text gate is not green on this device/provider combination.
- ISO and shutter time remain observe-only; the app does not expose them until coupled manual exposure passes its apply, still-metadata, and Auto-reset gate.

The repeatable 12-call semantic harness is `scripts/qwen-intent-smoke.py`. It reads only `EVALUATION_MODEL_NAME` and `EVALUATION_MODEL_KEY` from `.env`, writes redacted JSONL under ignored `outputs/qa/`, and never writes an image or key copy. A prior content-hash-matched run passed 12/12 strict complaint-intent cases. Two clean-commit production-rate runs passed 11/12 and 10/12 with zero semantic mismatches among received responses; one request timed out in the first run, while a different request timed out and one had a network error in the second. Qwen may return only an allowlisted semantic label or clarification reason; Android reparses it and plans all values and camera actions locally, while provider failures remain fail-closed.

The separate visual harness is `scripts/qwen-live-smoke.py`. Its 2026-08-07 production-rate run passed 4/12 strict cases: six timed out, one had a network error, and one returned a schema-valid but semantically mismatched face result. Both hosted paths remain optional and fail-closed; local coaching, capture, and controls do not depend on them.

## Demo key

Open **Settings**, enter a disposable China (Beijing) Alibaba Cloud Model Studio key, and tap **Test key**. The app stores a non-exportable AES key in Android Keystore and the encrypted API-key ciphertext/IV in private preferences. When local wording is not understood, it may send the typed or transcribed comment alone; for two eligible visual questions it also sends one reduced live frame or saved-photo copy. It never sends audio or streams the preview, and it clears the key on request. Direct client-side API keys are suitable only for this private demo; a distributed build should use a backend proxy.

ML Kit face-detection inputs and results remain on-device. An explicitly enabled eligible visual request may separately send one reduced image to Alibaba Cloud as disclosed above. Google separately documents ML Kit SDK collection of device/app information and diagnostic usage metrics; the installed app discloses and links that boundary in Settings.
