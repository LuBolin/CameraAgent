# Composition photo probe, 2026-09-06

Four public teaching examples from [Portland Community College](https://www.pcc.edu/web-services/style-guide/media/photography/choosing-photos/composition/) were downloaded unchanged and passed through Android's production `CameraXSession.createReviewObservation`, including ML Kit FAST detection and face normalization. The resulting observations went through `automaticCompositionMembers`, the default local `compileComposition`, `measureGuidance`, and `GuidanceGovernor`.

This tests the local generic strategy. No evaluation API key was available, so the live VLM branch was not exercised. Each photo starts a fresh camera session: an initial run reusing the streaming detector across unrelated stills missed faces. The failed output is retained in `outputs/qa/composition-online/results-first.json`.

| Source photo | Detected faces | Actual stable feedback | Assessment |
| --- | ---: | --- | --- |
| [Distant subject](https://www.pcc.edu/web-services/wp-content/uploads/sites/6/2017/05/bad-focus-1-500x373.jpg) | 1 | Zoom in slightly | Sensible first correction. The face occupies 8.2% of frame width; the local recipe requests 21–29%. This does not fix poor light or focus. |
| [Tight portrait](https://www.pcc.edu/web-services/wp-content/uploads/sites/6/2017/05/bad-crop.jpg) | 1 | Zoom out slightly | Sensible for the balanced recipe. Face width is 34.3%. The detector reports full face visibility and cannot diagnose the clipped head covering. |
| [Group with cut-off feet](https://www.pcc.edu/web-services/wp-content/uploads/sites/6/2017/05/so-close.jpg) | 5 | Aim the phone slightly right | Correct direction to move the group's face union left toward centre. It does not solve the cut-off feet and can sacrifice the sign's context. Geometrically correct is not necessarily photographically better. |
| [Whiteboard activity](https://www.pcc.edu/web-services/wp-content/uploads/sites/6/2017/05/bad-composition.jpg) | 0 | Try placing your main subject on a third of the frame. | Advice only. The generic fallback does not diagnose the partly visible person, empty space, or activity context. |

The governor displayed the correction at 500 ms after its initial hold. These timings use repeated observations of one still, not real camera motion. Automatic selection here also uses repeated still measurements, so this does not validate tracking, intent selection, recovery, or photographic improvement during use.

## Phone directions

Internal correction directions describe image motion. User-facing directions describe phone motion: an image needs to move left when the rear camera aims right; an image needs to move up when the phone tilts down. Horizontal front-camera instructions account for mirroring. Zoom prompts change occupancy; the separate perspective target still asks to move the phone farther away.

Unit tests cover both horizontal directions, vertical directions, front-camera mapping, decreasing target error, and the perspective distinction. Physical hand-motion usability remains untested.

## Repeat

Download the four source JPGs above into `outputs/qa/composition-online`, then build `assembleDebug assembleDebugAndroidTest`. With the app and test APK installed:

```powershell
adb -s emulator-5556 shell mkdir -p /sdcard/Android/data/com.bolin.photohelper/files/composition-online
adb -s emulator-5556 push outputs/qa/composition-online/. /sdcard/Android/data/com.bolin.photohelper/files/composition-online/
adb -s emulator-5556 shell am instrument -w -e compositionPhotos true -e class com.bolin.photohelper.capture.CompositionPhotoTest com.bolin.photohelper.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5556 pull /sdcard/Android/data/com.bolin.photohelper/files/composition-online/results.json outputs/qa/composition-online/results.json
```

The probe is skipped during ordinary instrumented test runs. Source photos and raw results remain local under the ignored `outputs/qa` directory. The probe asserts the distant subject's zoom direction and all five group members plus their pan direction.
