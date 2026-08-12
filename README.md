# Photo Helper

An Android camera coach designed to be used with an image-capable LLM. With Qwen enabled, every spoken request is sent with the exact clean CameraX frame and an in-memory aspect-aware gridded copy (normally 6×8 portrait or 8×6 landscape). Qwen returns one strictly parsed, ordered plan of up to eight allowlisted actions: bounded semantic setting adjustments, front/rear/toggle camera selection, a validated focus grid cell, and immediate or delayed capture. Android alone converts semantic adjustments into device-supported values and executes the actions. Object-focus requests such as “focus on the red watch” therefore return a grid row and column—not an object name or free-form coordinate—and Android shows that cell and focus marker for confirmation before tapping it locally. A sequence pauses for Apply or focus confirmation, then continues automatically. Without a key or when Qwen fails, the app retains a limited local wording fallback and manual tap-to-focus.

The current prompts, schemas, response validation, and latency assumptions are tuned specifically for **Qwen3.7 Flash (2026-07-15)**. Other models may produce different results, and this build has no API adapter or response contract for them. The only supported credential is an **Alibaba Cloud Model Studio (Bailian) API key with access to Qwen3.7 Flash**, obtained from Alibaba Bailian; keys for other providers or models will not work.

Before a compound recommendation is shown, every requested setting must pass its capability and range checks. Apply serializes the CameraX controls as one local transaction: any failed control restores the exact pre-apply settings, and the camera is blocked if recovery cannot be proved. One Reset restores the original baseline for the whole accepted recommendation. Because Android camera controls are issued sequentially, a short-lived intermediate preview frame can still be visible while the transaction runs.

