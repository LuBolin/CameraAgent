# Photo Helper — Demo Day Presentation Script

Format: **5-minute presentation/demo + 3-minute Q&A**  
Presenter: **Lu Bolin, Team Fivecent**  
Stage command: **“Make the picture brighter, focus on the panda, and take a photo in five seconds.”**

## Five-minute presentation

### 0:00–0:25 — Hook

**Show:** The one-page poster, with the panda image visible.

**Say:**

> “People know the photo they want. They say, ‘make it brighter,’ ‘focus on the panda,’ or ‘give me five seconds.’ But cameras still make them translate that goal into exposure, focus and timer controls.
>
> Photo Helper removes that translation step.”

### 0:25–0:50 — Product promise

**Show:** Point briefly to the problem and solution on the poster, then move to the prepared phone.

**Say:**

> “Photo Helper is a voice-first camera agent for Android. It combines a spoken goal, the live frame and the phone’s current capabilities, then turns them into a safe camera plan.
>
> You describe the result. Photo Helper handles the controls.”

### 0:50–2:15 — Live panda demo

**Show:** The app is already open in landscape. The panda is framed, permissions are granted and the API key is tested.

**Say before tapping:**

> “This is the real APK running on a real Android phone. I will give it one compound request.”

**Tap the microphone and say clearly:**

> “Make the picture brighter, focus on the panda, and take a photo in five seconds.”

**As the request is interpreted and executed, say:**

> “Photo Helper combines my request with a clean planning frame and current camera telemetry. Qwen returns semantic actions—not direct camera commands.
>
> Android checks the plan against a strict action contract and the capabilities of this camera. It then applies the supported change, places focus on the panda, runs the countdown and captures.”

**Let the last three countdown beats and shutter sound play without narration.**

**After capture, show Reset and say:**

> “The interaction stays reversible. Stop interrupts an active run, and Reset restores the camera baseline.”

**Fallback if the request has not progressed after 10 seconds:**

> “I’m switching to the recorded run from this same device.”

Play the backup immediately; do not troubleshoot on stage.

### 2:15–2:35 — Smart Mode

**Show:** Point to the sparkle button. Do not start a second live network request unless rehearsal has proven it reliable.

**Say:**

> “If the user knows something looks wrong but does not know what to ask for, Smart Mode offers a one-tap path: ‘Make this shot look nicer.’ Qwen checks the scene and can conservatively adjust exposure, colour, framing or focus through the same bounded controls.”

### 2:35–3:20 — Why this is an agent

**Show:** Return to the poster’s `SPEAK → PLAN → CHECK → ACT → RESET` flow.

**Say:**

> “The innovation is the grounded agent loop.
>
> The model interprets language in the context of the current scene. It proposes an ordered semantic plan. Android reparses that plan, validates it against the active device and executes through CameraX.
>
> The boundary is simple: AI proposes. The phone decides. The human starts, stops and resets.”

### 3:20–3:55 — WorkBuddy’s role

**Show:** Point to the WorkBuddy section on the poster. If desired, show one clean WorkBuddy workflow screenshot for no more than eight seconds.

**Say:**

> “WorkBuddy accelerated implementation and strengthened product quality. HY3 supported repository-scale implementation, while specialist review covered architecture, UX and QA.
>
> That created a repeatable build, review and verification loop around the product.”

### 3:55–4:35 — Business value

**Show:** Return to the phone and the completed panda photo.

**Say:**

> “The starting users are families, casual photographers, older adults and anyone who knows the outcome they want but not the camera vocabulary.
>
> The stronger distribution path is an assistive camera mode or SDK for device makers, camera apps and accessibility suites. The next pilot measures successful completion, time to capture, retakes, manual interactions and confidence.”

### 4:35–5:00 — Close

**Show:** End on the one-page poster.

**Say:**

> “Photo Helper turns everyday language into safe action on real camera hardware.
>
> One spoken goal. A safe camera plan.
>
> Photo Helper: speak in outcomes, not camera settings.”

Stop speaking by 4:55.

## Three-minute Q&A bank

### How is this different from fixed voice shortcuts?

> “A shortcut maps one phrase to one command. Photo Helper grounds an open-ended request in the current frame and device telemetry, then creates an ordered plan. That lets it resolve a visible subject and combine focus, camera adjustments, timing and capture in one request.”

### What prevents unsafe or unsupported actions?

> “The model has no direct camera API. Android accepts only a strict semantic contract, rejects malformed or stale plans, checks current capabilities and constrains every supported value before CameraX executes it.”

### What happens if the model or network fails?

> “The hosted planner fails closed. The camera remains usable, and the user retains Stop, Reset, the shutter and manual focus. A production path would combine on-device handling for common requests with cloud planning for harder visual requests.”

### What is the privacy boundary?

> “Voice transcription stays on-device. Planning uses the transcribed request, still planning frames and camera telemetry—not continuous preview video or the saved full-resolution photo. A production release would add explicit retention controls and a server-side credential layer.”

### What did WorkBuddy contribute?

> “WorkBuddy accelerated implementation and added structured specialist review across architecture, UX and QA. It turned agent assistance into a repeatable product-quality workflow rather than a single generation step.”

### Who pays for this?

> “The strongest route is B2B2C: an assistive mode or SDK licensed to phone makers, camera applications or accessibility suites. That puts the capability inside a camera surface people already use.”

### Have you proven quantified user impact?

> “The prototype proves the interaction and technical boundary on real hardware. The next pilot measures task completion, time to capture, retakes, manual interactions and confidence against the existing camera experience.”

### Why does the prototype use a user-supplied API key?

> “It is a prototype deployment choice that makes multimodal planning directly testable. Production would use a server-side token broker, rate limits, device attestation and short-lived credentials.”

## Rehearsal rules

- Keep the panda, lighting, phone orientation and command identical across rehearsals, the live demo and the backup recording.
- Start with the app open, permissions granted and the API key already tested.
- Do not explain setup, test counts or internal changelogs on stage.
- Use one live request. Smart Mode is a short supporting feature, not a second risky live demo.
- If the live request stalls for 10 seconds, switch immediately to the backup recording.
- Rehearse to finish by 4:50–4:55.
