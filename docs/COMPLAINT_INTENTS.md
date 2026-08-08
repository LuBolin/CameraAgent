# Complaint intent contract

A Complaint is language from the photographer. A Control Intent is only a semantic direction. It cannot execute until Android checks the original text, camera session, capabilities, telemetry, and current observation and creates a Recommendation.

## Core allowlist

| Control Intent | Example complaint | Local plan |
|---|---|---|
| `EXPOSURE_BRIGHTER` | “The whole photo is too dark.” | Bounded positive exposure compensation |
| `EXPOSURE_DARKER` | “The whole photo is too bright.” | Bounded negative exposure compensation |
| `ZOOM_IN` | “This is too zoomed out.” | Increase digital zoom by at most `1.25x` |
| `ZOOM_OUT` | “This is too zoomed in.” | Decrease digital zoom by at most `1.25x` |
| `WHITE_BALANCE_WARMER` | “The whole image is too blue.” | Qualified warmer AWB preset |
| `WHITE_BALANCE_COOLER` | “The whole image is too yellow.” | Qualified cooler AWB preset |
| `FOCUS_POINT_REQUIRED` | “Focus missed.” | Show the generic tap-to-focus target; the user supplies the point |
| `LEVEL_FRAME` | “The photo is crooked.” | Measured roll guidance |

`WHITE_BALANCE_AUTO` is also understood locally for an explicit Auto request. It is not part of the initial hosted-model allowlist.

## Hosted-model output

The initial hosted classifier may return exactly one of these JSON objects, with no Markdown, prose, confidence, numeric value, coordinate, command, or extra field:

```json
{"schemaVersion":1,"outcome":"INTENT","intent":"EXPOSURE_BRIGHTER"}
```

```json
{"schemaVersion":1,"outcome":"CLARIFY","reason":"BLUR_TYPE"}
```

`INTENT` accepts only the eight core labels above. `CLARIFY` accepts `AMBIGUOUS`, `NEGATED_DIRECTION`, `CONFLICTING_DIRECTIONS`, `MULTIPLE_COMPLAINTS`, `REGIONAL_REQUEST`, `BLUR_TYPE`, or `ZOOM_OR_DISTANCE`. Wrong versions, malformed JSON, trailing content, extra keys, or unknown labels fail closed.

Explicit ISO, shutter, exposure-time, and noise requests are rejected locally before model use. They remain advisory until manual exposure is qualified as one coupled transaction.

## Local execution gates

- Negated, conflicting, multi-axis, regional, blur-ambiguous, and distance-ambiguous wording cannot execute.
- Exposure and white balance require a whole-frame complaint. A named region never receives a global adjustment silently.
- Exposure requires supported compensation, known telemetry, and an in-range target.
- Zoom requires explicit zoom wording, a reported ratio range, and room in the requested direction.
- White balance requires a supported qualified preset; Kelvin values are not accepted.
- Focus requires live preview and supported AF metering. The model never supplies the tap point.
- Level guidance requires current roll telemetry.
- Apply is offered only after all gates pass. Reset restores the first coached baseline in the current camera session.
- Focus, exposure, zoom, white balance, and level never require a face.

## Non-executable boundaries

| Complaint | Result |
|---|---|
| “Blurry” / “too blur” | Clarify motion blur versus missed focus |
| “Too close” | Clarify zoom, physical perspective, or frame occupancy |
| “Not too dark” / “isn’t overexposed” | Clarify; no opposite direction is inferred |
| “Too dark and too bright” | Clarify; no first match executes |
| “Too dark and too blue” | Ask for one change at a time |
| “Sky too bright” / “skin too blue” | Clarify whole frame versus named region |
| “Set ISO 100” / “use 1/500 s” | Advisory; exposure compensation is not substituted |
| “Too noisy” / “grainy” | Advisory until ISO and shutter are qualified together |
| Non-person subject position or distance | Manual reframe advisory until generic subject tracking exists |

Physical movement is guidance, never a one-tap camera setting.
