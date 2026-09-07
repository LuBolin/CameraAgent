---
name: composition-plan
description: "Frozen implementation plan for composition guidance — capability-compiler architecture, phased experiment, deliberately no ML training in v1"
metadata:
  node_type: memory
  type: project
  originSessionId: d4feea79-64da-4234-8815-3182f52d3ae6
  modified: 2026-09-06
---

Actionable plan for context-sensitive composition guidance. Background in [[composition-research]].

**Status: architecture frozen 2026-09-05.** Do not reopen the model-selection debate. The earlier version of this plan started with training MobileNetV2 on CADB; that was inverted risk ordering and has been removed from v1 entirely. If you are tempted to add a model, read "Explicitly not in v1" first.

Implementation started 2026-09-06. The working tree now includes the compiler, governor, session logging, automatic single/group proposals, correction UI, membership recovery, semantic VLM strategy requests, and scene-change checks. Existing code anchors below describe the pre-implementation audit and are historical. Both single-person and multi-person composition use the existing face detector.

Implementation locations: `coach/Composition.kt`, `capture/CaptureViewModel.kt`, `capture/CompositionOverlays.kt`, and `visual/VisualContracts.kt`. Selected subsets receive input-only outlines through `visual/CompositionImage.kt`; no output geometry or trackability claim is accepted from the VLM. `test-fixtures/composition-pilot.md` describes engineering trials and the remaining evaluation work. Thresholds are provisional; Phase 3 participant data, calibrated gates, and the deferred 0D audit have not been collected. Phase 4 remains gated.

**The question this project exists to answer:**

> Can context-sensitive AI photographic intent, translated into measurable spatial constraints, help ordinary users produce better-composed photographs than either no guidance or fixed composition rules?

Testable, falsifiable, and answerable with what the app already has.

---

## Core architecture

### Independent decisions, implemented 2026-09-06

Composition requests now use schema version 2: `problem`, `horizontal`, `vertical`, `size`, `movement`, and `reason`. Horizontal and vertical describe desired face/group positions in the image. Each axis and size can KEEP the observed value. Size changes are relative, one modest step per plan. Movement distinguishes ZOOM from WALK for size changes; KEEP size requires NONE. The local compiler translates image displacement into phone pan/tilt and routes WALK through the existing physical-movement controls. It does not invent centimetres, camera clearance, or verify perspective quality.

The parser rejects incompatible decisions, including KEEP plus WALK/ZOOM, an unchanged axis for its declared placement problem, and changes attached to NONE or BACKGROUND. NONE yields hold advice without a completion claim. BACKGROUND remains advice-only because face boxes cannot verify background separation. The compiler preserves unchanged axes and scale, checks the selected face/group geometry, and rejects a target whose expected face bounds would leave the image. These checks cannot establish whether the model's photographic diagnosis is true.

Legacy schema 1 remains readable for existing evidence, and local generic fallback retains its original recipe. The semantic-vocabulary sections below describe that original strategy-based path. New requests use independent decisions, not scene strategies. Physical camera-height changes, compound sidestep-and-rotate plans, and verification of background/perspective improvements remain unsupported. See `test-fixtures/composition-qwen-live.md` for actual provider trials.

### Movement interaction, accepted 2026-09-06

Photographic reasons may inform planning but are not shown to the photographer. Display the current action and continuous approach/stop feedback. Show **Can't move further** only while issuing a physical movement instruction. Tapping it blocks that direction, not the entire axis or all movement, for the current attempt. Stop the blocked command immediately, continue useful available directions, and never claim completion while blocked constraints remain unsatisfied. Hide the control during hold, recovery, zoom, and advice. Use **A little more** near the target and **Stop. Hold there.** on entering it; retain the joint completion dwell. Do not invent travel distances.

Three layers. The middle one is the contribution.

