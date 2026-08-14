# Photo Helper — Full Demo Video Script

Target runtime: **4:20**  
Format: **1920×1080, 16:9, 30 fps**  
Delivery: calm, conversational, approximately 125–135 words per minute

This is the record-ready version. Text in quotation marks is the exact narration. Directions outside quotation marks are filming and editing instructions.

## Prop scene

Build one simple tabletop scene:

- **Primary subject:** one red, orange, or otherwise distinctive soft toy. This is the focus target.
- **Secondary objects:** a plain cup and a keyboard, placed farther behind or to the side.
- **Lighting:** use one warm lamp from the side. Keep the starting image slightly dim so the exposure change is visible, but not so dark that the model cannot identify the toy.
- **Background:** uncluttered and stationary. Remove reflective screens, personal documents, notifications, and brand-heavy packaging.
- **Framing:** the toy should occupy roughly one-third of the preview. Do not let the cup overlap its face or defining features.

Use this exact hero command:

> **“Make the picture brighter, focus on the red soft toy, and take a photo in five seconds.”**

If the toy is not red, replace only `red` with its actual distinctive colour. Rehearse the exact final wording before recording.

## Full timeline and script

### 0:00–0:08 — Title and problem

**Capture/edit**

- Show `photo-helper-cover-16x9-v6-actual-ui.png` for two seconds.
- Cut to an external shot of the real phone aimed at the toy, cup, and keyboard.
- Lower-third: `PHOTO HELPER · LIFE AGENT`

**Say**

> “People usually think ‘make it brighter’ or ‘focus on that’—not ‘change exposure compensation’ or ‘set a metering point.’”

### 0:08–0:21 — Speak the real request

**Capture/edit**

- Switch to the clean phone screen recording.
- Tap **Mic** and wait for `Listening…`.
- Speak the command naturally, then tap the square Stop control.
- Subtitle the command exactly. Do not add voice-over here.

**Say live into the app**

> “Make the picture brighter, focus on the red soft toy, and take a photo in five seconds.”

### 0:21–0:35 — Introduce the product

**Capture/edit**

- Hold on the complete transcript and the interpreting state.
- If the network wait is long, shorten it with one clean cut and add `Network wait shortened`.
- On-screen label: `OUTCOME → CAMERA PLAN`

**Say**

> “This is Photo Helper, a voice-first camera-control translator for everyday photographers, especially older adults and first-time users. It operates supported camera controls for the user; it does not teach settings or pretend to have perfect photographic taste.”

### 0:35–0:58 — Show the plan and Apply

**Capture/edit**

- Show the ordered plan or exposure recommendation.
- Let the viewer read it for two seconds.
- Tap **Apply** and show the preview becoming brighter.
- Briefly place `Capability checked on device` beside the Apply action.

**Say**

> “Qwen receives the spoken request, a reduced clean camera frame, a labelled grid copy, and trusted camera capabilities. It returns a constrained semantic plan. Android checks the plan against the active lens, then asks me to approve the setting change.”

### 0:58–1:22 — Focus, countdown, and capture

**Capture/edit**

- Show the cyan focus cell or reticle on the soft toy.
- Tap the visible focus target.
- Keep the five-second countdown at normal speed.
- Use a brief external-camera cut during the countdown to prove that the physical phone is taking the shot.
- Return to the phone screen for Capture Review.

**Say**

> “The model identifies the requested object only as a cell in the supplied grid. I confirm that visible target, Android focuses locally, and the plan continues into the five-second countdown and capture.”

Let the final two countdown beats and shutter sound play without narration.

### 1:22–1:43 — Reset and reversibility

**Capture/edit**

- Show Capture Review and the compact **Reset** control.
- Tap Reset and show `Automatic camera settings restored.`
- If available, add a small before/adjusted/restored comparison.
- On-screen label: `ONE RESET → ORIGINAL BASELINE`

**Say**

> “The adjustment is reversible. Reset restores the original camera baseline, even after chained changes. Setting changes run as a transaction, so a failed control rolls back instead of leaving the camera in an unknown state.”

### 1:43–2:00 — Graceful fallback

**Capture/edit**

- Use a separately prepared offline or failed-model clip.
- Show an AI-unavailable message, then demonstrate that the shutter and manual tap-to-focus still work.
- Never show the API-key field.

**Say**

> “The hosted model is optional and fail-closed. If the key, network, response, or camera context is invalid, Photo Helper does not execute the plan. Ordinary capture, manual focus, limited local wording, and a valid Reset remain available.”

### 2:00–2:25 — Explain why it is an agent

**Capture/edit**

- Overlay this simple flow on top of the phone footage:

  `VOICE + TWO FRAMES + TELEMETRY`  
  `↓`  
  `ALLOWLISTED JSON PLAN`  
  `↓`  
  `ANDROID VALIDATES AND ACTS`  
  `↓`  
  `VERIFY · RESET · ROLLBACK`

- Briefly show `CommandContracts.kt` with the six action types highlighted.

**Say**

> “This is more than a voice shortcut or chat response. The agent observes the current scene, interprets a compound goal, orders multiple actions, pauses for approval, acts on real hardware, and verifies or recovers. The model never receives direct camera authority.”

### 2:25–2:40 — Value and limits

**Capture/edit**

- Return to the cover’s family image, then show the captured soft-toy photo for proof.
- On-screen text: `Consumer utility → Camera mode / SDK`

**Say**

> “The starting use case is everyday family photography, where a moment can pass while someone searches for the right control. A production pilot would still need to measure task completion, time-to-capture, retakes, and user confidence before making accessibility or photo-quality claims.”

