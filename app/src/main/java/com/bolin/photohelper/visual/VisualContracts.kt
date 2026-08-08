package com.bolin.photohelper.visual

import com.bolin.photohelper.coach.VisualClarificationReason
import com.bolin.photohelper.coach.VisualFamily
import com.bolin.photohelper.coach.VisualHint
import com.bolin.photohelper.coach.VisualIntent
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

internal const val BAILIAN_ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
internal const val QWEN_MODEL = "qwen3.7-flash-2026-07-15"
internal const val MAX_API_KEY_CHARACTERS = 512
internal const val MAX_COMMENT_CHARACTERS = 300
internal const val MAX_OBSERVATION_JPEG_BYTES = 300 * 1024
internal const val MAX_REQUEST_BODY_BYTES = 450 * 1024
internal const val MAX_RESPONSE_CONTENT_BYTES = 512
internal const val VISUAL_CALLS_PER_MINUTE = 6

internal fun isValidApiKey(apiKey: CharArray): Boolean =
    apiKey.size in 1..MAX_API_KEY_CHARACTERS && apiKey.all { it.code in 0x21..0x7e }

class VisualRequest(
    val family: VisualFamily,
    val comment: String,
    val observationJpeg: ByteArray,
) {
    init {
        require(comment.isNotBlank() && comment.length <= MAX_COMMENT_CHARACTERS) {
            "Comment must contain 1..$MAX_COMMENT_CHARACTERS characters"
        }
        require(observationJpeg.size in 1..MAX_OBSERVATION_JPEG_BYTES) {
            "Observation Image must contain 1..$MAX_OBSERVATION_JPEG_BYTES bytes"
        }
    }

    override fun toString(): String =
        "VisualRequest(family=$family, comment=<redacted>, observationJpeg=<${observationJpeg.size} bytes>)"
}

sealed interface VisualResult {
    data class Available(val hint: VisualHint) : VisualResult
    data object CredentialsRejected : VisualResult
    data object Unavailable : VisualResult
}

internal class VisualCallLimiter {
    private val attempts = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(nowNanos: Long = System.nanoTime()): Boolean {
        while (attempts.isNotEmpty() && nowNanos - attempts.first >= WINDOW_NANOS) {
            attempts.removeFirst()
        }
        if (attempts.size >= VISUAL_CALLS_PER_MINUTE) return false
        attempts.addLast(nowNanos)
        return true
    }

    private companion object {
        const val WINDOW_NANOS = 60_000_000_000L
    }
}

internal fun buildVisualRequestBody(request: VisualRequest): ByteArray {
    val dataUrl = "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(request.observationJpeg)}"
    val prompt = when (request.family) {
        VisualFamily.COLOR_CAST ->
            "Prompt v2: family=COLOR_CAST; comment=${request.comment}; " +
                "WHITE_BALANCE_WARMER when neutral objects look blue/cyan; " +
                "WHITE_BALANCE_COOLER when neutral objects look yellow/orange; " +
                "choose one allowed intent only when image evidence supports it, otherwise clarify; " +
                "return JSON only in exactly one shape: " +
                "{\"schemaVersion\":2,\"outcome\":\"INTENT\",\"intent\":\"<INTENT>\"} or " +
                "{\"schemaVersion\":2,\"outcome\":\"CLARIFY\",\"reason\":\"<REASON>\"}; " +
                "outcome must be the literal value INTENT or CLARIFY, and an intent label may appear only in intent; " +
                "allowed INTENT labels=WHITE_BALANCE_WARMER|WHITE_BALANCE_COOLER; " +
                "allowed REASON labels=VISUAL_INSUFFICIENT|SUBJECT_UNCLEAR|SCENE_CONFOUND; no other keys or prose"
        VisualFamily.FACE_SIZE_AMBIGUOUS ->
            "Prompt v3: family=FACE_SIZE_AMBIGUOUS; comment=${request.comment}; inspect facial proportions only. " +
                "Is there visible close or wide-angle perspective distortion, such as central features or the nose " +
                "enlarged relative to the ears and sides of the face? Do not infer distortion from a large face, tight " +
                "crop, or proximity alone. Return JSON only in exactly one shape: " +
                "{\"schemaVersion\":3,\"outcome\":\"INTENT\",\"distortionVisible\":true} or " +
                "{\"schemaVersion\":3,\"outcome\":\"INTENT\",\"distortionVisible\":false} or " +
                "{\"schemaVersion\":3,\"outcome\":\"CLARIFY\",\"reason\":\"<REASON>\"}; " +
                "outcome must be the literal value INTENT or CLARIFY; distortionVisible must be a JSON boolean; " +
                "allowed REASON labels=VISUAL_INSUFFICIENT|SUBJECT_UNCLEAR|SCENE_CONFOUND; no other keys or prose"
    }
    val body = JSONObject()
        .put("model", QWEN_MODEL)
        .put(
            "messages",
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("type", "image_url")
                                    .put("image_url", JSONObject().put("url", dataUrl)),
                            )
                            .put(JSONObject().put("type", "text").put("text", prompt)),
                    ),
            ),
        )
        .put("enable_thinking", false)
        .put("temperature", 0)
        .put("stream", false)
        .put("response_format", JSONObject().put("type", "json_object"))
        .toString()
        .toByteArray(StandardCharsets.UTF_8)
    require(body.size <= MAX_REQUEST_BODY_BYTES) { "Visual request exceeds $MAX_REQUEST_BODY_BYTES bytes" }
    return body
}