```
CREATIVE DIRECTOR (VLM, once per scene change)
  emits photographic intent as semantic vocabulary
         |
CAPABILITY COMPILER (deterministic, on-device)
  semantics -> VerificationTargets, or -> advice
         |
CAMERA OPERATOR (existing loop, 4 Hz)
  per-frame verify -> GuidanceGovernor -> human-paced instruction
```

### The VLM never declares what is trackable

This is the load-bearing invariant. The VLM outputs *intent only* — strategy, framing, placement. It does **not** output `guidanceMode`, coordinates, or tolerances. The compiler decides:

```
strategy -> can every required constraint become a VerificationTarget?
              YES -> CLOSED_LOOP
              NO  -> ADVICE_ONLY
```

Capability determination is therefore deterministic and unfakeable. There is no requested mode to trust.

Enforce the semantic schema at parse time, not by prompting. Precedent already in the codebase: `VisualContracts.kt` rejects malformed grounding via exact key-set matching and a containment check. Reject extra keys, unknown enum values, coordinates, and claimed guidance modes. Then let the compiler check the valid intent against current subject observations and local capabilities. A valid but unmeasurable strategy becomes advice-only; malformed output is rejected and gets a local fallback. Parsing alone cannot establish runtime trackability.

### Two service levels, both first-class

`ADVICE_ONLY` is not a degraded fallback. It is a different kind of help.

- **CLOSED_LOOP** — "Move slightly left… step back… hold there." Only when geometry is measurable.
- **ADVICE_ONLY** — "The building's symmetry is strong, try centring it." One sentence, no tracking, no target circle, no fake precision.

The system must know the difference between *"I can measure this"* and *"this is a suggestion."*

---

## Constraint taxonomy (decides what compiles)

| Tier | Examples | Grounding needed | v1 |
|---|---|---|---|
| **A — frame-relative** | centre, thirds, face-box top margin, vertical placement | fixed frame coordinates plus tracked face geometry to verify placement | yes |
| **B — subject-relative** | face scale, face position, face-group framing | local face boxes; no VLM grounding | yes |
| **C — scene-relative** | symmetry axis, horizon line, leading lines, negative-space direction | scene geometry required to verify | advice only |

The semantic vocabulary may include Tier C photographic ideas as advice. Only supported Tier A/B face constraints compile in v1; Tier C strategies always produce advice-only. No coordinates cross the composition VLM boundary. Do not reduce a symmetry strategy to a centre target and imply symmetry was verified. Actual hair headroom and whole-body framing also remain advice. Tier C tracking requires both the Phase 0D audit and an implemented, validated local verifier.

---

## Verified codebase anchors

Checked against the current workspace on 2026-09-06. Line numbers approximate, symbols exact.

**The loop already exists and runs.** This is not greenfield.

