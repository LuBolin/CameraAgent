package com.bolin.photohelper.voice

import com.bolin.photohelper.coach.ControlIntent
import com.bolin.photohelper.capture.FlashMode

sealed interface VoiceCommand {
    data object TakePicture : VoiceCommand
    data class CountdownAndTakePicture(val seconds: Int) : VoiceCommand {
        init { require(seconds in 1..30) }
    }
    data object SwitchCamera : VoiceCommand
    data object UseFrontCamera : VoiceCommand
    data object UseRearCamera : VoiceCommand
}

sealed interface CommandPlanStep {
    data class Coach(val text: String) : CommandPlanStep
    data class Adjust(val intents: List<ControlIntent>, val small: Boolean = false) : CommandPlanStep {
        init { require(intents.isNotEmpty() && intents.size <= 3 && intents.distinct().size == intents.size) }
    }
    data class SetCamera(val facing: CameraFacing) : CommandPlanStep
    data class SetFlash(val mode: FlashMode) : CommandPlanStep
    data class FocusPoint(val xFraction: Float, val yFraction: Float) : CommandPlanStep {
        init {
            require(xFraction in 0f..1f && yFraction in 0f..1f)
        }
    }
    data object Reset : CommandPlanStep
    data class Capture(val countdownSeconds: Int? = null) : CommandPlanStep {
        init { require(countdownSeconds == null || countdownSeconds in 1..30) }
    }
}

enum class CameraFacing { TOGGLE, FRONT, REAR }

data class CommandPlan(val steps: List<CommandPlanStep>) {
    init { require(steps.isNotEmpty() && steps.size <= 8) }
}

private val FRONT_CAMERA = Regex(
    "\\b(?:(?:switch|change|flip|use)(?:\\s+(?:it|the|my))?(?:\\s+to)?(?:\\s+the)?\\s+)?" +
        "(?:front|selfie)(?:[- ]facing)?\\s+(?:camera|mode)\\b|" +
        "\\bcamera\\s+(?:to\\s+)?(?:front|selfie)\\b|" +
        "\\bcamera\\s+(?:facing|pointing)\\s+(?:me|towards me)\\b",
)
private val REAR_CAMERA = Regex(
    "\\b(?:(?:switch|change|flip|use)(?:\\s+(?:it|the|my))?(?:\\s+to)?(?:\\s+the)?\\s+)?" +
        "(?:rear|back)(?:[- ]facing)?\\s+camera\\b|" +
        "\\bcamera\\s+(?:to\\s+)?(?:rear|back)\\b|" +
        "\\bcamera\\s+(?:facing|pointing)\\s+away\\b",
)
private val SWITCH_CAMERA = Regex("\\b(?:switch|flip|change)(?:\\s+(?:the|my))?\\s+camera\\b")
private val NEGATED_CAMERA_SWITCH = Regex(
    "\\b(?:don't|dont|do not|never)\\b.{0,20}\\b(?:switch|flip|change|use)\\b.{0,20}\\bcamera\\b",
)

private val CAPTURE_WORD = Regex(
    "\\b(?:take(?:\\s+(?:a|the))?\\s+(?:photo|picture|shot)|capture(?:\\s+(?:a|the))?\\s*(?:photo|picture|shot)?|" +
        "take\\s+one|get(?:\\s+(?:a|the))?\\s+(?:photo|picture|shot)|shoot|" +
        "snap(?:\\s+(?:a|the))?\\s*(?:photo|picture|shot)?|(?:press|hit)\\s+(?:the\\s+)?shutter)\\b",
    RegexOption.IGNORE_CASE,
)
private val NEGATED_CAPTURE = Regex(
    "\\b(?:don't|dont|do not|not|never)\\b.{0,20}\\b(?:take|capture|get|shoot|snap|press|hit)\\b",
)
private val TIMER_CUE = Regex("\\b(?:countdown|timer|in|after)\\b")
private const val NUMBER_TOKEN =
    "(\\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|" +
        "sixteen|seventeen|eighteen|nineteen|twenty(?:[- ](?:one|two|three|four|five|six|seven|eight|nine))?|thirty)"
private val SELFIE_CAPTURE = Regex(
    "\\b(?:take|capture|shoot|snap)(?:\\s+(?:a|the))?\\s+selfie" +
        "(?:\\s+(?:in|after)\\s+$NUMBER_TOKEN(?:\\s+seconds?)?)?\\b",
)
private val TIMER_PATTERNS = listOf(
    Regex("\\b(?:in|countdown|timer(?:\\s+for)?)\\s+$NUMBER_TOKEN(?:\\s+seconds?)?\\b", RegexOption.IGNORE_CASE),
    Regex("\\b$NUMBER_TOKEN\\s+seconds?\\b", RegexOption.IGNORE_CASE),
)
private val NUMBER_WORDS = mapOf(
    "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
    "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
    "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
    "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
    "thirty" to 30,
)

private fun parseSeconds(token: String): Int? {
    token.toIntOrNull()?.let { return it }
    val words = token.replace('-', ' ').split(' ')
    return if (words.size == 2 && words.first() == "twenty") {
        20 + (NUMBER_WORDS[words.last()] ?: return null)
    } else {
        NUMBER_WORDS[token]
    }
}

