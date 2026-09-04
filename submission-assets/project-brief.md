# Photo Helper — Project Brief

**Direction:** Life Agent  
**Product used:** WorkBuddy  
**Positioning:** A trustworthy camera-control translator for everyday family moments

## Scenario and user value

A family photo is time-sensitive. The photographer can often see what is wrong—people look dark or far away, or the camera is focused on the wrong person—but may not know terms such as exposure compensation, metering region, white balance, or zoom ratio. This is especially relevant to older adults and first-time smartphone photographers, although the problem is not age-specific.

Photo Helper lets the user state the outcome in ordinary language: “Make the picture brighter, focus on the panda, and take a photo in five seconds.” The agent translates that request into a visible, bounded camera plan and executes it with capability checks, Reset and recovery. It operates supported camera controls instead of teaching settings.

## Agent and technical architecture

```text
Push-to-talk voice + current clean frame + gridded frame + camera telemetry
                                  ↓
                  Qwen3.7 Flash semantic planner
                                  ↓
          Strict JSON: ≤8 actions from 6 allowlisted types
                                  ↓
        Android parser + stale-context + capability validation
                                  ↓
     Local CameraX/Camera2 execution → verify, rollback, or Reset
```

The native Android app uses Kotlin, Jetpack Compose, CameraX, Camera2 interop, coroutines, on-device Android speech recognition, and ML Kit face detection. The model can interpret language and visual references but has no camera API. Android alone converts semantic directions into values supported by the active lens and performs the camera action.

Settings execute transactionally: failure restores the pre-Apply state, and Reset restores the original baseline across chained changes. Plans tied to an old frame, lens, session, or capture cannot execute. Voice is push-to-talk, limited to 15 seconds, transcribed on-device, never saved, and never sent to Alibaba. Hosted interpretation receives reduced still frames rather than continuous preview video or the full-resolution saved photo.

## Business value and rollout

The initial product is a voice-controlled camera utility for families, older adults, and other casual photographers. Its differentiation is a natural-language control layer over existing phone hardware—not a tutorial, chat wrapper, or automatic beauty filter. A longer-term route could be an OEM camera mode or SDK for assisted capture in family photography, product listings, and field documentation.

The prototype demonstrates the complete real-device loop, including grounded focus, bounded camera changes, timed capture, Reset restoration, responsive layouts, encrypted key storage and voice capture. A production pilot should measure successful outcome-to-capture completion, time-to-capture, manual control interactions, retakes and user confidence.

## WorkBuddy contribution

WorkBuddy accelerated implementation and strengthened product quality through repository-scale HY3 work and specialist review across architecture, UX and QA. The resulting build, review and verification loop made WorkBuddy a substantive part of product development.
