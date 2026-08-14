# Photo Helper — 3–5 Minute Demo Video Plan

For the complete word-for-word narration and filming checklist, use `demo-video-full-script.md`.

Target runtime: **4:15**  
Format: **1920×1080, 16:9, 30 fps**  
Style: explicit project overview, uninterrupted product demo, then build reflection and tool tips

## Requirement coverage

| Required section | Time | Purpose |
|---|---:|---|
| **Project overview** | 0:00–0:30 | Name the user, problem, positioning, and product promise. |
| **Core Agent features and usage** | 0:30–2:30 | Show one complete request plus Reset, fallback, and the model/device boundary. |
| **Build reflection and development-tool tips** | 2:30–4:05 | Show how Codex and WorkBuddy contributed, what changed, and the reusable lessons. |
| **Close** | 4:05–4:15 | Restate the narrow value proposition and project name. |

## Judging-criteria coverage

| Criterion | Weight | Evidence the video must show |
|---|---:|---|
| **AI innovation** | 30% | One spoken request grounded in a real frame becomes a multi-step semantic plan; show that the model interprets language and vision instead of merely generating text. |
| **Technical Excellence** | 20% | Show the strict action contract, Android-only camera authority, live capability validation, Reset/rollback, and verified test results. |
| **User Experience & Demo** | 25% | Keep the product flow smooth and readable: speak once, review the plan, Apply, see focus/countdown/capture, then Reset. Use captions and real-device footage. |
| **Business Value & Viability** | 25% | Ground the story in a family photo and the outcome-language gap; state the realistic consumer/OEM path and the need for a measured user pilot. |

The product itself occupies the first 2:30 because AI innovation, UX, and viability together carry 80% of the score. Codex and WorkBuddy appear as concise evidence of build quality and tool mastery, not as the subject of the project.

## The story in one sentence

Everyday photographers—including many older adults—think in outcomes such as “make it brighter,” not camera terms such as “increase exposure compensation.” Photo Helper translates between the two and safely operates the real camera.

## Positioning guardrails

- Call Photo Helper a **camera-control translator** or **camera co-pilot**, not an AI photographer.
- Say it is for everyday photographers, **especially older adults and first-time users**. Do not claim that it has been clinically or statistically validated for elderly users.
- Say that it **operates supported controls for the user**. Do not describe it as a settings tutorial or a photoshoot director that understands light direction and shooting angle.
- Promise translation, bounded execution, capability checks, and Reset. Do not promise consistently beautiful photos.
- Treat `Make this shot look nicer` as an experimental secondary check. Do not use it as the hero feature or as evidence of model taste.
- Keep the hero demo user-directed: the photographer states the desired outcome and the agent performs only those requested actions.

## Recommended pacing and script