The **?** control beside **Settings** opens a scrollable, capability-aware guide. It gives example requests, explains the non-always-on **Mic**/**square Stop** lifecycle, reports which exposure, tap-focus, digital-zoom, and native white-balance controls the active camera exposes, separates guidance from camera settings, and expands into plain-language camera terms. ISO and shutter speed are identified as not adjustable in this version, while exact Kelvin control is represented only by native presets the active camera actually exposes.

Voice input is never always-on: tap **Mic** to start one comment, then tap the square **Stop** button when finished. On Android 13+, Photo Helper starts privacy-sensitive app-owned microphone capture before the user speaks, holds at most 15 seconds of PCM in memory, releases the recorder on the square **Stop** button, and passes the finite audio through a local pipe to Android's installed on-device recognizer. The whole operation times out after 20 seconds, backgrounding cancels it, and audio buffers are overwritten after use; audio is never saved or sent to Alibaba. Android 12/12L retain the platform's direct-microphone on-device recognizer, which may endpoint automatically. Injected-audio support is provider-dependent, so the physical gate qualifies the connected OnePlus/provider and other phones fall back to manual controls if recognition is unavailable or fails.

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

The runner ignores emulators and network ADB targets, rejects unauthorized or ambiguous devices and stale APKs, installs the debug/test APKs, and saves a local-only timestamped report under `outputs/qa/physical-device/`. The report fingerprints both APKs but does not retain the raw device serial. Seven tests capture/decode from the expected MediaStore album, verify stalled-driver recovery, qualify the active rear lens as standard-wide, enforce the 4 Hz analysis and 10% steady-preview jank gates, require visible apply/reset convergence across five sub-second EV trials, prove chained adjustments reset to the original baseline, require EV/ISO/exposure time from the actual saved-still result, exercise `too zoomed out → 1.00x → Apply 1.25x → verify → Reset 1.00x`, exercise a two-setting comment → one Apply → both control setpoints → one Reset, and exercise the generic focus recommendation → visible marker → selected preview tap → AF-lock path without face gating. They delete the exact test-created MediaStore images and the test package, restore the stay-awake setting, and leave a fresh debug app installed at onboarding.

For the unattended acoustic gate, leave the phone aimed at the portrait display and run:

```powershell
.\scripts\voice-acoustic-acceptance.ps1
```

The runner waits for app-owned microphone capture to be active, routes Edge's fixture voice through the portrait display's speaker, waits for browser playback to finish, presses the real square **Stop** button, and requires transcription → compound recommendation → one Apply → both setpoints → Reset. It then performs a silent second capture in the same app instance to prove that no transcript or camera change can leak from the prior audio.

For safety, the runner stops if Photo Helper is already installed. `-AllowExistingApp` explicitly permits replacement while preserving its private data; the tests can then complete onboarding and leave Camera permission granted.

The stage page starts with the required neutral portrait and hides its controls; number keys 1–6 switch among the synthetic fixtures, and moving the pointer to the top reveals the buttons. The API key is not needed or copied to the device for this hardware gate.

## Verification status

- JVM: 188 tests pass with 0 failures, 0 errors, and 0 skips, including model-first ordered plan execution, focus-cell confirmation followed by countdown capture, local fallback parsing, fresh-telemetry replanning, all-or-none capability gates, rollback, session/lens invalidation, PCM trimming/silence handling, queued voice finalization/timeout, and provider failure handling.
- API 34 emulator: all 48 instrumentation tests pass, including strict scalar/compound and object-grid Qwen contracts, model-selected focus-cell/marker UI, cancellable countdown UI, MediaStore capture/decode, EV apply/reset, focus metering, the capability-aware guide's open/close flow, active-camera status reporting, deterministic TalkBack traversal, encrypted key storage, stalled-camera recovery, blocked-camera Retry, atomic Capture Review navigation, microphone error semantics, 512-character key bounding, and 200% text usability.
- API 34 system integration: a clean install exercised the real permission dialog, denial recovery, App Info handoff, permission grant on resume, background/foreground camera recovery, and portrait↔landscape rotation; the shutter returned enabled without a camera-blocked state.
- API 31 AOSP emulator: all 45 instrumentation tests pass, including strict virtual-camera capture/decode and the compound/guide coverage above.
- Fresh guide inspection on API 31 and the physical phone, plus a fresh physical compound-card inspection, found no clipping or overlap. Android lint passes with 0 errors and 11 warnings.
- The OnePlus CPH2411/API 35 passed all seven physical CameraX tests in final-APK report `20260809-191636`. The compound gate proved `Zoom 1.00× → 1.25× → 1.00×` and `White balance Auto → Cooler → Auto` through one **Apply both** and one Reset; the standalone zoom, exposure, capture, analysis, recovery, and generic tap-to-focus gates also passed.
- Debug/test APKs, the minified unsigned release APK, and the unsigned release app bundle build successfully. The configured key has zero tracked-file matches and `.env` remains ignored.
- Voice is physically green on the qualified OnePlus/API 35 path across four final unattended runs (`20260809-185305`, `20260809-185406`, `20260809-190327`, and final-APK run `20260809-191527`). Each proves app-owned capture was active before Edge fixture speech played through the portrait display's speaker, playback completed before the square **Stop** button, the finite in-memory PCM reached the on-device recognizer, and a usable transcript produced one exposure-plus-zoom recommendation. **Apply both** changed EV `0 → 4` and zoom `1.00× → 1.25×`; Reset restored both baselines. A silent second Mic/square-Stop cycle in the same process produced no transcript, recommendation, reset state, or camera change, proving that captured audio was not reused. Earlier error-7 reports document the superseded recognizer-owned-microphone route rather than the current gate. API 31 and API 34 emulator suites remain green for the direct-recognizer fallback and guarded API boundary.
- ISO and shutter time remain observe-only; the app does not expose them until coupled manual exposure passes its apply, still-metadata, and Auto-reset gate.

`scripts/qwen-intent-smoke.py` retains the historical schema-v2 text-classifier measurements for comparison; it is no longer the app contract. The current schema-v3 contract is exercised by Android contract tests: Qwen receives the clean and gridded frames plus the complete request and may return only allowlisted ordered actions or an allowlisted clarification. Android strictly reparses the full result, rejects extra keys and out-of-range grid/countdown values, and keeps all device-specific setting values and camera execution local.

The real Android private-demo path was also exercised on the OnePlus: **Settings** tested and encrypted the configured demo key, enabled AI interpretation, and sent the locally unknown comment `The overall image needs a touch more light.` Qwen supplied the semantic direction, Android produced a bounded `+0.7 EV` recommendation, rejected an intentionally stale Apply, accepted a fresh Apply, and Reset restored automatic camera settings. The final non-secret UI is recorded at `outputs/qa/physical-device/qwen-android-chain-final.png`; this proves the client/key-store/UI/action wiring, not general model reliability.

The separate visual harness is `scripts/qwen-live-smoke.py`. Its 2026-08-07 production-rate run passed 4/12 strict cases: six timed out, one had a network error, and one returned a schema-valid but semantically mismatched face result. Both hosted paths remain optional and fail-closed; local coaching, capture, and controls do not depend on them.

## Demo key

During onboarding or in **Settings**, enter an Alibaba Cloud Model Studio (Bailian) API key with Qwen3.7 Flash access and tap **Test, save & enable**. The app stores a non-exportable AES key in Android Keystore and the encrypted API-key ciphertext/IV in private preferences. When enabled, it sends each spoken comment, one reduced clean live frame, and an in-memory gridded copy to Qwen. It never sends audio or streams the preview, and it clears the key on request. Direct client-side API keys are suitable only for this private demo; a distributed build should use a backend proxy.

ML Kit face-detection inputs and results remain on-device. An explicitly enabled eligible visual request may separately send one reduced image to Alibaba Cloud as disclosed above. Google separately documents ML Kit SDK collection of device/app information and diagnostic usage metrics; the installed app discloses and links that boundary in Settings.
