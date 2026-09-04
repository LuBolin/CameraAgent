# Photo Helper — Demo Day Readiness

## Demo links

- Public repository: https://github.com/LuBolin/CameraAgent/
- Release page: https://github.com/LuBolin/CameraAgent/releases/tag/v0.1.0
- APK: https://github.com/LuBolin/CameraAgent/releases/download/v0.1.0/Photo-Helper-v0.1.0-demo.apk
- Existing video: https://www.youtube.com/watch?v=3WSyFYj25hw
- Draft offline fallback: `demo_videos/photo-helper-backup-demo-under-3min.mp4` (2:48). Replace this trimmed-presentation cut with the structure in `demo-video-plan.md` before final submission.

No login is required for the repository, release page or video. The Android app requires a Bailian-compatible API key for live semantic planning.

## Device and setup requirements

- Android 12 / API 31 or newer.
- Rear camera, microphone and working speakers.
- Camera and microphone permissions granted before stage time.
- Stable Wi-Fi or mobile data.
- API key saved and tested in the installed build.
- Phone charged above 70%, Do Not Disturb enabled, screen timeout extended, rotation locked and brightness raised.
- Demo subject staged in a well-lit area; use the same panda soft toy and the exact rehearsed command.
- Keep the APK, backup recording and one-page PDF available offline on both the phone and the presentation laptop.

## Required owner checks before Thursday

- **APK readiness:** install the release APK on the exact stage phone, cold-launch it, grant permissions, and complete two consecutive full runs.
- **API quota:** check the provider console for remaining balance/rate limits, then perform three consecutive requests on the event network or hotspot. This cannot be verified from the repository alone.
- **Fallback:** after the new 2:40–2:50 cut is exported, confirm it plays offline with audible narration on the presentation laptop and phone. It must open on the real panda flow, include Smart Mode, and end on the current one-pager.
- **Links:** open every public URL in a signed-out/private browser window.
- **Hardware:** pack the phone, charger/power bank, data cable, USB-C/HDMI adapter if required, and a second hotspot-capable device.

## Stage runbook

1. Open Photo Helper before being introduced.
2. Frame the panda and confirm the preview is stable.
3. Say: “Make the picture brighter, focus on the panda, and take a photo in five seconds.”
4. Narrate the bounded plan while the action runs.
5. Point out Stop, Reset and the Smart Mode sparkle button; do not run a second live scenario.
6. If progress stalls for 10 seconds, say the fallback line and play the recording.

## Backup demo video: exact 2:50 storyboard

Record one clean landscape screen capture on the final stage phone. Keep the real spoken command and five-second countdown at normal speed. Target **2:40–2:50**; never exceed 2:55.

| Time | Picture | Narration / on-screen message |
|---|---|---|
| **0:00–0:10** | Open directly on the real phone aimed at the panda. Brief title over live footage. | “People know the photo they want, but not always the camera setting that creates it.” |
| **0:10–0:22** | Clean ready state. Mic, shutter, sparkle and panda are visible. | “Photo Helper turns a spoken outcome into a safe, reversible camera plan on Android.” |
| **0:22–0:37** | Tap Mic and say the full request. Show the square Stop control. | Say live: “Make the picture brighter, focus on the panda, and take a photo in five seconds.” |
| **0:37–0:55** | Complete transcript, then Qwen interpreting. Shorten only dead network time. | “Qwen combines the request with a clean planning frame and camera telemetry.” Add `NETWORK WAIT SHORTENED` if edited. |
| **0:55–1:25** | Exposure change, yellow reticle on the panda, countdown and capture. | “Android checks the plan against the action contract and the active camera, then executes locally.” Let the final countdown beats play cleanly. |
| **1:25–1:40** | Capture result, Stop/Reset close-up and restored baseline. | “The user remains in control. Stop interrupts a run, and Reset restores the baseline.” |
| **1:40–1:58** | Smart Mode sparkle button and conservative result. | “Smart Mode gives users a one-tap path when they know something looks wrong but not what to ask.” |
| **1:58–2:18** | Simple flow: `SPEAK → PLAN → CHECK → ACT → RESET`. | “AI proposes. Android validates and acts. The human starts, stops and resets.” |
| **2:18–2:33** | `HY3 IMPLEMENTATION → SPECIALIST REVIEW → PRODUCT VERIFICATION`. | “WorkBuddy accelerated implementation and strengthened product quality across architecture, UX and QA.” |
| **2:33–2:45** | Completed photo, target users and OEM/SDK path. | “Photo Helper starts with everyday photographers and can grow into an assistive camera mode or SDK.” |
| **2:45–2:50** | Final one-pager. | “Photo Helper: speak in outcomes, not camera settings.” |

### Video editing rules

- Use captions throughout; manually correct `Qwen`, `CameraX`, `WorkBuddy`, `HY3` and `Kimi-K3`.
- Do not speed up the spoken request, countdown or shutter.
- Shorten network waiting only, and label the edit.
- Do not show the API key, notifications, device serial, clipboard or personal accounts.
- Keep one device, one orientation, one panda and one command throughout the hero flow. Record Smart Mode as a clearly separate supporting clip.
- Do not splice two requests to look like one. If the compound request is unreliable, show two clips labelled `REQUEST 1` and `REQUEST 2`.
- Export H.264/AAC MP4, 1080p or the phone’s native landscape resolution, with captions readable on a projector.
- Watch once muted for visual clarity and once with headphones for narration balance.

