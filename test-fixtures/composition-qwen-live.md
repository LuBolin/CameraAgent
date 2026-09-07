# Actual Qwen composition run, 2026-09-06

This run used the user-provided `qwen_key.env` credential and the app's configured Qwen model, `qwen3.7-flash-2026-07-15`. The key was read only by the host runner, never printed or copied to the emulator, and its filename is ignored by Git.

The four public [PCC photography examples](https://www.pcc.edu/web-services/style-guide/media/photography/choosing-photos/composition/) were processed by Android. `CompositionQwenTest` exports JPEGs through the production encoder and requests through `buildVisualRequestBody`. `scripts/composition-qwen-live.py` sends these exact bytes to the production endpoint. Android then parses the real provider envelopes with `parseVisualResponse` and passes the accepted intent and saved input observations through the compiler and governor.

## Steady-frame results

| Image | Selected faces | Qwen strategy / framing / placement | Host latency | Resulting app instruction |
| --- | ---: | --- | ---: | --- |
| Distant subject | 1 | PORTRAIT / BALANCED / CENTRE | 2.625 s | Zoom in slightly |
| Tight portrait | 1 | PORTRAIT / BALANCED / CENTRE | 1.625 s | Zoom out slightly |
| Five-person group | 5 | PORTRAIT / BALANCED / CENTRE | 1.312 s | Aim the phone slightly right |
| Whiteboard activity | 0 | LEADING_LINES / BALANCED / RIGHT_THIRD | 6.922 s | Late response; local generic advice |

All four provider envelopes passed the app parser. Three arrived within the app's five-second deadline. The host uses socket timeouts and also records elapsed wall time; a successful response after five seconds is retained for diagnosis but excluded from the usable AI result during replay. This is not a device-network latency measurement.

All three portrait choices match the generic recipe. This run proves real requests and accepted responses, not that Qwen improves photographic composition or chooses useful physical movements. The current composition schema still selects strategy/framing/placement, not walking versus zoom or camera height versus tilt.

## Initial-run findings

Four earlier real calls also returned HTTP 200 and valid responses, all within five seconds. However, single-frame detection missed the tight portrait and detected only two group members. The second export sampled each fixed image eight times at 250 ms intervals: the portrait settled at one face, the group at five, and the distant portrait stayed at one. Whiteboard detections fluctuated between zero and two; the final frame had none. This exposes detector/selection limitations in addition to model behavior. Input observations are saved with each request so replay never substitutes a later detection result.

## Evidence and repeatability

Local artifacts are in ignored `outputs/qa/composition-qwen/`: `report.html`, exact `*-submitted.jpg` images, `*-request.json` bodies, actual `*-response.json` envelopes, `*-observation.json` snapshots, network timings, and app replay results. `provenance.json` includes image hashes, provider request IDs, and source hashes. Every submitted image was byte-compared with the base64 image in its request. Earlier evidence is retained under `initial-run/`.

With source photos in the emulator app's external `composition-qwen` directory, run the opt-in Android test with `-e compositionQwen export`, pull its outputs into the local artifact directory, and run `python scripts/composition-qwen-live.py`. Push only the returned response JSON files and `network-results.json` back to the emulator, then run the test with `-e compositionQwen replay`. The test class is `com.bolin.photohelper.capture.CompositionQwenTest`; normal test runs skip it. Re-exporting should begin with app-writable output files, not JPEGs overwritten by an adb push.

These tests did not run camera movement, ViewModel request cancellation, blocked-direction interaction, or the device HTTP client. Those are separate from the demonstrated real LLM request/response and deterministic compilation path.

## Harder positional examples

Three further images were evaluated with the unchanged production prompt. Sources: [portrait composition mistakes](https://ohmycamera.com/portrait-photography-composition-mistakes/) and [excessive headroom](https://commons.wikimedia.org/wiki/File:Far_too_much_headroom.png), a published teaching edit. Evidence is in `outputs/qa/composition-position/report.html` and adjacent raw requests, responses, observations, and provenance.

| Defect | Actual Qwen choice | App's first instruction | Assessment |
| --- | --- | --- | --- |
| Man looking left out of the left edge | LEADING_LINES / WIDE / LEFT_THIRD | Try framing the lines so they lead toward your subject. | Misses looking room; advice only. |
| Excessive headroom | PORTRAIT / BALANCED / CENTRE | Zoom out slightly | Default targets; no AI-specific correction. Zooming out alone increases empty space. |
| Boat distracting behind the face | LOOK_SPACE / BALANCED / RIGHT_THIRD | Try leaving space in the direction your subject is looking. | Recommends existing placement; misses distraction. |

Eight HTTP attempts produced four successful responses and four network failures; all attempts are retained. Final selected results are the second headroom response (3.204 s) and third attempts for the other two (1.578 s and 2.250 s). All three pass the production parser and deadline check. The boat portrait's detector fluctuated between zero and one face, ending at zero; the other two had stable single faces. No example demonstrated an AI-driven correction of the chosen defect.

The headroom position-only diagnostic says “Tilt the phone slightly down”, but this comes from the fixed local vertical target. It is neither the actual first instruction nor evidence that the model selected vertical movement. LOOK_SPACE and LEADING_LINES produce no tracked targets, even when the response specifies a horizontal placement.

The probe accepts `-e compositionDirectory composition-position` and a comma-separated `-e compositionFiles` list including extensions. The host runner accepts the local output directory as its first argument. Every submitted JPEG was byte-compared with its actual request payload; no physical before/after movement was tested.

## Improvement experiment, rejected

Tried a critique-first prompt and a KEEP framing option that preserves observed face/group width while correcting placement. Nine real calls covered two prompt revisions on the three positional examples and the final candidate on the earlier distant, tight, and group photos. All nine returned HTTP 200 within five seconds and passed the candidate parser. Candidate builds and unit tests passed. Exact baseline JPEGs and detector snapshots were reused via the probe's new `refresh` phase, which rebuilds only the production request.

Both revisions changed the headroom case from “Zoom out slightly” to “Tilt the phone slightly down”. The first revision recognized the outward-looking man's problem but selected advice-only LOOK_SPACE. Removing that option from the prompt made the next response revert to LEADING_LINES/LEFT_THIRD. The boat distraction remained unaddressed. On the earlier photos, KEEP preserved the distant subject's tiny face, and the tight portrait's stated correction contradicted its compiled direction. The group changed to a closer crop, without evidence that this was better.

The candidate did not establish an overall improvement, so its production prompt, enum/compiler changes, and candidate-only unit tests were reverted. Candidate source snapshots, all nine responses, baseline comparisons, and hashes remain in ignored `outputs/qa/composition-improved/`, with `report.html` as the entry point. The reusable `refresh` probe remains. This was prompt development on known examples, not a held-out evaluation.

Next experiment should separate visible-defect assessment from desired horizontal position, vertical position, and scale change. Each should allow no change. The local compiler should check measurable contradictions before issuing movement. Background parallax and walking versus zoom still need a richer contract and verification; changing a prose prompt alone did not solve them.

## Independent decisions implemented

Schema v2 now separates problem, horizontal position, vertical position, size change, and size-change method. KEEP preserves the selected face/group's observed geometry. Relative size changes use one modest step; WALK routes through physical guidance and the existing blocked-direction control. NONE and BACKGROUND remain untracked advice. The parser rejects contradictory combinations and extra geometry. The compiler rejects target bounds that would cut off selected faces. These are consistency checks, not proof that the photographic diagnosis is correct.

Two revisions produced twelve real HTTP 200 responses, all within five seconds. In the first six, the parser rejected two KEEP-size plus ZOOM contradictions. A clarification that pan/tilt uses movement NONE resolved these in the final six. Every final response passed parsing. Exact images and detector snapshots were reused and byte-verified against baseline.

| Final case | Actual first app instruction | Assessment |
| --- | --- | --- |
| Outward-looking man | Aim the phone slightly left | Useful right-third correction, preserving size and vertical placement. |
| Excessive headroom | Tilt the phone slightly down | Useful upward face placement without forced zoom. |
| Boat portrait | Keep the selected faces visible, then try again. | Model still misreads looking room and misses boat; saved detection has no faces. |
| Distant subject | Aim the phone slightly right | Centers subject but misses small size. |
| Tight crop | Tilt the phone slightly up | Adds room above, but model incorrectly describes excess headroom. |
| Five-person group | Zoom in slightly | Face-focused crop; photographic improvement not demonstrated. |

No final response chose WALK. Deterministic compiler and ViewModel tests verify its route, not the quality of an AI walking decision. Background parallax, physical camera-height changes, and compound movement remain unsupported. These known examples are a development set, not held-out validation or physical before/after testing.

Evidence: ignored `outputs/qa/composition-v2/report.html`, exact requests/responses, snapshots, timings, image hashes, and provider request IDs. First-revision evidence is preserved under `first-run/`. APK builds, lint, the unit suite, and Android production-parser/compiler replay passed.

## Schema v3 movement check, 2026-09-08

Schema v3 asks Qwen for two narrow visual facts in addition to its normal composition choice: a head/background collision and a too-small selected subject. The app compiles those facts locally, so it does not rely on the model to supply mechanically consistent movement fields.

Three new live Qwen calls returned HTTP 200 within the five-second host timeout and passed the production parser and Android replay:

| Published teaching image | Actual Qwen finding | Replayed app instruction |
| --- | --- | --- |
| [Extreme wide-angle portrait](https://www.iphotography.com/blog/what-is-lens-barrel-distortion/) | `PERSPECTIVE`; enlarged nose and central face | Move the phone farther away |
| [Pole behind head](https://www.nikonusa.com/learn-and-explore/c/tips-and-techniques/take-better-portraits) | `backgroundCollision: true` | Move sideways to separate the subject from the background. |
| [Distant subject](https://www.pcc.edu/web-services/wp-content/uploads/sites/6/2017/05/bad-focus-1-500x373.jpg) | `subjectTooSmall: true`; detector context measured an 8% face width | Zoom in slightly |

The wide-angle response itself supplied `KEEP` and `NONE` for the dependent movement fields; the parser deliberately canonicalized the valid `PERSPECTIVE` finding to `SMALLER` plus `WALK`. The pole remains advice-only because a face box cannot verify that a sideways move clears the background. Raw requests, responses, detector observations, timings, and Android replay output are local under ignored `outputs/qa/composition-next/` and `outputs/qa/composition-next-distant/`. These are development examples, not a physical before/after validation.