| Time | What viewers see | Suggested narration |
|---|---|---|
| **0:00–0:08 — Overview** | Start on the cover for one second, then cut immediately to the real phone and subject. Add a small lower-third: `PROJECT OVERVIEW`. | “Most people think ‘make it brighter’ or ‘zoom in’—not ‘change exposure compensation.’ That gap is especially real for older adults and first-time photographers.” |
| **0:08–0:20 — Overview** | Frame two or three family members, with one wearing a clearly visible red top. Show the app listening while you say: **“Focus on the person in red, make the picture brighter, and take a photo after three seconds.”** Let the real command audio play without narration over it. | The spoken product command is the narration. Subtitle it exactly. |
| **0:20–0:30 — Overview** | Hold on the transcript/request entering the agent. Brief title overlay: `Camera-control translator`. | “Photo Helper translates the outcome you describe into safe, reversible camera actions. It does not claim to be an AI photographer.” |
| **0:30–1:15 — Core features and usage** | Add lower-third: `CORE AGENT`. Show the request becoming an ordered plan, then setting recommendation → Apply → blue focus cell/reticle → focus confirmation → three-second countdown → Capture Review. Keep the model wait honest, but trim dead time and label it `Network wait shortened`. | “Qwen receives the request, a clean frame, and a gridded copy. It can return only a strict ordered plan. Android checks every action against the active camera, asks for approval where needed, focuses locally, and captures the photo.” |
| **1:15–1:40 — Core features and usage** | Show a visible user-requested exposure, zoom, or white-balance change, then the persistent **Reset** button and successful restoration. Use a brief before/after split-screen if available. | “Changes are bounded and reversible. Reset restores the original baseline, even after chained adjustments. If a control fails, the transaction rolls back instead of leaving the camera in an unknown state.” |
| **1:40–2:00 — Core features and usage** | Show graceful fallback: no AI key or a prepared failed request while shutter and manual tap-to-focus remain usable. Do not linger in Settings. | “The hosted model is optional and fail-closed. Capture, local fallback, manual focus, and Reset remain usable when the key, network, or model is unavailable.” |
| **2:00–2:18 — Core features and usage** | Overlay one simple flow on real footage: `Voice + two frames` → `Allowlisted JSON plan` → `Android validates and acts`. Briefly show `CommandContracts.kt` with the six action types highlighted. | “The model proposes semantic actions but never receives camera authority. Android rejects extra keys, stale context, invalid combinations, free-form coordinates, and unsupported values.” |
| **2:18–2:30 — Business value** | Return briefly to the family photo and show the final saved result or Capture Review. | “The first market is everyday family photography; the same bounded control layer could later become an OEM camera mode or SDK. A real pilot would measure task completion, time-to-capture, retakes, and confidence.” |
| **2:30–2:58 — Build reflection** | Add lower-third: `BUILD REFLECTION`. Screen-record Codex: a real bug report, one focused test/code diff, and the green test result. Use the long-utterance or focus-reticle fix. | “I used Codex for rapid implementation and debugging loops: trace the failing path, write a regression test, make a focused change, build, and install on the phone. The current code passes 209 unit tests, Android lint, and a clean debug build.” |
| **2:58–3:35 — Build reflection** | Screen-record WorkBuddy: HY3 audit → Kimi-K3 with UI/UX expert → your approval/correction → focused implementation → physical-device evidence. | “I used WorkBuddy as an independent product-quality pipeline. HY3 handled repository-scale investigation and implementation; Kimi-K3 and the UI/UX expert made bounded product decisions and checked the final result. I kept human approval between stages and rejected suggestions that did not fit the product.” |
| **3:35–3:50 — Build reflection** | Before/after evidence: incorrect `Done` guidance → square Stop guidance; transient Reset → persistent compact Reset. End on `57/57`. | “That workflow changed the shipped product, and WorkBuddy’s deterministic device pass finished 57 out of 57 tests.” |
| **3:50–4:05 — Development-tool tips** | Three short full-screen callouts over proof footage: `Keep tasks bounded`, `Test on real hardware early`, `Require evidence before accepting agent output`. | “My main lessons were to give each agent a narrow role, keep expensive specialist context small, test device behavior early, and require code, tests, or screenshots before accepting a claim.” |
| **4:05–4:15 — Close** | Fast proof montage, then return to the generated cover. Keep only three callouts: `209 unit tests`, `57 device tests`, `Local camera authority`. | “This does not prove that AI takes better photos. It proves that everyday language can safely control real camera hardware. Photo Helper: speak in outcomes, not camera settings.” |

## What to film

### Phone footage

Record each state as a separate 10–30 second clip. Do not try to capture the entire video in one take.

1. Clean idle camera screen aimed at a visually clear subject.
2. Mic tap and active `Listening… / Speak now… / ■` state.
3. The full family-scene compound command and transcript.
4. Setting recommendation and Apply.
5. Model-selected focus grid cell and visible reticle.
6. Countdown and capture.
7. Capture Review.
8. A visible user-requested adjustment followed by persistent Reset and successful restoration.
9. One graceful failure/fallback state.
10. Landscape and 200% text-scale UI for the proof montage.

Use two recordings where practical:

- **Phone screen recording:** clean, readable UI for most of the demo.
- **External camera:** three or four short shots proving that this is a real phone controlling a real camera scene.

