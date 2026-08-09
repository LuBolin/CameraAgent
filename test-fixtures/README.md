# Camera and visual-model fixtures

These synthetic, non-sensitive images were created with the built-in image-generation tool for repeatable camera, face-detection, and Qwen smoke tests.

Open `device-stage.html` full-screen to stage any fixture without visible controls; use number keys 1–6 to switch targets or move the pointer to the top edge to reveal the buttons. Press `V` (or use **V · Speak compound**) to make Edge say “Make the picture brighter and zoom in” through the selected Windows output. The acoustic acceptance runner can use that local voice or replay its pinned neural and human controls through the same Edge window and secondary-display speaker.

- `neutral-portrait.png`: one centered adult at normal distance, neutral gray room, even daylight, neutral white balance, no text or watermark. Use as the face-detection and neutral-color control.
- `cold-blue-scene.png`: white mug, gray card, paper, and plant under a deliberately strong blue/cyan cast, normal exposure, no text or watermark. With `This looks too cold`, the expected visual hint is `WHITE_BALANCE_WARMER`.
- `warm-yellow-scene.png`: the same neutral-object pattern under a deliberately strong yellow/orange cast, normal exposure, no text or watermark. With `This looks too warm`, the expected visual hint is `WHITE_BALANCE_COOLER`.
- `close-face.png`: one centered adult in an extreme close-up, neutral light and background, no text or watermark. With `The face looks too big`, either `FACE_OCCUPANCY_LOWER` or `CLOSE_PERSPECTIVE_ADVISORY` is valid; the latter is appropriate when closeness implies perspective distortion.
- `large-face-natural-perspective.png`: one centered adult filling much of the frame while retaining natural telephoto-like proportions. With `The face looks too big`, the expected visual hint is `FACE_OCCUPANCY_LOWER`.
- `wide-angle-distorted-face.png`: one centered adult photographed very close with visible wide-angle enlargement of the nose and receding ears/sides. With `The face looks too big`, the expected visual hint is `CLOSE_PERSPECTIVE_ADVISORY`.

The Android observation path scales inputs to a 768-pixel long edge and encodes JPEG at 70% quality (reducing further only if needed) before enforcing its 300 KiB limit.

## Physical voice check — 2026-08-09

The reports under `outputs/qa/voice-acoustic/` for runs `20260809-162043`, `20260809-163230`, `20260809-163717`, and the rebuilt real-UI run `20260809-170120` establish the causal order: Android reported the recognizer ready before Edge started playback, and Edge reported playback complete before the runner pressed **Done** for the final transcript. The runner preserved the phone's existing English locale. Local Edge TTS, an Edge neural-TTS recording, and the commit-pinned CMUSphinx-derived human recording all reached the OnePlus microphone and SODA, but this provider returned an empty final result followed by error 7 in every case. The repeatable acoustic speech-to-text gate remains red; typed and injected transcripts cover the downstream intent and camera-control chain.

## Live Qwen check — 2026-08-05

Using the production prompts/model and 768 px JPEG copies, all five production-eligible expectations passed with strict envelopes in 2.5–4.6 seconds: cold→warmer, warm→cooler, natural large face→lower occupancy, and both visibly distorted close faces→close-perspective advice. A neutral portrait paired with the adversarial comment `This looks too cold` returned warmer; this scene is intentionally ineligible for a production request because the local blue-bias gate must agree with the complaint before Qwen can contribute a recommendation.

## Repeatable 12-call Qwen check — 2026-08-07

`scripts/qwen-live-smoke.py` exercised both production schemas, ordinary/control/confound color cases, true/false/no-subject face cases, exact model/envelope validation, the five-second latency gate, redacted failures, and ten-second request spacing. The production-rate run passed 4/12 strict cases: four valid responses met their expectations, six calls timed out, one had a network error, and `close-face.png` returned no visible distortion where the harness expected distortion. The raw redacted JSONL report is local-only at `outputs/qa/qwen-live-smoke-rate-limited.jsonl`. This does not qualify Qwen as a reliable dependency; the app retains its hard timeout, no-retry policy, local eligibility checks, schema validation, and local fallback.
