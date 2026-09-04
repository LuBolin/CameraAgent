# Photo Helper — Five-Minute Live Panel Script

Presenter: **Lu Bolin, Team Fivecent**  
Stage command: **“Make the picture brighter, focus on the panda, and take a photo in five seconds.”**  
Target finish: **5:00**

## 0:00–1:30 — Project introduction

**0:00–0:12 — Introduce the team.**

> Good afternoon. We’re Team Fivecent—Lu Bolin, Ethan Yap, and Nathanael Leong—and we built Photo Helper.

**0:12–0:35 — Establish the problem with the panda image visible.**

> People usually know the result they want: a brighter image, focus on the right subject, or enough time to join the picture. The difficult part is translating that intention into camera controls.

**0:35–1:18 — Explain the technical workflow.**

> Photo Helper is a voice-first camera agent designed for mobile phones; our current prototype runs natively on Android. The app captures speech on the phone, then combines the transcription with a reduced frame from the live scene and the camera’s current capabilities.
>
> Qwen turns those inputs into a small, ordered plan using supported actions such as brightness, zoom, white balance, focus, timer, and capture. Android remains the execution layer: it validates each step against the device, applies supported actions through the camera APIs, and preserves Reset whenever a change can be reversed.

**1:18–1:30 — Introduce Smart Mode and transition to the demo.**

> Users can state an exact outcome, or tap Smart Mode to make a one-step request: “Make this shot look nicer.” It uses the same bounded planning and validation path. Let me show you the result.

## 1:30–2:30 — Demo footage

**1:30–1:38 — Switch to the prepared demo footage.**

> This is the working prototype on a real phone. I am giving it one request that combines an adjustment, a visual focus target, and a timed capture.

**1:38–1:48 — Let the recorded command play clearly.**

> “Make the picture brighter, focus on the panda, and take a photo in five seconds.”

**1:48–2:15 — Narrate while the plan and camera actions appear.**

> The transcript, scene frame, and camera context produce the ordered plan you see here. Android validates each step, then executes the sequence: increase brightness, focus on the panda, run the countdown, and capture the photo.

**When the countdown reaches three, stop talking. Let the final beats and shutter carry the demo.**

**2:15–2:30 — Let the Smart Mode clip play and land the demo.**

> That entire flow came from one spoken request. The second clip shows Smart Mode using the same system when the user simply asks the camera to make the shot look nicer.

**Playback fallback:** Keep a second copy of the combined one-minute video on the presentation laptop. If playback fails, switch to that copy immediately.

## 2:30–4:30 — How WorkBuddy helped build it

**2:30–2:42 — Return to the deck and connect the demo to the development workflow.**

> That is the user experience. I also want to explain how WorkBuddy helped us move from an end-to-end prototype to this polished flow.

**2:42–3:10 — Explain why and how WorkBuddy was used.**

> We began with GPT-based tools because they were familiar and gave us tighter control over token usage during rapid build-and-debug loops. But setting up separate mobile-development and UI-design skills, plus their MCP integrations, became complicated. That led us to try WorkBuddy. It surprised us by turning different models and specialist roles into one structured product-quality workflow.

**3:10–3:43 — Stage one: mobile engineering review.**

> First, HY3 worked with the Mobile Application Developer expert. We supplied the Android workspace, product documents, screenshots, tests, and intended journey. It traced the experience from onboarding and permissions through voice input, AI planning, camera actions, and failure recovery. For every issue, it had to explain user impact, point to implementation evidence, and recommend the smallest credible fix.

**3:43–4:05 — Stage two: user-interface review.**

> Next, Kimi-K3 worked with the UI Designer expert as a fresh pair of eyes. This review focused on comprehension, accessibility, and recovery. It helped us align the listening guidance with the compact square Stop button and keep Reset available whenever an adjustment could still be reversed.

**4:05–4:20 — Stage three: implementation and device verification.**

> Finally, HY3 returned with the Software Workshop expert. It implemented the selected changes, rebuilt and installed the APK, and verified the result on a physical OnePlus phone.

**4:20–4:30 — Summarize the value of the workflow.**

> So WorkBuddy gave us more than suggestions. It created a repeatable loop—investigate, challenge, implement, and verify—with each specialist focused on where it added the most value.

## 4:30–5:00 — Future work and close

**Show the future-work slide.**

> Three gaps define the next version. For focus, replace the grid with an object mask. For speed, move commands on-device and accelerate image understanding. For aesthetics, capture a rapid burst with several safe configurations and let the user choose the best result. That choice becomes the preferred example; the alternatives become rejected examples for post-training. Across many choices, Smart Mode learns a shared baseline, then personalizes its ranking for each user. Precise, responsive, personal. Thank you.

## On-stage delivery notes

- Keep the opening accessible, but use the added technical detail to establish why this is an agent rather than a voice shortcut.
- Let the prepared footage control the demo pace. Do not pause between clips unless the one-minute export already includes the pause.
- During the demo, narrate only over processing or silent footage. Protect the spoken command and final three countdown beats.
- Spend the full two minutes on WorkBuddy; this is the presentation’s main development story.
- Rehearse at approximately 120 words per minute. Do not speed up to recover lost time; use the cut line below.
- Do not mention test counts, changelog language, or the missing WorkBuddy log.
- **Cut line if running long:** omit “the alternatives become rejected examples for post-training.”

### Pacing check at 120 WPM

| Section | Spoken words | Spoken time | Timing note |
|---|---:|---:|---:|
| Project introduction | 173 | 1:27 | Leaves about 0:03 for the demo handoff |
| Demo footage | 108 | 0:54 | Leaves about 0:06 for countdown and shutter |
| WorkBuddy | 236 | 1:58 | Keep the handoff between slides immediate |
| Future work and close | 76 | 0:38 | Connects rapid capture, user choice, and post-training |
| **Total** | **593** | **4:57 spoken** | **Approximately 5:00 with very tight transitions and protected demo beats** |