## Screenshot pack: five-frame core flow

The prepared pack is in `demo-day-screenshots/final-core-flow/`, extracted directly from `demo_videos/panda.mp4`. The source recording contains two honest requests—first focus, then brightness plus capture—so the screenshots preserve that sequence rather than pretending it was one compound request.

1. **`01-voice-input.png` — Voice input**  
   Listening state with the square Stop control visible over the panda scene.

2. **`02-ai-planning.png` — The agent interprets**  
   `Looking at the scene with Qwen…` with `Focus on the panda` readable.

3. **`03-grounded-focus.png` — The phone acts**  
   Yellow reticle clearly on the panda while the focus action is applying. This is the strongest screenshot and belongs on the poster too.

4. **`04-capture-request.png` — Adjustment and capture**  
   The second request, `Make it slightly brighter and take a picture`, visibly executing with Reset available.

5. **`05-ready-with-reset.png` — Completion and recovery**  
   Captured-photo review with `CAPTURED`, `Original remains saved` and Reset clearly visible.

### Screenshot export rules

- Crop away desktop chrome, editing timelines and black padding that is not part of the phone capture.
- Preserve the full app UI; do not place presentation text over transcripts, focus reticles or controls.
- Use descriptive filenames above, PNG format and identical pixel dimensions.
- Do not add fake reticles, transcripts or success states in editing.
- If only three screenshots are submitted, use `02-ai-planning`, `03-grounded-focus` and `05-ready-with-reset`.
- `demo-day-screenshots/optional-smart-mode.png` is available separately. Do not mix it into the core pack unless product breadth matters more than visual continuity.

## Copy-paste demo access sheet

```text
PHOTO HELPER — DEMO ACCESS

Repository: https://github.com/LuBolin/CameraAgent/
Release: https://github.com/LuBolin/CameraAgent/releases/tag/v0.1.0
APK: https://github.com/LuBolin/CameraAgent/releases/download/v0.1.0/Photo-Helper-v0.1.0-demo.apk
Public video: https://www.youtube.com/watch?v=3WSyFYj25hw

Login required for links: None
Live app dependency: Bailian-compatible API key, preconfigured privately on the demo phone
Do not publish/share: API key

Device: Android 12 / API 31 or newer
Permissions: Camera + microphone
Connectivity: Stable Wi-Fi/mobile data; backup hotspot recommended
Orientation: Landscape, rotation locked
Stage setup: App already open, permissions granted, key tested, panda framed
Offline fallback: photo-helper-backup-demo-under-3min.mp4
Estimated setup time: 2 minutes after the APK is installed
```

## Thursday delivery folder

```text
Photo-Helper-Demo-Day/
  Photo-Helper-One-Pager.pptx
  Photo-Helper-One-Pager.pdf
  Photo-Helper-Backup-Demo-2m45s.mp4
  demo-access.txt
  sources.txt
  screenshots/
    01-voice-input.png
    02-ai-planning.png
    03-grounded-focus.png
    04-capture-request.png
    05-ready-with-reset.png
```

## Product claims and sources

- **At most eight ordered actions:** `CommandContracts.kt` (`MAX_ACTIONS = 8`).
- **Six allowlisted action types:** `CommandContracts.kt` (`allowedTypes`).
- **WorkBuddy workflow:** `submission-assets/project-brief.md`, `submission-assets/demo-video-plan.md`, and `submission-assets/demo-video-full-script.md` document HY3 implementation and specialist review across architecture, UX and QA.

No quantified user-impact claim is currently supported. Present task completion, time-to-capture, retakes, manual interactions and confidence as planned pilot metrics—not achieved improvements.

Use this exact text in `sources.txt`:

```text
QUANTIFIED IMPACT CLAIMS

Photo Helper currently makes no quantified claim that it improves photo quality,
reduces time-to-capture, reduces retakes, or improves usability.

Planned pilot measures:
- successful outcome-to-capture completion
- time to capture
- manual camera-control interactions
- retakes
- user confidence

Technical implementation facts are sourced from the repository:
- Maximum plan length: CommandContracts.kt, MAX_ACTIONS = 8
- Allowlisted action types: CommandContracts.kt, allowedTypes

Any future percentage or comparison will be added only with the study method,
sample size, baseline, date and source URL/report.
```

## 15-second social-media answer

“Photo Helper lets you tell your phone the photo you want instead of hunting through camera settings. It turns a spoken goal into a safe, reversible camera plan on a real Android device. WorkBuddy helped us review, improve and verify the experience.”

## 30-second social-media answer

“We built Photo Helper because people think in outcomes—‘make it brighter, focus on Mum, and take the photo when I’m ready’—not in camera settings. The agent combines voice, the live frame and device capabilities, then Android validates and executes a bounded plan. WorkBuddy was central to our quality loop: it investigated the code, implemented focused changes, coordinated specialist review and helped us verify the final interaction on a physical phone.”
