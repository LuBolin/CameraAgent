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
    TAP_ENHANCE,
    AUTO_CAPTURE,
    TAKE_PHOTO,
}
