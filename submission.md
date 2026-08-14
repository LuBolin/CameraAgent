# Photo Helper — Submission Copy

This file follows the portal field order. Copy only the field contents into the submission form.

## 1. Project title

Photo Helper

## 2. Project image

Use `submission-assets/photo-helper-cover-16x9-v6-actual-ui.png`.

![Photo Helper family cover](submission-assets/photo-helper-cover-16x9-v6-actual-ui.png)

## 3. Short blurb

Speak in outcomes, not camera settings.

Word count: 6 (hard limit: under 10)

## 4. Project Description

### Project Overview: scenario, users, and value proposition

Photo Helper is a voice-first camera-control translator for everyday smartphone photographers, especially older adults and first-time users. Its starting scenario is a familiar family moment: a photographer knows the people look too dark or far away, but does not know which camera controls to change before the moment passes. They can simply say, “Make it brighter, focus on the person in red, and take a photo after three seconds.”

The agent turns that outcome into a bounded camera plan, asks for approval where appropriate, and carries it out on the real Android camera. It operates supported zoom, brightness, color, flash, focus, and capture controls rather than teaching the user where those settings are. Its value is translation and safe execution—not a claim that AI has better photographic taste or can direct a photoshoot. The user stays in control through visible recommendations, capability checks, focus confirmation, Reset, and graceful fallback.

Photo Helper is an agent rather than a photography chatbot because it closes an **observe → understand → plan → approve → act → verify or recover** loop against a physical device. It can combine exposure, zoom, supported white-balance modes, camera selection, flash, visual subject focus, countdown, capture, and restoration in one ordered workflow. The build includes an experimental “Make this shot look nicer” check for exposure, white balance, crop, and visible focus, but that subjective feature is secondary. It does not infer light direction or shooting angle to direct the photographer around a scene.

### Real-World Scenario Insights: pain points, audience, and problem solved

People normally describe a photo as “too dark,” “too warm,” “too far away,” or “not focused on Mum.” Phone cameras expose different language: exposure compensation, zoom ratios, white-balance modes, metering regions, and timers. Translating between the two creates cognitive load, screen searching, and missed moments. The gap can be larger for people who did not grow up with smartphone camera controls, but it is not age-specific.

The scenario is a product hypothesis informed by tracing common camera tasks and repeatedly testing the complete interaction on a physical phone; it is not presented as completed user research with older adults. That boundary shaped a conservative design. Photo Helper performs only requested, supported changes. It does not silently beautify a photo, invent unsupported controls, or edit the saved original during review. Ambiguous requests lead to clarification, stale recommendations cannot execute, and unavailable AI leaves ordinary camera functions usable.

The core problem solved is therefore precise: a user can express the photographic outcome they understand while the agent handles the camera terminology and multi-step control sequence they do not.

### Comprehensive Solution Design

#### User and business architecture

The hackathon prototype is a native Android app that works with existing phone hardware and needs no dedicated accessory. The initial route to users is a voice-controlled camera utility for families, older adults, and other casual photographers. A production version would place model credentials behind a product-owned service and could later expose the same bounded planner as an SDK or OEM camera feature. The underlying interaction also extends to assisted evidence capture, product listings, and field documentation—situations where users know the desired result but not camera terminology.

#### Technical architecture

The app is built with Kotlin, Jetpack Compose, CameraX, Camera2 interop, coroutines, Android on-device speech recognition, and ML Kit face detection. CameraX owns the preview, telemetry, image analysis, capture, and ordinary controls. Device capabilities are discovered at runtime, so unavailable settings are reported rather than simulated.

For an AI request, Photo Helper sends Qwen3.7 Flash through Alibaba Cloud Model Studio:

1. the complete spoken request;
2. a reduced clean CameraX frame; and
3. an aspect-aware gridded copy of the same frame, plus trusted telemetry, capability bounds, and recent changes.

Qwen can interpret language and visual references, but it never receives a camera-control interface. It proposes a semantic plan; Android remains the sole authority that validates and executes it. Settings are applied as a local transaction. If any step fails, the app rolls back to the exact pre-Apply state and blocks further work if recovery cannot be proven. Reset restores the original baseline across chained adjustments. Recommendations also carry frame, lens, session, and capture context so stale plans cannot act on a changed camera.

