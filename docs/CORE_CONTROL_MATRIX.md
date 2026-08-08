# Core control matrix

This is the control boundary for the comment-to-action chain. A model may return a `Control Intent`; only the app can turn it into a capability-checked Recommendation and execute it.

## Direct controls

| Control | Product status | OnePlus CPH2411 evidence | Reset / verification |
|---|---|---|---|
| Tap focus | Reachable | CameraX AF metering; any preview point; physical AF lock passed | No persistent setting to reset; reports CameraX AF success/failure |
| Exposure | Reachable | CameraX EV range `-12..12`, step `1/6 EV`; five physical Apply/Reset trials passed | Restores the pre-coaching baseline; verifies comparable luma/clipping movement |
| Zoom | Reachable | Digital `1.0..10.0x`; five `1.00 -> 1.25x -> Reset` trials and adjusted-still `1.25x` telemetry passed | Restores baseline zoom; verifies the camera-reported ratio and rejects a lens/session change |
| White balance | Reachable | Camera2 AWB modes `0..8`; five warmer, five cooler, and ten Reset trials passed | Auto/previous preset is restored; verifies blue-minus-red chroma movement |

White balance is deliberately coarse: `Warmer`, `Cooler`, and `Auto`. Android exposes native AWB modes here, not a calibrated Kelvin control.

The brightness slider shown after tapping in many OEM camera apps is normally auto-exposure compensation. It is not direct shutter-time control. Photo Helper keeps tap focus AF-only and changes whole-frame brightness through EV compensation.

## Not direct controls yet

| Candidate | Hardware facts | Current decision |
|---|---|---|
| ISO | FULL `MANUAL_SENSOR`; ISO `100..6400` | Observe-only. Do not expose until a repeated AE-off + ISO/shutter + still-metadata + Auto-restore gate passes. |
| Shutter time | `0.1 ms..32 s`; maximum frame duration `32 s` | Observe-only. ISO and shutter form one manual-exposure transaction; neither is independently executable yet. |
| Lens selection | App binds the default public rear camera; selected ID reports no public physical IDs | No explicit lens action. Current zoom is digital crop on this camera ID. |
| Color temperature in Kelvin | No calibrated Kelvin contract in the app | Use qualified AWB presets only. |

## Guidance, not settings

- Phone roll works for any scene and verifies level within `1.5 degrees`.
- Distance and subject-position verification currently use one stable face only for person-specific complaints.
- Focus, exposure, zoom, white balance, level, capture, and review do not require a face.
- Physical movement remains an instruction; it never appears as an Apply-able camera setting.

## Qualification evidence

Local redacted reports are generated under ignored `outputs/qa/physical-device/`:

- `20260808-white-balance-30760D9E682B.txt`
- `20260808-zoom-30760D9E682B.txt`

These measurements qualify the attached phone's default rear camera path. Other devices are capability-checked at runtime and still require their own behavioral qualification before unsupported controls are advertised as proven.
