# Photo Helper — Pitch Day Plan

The handbook contains two different pitch timings: the checklist says **5-minute presentation + 5-minute Q&A**, while the later judging-process section says **8-minute presentation + 5-minute Q&A**. Prepare a complete five-minute version because it satisfies the stricter limit; keep three optional appendix slides if organizers later confirm eight minutes.

## Five-minute presentation

| Time | Slide / action | Message |
|---|---|---|
| **0:00–0:35** | 1. Family moment and problem | “People think in outcomes, while cameras expose settings.” Introduce older adults and first-time users as the starting audience, without claiming completed user validation. |
| **0:35–2:05** | 2. Live demo | Speak the family-scene command, show plan → Apply → focus → countdown → capture, then Reset. If the hosted request stalls, switch immediately to the local backup recording. |
| **2:05–3:05** | 3. Why this is an agent | Show `voice + frame + telemetry → constrained plan → Android validation → camera → verify/rollback`. Emphasize multimodal interpretation and local camera authority. |
| **3:05–3:50** | 4. Engineering evidence | Six allowlisted action types, up to eight actions, stale-context checks, transactional rollback, 209 unit tests, and 57 physical-device tests. |
| **3:50–4:35** | 5. Value and route to market | Consumer family-camera utility first; possible OEM/SDK integration later. State what a user pilot must measure. |
| **4:35–5:00** | 6. Close | “Photo Helper does not promise AI taste. It lets people speak in outcomes, not camera settings.” End on the cover and working phone. |

## Live demo and backup

- Use the same tested phone, app build, API configuration, family framing, lighting, and command as the submitted video.
- Start with the app already open and permissions granted.
- Put the phone on Do Not Disturb and keep it powered.
- Store the backup video on both the presentation laptop and phone.
- The presenter should be able to switch to backup within five seconds, saying: “Here is the same recorded run from this device.”
- Keep a second, simpler request ready: `Make the picture brighter`, followed by a local capture.
- Do not demonstrate the experimental automatic enhancer unless a judge specifically asks.

## Likely Q&A

### Why is this an agent rather than voice shortcuts?

It grounds an open-ended request in a current image and telemetry, produces an ordered multi-step plan, waits for approval, acts on a physical device, and verifies or recovers. A fixed shortcut parser would not resolve visual references or plan across camera, focus, timing, and capture.

### Why focus on older adults without user research?

Older adults are the initial scenario because unfamiliar terminology and time-sensitive interactions make the language gap especially visible. The prototype demonstrates the technical and interaction hypothesis; direct research and measured pilots are explicitly the next validation step.

### What prevents the model from damaging camera state?

The model has no camera API. It can return only a strict semantic plan. Android reparses it, checks current capabilities and context, applies changes transactionally, and restores the exact baseline on failure or Reset.

### What happens offline or when the model fails?

The hosted interpretation fails closed. The ordinary shutter, manual focus, supported local fallback, and valid Reset remain usable. The app does not pretend an unsupported action succeeded.

### What is the privacy boundary?

Voice is push-to-talk and transcribed on-device; audio is not saved or sent to Alibaba. Hosted interpretation receives the request and reduced still frames, not continuous preview video or the full-resolution saved photo.

### What is the business model?

The first route is a focused consumer utility or accessibility-oriented camera mode. The stronger distribution route may be an OEM or SDK integration because the value sits on top of existing camera hardware. Pricing and demand should be tested after user research rather than asserted from the prototype.

### What did WorkBuddy contribute?

WorkBuddy owned a documented review-to-verification loop: repository-scale investigation, specialist UI/UX judgment, approved implementation, regression testing, APK installation, and physical-device evidence. Its findings changed the listening and Reset interactions in the shipped product.

## Three optional slides for an eight-minute version

1. Prompt contract and rejected-response examples.
2. WorkBuddy/Codex build workflow and before/after UX evidence.
3. Pilot design: task completion, time-to-capture, manual interactions, retakes, and confidence.

## One-page project brief structure

1. **Scenario:** family photo, target user, outcome-language gap.
2. **Architecture:** multimodal input, constrained plan, Android authority, rollback, privacy boundary.
3. **Business value:** initial consumer use, OEM/SDK path, defined pilot metrics.
4. **Proof:** working Android build, 209 unit tests, 57 device tests, backup demo link.