| Thing | Location |
|---|---|
| `VerificationTarget` sealed interface | `coach/CoachingModels.kt:114` |
| variants | `Exposure`, `FaceOccupancy(min,max)`, `FacePosition(xRange,yRange)`, `StepBack(maxFaceWidthFraction)`, `Level`, `ColorBalance`, `Zoom` |
| `verify()` branches | `coach/DefaultCoachEngine.kt:257-307` |
| `singleFace()` = `faces.singleOrNull()` | `DefaultCoachEngine.kt:758`; one of several single-person restrictions |
| `coachingFace` requires `faces.size == 1` | `DefaultCoachEngine.kt:761` |
| `StableFaceTracker.update` requires a single qualified face | `CaptureViewModel.kt:2191`; three samples over at least 500 ms |
| `startGuidance` locks one stable face | `CaptureViewModel.kt:621`; checks the recommendation's subject against `stableFace` |
| `verifyActiveWork` rejects multiple faces | `CaptureViewModel.kt:1835`; also updates the locked face by copying `ActiveGuidance` |
| `Level` produced / verified / rendered | `DefaultCoachEngine.kt:547` / `:284` / `CompositionOverlays.kt:23` |
| `RecommendationAction.GuidePosition` | routes to `onStartGuidance()` |
| `ActiveGuidance(instruction, target, startedAtMs, subjectTrackingId, subjectFace)` | `capture/CaptureUiState.kt:77` |
| `guidanceTimeoutJob` | `capture/CaptureViewModel.kt:173` |
| Completion dwell already exists | `CaptureViewModel.kt:1870`; target must stay satisfied for 500 ms. This is not instruction-switch hysteresis or a governor |
| Existing guidance timeout | `CaptureViewModel.kt:651`; 10 seconds, but its `=== guidance` identity guard no longer matches after a tracked-face update copies `ActiveGuidance` |
| `FrameObservation` fields | `capture/CaptureModels.kt:21-36` |
| `FaceObservation` (+ `visibleFraction`, `widthFraction`, `centerX/Y`, `trackingId`) | `CaptureModels.kt:8-18` |
| `visibleFraction` computed | `CameraXSession.kt:975`; already thresholded `>= 0.90f` at `DefaultCoachEngine.kt:430` and `CaptureViewModel.kt:2204` |
| `ANALYSIS_INTERVAL_MS = 250L` (4 Hz) | `CameraXSession.kt:1376` |
| `detectorGate = Semaphore(1)` | `CameraXSession.kt:206` |
| `analyze()` already yields an upright `Bitmap` | `CameraXSession.kt` — expensive conversion already paid for |
| `VisualFamily` enum (3 values) | `CoachingModels.kt:11` — add `COMPOSITION` here |
| VLM temperature already `0` | `visual/VisualContracts.kt:132` |
| existing `point_2d`/`box_2d` grounding, schemaVersion 3 | `VisualContracts.kt:99-108`, validation `:176-178` |
| `CompositionOverlays.kt` | one composable (`GuidanceTarget`); draws a fixed centre circle for every target, no grid or target-aware geometry |
| `exposureInvariantSceneDifference` | `CameraXSession.kt` — the scene-change trigger |
| `SpatialTracker` / `isStill` | `arcore/SpatialTracker.kt` |
| only ML dependency | `com.google.mlkit:face-detection:16.1.7` |

**Hard constraint:** the fast loop has **no object detection**. Faces only. Anything that is not a face has no trackable subject.

---

## Phase 0 — Foundation (no AI, no models)

Phases 0A–0C need no API key, dataset, or new dependency. **Complete 0A–0C before composition VLM integration.** The separate 0D audit uses the existing Qwen integration and gates future scene-relative work; it does not block v1.

### 0A — Instrument the existing loop
Add logging to the live `FacePosition` / `FaceOccupancy` / `StepBack` guidance path. Per session record:

```
time-to-target, instruction reversals, overshoots,
tracking losses, frames within tolerance,
completion / timeout / abandonment
```

**Instruction reversals is the key metric.** `LEFT LEFT RIGHT LEFT RIGHT` is a failure even when the tracker is mathematically correct.

### 0B — GuidanceGovernor (prerequisite #0, not a feature)
Without this every later capability feels bad. Frame-by-frame reactive instructions are worse than no guidance.

```
4 Hz observations -> filter -> desired correction -> GOVERNOR -> displayed instruction
```

Governor holds: hysteresis (Schmitt-trigger, not threshold crossing), dwell before switching instruction, cooldown, progress tracking, reversal protection, timeout, give-up.

Reuse the existing 500 ms completion dwell as the starting point. Current guidance speaks its initial instruction and verifies the target; it does not yet generate per-frame directional corrections. Add that correction calculation before measuring instruction reversals, and distinguish those measurements from the existing baseline. `VerificationResult.Progress` currently means only "outside tolerance" for face targets, so compute actual error reduction for progress and no-progress decisions.

Repair timeout ownership before extending guidance: tracking updates copy `ActiveGuidance`, so reference equality with the original object is not a reliable session check. Tie expiry to the guidance session across tracking updates, and verify that an old timeout cannot end a newly started session. Reconcile the existing 10-second timeout with the pilot's time-to-target gate rather than silently keeping conflicting limits.