internal fun parseVisualResponse(
    response: String,
    family: VisualFamily,
): VisualHint? {
    return try {
        val root = strictObject(response) ?: return null
        if (root.opt("object") != "chat.completion") return null
        if ((root.opt("id") as? String)?.isNotBlank() != true || root.opt("model") != QWEN_MODEL) return null

        val choices = root.opt("choices") as? JSONArray ?: return null
        if (choices.length() != 1) return null
        val choice = choices.optJSONObject(0) ?: return null
        if (choice.opt("finish_reason") != "stop") return null
        val message = choice.opt("message") as? JSONObject ?: return null
        if (message.opt("role") != "assistant") return null
        if (!isAbsentNullOrEmptyArray(message, "tool_calls")) return null
        if (!isAbsentNullOrEmpty(message, "reasoning_content")) return null
        if (!isAbsentNullOrEmpty(message, "refusal")) return null

        val content = message.opt("content") as? String ?: return null
        if (content.toByteArray(StandardCharsets.UTF_8).size > MAX_RESPONSE_CONTENT_BYTES) return null
        parseVisualHint(content.trim(), family)
    } catch (_: JSONException) {
        null
    }
}

private fun parseVisualHint(content: String, family: VisualFamily): VisualHint? {
    val value = strictObject(content) ?: return null
    val schemaVersion = value.opt("schemaVersion") as? Int ?: return null
    return when (value.opt("outcome")) {
        "INTENT" -> {
            when (family) {
                VisualFamily.COLOR_CAST -> {
                    if (schemaVersion != 2 || value.keysSet() != setOf("schemaVersion", "outcome", "intent")) return null
                    (value.opt("intent") as? String)
                        ?.let { runCatching { VisualIntent.valueOf(it) }.getOrNull() }
                        ?.takeIf { it == VisualIntent.WHITE_BALANCE_WARMER || it == VisualIntent.WHITE_BALANCE_COOLER }
                        ?.let { VisualHint.Intent(it) }
                }
                VisualFamily.FACE_SIZE_AMBIGUOUS -> {
                    if (schemaVersion != 3 || value.keysSet() != setOf("schemaVersion", "outcome", "distortionVisible")) return null
                    val distortionVisible = value.opt("distortionVisible") as? Boolean ?: return null
                    VisualHint.Intent(
                        if (distortionVisible) VisualIntent.CLOSE_PERSPECTIVE_ADVISORY
                        else VisualIntent.FACE_OCCUPANCY_LOWER,
                    )
                }
            }
        }
        "CLARIFY" -> {
            if (schemaVersion != if (family == VisualFamily.COLOR_CAST) 2 else 3) return null
            if (value.keysSet() != setOf("schemaVersion", "outcome", "reason")) return null
            (value.opt("reason") as? String)
                ?.let { runCatching { VisualClarificationReason.valueOf(it) }.getOrNull() }
                ?.let { VisualHint.Clarify(it) }
        }
        else -> null
    }
}

private fun strictObject(json: String): JSONObject? {
    if (json.isEmpty()) return null
    val tokener = JSONTokener(json)
    val value = tokener.nextValue() as? JSONObject ?: return null
    return value.takeIf { tokener.nextClean().code == 0 }
}

private fun JSONObject.keysSet(): Set<String> = buildSet {
    val iterator = keys()
    while (iterator.hasNext()) add(iterator.next())
}

private fun isAbsentNullOrEmpty(objectValue: JSONObject, key: String): Boolean {
    if (!objectValue.has(key) || objectValue.isNull(key)) return true
    return objectValue.opt(key) == ""
}

private fun isAbsentNullOrEmptyArray(objectValue: JSONObject, key: String): Boolean {
    if (!objectValue.has(key) || objectValue.isNull(key)) return true
    return (objectValue.opt(key) as? JSONArray)?.length() == 0
}