### Codex footage

Capture short, intentional clips rather than scrolling quickly through an entire task:

1. The user-reported bug or requested change.
2. Codex tracing the relevant seam.
3. One small test/code diff.
4. The green test result or successful phone installation.

Hide unrelated task titles, notifications, usernames, local paths, API keys, and terminal history.

### WorkBuddy footage

Show the workflow as a sequence:

1. `HY3_AUDIT_PACKET.md` with an evidence-backed finding visible.
2. Kimi-K3 with the UI/UX expert selecting the useful fixes.
3. Your human correction rejecting the text-heavy microphone proposal or typed-input scope.
4. `WORKBUDDY_CHANGELOG.md` or the focused implementation diff.
5. Physical-device screenshots and the final `57/57` verification result.

Do not spend time explaining model pricing. The useful build tip is the **division of labor**: inexpensive long-context work for investigation and implementation, stronger specialist judgment for bounded UI/UX decisions.

## Recording setup

- Record the phone vertically at its native resolution; place it inside the 16:9 edit with a dark background rather than stretching it.
- Use 1080p/30 fps for screen recordings and external footage. Lock the external camera’s exposure and focus so brightness does not pulse.
- Record the actual command audio live because voice is a product input. Record the remaining narration separately afterward for cleaner pacing.
- Use a quiet room and keep the phone at a natural portrait distance. Give the model an unambiguous visual reference, such as one family member in a bright red top, and avoid identifying people by a name the model cannot know.
- Put the API key into the app before recording. Never show the key-entry screen, clipboard, device serial, or secret-bearing logs.
- Turn on Do Not Disturb and hide personal notifications on both the phone and computer.
- Capture two successful takes of the complete product flow before editing.
- Do not tap the sparkle/automatic-enhancement control in the main demo. If you mention it at all, label it `Experimental` and keep it under five seconds.

## Editing rules

- The first working product action must appear within **12 seconds**.
- Overview plus product usage should occupy roughly **2:30**; build reflection and tool tips should occupy roughly **1:35**.
- Keep cuts purposeful. Use simple jump cuts, crossfades, crop zooms, and two or three on-screen callouts—no elaborate transitions.
- Subtitle the exact spoken camera command.
- Trim network waiting, but label the edit. Never reorder steps or imply a result the app did not produce.
- Keep code or report text on screen for at least three seconds and highlight only one relevant line at a time.
- Add quiet, royalty-free background music only if it does not compete with speech. Lower it substantially during the spoken product command.
- Use captions throughout; check them manually for `Qwen`, `CameraX`, `Kimi-K3`, and `WorkBuddy`.
- Finish at **4:05–4:25**. Do not fill the five-minute limit merely because it exists.

## Backup plan

Because the final deliverable is prerecorded, do not rely on a live model call during the final edit. Keep:

- one complete successful screen recording;
- one external-camera take of the same flow;
- a clean idle-state clip for voice-over;
- still screenshots of focus, Reset, and Capture Review;
- a screen recording of the green test results.

If the compound command is unreliable on recording day, use two honest flows: first `Focus on the person in red and make the picture brighter`, then `Take a photo after three seconds`. Do not splice unrelated states to simulate one successful request.

## Final review checklist

- Runtime is between 3:00 and 5:00.
- The product works on screen before development tools are discussed.
- The exact problem and target user are clear: everyday photographers, especially older adults and first-time users.
- Voice, vision, planning, local execution, capture, and Reset are all visible.
- The model/local-authority boundary is explained in one sentence.
- Codex and WorkBuddy each have a distinct, truthful role.
- WorkBuddy proof includes its UI/UX expert and an output that changed the product.
- No secret, device serial, personal notification, or private path is visible.
- Captions are readable on a phone-sized player.
- The video does not claim that Photo Helper reliably makes photos beautiful or that the older-adult use case has already been validated by user research.
- The final frame uses `photo-helper-cover-16x9-v6-actual-ui.png`.