### 2:40–3:08 — Codex build workflow

**Capture/edit**

- Lower-third: `BUILD REFLECTION · CODEX`
- Screen-record the real long-utterance bug report.
- Show the focused `AndroidVoiceIo` change or regression test.
- End on the green test result: `209 unit tests passed`.
- Keep each view still for at least three seconds; do not rapidly scroll.

**Say**

> “I used Codex for fast implementation and debugging loops. For example, Android speech recognition sometimes returned the end of a long command and overwrote the complete sentence. Codex traced the callback sequence, reproduced it with regression tests, fixed the selection boundary, rebuilt the app, and installed it on the phone. The current code passes 209 unit tests, Android lint, and a debug build.”

### 3:08–3:43 — WorkBuddy build workflow

**Capture/edit**

- Lower-third: `BUILD REFLECTION · WORKBUDDY`
- Show, in order:
  1. the HY3 audit packet with one evidence-backed finding;
  2. Kimi-K3 with WorkBuddy’s UI/UX expert reviewing the interaction;
  3. your correction rejecting typed input or the text-heavy microphone design;
  4. the before/after Stop or Reset UI;
  5. the `Starting 57 tests` and successful result from the device run.

**Say**

> “I used WorkBuddy differently: as an independent product-quality pipeline after the core app worked. HY3 handled repository-scale investigation and implementation, while Kimi-K3 and the UI/UX expert reviewed bounded product decisions. I approved changes between stages and rejected suggestions that did not fit the voice-first product. That process changed the listening and Reset interactions, then verified the result through 57 deterministic tests on a physical OnePlus.”

### 3:43–4:03 — Development-tool lessons

**Capture/edit**

- Show three full-screen callouts, about six seconds each:
  1. `GIVE EACH AGENT A BOUNDED ROLE`
  2. `TEST REAL HARDWARE EARLY`
  3. `REQUIRE CODE, TESTS, OR SCREENSHOTS`
- Use quiet proof footage behind each callout.

**Say**

> “My main development lessons were to give each agent a narrow role, keep specialist context focused, test real hardware early, and require code, tests, or screenshots before accepting an agent’s claim.”

### 4:03–4:20 — Close

**Capture/edit**

- Use a fast montage: transcript, Apply, focus reticle, countdown, Reset, green tests.
- Finish on `photo-helper-cover-16x9-v6-actual-ui.png`.
- Final callouts: `209 UNIT TESTS · 57 DEVICE TESTS · LOCAL CAMERA AUTHORITY`

**Say**

> “Photo Helper does not prove that AI takes better photos. It proves that everyday language can safely control real camera hardware. Photo Helper: speak in outcomes, not camera settings.”

Hold the cover silently for one final second.

## Complete capture checklist

### Phone screen recordings

- [ ] Clean idle preview of the prop scene
- [ ] Mic → Listening → full spoken command → square Stop
- [ ] Complete transcript
- [ ] Model interpreting state
- [ ] Ordered recommendation or plan
- [ ] Apply and visible exposure change
- [ ] Focus cell/reticle on the soft toy
- [ ] Tap focus confirmation
- [ ] Five-second countdown
- [ ] Capture Review
- [ ] Persistent Reset and successful restoration
- [ ] Prepared AI-unavailable fallback
- [ ] Manual tap-to-focus while hosted AI is unavailable

### External-camera footage

- [ ] Wide shot of the tabletop setup
- [ ] Hand holding the real phone toward the props
- [ ] Finger tapping Mic
- [ ] Countdown and physical capture
- [ ] Optional over-the-shoulder view showing phone and props together

### Codex footage

- [ ] Long-command truncation bug report
- [ ] Focused regression test or `AndroidVoiceIo` diff
- [ ] Green `209`-test result
- [ ] Optional successful APK installation result

### WorkBuddy footage

- [ ] HY3 audit finding
- [ ] Kimi-K3/UI-UX expert review
- [ ] Your rejection or correction between stages
- [ ] Stop or Reset before/after evidence
- [ ] Successful `57`-test physical-device result

## Recording order

Record in this order to reduce risk:

1. Two complete successful hero-flow screen recordings.
2. One external-camera take of the same flow.
3. Separate Reset close-up.
4. Separate fallback clip.
5. Codex screen recordings.
6. WorkBuddy screen recordings.
7. Voice-over, recorded after the edit has a rough timing pass.
8. Pickups for any missing transitions or proof shots.

## Honest backup version

If the complete compound request is unreliable, record these as two clearly separate demonstrations:

1. **“Make the picture brighter and focus on the red soft toy.”**
2. **“Take a photo in five seconds.”**

Introduce the second clip with a visible `SECOND REQUEST` label. Do not edit two requests to look like one successful plan.

If white balance is more visually reliable than exposure on recording day, the approved replacement is:

> **“Make the picture warmer, focus on the red soft toy, and take a photo in five seconds.”**

Use only the version that visibly succeeds on the final device.

## Editing notes

- Keep the real spoken command audio. Record the remaining narration separately.
- Caption everything and manually correct `Qwen`, `CameraX`, `Kimi-K3`, `WorkBuddy`, and `OnePlus`.
- Leave the countdown at normal speed. It proves the agent executed the timed action.
- Shorten only dead network time, and label that cut.
- Do not show the automatic-enhancement button as a core feature.
- Do not show an API key, clipboard, device serial, personal notification, or private task title.
- Keep music quiet and remove it entirely beneath the live spoken command.
- Export once, watch it muted for visual clarity, then watch it with headphones for narration balance.
