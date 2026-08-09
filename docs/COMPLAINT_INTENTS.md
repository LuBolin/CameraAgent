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

The hosted classifier may return exactly one of these schema-v2 JSON objects, with no Markdown, prose, confidence, numeric value, coordinate, command, or extra field:

```json
{"schemaVersion":2,"outcome":"INTENT","intent":"EXPOSURE_BRIGHTER"}
```

```json
{"schemaVersion":2,"outcome":"INTENTS","intents":["EXPOSURE_BRIGHTER","ZOOM_OUT","WHITE_BALANCE_COOLER"]}
```

```json
{"schemaVersion":2,"outcome":"CLARIFY","reason":"BLUR_TYPE"}
```

`INTENT` accepts only the eight core labels above. `INTENTS` accepts two or three labels from the six direct-setting directions only, with no duplicate or same-axis pair; focus and level are never valid members. Android canonicalizes accepted lists to exposure → zoom → white balance before local planning. `CLARIFY` accepts `AMBIGUOUS`, `NEGATED_DIRECTION`, `CONFLICTING_DIRECTIONS`, `MULTIPLE_COMPLAINTS`, `REGIONAL_REQUEST`, `BLUR_TYPE`, or `ZOOM_OR_DISTANCE`. Wrong versions, malformed JSON, trailing content, extra keys, invalid list sizes, or unknown labels fail closed.

Explicit ISO, shutter, exposure-time, and noise requests are rejected locally before model use. They remain advisory until manual exposure is qualified as one coupled transaction.

## Local execution gates

- Negated, same-axis-conflicting, regional, blur-ambiguous, and distance-ambiguous wording cannot execute.
- Two or three compatible direct-setting intents may execute together only when there is at most one exposure, one zoom, and one white-balance direction. Focus, level, and physical movement remain separate flows; mixing one with a direct setting requires clarification.
- Exposure and white balance require a whole-frame complaint. A named region never receives a global adjustment silently.
- Exposure requires supported compensation, known telemetry, and an in-range target.
- Zoom requires explicit zoom wording, a reported ratio range, and room in the requested direction.
- White balance requires a supported qualified preset; Kelvin values are not accepted.
- Focus requires live preview and supported AF metering. The model never supplies the tap point.
- Level guidance requires current roll telemetry.
- A compound Apply is offered only when every member passes its gate. The controls run as one serialized local transaction; any failure restores the exact pre-apply settings, and an unprovable rollback blocks further adjustment until recovery.
- One Reset restores the first coached baseline for every setting in the accepted recommendation. Compound success confirms requested control setpoints and asks the photographer to check the shot; it does not claim that separate visual effects were isolated.
- Focus, exposure, zoom, white balance, and level never require a face.

## Examples and non-executable boundaries

| Complaint | Result |
|---|---|
| “Blurry” / “too blur” | Clarify motion blur versus missed focus |
| “Too close” | Clarify zoom, physical perspective, or frame occupancy |
| “Not too dark” / “isn’t overexposed” | Clarify; no opposite direction is inferred |
| “Too dark and too bright” | Clarify; no first match executes |
| “Too dark and too blue” | One recommendation with exposure brighter and white balance warmer; one Apply |
| “Too warm and too zoomed in” | One recommendation with white balance cooler and zoom out; one Apply |
| “Make it warmer and focus on the cup” | Clarify; the direct setting and user-selected focus flow stay separate |
| “Move the camera higher and make it warmer” | Clarify; physical guidance and direct settings stay separate |
| “Sky too bright” / “skin too blue” | Clarify whole frame versus named region |
| “Set ISO 100” / “use 1/500 s” | Advisory; exposure compensation is not substituted |
| “Too noisy” / “grainy” | Advisory until ISO and shutter are qualified together |
| Non-person subject position or distance | Manual reframe advisory until generic subject tracking exists |

Physical movement is guidance, never a one-tap camera setting. Focus likewise needs the user's tap point, and level uses measured guidance rather than being bundled into a direct-setting transaction.