private data class LocatedSteps(val range: IntRange, val steps: List<CommandPlanStep>)

fun parseCommandPlan(text: String): CommandPlan {
    val normalized = text.trim().lowercase()
    if (normalized.isBlank()) return CommandPlan(listOf(CommandPlanStep.Coach(text.trim())))
    if (NEGATED_CAMERA_SWITCH.containsMatchIn(normalized) || NEGATED_CAPTURE.containsMatchIn(normalized)) {
        return CommandPlan(listOf(CommandPlanStep.Coach(text.trim())))
    }

    val located = mutableListOf<LocatedSteps>()
    SELFIE_CAPTURE.findAll(normalized).forEach { match ->
        val seconds = TIMER_PATTERNS.firstNotNullOfOrNull { pattern ->
            pattern.find(match.value)?.groupValues?.get(1)?.let(::parseSeconds)
        }
        located += LocatedSteps(
            match.range,
            listOf(
                CommandPlanStep.SetCamera(CameraFacing.FRONT),
                CommandPlanStep.Capture(seconds),
            ),
        )
    }
    fun addCameraMatches(pattern: Regex, facing: CameraFacing) {
        pattern.findAll(normalized).forEach { match ->
            if (located.none { rangesOverlap(it.range, match.range) }) {
                located += LocatedSteps(match.range, listOf(CommandPlanStep.SetCamera(facing)))
            }
        }
    }
    addCameraMatches(FRONT_CAMERA, CameraFacing.FRONT)
    addCameraMatches(REAR_CAMERA, CameraFacing.REAR)
    addCameraMatches(SWITCH_CAMERA, CameraFacing.TOGGLE)

    val captureCommand = Regex(
        "(?:(?:countdown|timer(?:\\s+for)?)\\s+$NUMBER_TOKEN(?:\\s+seconds?)?\\s+(?:and\\s+)?)?" +
            CAPTURE_WORD.pattern +
            "(?:\\s+(?:in|after)\\s+$NUMBER_TOKEN(?:\\s+seconds?)?)?",
        RegexOption.IGNORE_CASE,
    )
    captureCommand.findAll(normalized).forEach { match ->
        if (located.none { rangesOverlap(it.range, match.range) }) {
            val seconds = TIMER_PATTERNS.firstNotNullOfOrNull { pattern ->
                pattern.find(match.value)?.groupValues?.get(1)?.let(::parseSeconds)
            }
            if (seconds == null || seconds in 1..30) {
                located += LocatedSteps(match.range, listOf(CommandPlanStep.Capture(seconds)))
            }
        }
    }

    if (located.isEmpty()) return CommandPlan(listOf(CommandPlanStep.Coach(text.trim())))
    val steps = mutableListOf<CommandPlanStep>()
    var cursor = 0
    located.sortedBy { it.range.first }.forEach { action ->
        coachingFragment(normalized.substring(cursor, action.range.first))?.let {
            steps += CommandPlanStep.Coach(it)
        }
        steps += action.steps
        cursor = action.range.last + 1
    }
    coachingFragment(normalized.substring(cursor))?.let { steps += CommandPlanStep.Coach(it) }
    if (steps.size > 8 || steps.dropLast(1).any { it is CommandPlanStep.Capture }) {
        return CommandPlan(listOf(CommandPlanStep.Coach(text.trim())))
    }
    return CommandPlan(steps)
}

private fun rangesOverlap(first: IntRange, second: IntRange): Boolean =
    first.first <= second.last && second.first <= first.last

private fun coachingFragment(value: String): String? = value
    .trim(' ', ',', '.', ';', ':', '-')
    .replace(Regex("^(?:and|then|please)\\b\\s*|\\s*\\b(?:and|then)$"), "")
    .trim(' ', ',', '.', ';', ':', '-')
    .takeIf { it.isNotBlank() }

fun parseVoiceCommand(text: String): VoiceCommand? {
    val normalized = text.trim().lowercase()
    if (NEGATED_CAMERA_SWITCH.containsMatchIn(normalized)) return null
    if (FRONT_CAMERA.containsMatchIn(normalized)) return VoiceCommand.UseFrontCamera
    if (REAR_CAMERA.containsMatchIn(normalized)) return VoiceCommand.UseRearCamera
    if (SWITCH_CAMERA.containsMatchIn(normalized)) return VoiceCommand.SwitchCamera
    if (NEGATED_CAPTURE.containsMatchIn(normalized)) return null
    val mentionsCapture = CAPTURE_WORD.containsMatchIn(normalized) ||
        normalized in setOf("cheese", "shutter")
    if (!mentionsCapture) return null
    val seconds = TIMER_PATTERNS.firstNotNullOfOrNull { pattern ->
        pattern.find(normalized)?.groupValues?.get(1)?.let(::parseSeconds)
    }
    return if (seconds != null) {
        seconds.takeIf { it in 1..30 }?.let(VoiceCommand::CountdownAndTakePicture)
    } else if (TIMER_CUE.containsMatchIn(normalized)) {
        null
    } else {
        VoiceCommand.TakePicture
    }
}
