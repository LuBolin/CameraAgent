# Photo Helper AI Component Decisions

Status: implementation decision note
Last reviewed: 2026-08-04

Companion documents: [Android Technical Architecture](ANDROID_TECHNICAL_ARCHITECTURE.md), [Android Product Design](ANDROID_PRODUCT_DESIGN.md), [ADR 0004 — Call Bailian Qwen directly from the private demo app](../docs/adr/0004-call-bailian-qwen-directly-from-private-demo.md), and [Unselected Model Research Appendix](AI_MODEL_RESEARCH_APPENDIX.md).

## Decision summary

The judged APK uses Android's on-device `SpeechRecognizer`, bundled ML Kit face detection, deterministic frame statistics/planning/control/verification, and one hosted model: Qwen `qwen3.7-flash-2026-07-15` through Alibaba Cloud Model Studio (Bailian) in China (Beijing). The local planner is the agent through its closed observe–propose–act–verify loop.

Qwen3.7 Flash is a visual-only semantic helper for two already-classified families—whole-frame color cast and face-size ambiguity. It is not a language fallback, camera controller, source of numeric settings, or source of user-facing prose. The app calls Alibaba Cloud Model Studio directly with a disposable key entered by the operator because this is a private, single-device hackathon demo.

There is no provider abstraction, runtime model selector, automatic fallback, or hosted language-classification path.

## Selected APK components

### Speech-to-text: Android on-device `SpeechRecognizer`

- Use push-to-talk with `isOnDeviceRecognitionAvailable` and `createOnDeviceSpeechRecognizer` only.
- Typed input is always available.
- Never fall back silently to Android's default cloud-capable recognizer.
- If the installed service/language is missing, disable voice for that session and keep typed input.
- Before first use: `Android transcribes voice on this device. Photo Helper does not store or send your audio.`

This is the smallest native solution and has no per-call API fee. Add a bundled recognizer only after a recorded acceptance set on the exact devices/languages/noise conditions proves the native service inadequate.

### Local visual evidence: ML Kit plus frame statistics

- Bundle ML Kit face detection so the demo does not wait for a model download.
- Compute luma, clipping, and coarse chroma statistics from CameraX analysis frames.
- Use rotation/gravity sensors for level/pitch.
- Defer ML Kit Pose. Therefore `person looks short` is local clarification/advice and never a VLM upload in the current scope.
- No face recognition, identity, embeddings, persistent tracking, or beauty score.

### Local planner and verifier

The Kotlin parser/planner owns the supported wording map, polarity/negation guards, capability checks, bounded setting calculations, app copy, action approval, camera control, rollback, and verification. Hosted output cannot choose numeric ISO, shutter, white-balance, zoom, movement, or user-facing text.

## Hosted visual helper: `qwen3.7-flash-2026-07-15`

### Why it is the one candidate

Alibaba Cloud documents image understanding and structured JSON output for Qwen3.7 Flash in non-thinking mode. Pricing and free quotas can change and are not product assumptions. The exact hosted model identifier is fixed as `qwen3.7-flash-2026-07-15`; there is no runtime model picker or alternate provider.

For the installed demo, the operator enters a low-quota disposable key in Settings. The app encrypts it using a non-exportable Android Keystore AES/GCM key and stores only ciphertext plus IV in private preferences with backup disabled. `Test key`, `Clear key`, and post-demo revocation are part of the demo workflow. Direct client-side use can expose a key on a compromised or instrumented device; that risk is accepted only for this operator-controlled artifact. A public release would need an owned backend or a capable on-device model.

Desktop smoke/evaluation scripts read `EVALUATION_MODEL_NAME` and `EVALUATION_MODEL_KEY` from the process environment or an ignored `.env` file. Neither value is copied into Android source, resources, `BuildConfig`, logs, or the APK; the installed app uses the fixed non-secret model constant and an operator-entered runtime key.

### Minimum role

```text
exact locally classified Complaint family
  + one reduced, metadata-free current frame
  -> direct HTTPS -> Alibaba Cloud Model Studio Qwen3.7 Flash (China, Beijing)
  -> fixed family-specific INTENT-or-CLARIFY JSON union
  -> fresh local evidence/capabilities/planner
  -> optional Apply, one-step guidance, or advice
```

Allowed visual families and outputs:

| Family | Provider `INTENT` payload | Android mapping and product effect |
|---|---|---|
| `COLOR_CAST` | Prompt/schema v2 `intent`: `WHITE_BALANCE_WARMER` or `WHITE_BALANCE_COOLER` | Apply only with fresh matching local evidence and tested WB control; otherwise advice |
| `FACE_SIZE_AMBIGUOUS` | Prompt/schema v3 boolean `distortionVisible` | Android maps `false` to `FACE_OCCUPANCY_LOWER`, which may start one-step guidance after fresh Subject Lock evidence; `true` maps to advice-only `CLOSE_PERSPECTIVE_ADVISORY` |

The alternative model outcome is `CLARIFY` with `VISUAL_INSUFFICIENT`, `SUBJECT_UNCLEAR`, or `SCENE_CONFOUND`, using schema v2 for color and v3 for face size. Novel wording and height appearance stay local and send nothing.

Every card derived from an accepted Visual Hint keeps the visible label `AI-interpreted by Qwen via Alibaba Cloud; camera controls checked on device`; local-only cards never show it.

