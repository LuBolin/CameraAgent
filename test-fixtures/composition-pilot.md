# Composition pilot

The implementation is ready for engineering trials. Its thresholds are provisional; no photographic preference result or production-quality selection rate has been established.

## Run the feature

Tap **Help me frame**, or say "help me frame". With visual AI disabled, the app uses the local balanced portrait recipe. With visual AI enabled and a configured key, it requests one semantic strategy for the scene and compiles it locally. Failure falls back to the same generic recipe. Non-face scenes get advice without a tracking target.

Selected faces have outlines. **Change people** opens face selection; tap the intended faces, then **Use selection**. Ambiguous automatic selection opens this choice directly. Temporary loss pauses instructions and completion, preserving membership. Cancel, taking a photo, or switching cameras ends the attempt. While composition remains active, stable new scenes may request a new strategy; ordinary active reframing does not request one every frame.

Photographic reasoning is kept out of recommendation and guidance copy. During a physical movement instruction, **Can't move further** blocks only that direction for the current attempt. Other directions remain available, including the opposite direction on the same axis. The control is hidden during zoom, hold, and tracking recovery. Blocking an instruction stops it immediately; guidance works through other unmet targets and stops without success if the remaining framing requires a blocked movement. A new attempt starts without those restrictions.

Near the target, the instruction becomes **A little more** with the same direction. Entering the target produces **Stop. Hold there.** immediately; completion still requires the dwell. Near-target wording uses separate entry/exit thresholds to reduce chatter. No physical distance is claimed. Existing walking guidance preserves closer/farther prompts instead of silently replacing them with zoom.

The local `GuidancePolicy` in `coach/Composition.kt` is the calibration point. Initial values are 30 seconds overall, 8 seconds without progress, 2 seconds of recovery, 500 ms completion dwell, 500 ms instruction-switch dwell, 750 ms cooldown, and at most 3 reversals. Tune using pilot recordings, then freeze the values before evaluation.

The scene-change heuristic detects a large change after three stable samples. It cannot reliably distinguish gradual background changes from intended reframing of the same people. Tap Help me frame again after canceling to explicitly reconsider a scene. No additional detector or learned model is used.

Composition images must match the exact observation used for selected face boxes. If that image has already been replaced, the request uses the generic local fallback instead of sending mismatched outlines. An empty-scene proposal requires all three samples to contain no faces; newly appearing or undersized detections leave selection unresolved.

## Engineering observations

Use consenting single-person and group scenes, including children or people at different distances, bystanders, turn-away/reappearance, partial face clipping, crossed tracks, low light, both camera directions, and large text/accessibility settings. Include stationary trials with noisy measurements and deliberate overshoots. Face boxes cannot verify hair, bodies, or feet.

Capture local event logs with:

```powershell
adb logcat -v time CompositionGuidance:I '*:S' > composition-pilot.log
```

Each guidance attempt has a session ID and logs elapsed time, completion time when successful, displayed reversals, tolerance exits, tracking losses, frames within tolerance, total controller updates, and terminal outcome. Selection, strategy, advice-only, and AI fallback events are also logged with elapsed-clock timestamps. Logs contain no image, face coordinates, detector IDs, key, or user/model prose. Keep the build revision and policy values alongside each run. A watchdog update for missing data is a controller update, not a newly captured frame.

Record whether automatic selection was correct, whether it required correction, and whether an ambiguity prompt was justified. Prompt frequency alone rewards overly aggressive guesses. Distinguish current face-target baseline behavior from the new controller when comparing reversal counts; the previous implementation did not change directional instructions dynamically.

## Evaluation preparation

Use `composition-pilot.csv` as an empty recording template. Assign anonymous photographer, scene, and rater IDs. Do not place participant names or photos in the repository.

- **A:** ordinary capture, no composition guidance.
- **B:** Help me frame with visual AI disabled, using generic rules/advice.
- **C:** Help me frame with visual AI enabled. Retain generic fallbacks and failed attempts in C.

Counterbalance A/B/C order within photographer and scene. Return to the same starting frame between captures and record order effects. Keep camera settings, lighting, capture timing, and the B/C controller identical.

For technical validation, establish the intended subjects and prepare the B/C plans before a common fixed capture window. Standardize the initial explanatory sentence and advice onset in the trial build; the normal app displays the model reason and includes live request latency, so it is not by itself a pure strategy-only experiment. A gets neutral timing without selection outlines. Include both single and group portraits.

For product validation, use a representative scene mix and start the same fixed window before selection and AI waiting. Include those delays in the result. Have participants capture at the window endpoint; the app does not enforce or randomize this research protocol. Log deviations and missing captures. Do not call an unconstrained "take your time" instruction time matching.

Before collecting the evaluation set, freeze the sample-size rationale, primary C-versus-B contrast, tie handling, practical equivalence margin, and uncertainty method accounting for repeated photographers/scenes/raters. Blind raters to arm and randomize left/right presentation. Overall assigned-arm comparisons are primary; actual closed-loop/advice-only splits are descriptive because treatment affects service mode.

Do not run Phase 4 detection/training work or claim a benefit from this engineering pilot. The deferred grounding audit and human preference study remain separate work.