Principle: **fast sensing, slow and stable instruction.** Do not hardcode 1 Hz — the human perceive-decide-move latency is ~0.5-1 s against a 250 ms loop, so oscillation is predictable; measure the right cadence in 0A rather than guessing.

Hang the state off `ActiveGuidance`, which already carries `startedAtMs` and `subjectTrackingId`.

### 0C — Capability compiler skeleton
`CompositionPlan` -> `List<VerificationTarget>` or `ADVICE_ONLY`. Hand-author 2-3 plans as literals and drive the existing loop with them. No VLM yet.

`CompositionPlan` shape:

```
strategy         (semantic enum; Tier A/B candidates and advisory Tier C ideas)
framing          (semantic enum from the supported local combinations)
placement        (semantic enum from the supported local combinations)
reason           (string, shown to user)
targets          (compiled VerificationTargets — empty means ADVICE_ONLY)
guidanceMode     (DERIVED, never supplied)
successCondition (local: all required targets satisfied together for the dwell)
failurePolicy    (local: timeout, no-progress duration, maxReversals, fallbackAdvice)
```

`failurePolicy` from day one, not later. Distinguish *"not there yet"* from *"this target is not achievable, stop asking."* Precedent: `CoachingCard.kt:419-420` already blocks walking guidance when touch exploration is on. Prefer "step back slightly" over "step back 30 cm" unless spatial tracking justifies the precision.

The current `ActiveGuidance` and `GuidePosition` each hold one target. Extend their composition path to verify all required targets against the same observation and selected membership. Completion requires all of them to hold together for the dwell period. For every face-based plan, all selected members must also be currently matched and have `visibleFraction >= 0.90f` throughout that dwell, including position-only plans. If any required constraint cannot be measured for the selected subject, the whole plan becomes advice-only, rather than silently dropping that constraint. Frame-relative constants still need an observed subject position to verify placement.

Compile a small local table of supported strategy/framing/placement combinations. The VLM supplies semantic choices and a bounded reason, never target ranges, success conditions, or failure policies. Intersect repeated bounds and reject empty intersections or targets outside the frame. Static checks cannot prove that a physical viewpoint is reachable; the runtime failure policy stops stalled attempts.

Generate one correction at a time. Pause first for missing or ambiguous subjects; otherwise address selected-face clipping, then scale, then position, re-evaluating every constraint each observation. Clipping is a priority condition, not an automatic "step back": choose a supported position or scale correction from measured geometry, or give advice if no correction is justified. For competing position axes, use the largest error measured relative to its tolerance, with a fixed tie-break. The governor holds the current correction through small changes and controls switches and reversals. Neither a satisfied individual target nor an oscillating sequence counts as success. Measure total target error reduction over time; pause no-progress accumulation during tracking recovery, but keep the overall timeout running. Calibrate instruction timing, loss grace, and failure limits in the pilot and freeze them before evaluation.

Replace the existing fixed centre circle with geometry appropriate to the compiled target. Advice-only plans must not render a tracking target.

### 0D — Deferred audit of the existing Qwen grounding
Required before future Tier C tracking, not before v1. Temperature is already `0`; repeat identical bytes as a control, but do not use that alone to establish stability across real camera frames.

**Correct test:** tripod, stationary subject, unchanged scene, capture frames at 0/250/500/750/1000 ms, send independently.

**Measure action stability, not coordinate stability.** `.47 .50 .46 .51 .48` compiling to five HOLDs is a pass. `.44 .56 .45 .55` compiling to LEFT RIGHT LEFT RIGHT is catastrophic. The metric is: *does grounding noise change the instruction?*

Also record valid-JSON rate, point-inside-box failure rate, rejection rate, semantic misidentification.

---

## Phase 1 — Closed-loop coverage

