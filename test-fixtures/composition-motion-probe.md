# Phone movement verification

The photo probe checks whether a still produces useful advice. `CompositionMotionTest` instead fixes subjects in 3D and moves a pinhole camera. Its projected face observations go through the production compiler, membership matcher, measurements, and governor.

## What is covered

Twelve motions are tested for both a single person and a two-person group:

| Physical motion in the simulation | Current app prompt for that framing error |
| --- | --- |
| Walk closer | Zoom in slightly |
| Step back | Zoom out slightly |
| Move right / left | Aim the phone slightly right / left |
| Lower / raise the camera | Tilt the phone slightly down / up |
| Aim right / left | Aim the phone slightly right / left |
| Tilt down / up | Tilt the phone slightly down / up |
| Rotate counterclockwise / clockwise | Rotate the phone counterclockwise / clockwise |

For every case, the first corrective step must reduce target error and the opposite step must increase it. At 250 ms intervals, a simulated operator waits for the governor, moves incrementally, preserves matched membership, and stops when all targets are satisfied. The governor must complete after its dwell without reversing the instruction or failing. These are fixed-step simulation timings, not measured human task times.

Additional tests cover the explicit perspective StepBack target, a rightward move cancelled by leftward aiming that must not complete, and sequential rightward movement plus counterclockwise roll with joint completion. The combined roll test explicitly adds the existing Level target; the default portrait compiler does not automatically include levelling.

## Limits and product gaps

The generic recipe currently chooses zoom and tilt prompts. Passing a physical translation test does not mean the app instructed that translation. The explicit perspective correction does say "Move the phone farther away".

A single face box cannot tell whether translation, aiming, zoom, or subject motion caused a change. Lowering the phone and tilting it down can both move a subject up in the image, but change perspective differently. The tests establish direction and closed-loop behavior, not which camera position makes the best photograph.

The simulator has ideal face measurements, fixed focal length, stationary subjects at one depth, and small movements. It does not exercise ML Kit, camera hardware, optical limits, parallax across different depths, motion blur, or a person following instructions. Roll inputs use synthetic gravity and the production gravity conversion; the hardware rotation-vector sensor path is not qualified by this test. Existing direction unit tests cover the mirrored horizontal text mapping; the 3D motion cases use a rear-camera coordinate system.

## Run

```powershell
.\gradlew.bat testDebugUnitTest --tests '*CompositionMotionTest' --offline
```

Numerical results are written to `app/build/reports/composition-motion.csv`. For physical acceptance, repeat the movements with a stationary subject and a real phone, recording the prompt, actual action, target error, and completion. Test translation separately from aiming and roll separately from yaw. Confirm the camera sensor and front-camera conventions on device before claiming physical validation.
