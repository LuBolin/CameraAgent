# Model command-plan contract

When Qwen is enabled, one planning request contains the photographer's complete typed/transcribed request, the exact reduced clean CameraX frame, and a trusted snapshot of current camera settings and capabilities. The model returns semantic actions only; Android owns device values, execution, rollback, and capture.

The **Make it nicer** button uses this contract in auto-enhance mode. Only `ADJUST` and `FOCUS_POINT` are accepted; capture, camera switching, flash changes, and reset are rejected locally.

## Schema v3

The only successful envelope is:

```json
{"schemaVersion":3,"outcome":"PLAN","actions":[<ACTION>]}
```

One to eight ordered actions are allowed:

```json
{"type":"ADJUST","intents":["EXPOSURE_BRIGHTER","WHITE_BALANCE_WARMER"]}
{"type":"SET_CAMERA","facing":"FRONT"}
{"type":"SET_FLASH","mode":"OFF"}
{"type":"FOCUS_POINT","point_2d":[417,688]}
{"type":"RESET"}
{"type":"CAPTURE","countdownSeconds":5}
```

- `ADJUST` accepts one to three compatible directions from `EXPOSURE_BRIGHTER`, `EXPOSURE_DARKER`, `ZOOM_IN`, `ZOOM_OUT`, `WHITE_BALANCE_WARMER`, and `WHITE_BALANCE_COOLER`, with at most one direction per setting axis.
- `SET_CAMERA` accepts only `FRONT`, `REAR`, or `TOGGLE`.
- `SET_FLASH` accepts `OFF`, `ON` (flash during capture), or `TORCH` (continuous light). It is emitted only when the user explicitly mentions flash, torch, or camera light; brightness requests continue to use exposure.
- `FOCUS_POINT` accepts two integer coordinates normalized to 0 through 999. The point must identify visible, solid material belonging to the requested object, away from boundaries and empty centers.
- `FOCUS_POINT` and `SET_CAMERA` cannot appear in the same plan because the point describes only the currently active lens.
- `RESET` must be the only action. It restores exposure, zoom, white balance, flash off, and continuous autofocus without changing the selected camera.
- `CAPTURE` accepts `countdownSeconds` from 0 through 30 and must be the final action.

The only alternative envelope is an allowlisted clarification:

```json
{"schemaVersion":3,"outcome":"CLARIFY","reason":"AMBIGUOUS"}
```

Unknown versions, malformed JSON, trailing content, extra keys, unknown actions, bad ordering, incompatible settings, and out-of-range focus/countdown values fail closed and invoke the limited local fallback.

## Local execution

- Android replans semantic adjustments from fresh capabilities and telemetry; the model never supplies EV indices, zoom ratios, or white-balance device values.
- Compatible setting changes require one Apply confirmation and run as one rollback-safe transaction.
- A model focus point becomes the existing visible focus marker. The user confirms it by tapping; CameraX then focuses there locally.
- A pending plan resumes only after Apply, camera rebinding, or focus confirmation succeeds.
- Approved settings are reproduced after a planned lens switch before later actions continue; failure stops the sequence.
- Capture remains terminal and a countdown remains visible and cancellable.
- Without a valid key or usable model response, Android uses its narrower phrase parser and manual tap-to-focus fallback.