**Groups before objects.** Not because detection is harder, but because subject selection is ambiguous for objects (is the subject the plate, the food, the plate plus drink?).

Groups need no new ML — `FrameObservation.faces` is already a `List` with per-face bboxes and tracking IDs.

1. Preserve single-person `FacePosition`, `FaceOccupancy`, and `StepBack` behavior. Add group targets without globally replacing `singleOrNull()` with a union. A union cannot stand in for an individual face or justify individual facial-proportion advice.
2. **Subject intent and membership first.** Automatically select a clear single-person or group candidate before compiling targets; ask only when intent remains ambiguous. Detection of several faces alone does not establish a group. Persistence, spatial grouping, and relative size are candidate signals to validate in the pilot, not proof of intent. Size alone must not exclude children or more distant group members.
3. Freeze logical group membership when guidance starts, retaining each member's latest face observation and nullable detector `trackingId` within the session. IDs support continuity but are not recognized identities. Apply plausible geometric consistency checks to matching IDs; with missing or changed IDs, accept reassociation only when a unique plausible correspondence exists for all affected members. Crossings or competing matches pause guidance rather than guessing. During temporary member loss, pause movement instructions and completion, retain membership, and resume automatically once the same members are confidently reacquired. Reset completion dwell on loss. After a bounded grace period, stop the attempt with a short recovery instruction and an option to change selection. Do not shrink the group automatically and declare success. Ignore nonmember passers-by when computing targets.
4. Union selected members' face boxes into `groupLeft/Right/Top/Bottom` to measure centre, width, height, and edge margins. This measures face framing only; it cannot verify full bodies, limbs, or headroom above hair.
5. Add `GroupPosition` and `GroupOccupancy`. Carry selected members through recommendation creation, stable-subject acquisition, `startGuidance`, `ActiveGuidance`, `verifyActiveWork`, engine verification, and overlays. These paths currently assume one face; changing `singleFace()` alone is insufficient.
6. Check clipping only for selected members: `selectedFaces.any { it.visibleFraction < 0.9f }` -> "someone is too close to the edge". Keep eligibility separate from clipping so an edge-clipped member does not disappear from the group being checked.
7. Verify both modes with replayable observations: existing single-person behavior, stable group completion, passer-by exclusion, temporary member loss, persistent loss, missing/changing IDs, clipping, and timeout after tracking-state updates.

Level verification already ships. Its overlay is currently the same fixed circle as other guidance; target-aware feedback belongs to Phase 0C.

A **Coaching Subject** is the selected person or fixed set of people for one guidance attempt; a **Subject Lock** associates those members with continuing observations without recognizing identity. `CONTEXT.md` now records this expansion. Existing single-person acquisition still uses `sameSubject()`; active composition uses unique member association with bounded geometric checks and recovery so intended movement can preserve continuity.

### Agreed selection experience, 2026-09-06

Default to automatic selection as much as possible. Use a sole qualified face automatically; with multiple faces, automatically select a clear person or group when the evidence supports it. Show the selected face outlines and a lightweight option to change the selection, without requiring routine confirmation before guidance.

If several selections remain plausible, show "Who are you framing?" in the preview. Let the user tap one or more faces, choose "Everyone", and confirm with "Use selection". User selection overrides inference. Freeze membership when guidance starts; changing the selection starts a new guidance attempt. Do not repeatedly ask while the same selection remains valid. Automatic selection thresholds remain pilot work; measure wrong selections and correction frequency as well as prompt frequency.

Temporary detection loss must preserve the intended photograph. If a selected child turns away, keep them in the group, pause movement instructions and completion, then resume automatically when the same group is confidently reacquired. Apply the same recovery behavior to a single selected person. Never use stale face geometry to continue corrections during the pause. If recovery fails, stop with a short explanation and an option to change selection. Measure the grace period in the pilot; recovery must remain bounded by the attempt's timeout.

### Review decisions, 2026-09-06

