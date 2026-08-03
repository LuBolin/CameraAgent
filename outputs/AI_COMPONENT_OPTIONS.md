# Photo Helper AI Component Decisions

Status: implementation decision note
Last reviewed: 2026-08-04

Companion documents: [Android Technical Architecture](ANDROID_TECHNICAL_ARCHITECTURE.md), [Android Product Design](ANDROID_PRODUCT_DESIGN.md), and [Unselected Model Research Appendix](AI_MODEL_RESEARCH_APPENDIX.md).

## Decision summary

The planned judged APK uses Android's on-device `SpeechRecognizer`, bundled ML Kit face detection, deterministic frame statistics/planning/control/verification, and one hosted model: Z.AI `glm-4.6v-flash`. The local planner is the agent through its closed observe–propose–act–verify loop.

GLM-4.6V-Flash is a visual-only semantic helper for two already-classified families—whole-frame color cast and face-size ambiguity. It is not a language fallback, camera controller, source of numeric settings, or source of user-facing prose. The app calls Z.AI directly with a disposable key entered by the operator because this is a private, single-device hackathon demo.

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

## Hosted visual helper: `glm-4.6v-flash`

### Why it is the one candidate

Z.AI documents image understanding for GLM-4.6V-Flash and currently lists its input/output as free. Pricing is changeable and is not a product assumption. The exact hosted model identifier is fixed as `glm-4.6v-flash`; there is no runtime model picker or alternate provider.

For the installed demo, the operator enters a low-quota disposable key in Settings. The app encrypts it using a non-exportable Android Keystore AES/GCM key and stores only ciphertext plus IV in private preferences with backup disabled. `Test key`, `Clear key`, and post-demo revocation are part of the demo workflow. Direct client-side use can expose a key on a compromised or instrumented device; that risk is accepted only for this operator-controlled artifact. A public release would need an owned backend or a capable on-device model.

Before the key-entry UI exists, desktop smoke/evaluation scripts read `ZAI_API_KEY` from the process environment. The environment value is never copied into Android source, resources, `BuildConfig`, logs, or the APK.

### Minimum role

```text
exact locally classified Complaint family
  + one reduced, metadata-free current frame
  -> direct HTTPS -> Z.AI GLM-4.6V-Flash
  -> fixed INTENT-or-CLARIFY union
  -> fresh local evidence/capabilities/planner
  -> optional Apply, one-step guidance, or advice
```

Allowed visual families and outputs:

| Family | Model `INTENT` values | Product effect |
|---|---|---|
| `COLOR_CAST` | `WHITE_BALANCE_WARMER`, `WHITE_BALANCE_COOLER` | Apply only with fresh matching local evidence and tested WB control; otherwise advice |
| `FACE_SIZE_AMBIGUOUS` | `FACE_OCCUPANCY_LOWER`, `CLOSE_PERSPECTIVE_ADVISORY` | first may start one-step guidance after fresh Subject Lock evidence; second is advice only |

The alternative model outcome is `CLARIFY` with `VISUAL_INSUFFICIENT`, `SUBJECT_UNCLEAR`, or `SCENE_CONFOUND`. Novel wording and height appearance stay local and send nothing.

Every card derived from an accepted Visual Hint keeps the visible label `AI-interpreted by Z.AI; camera controls checked on device`; local-only cards never show it.

### Direct-call boundary and smoke gate

Z.AI's API DPA describes real-time processing/no saving and says processing is generally in Singapore; its FAQ and cache documentation also describe caching of some request content. This is not a blocker for the private demo, but the app makes no zero-retention, deletion, or guaranteed-residency claim. Enabling Visual AI after entering the key is the operator's setup acknowledgement.

The real account must prove inline base64/data-image support with `glm-4.6v-flash`, thinking disabled, JSON-object output, no tools, `max_tokens: 80`, exact returned model, and hard quota behavior in a 12-call smoke. Official examples establish multimodal `image_url` input but do not clearly establish inline data-image support for this exact path. Failure keeps both families on local clarification; do not add temporary image hosting or another service.

The app sends at most one reduced image per eligible Complaint and never sends preview streams, audio, EXIF, content URIs, face landmarks, tracking IDs, hardware IDs, or local metrics. Missing/invalid key, offline, timeout, malformed output, or a stale result falls back to the same local clarification. All response fields cross a strict allowlist before the local planner sees a Visual Hint.

## Sole research fallback: MiniCPM-V 4.6

MiniCPM-V 4.6 is not an APK dependency or automatic fallback. It is the first on-device research candidate only if hosted privacy, pricing, latency, or availability makes GLM unsuitable.

The official mobile path is substantial: roughly a 1.6 GB model download, a device with at least 6 GB RAM recommended, native/NDK/JNI integration, and device-specific latency, peak-memory, battery, and thermal measurement. It is not a small drop-in Android library. Do not implement it until the fixed two-family corpus shows that image input adds enough value to justify this cost.

Repository code licensing and model-weight/redistribution terms are separate checks. Do not infer weight rights from a repository LICENSE; review both the official repository and the exact model card/license before bundling or downloading weights in an app.

## Evidence sequence

1. Build and rehearse the complete local observe–propose–act–verify loop first.
2. Run the 12-call real-account contract smoke on owned, non-sensitive fixtures using `ZAI_API_KEY` from the desktop environment.
3. Wire the same fixed contract into Android direct HTTPS, then verify key entry/test/clear, encryption, no static secret, airplane-mode fallback, timeout, cancellation, and response validation.
4. Evaluate 24 ordinary and 18 adversarial staged fixtures. Remove the hosted path if it cannot improve completion time and clarification count without unsafe outcomes.
5. Test MiniCPM-V on the exact phone only if a measured GLM failure justifies its mobile cost.

## License and terms status

| Component | Repository/code terms | Model weights or hosted terms |
|---|---|---|
| Android `SpeechRecognizer` | Android platform API terms | Installed recognition service/model terms belong to the device/service provider; verify target-device behavior |
| ML Kit face detection | Google ML Kit SDK terms | Bundled model use follows the SDK/service terms; no redistribution claim is made here |
| Hosted GLM-4.6V-Flash | No provider repository or SDK is embedded | Use is governed by the exact Z.AI API account, Terms, DPA/privacy, caching, pricing, and quota settings; archive their demo-time versions |
| MiniCPM-V 4.6 | Verify the official repository LICENSE before integration | Separately verify the exact model-card/weight/redistribution license before any download or bundling |

## Primary sources

- [Android `SpeechRecognizer`](https://developer.android.com/reference/android/speech/SpeechRecognizer)
- [ML Kit face detection on Android](https://developers.google.com/ml-kit/vision/face-detection/android)
- [Z.AI GLM-4.6V documentation](https://docs.z.ai/guides/vlm/glm-4.6v)
- [Z.AI Chat Completions API](https://docs.z.ai/api-reference/llm/chat-completion)
- [Z.AI pricing](https://docs.z.ai/guides/overview/pricing)
- [Z.AI API terms](https://docs.z.ai/legal-agreement/terms-of-use)
- [Z.AI API privacy/DPA](https://docs.z.ai/legal-agreement/privacy-policy)
- [Z.AI caching FAQ](https://docs.z.ai/help/faq)
- [MiniCPM-V repository](https://github.com/OpenBMB/MiniCPM-V)
- [MiniCPM-V-Apps](https://github.com/OpenBMB/MiniCPM-V-Apps)