Voice is push-to-talk and never always-on. On supported devices, the app holds at most 15 seconds of PCM in memory, passes finite audio to Android’s installed on-device recognizer, overwrites the buffer, and never saves or sends audio to Alibaba. Hosted requests receive reduced still frames for that request—not a preview stream or the full-resolution saved photo. The private demo key is encrypted with Android Keystore and can be cleared in Settings; production would replace the client-side provider key with a backend credential boundary.

#### How prompts drive AI generation

The prompt is a constrained planning interface, not an invitation for free-form advice. It defines a strict JSON schema with at most eight ordered actions chosen from six allowlisted types: `ADJUST`, `SET_CAMERA`, `SET_FLASH`, `FOCUS_CELL`, `RESET`, and `CAPTURE`. It requires preparation before focus and capture, limits focus to a valid row and column in the supplied grid, and forbids raw device values, free-form coordinates, extra keys, and prose.

Android reparses the complete response and rejects invalid combinations, stale context, and out-of-range values. Semantic directions such as `EXPOSURE_BRIGHTER` are mapped locally to values supported by the active camera. This split uses the model where interpretation is valuable while keeping physical authority deterministic, testable, and reversible.

### Quantifiable Metrics and Defined Impact

- **One natural-language request can produce up to eight ordered camera actions**, replacing several manual control and capture steps without requiring the user to know their technical names.
- **Six allowlisted action types** form the hosted planner’s model-to-device vocabulary; all outputs are strictly parsed and checked against live capabilities.
- The current code passes **209/209 JVM unit tests**. Android lint and the debug APK build also pass.
- WorkBuddy’s final deterministic physical-device run recorded **57/57 instrumented tests** on a OnePlus, covering the main Compose UI, visual contracts, encrypted key storage, and PCM capture.
- Physical-device evidence covers the listening control, Reset persistence beyond 15 seconds, baseline restoration, portrait and landscape layouts, and 200% font scale.
- Camera analysis is bounded to **4 Hz**, voice capture to **15 seconds**, and hosted interpretation to reduced still images instead of continuous video.

The defined user impact is lower camera-control complexity: people state the outcome once, see what the agent intends to do, and can reverse it. A production pilot should measure successful outcome-to-capture completion, time-to-capture, manual control interactions, retakes, and user confidence before making claims about accessibility or improved photo quality.

### Business Value and Viability

Photo Helper addresses a common mismatch between how people describe photos and how cameras expose controls. Its differentiation is not another chat screen or automatic beauty filter; it is a natural-language control layer over real phone hardware with explicit capability checks, local authority, reversible actions, and graceful degradation. The Android prototype proves that loop end to end. Commercial rollout is plausible as a focused consumer utility, accessibility-oriented camera mode, or OEM/SDK capability, but direct research with older adults, a credential backend, and measured pilot results remain necessary before broader claims.

## 5. Demo video

**URL:** `[ADD YOUTUBE OR GOOGLE DRIVE LINK AFTER UPLOAD]`

The recording plan is in `submission-assets/demo-video-plan.md`.

## 6. Product Sharing

I used WorkBuddy as a deliberate product-quality pipeline rather than a one-shot generator. I split the work into bounded, credit-efficient roles: HY3 handled repository-scale investigation and implementation, while Kimi-K3 was paired with WorkBuddy’s UI/UX expert for high-leverage product decisions and final visual judgment. WorkBuddy traced the real Android journey, produced an evidence-backed audit, edited approved Kotlin, Compose, test, and documentation files, ran Gradle checks, installed the APK, operated a connected OnePlus through ADB, and captured device evidence. Its work changed the shipped product: voice guidance now matches the compact square Stop control, Reset remains available while an adjustment can still be reversed, and the final Reset treatment was corrected and verified on-device. I kept human approval between stages, rejected an unnecessary typed-input proposal and an overly textual microphone design, and returned focused corrections for implementation and re-verification. This taught me that WorkBuddy is most useful when it owns a clear review-to-verification loop and when every recommendation must earn acceptance through code, tests, or physical-device evidence.

## 7. Project link (optional bonus)

**URL:** `[ADD LIVE URL OR DOWNLOADABLE DEMO LINK IF AVAILABLE]`

## Portal metadata

- **Direction:** Life Agent
- **Product used:** WorkBuddy
- **Team name:** Fivecent
- **Team members:** Lu Bolin, Ethan Yap, Nathanael Leong