The agent and review subagent resolve design questions here rather than interviewing the user. Settled behavior: automatic selection with correction and ambiguity prompts; frozen membership with bounded automatic recovery; Tier C advice without tracking; joint target verification with one governed correction; and matched experimental machinery and capture windows. Numerical thresholds require pilot evidence, not further product decisions.

---

## Phase 2 — VLM strategy selection

- Add `COMPOSITION` to `VisualFamily` (`CoachingModels.kt:11`)
- New prompt in `VisualContracts.kt` returning **semantic vocabulary only**, including explicitly advisory Tier C strategies, no coordinates
- Compiler maps vocabulary -> `VerificationTarget`s; anything uncompilable becomes `ADVICE_ONLY`
- New `VisualHint.CompositionPlan` variant; branch in `DefaultCoachEngine.continueWithVisualHint`
- Reject malformed semantic output at parse time; determine capability locally at compilation and revalidate tracking during guidance

**Trigger: meaningful scene or subject change, or explicit request.** Use `isStill` and `exposureInvariantSceneDifference` to debounce scene changes. Ignore ordinary guided reframing and temporary tracking loss/reacquisition within the same attempt: resume the existing plan. A material scene change ends the stale attempt before replanning; a new subject selection or restart after failed recovery may request a new strategy. Keep at most one composition request in flight and discard responses for a superseded scene, selection, or camera session. A bounded request failure uses the same local generic fallback as arm B, and records that fallback.

**Do not trigger on an aesthetic score.** The earlier plan proposed intervening when a learned score fell below threshold. That is self-defeating: it re-creates the regression-to-the-mean problem the project exists to escape, and would fire on deliberate negative space or a deliberately centred portrait. A low score is evidence, never a declaration that the photographer is wrong.

---

## Phase 3 — The experiment

Three arms. This isolates the actual research question.

- **A** — no guidance
- **B** — generic deterministic rules for face placement, face-box margins, and framing, no VLM
- **C** — VLM-selected strategy compiled into the same machinery

**B is not optional.** C > A only proves guidance helps. The thesis is that context-sensitive strategy beats fixed rules, so **C vs B is the primary outcome**.

For scenes without trackable faces, B supplies a fixed generic advisory sentence, such as "Try placing your main subject on a third of the frame." Freeze this fallback before evaluation and use the same advice presentation as C, without a tracking overlay or success claim. This compares contextual advice with generic advice instead of comparing advice with silence. Levelling availability is identical in B and C.

| Result | Meaning |
|---|---|
| `C > B > A` | thesis supported, expand scope |
| `B > A`, `C ≈ B` | guidance works, AI strategy adds little — ship the simple product |
| `C ≈ B ≈ A` | stop investing in the feature |

### Gates

Run a **small pilot first** to check the engineering thresholds are realistic, then **freeze criteria before collecting the evaluation set**. Avoids both arbitrary numbers and post-hoc goalpost movement.

| Metric | Initial gate |
|---|---|
| Closed-loop completion | ≥80% |
| Median time-to-target | ≤20 s |
| Median instruction reversals | ≤2 |
| Tracking failure | ≤10% |
| AI vs no-guidance preference | >50% |
| **AI vs generic-rule preference** | **primary outcome** |

For the photographic outcome prefer a preregistered hypothesis with a confidence interval over a magic percentage. `AI 56% / Generic 32% / Tie 12%` with a convincing interval is interesting even though it misses an arbitrary ≥60% bar.

### Protocol

Blinded pairwise, same photographer / subject / scene, capture-arm order counterbalanced, raters answer "which has the stronger composition" (left / right / no meaningful difference), optionally "which would you keep." Randomise image side independently and hide arm labels. Return to a common starting view between captures to reduce carryover; counterbalancing cannot erase learning, so report order effects.

