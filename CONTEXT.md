# Photo Coaching

This context describes how a photographer expresses dissatisfaction with a shot and how Photo Helper turns it into bounded, verifiable coaching.

## Language

**Complaint**:
A photographer's statement about what feels wrong in the live preview or a captured photo.
_Avoid_: Prompt, query

**User Preference**:
A requested aesthetic direction that is authoritative for the photographer but does not imply that the current image has a measurable defect.
_Avoid_: Diagnosis, correction

**Measured Diagnosis**:
A claim that a specific photographic defect exists, supported by current visual or camera evidence.
_Avoid_: Opinion, preference

**Recommendation**:
One proposed response to a Complaint, including its expected effect and whether it can be executed on the current camera.
_Avoid_: Tip, answer

**Recommendation Provenance**:
The frame, camera, qualified Coaching Subject, origin, and time context that determine whether a Recommendation is still valid.
_Avoid_: Metadata, cache key

**Coaching Subject**:
The only stable detected person in a single-person coaching interaction, tracked only for that interaction without recognizing identity.
_Avoid_: Selected face, primary face, recognized person

**Subject Lock**:
The temporary association between a Coaching Subject and continuing frame observations.
_Avoid_: Face recognition, first face

**Frame Goal**:
The desired visual result expressed in image coordinates, independent of how the photographer achieves it.
_Avoid_: Movement instruction, camera command

**Guidance Action**:
An actor-specific physical instruction to the photographer or camera that is expected to move the frame toward a Frame Goal.
_Avoid_: Frame goal, subject movement

**Executable Recommendation**:
A Recommendation that the current camera can apply or guide and whose effect can be checked.
_Avoid_: Advice, suggestion

**Advisory Recommendation**:
A Recommendation that explains a useful change but cannot be executed on the current camera.
_Avoid_: Executable recommendation

**Locally Understood Complaint**:
A Complaint that the installed app can interpret without a network connection; understanding does not guarantee an Executable Recommendation.
_Avoid_: Offline action, supported setting

**Effect Verification**:
Evidence that a requested directional change occurred, without claiming that image quality objectively improved.
_Avoid_: Defect verification, success

**Defect Verification**:
Evidence that the metric supporting a Measured Diagnosis moved into its target range.
_Avoid_: Effect verification, user approval

**Applied**:
The camera acknowledged a requested setting; this says nothing about whether the image visibly changed or the Complaint was resolved.
_Avoid_: Improved, satisfied

**Effect Observed**:
Comparable evidence shows that an Applied setting or Guidance Action produced its predicted visual direction.
_Avoid_: Applied, request satisfied

**Request Satisfied**:
A Measured Diagnosis reached its target range, or the photographer confirmed that an observed preference change is closer to what they wanted.
_Avoid_: Applied, effect observed

**Comparable Observation**:
A before-or-after view whose camera, subject, framing, and scene stability allow a visual change to be attributed to the coaching action.
_Avoid_: Latest frame, applied result

**Capture Review**:
The post-capture experience for an already-saved photo in which the photographer may finish or request coaching for a retake.
_Avoid_: Editor, unsaved preview, keep

**Retake Baseline**:
The actual captured pixels and available capture settings used to interpret a Complaint and plan the next capture.
_Avoid_: Preview frame, live baseline, edit state

**Remote Coaching Consent**:
The photographer's explicit choice to send a complete comment, coarse scalar scene measurements, and a random resettable app ID through the disclosed Cloudflare/OpenAI path; camera pixels and audio are never included.
_Avoid_: Internet permission, image consent, comment submission

**Client Instance ID**:
A random resettable UUID created only for advanced coaching and used as an abuse-control signal; it is not an account, credential, hardware ID, or recognized identity.
_Avoid_: User ID, device ID, advertising ID

**Control Baseline**:
The complete camera-control state observed before the first coached Apply in the current keyed camera session and restored by Reset across chained adjustments.
_Avoid_: Previous action, automatic defaults, Retake Baseline

**Face Occupancy**:
The amount of the image frame occupied by the Coaching Subject's face.
_Avoid_: Face size, perspective distortion

**Close-Perspective Distortion**:
The change in apparent facial proportions caused by a short camera-to-subject distance.
_Avoid_: Face occupancy, big face
