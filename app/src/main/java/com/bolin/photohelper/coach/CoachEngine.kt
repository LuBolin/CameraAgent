package com.bolin.photohelper.coach

import com.bolin.photohelper.capture.FrameObservation

interface CoachEngine {
    fun classifyComplaint(complaint: String): IntentClassification
    fun evaluateLocal(input: CoachingInput): LocalDecision
    fun planIntent(input: CoachingInput, intent: ControlIntent): LocalDecision
    fun continueWithVisualHint(input: CoachingInput, family: VisualFamily, hint: VisualHint): LocalDecision
    fun verify(target: VerificationTarget, current: FrameObservation): VerificationResult
}