**Control the deliberation confound.** Choose one fixed capture window from the pilot and freeze it for all arms, with the final photo taken at the same elapsed point. Arm A can reframe freely throughout that window and sees no composition cues. "Take as long as you like" is not time matching. Record selection time, VLM waiting time, guidance time, and total elapsed time separately. Use the same camera settings and capture procedure in all arms.

B and C share subject selection, supported constraints, compiler, governor, overlays, instruction templates, timeout, and recovery behavior. Freeze B's deterministic strategy mapping before evaluation. For technical validation, establish the same intended person/group before all captures and prepare B/C strategies before the common capture window; keep explanatory prose equivalent so the comparison tests strategy choice. Arm A gets no selection overlay. For product validation, use a common window starting when the participant begins each attempt, including selection and model latency, to measure the delivered experience. Treat these as separate estimates.

Pre-register the C-versus-B preference contrast, tie handling, sample-size rationale, and an interval method accounting for repeated photographers/scenes and raters. Keep timeouts, selection mistakes, and C-to-generic fallbacks in their assigned arm; log missing captures and report sensitivity to them instead of silently dropping failures. This preserves the comparison of the assigned products.

In the result table, `≈` means evidence within a predeclared practical equivalence margin. An inconclusive or underpowered result does not establish equivalence and does not by itself justify the table's stop decision.

### Two stages — do not merge them

1. **Technical validation** — deliberately portrait-heavy. Tests the closed loop under its strongest supported condition.
2. **Product validation** — diverse, representative scene mix. Measures how often users actually get closed-loop vs advice-only, and whether each helps.

Report **three cuts**: overall, closed-loop-only, advice-only. Then break down by scene: single portrait, group, food/object, pet, landscape, architecture/interior.

The overall assigned-arm comparison is primary. Actual service mode is affected by strategy choice and tracking failures, so mode-specific results are descriptive, not automatically causal comparisons. Also report capability strata defined from the initial scene before treatment, alongside automatic-selection correction and prompt rates.

This can surface something commercially important that has nothing to do with the expected architecture. `closed-loop portrait +8% / advice-only landscape +21%` would say the valuable product is a photographic director that gives one good idea, not a real-time control system. The inverse would say double down on trackable subjects. Either result saves months.

---

## Phase 4+ — Only if Phase 3 justifies it

In order:

1. **Objects / pets / food** — MLKit Object Detection & Tracking is off-the-shelf and gives the same bbox+trackingId shape as faces. The hard part is subject selection, not detection. Candidate approach: VLM names the primary subject, local tracker matches a candidate region.
2. **Tier C scene-relative** — gated on 0D. Needs symmetry-axis / horizon detection that does not exist and is not off-the-shelf.
3. **Local composition model** — CADB / PICD / CPC experiments. Only with a concrete answer to *"what problem does this solve that the current system doesn't?"* A dataset being available is not a reason to train on it.

If preference learning is ever revisited, the eventual objective is a policy model (`frame + geometry -> Δx, Δy, Δscale`), not generate-N-crops-and-rank, which is N× inference per frame.

---

## Explicitly not in v1

CADB · MobileNetV2 training · SAMP-Net distillation · CPC ranking · custom saliency · horizon segmentation · symmetry detection · vanishing points · general scene geometry · continuous VLM calls · aesthetic-score triggering

---

## Known scope boundaries (state these, don't discover them)

| Subject | v1 experience |
|---|---|
| Single person | existing face-target verification; composition selection and governor planned |
| Group | face-group closed-loop planned in Phase 1 |
| Any scene, levelling | existing roll verification, needs no subject; target-aware overlay planned |
| Object / pet / food | advice-only until Phase 4 |
| Landscape | advice-only + levelling |
| Architecture | advice-only + levelling |

The MVP is honestly **"context-aware guidance for single- and multi-person portraits, plus photographic advice for everything else."** That is a legitimate product. Frame it as a decision, not an accident.

The contribution is not assembling more CV models. It is separating **creative judgment, executable constraints, capability awareness, and human-paced control**.
