package com.bolin.photohelper.guide

data class GuidedExercise(
    val instruction: String,
    val hint: String? = null,
    val successMessage: String = "Nice work!",
    val type: ExerciseType,
)

enum class ExerciseType {
    HOLD_STEADY,
    TAP_TO_FOCUS,
    VOICE_COMMAND,
    LONG_PRESS_ORB,
    AUTO_CAPTURE,
    TAKE_PHOTO,
}
