# Photo Helper — Pitch Day Plan

Confirmed format: **5-minute presentation/demo + 3-minute Q&A**.

## Five-minute presentation

| Time | Slide / action | Message |
|---|---|---|
| **0:00–0:50** | Poster: problem and promise | “People know the photo they want, but cameras expose controls.” Introduce Photo Helper as the translator between an outcome and a safe camera plan. |
| **0:50–2:15** | Live panda demo | Speak the compound panda command, show planning → adjustment → focus → countdown → capture, then Reset. Switch to backup after 10 seconds of no progress. |
| **2:15–2:35** | Smart Mode | Point to the sparkle button and explain the one-tap `Make this shot look nicer` path. Do not start a second live request. |
| **2:35–3:20** | Why this is an agent | Use the poster flow: `SPEAK → PLAN → CHECK → ACT → RESET`. Land `AI proposes · Phone decides · Human controls`. |
| **3:20–3:55** | WorkBuddy | “WorkBuddy accelerated implementation and strengthened product quality.” Support it with HY3 implementation and specialist review across architecture, UX and QA. |
| **3:55–4:35** | Value and route to market | Everyday photographers first; assistive camera mode or OEM/SDK integration next. Name the planned pilot measures. |
| **4:35–5:00** | Close | “One spoken goal. A safe camera plan. Speak in outcomes, not camera settings.” |

## Live demo and backup

- Use the same tested phone, app build, API configuration, family framing, lighting, and command as the submitted video.
- Start with the app already open and permissions granted.
- Put the phone on Do Not Disturb and keep it powered.
- Store the backup video on both the presentation laptop and phone.
- The presenter should be able to switch to backup within five seconds, saying: “Here is the same recorded run from this device.”
- Keep a second, simpler request ready: `Focus on the panda`, followed by a local capture.
- Mention Smart Mode by pointing to the sparkle button. Use the prepared Smart Mode clip if a judge asks to see it.

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

WorkBuddy accelerated implementation and added structured specialist review across architecture, UX and QA. It created a repeatable build, review and product-verification loop.

## Three optional slides for an eight-minute version

1. Prompt contract and rejected-response examples.
2. WorkBuddy/Codex build workflow and before/after UX evidence.
3. Pilot design: task completion, time-to-capture, manual interactions, retakes, and confidence.

## One-page project brief structure

1. **Scenario:** family photo, target user, outcome-language gap.
2. **Architecture:** multimodal input, constrained plan, Android authority, rollback, privacy boundary.
3. **Business value:** initial consumer use, OEM/SDK path, defined pilot metrics.
4. **Proof:** working Android build, physical-device flow, bounded authority model and backup demo link.