### Direct-call boundary and smoke gate

The configured endpoint is Alibaba Cloud Model Studio's OpenAI-compatible China (Beijing) endpoint. Alibaba Cloud's China privacy notice says submitted data is not used for model training and that model/application call data is stored as required by applicable law. The app therefore makes no zero-retention, deletion, or broader residency claim. Enabling Visual AI after entering the key is the operator's setup acknowledgement.

The real account smoke must cover `POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`, an inline Base64 JPEG data URL, `enable_thinking: false`, `temperature: 0`, `response_format: {"type":"json_object"}`, no tools, both family-specific schemas, the exact returned model, a nonblank provider `id`, `object: "chat.completion"`, and hard quota behavior. Bailian does not echo a client `request_id`; the synchronous call and local Complaint/provenance checks provide correlation. Failure keeps both families on local clarification; do not add temporary image hosting or another service.

The live fixture gate rejected the earlier face enum contract because Qwen's choice changed with output-label ordering. Prompt v3 removes those app labels from the model decision and asks only whether facial-proportion distortion is visible. The JSON boolean is mapped to app intents locally, so future fixture gates must cover `true`, `false`, and clarification while ensuring large faces and tight crops alone do not count as distortion.

The app sends Alibaba Cloud at most one reduced image per eligible Complaint and never includes preview streams, audio, EXIF, content URIs, face landmarks, tracking IDs, hardware IDs, or local metrics in that request. Bundled ML Kit image inputs/results stay on-device, while Google separately documents SDK diagnostic and usage collection including device/app information, per-installation identifiers, configuration, event/error data, latency, and input/output sizes. Missing/invalid key, offline, timeout, malformed output, or a stale result falls back to the same local clarification. All response fields cross a strict allowlist before the local planner sees a Visual Hint.

## Sole research fallback: MiniCPM-V 4.6

MiniCPM-V 4.6 is not an APK dependency or automatic fallback. It is the first on-device research candidate only if hosted privacy, pricing, latency, or availability makes Qwen unsuitable.

The official mobile path is substantial: roughly a 1.6 GB model download, a device with at least 6 GB RAM recommended, native/NDK/JNI integration, and device-specific latency, peak-memory, battery, and thermal measurement. It is not a small drop-in Android library. Do not implement it until the fixed two-family corpus shows that image input adds enough value to justify this cost.

Repository code licensing and model-weight/redistribution terms are separate checks. Do not infer weight rights from a repository LICENSE; review both the official repository and the exact model card/license before bundling or downloading weights in an app.

## Evidence sequence

1. Build and rehearse the complete local observe–propose–act–verify loop first.
2. Run the bounded real-account contract smoke and repeatable 12-call harness on owned, non-sensitive fixtures using `EVALUATION_MODEL_NAME` and `EVALUATION_MODEL_KEY` from `.env`; keep the visual path optional until the strict reliability gate passes.
3. Wire the same fixed contract into Android direct HTTPS, then verify key entry/test/clear, encryption, no static secret, airplane-mode fallback, timeout, cancellation, and response validation.
4. Evaluate ordinary and adversarial staged fixtures against color schema v2 and both face schema v3 boolean outcomes. Retain the label-order-bias case as a regression; remove the hosted path if it cannot improve completion time and clarification count without unsafe outcomes.
5. Test MiniCPM-V on the exact phone only if a measured Qwen failure justifies its mobile cost.

## License and terms status

| Component | Repository/code terms | Model weights or hosted terms |
|---|---|---|
| Android `SpeechRecognizer` | Android platform API terms | Installed recognition service/model terms belong to the device/service provider; verify target-device behavior |
| ML Kit face detection | Google ML Kit SDK terms | Bundled model use follows the SDK/service terms; no redistribution claim is made here |
| Hosted Qwen3.7 Flash snapshot | No provider repository or SDK is embedded | Use is governed by the Alibaba Cloud Model Studio China account, service/privacy terms, pricing, quota, region, and workspace settings; archive their demo-time versions |
| MiniCPM-V 4.6 | Verify the official repository LICENSE before integration | Separately verify the exact model-card/weight/redistribution license before any download or bundling |

## Primary sources

- [Android `SpeechRecognizer`](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [ML Kit face detection on Android](https://developers.google.com/ml-kit/vision/face-detection/android)
- [ML Kit Android data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
- [Alibaba Cloud Model Studio visual understanding and Qwen model list](https://help.aliyun.com/en/model-studio/vision-model/)
- [Alibaba Cloud Model Studio OpenAI-compatible Chat API](https://help.aliyun.com/en/model-studio/qwen-api-via-openai-chat-completions)
- [Alibaba Cloud Model Studio structured output](https://help.aliyun.com/en/model-studio/qwen-structured-output)
- [Alibaba Cloud Model Studio API key guidance](https://help.aliyun.com/en/model-studio/get-api-key)
- [Alibaba Cloud Model Studio model pricing](https://help.aliyun.com/en/model-studio/model-pricing)
- [Alibaba Cloud Model Studio China privacy notice](https://help.aliyun.com/zh/model-studio/privacy-notice)
- [MiniCPM-V repository](https://github.com/OpenBMB/MiniCPM-V)
- [MiniCPM-V-Apps](https://github.com/OpenBMB/MiniCPM-V-Apps)
