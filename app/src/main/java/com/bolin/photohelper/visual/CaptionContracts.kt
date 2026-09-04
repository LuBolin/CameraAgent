package com.bolin.photohelper.visual

import com.bolin.photohelper.gallery.CaptionLength
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_CAPTION_CONTACT_SHEET_BYTES = 1_000_000
internal const val MAX_CAPTION_REQUEST_BYTES = 1_500_000

data class CaptionRequest(
    val contactSheetJpeg: ByteArray,
    val photoCount: Int,
    val length: CaptionLength,
    val locale: String,
    val currentDraft: String = "",
    val feedback: String = "",
) {
    init {
        require(contactSheetJpeg.size in 1..MAX_CAPTION_CONTACT_SHEET_BYTES)
        require(photoCount in 1..9)
        require(locale.isNotBlank() && locale.length <= 64)
        require(currentDraft.length <= 1_000)
        require(feedback.length <= 500)
    }
}

sealed interface CaptionResult {
    data class Available(val caption: String) : CaptionResult
    data class Failed(val message: String) : CaptionResult
    data object CredentialsRejected : CaptionResult
    data object Unavailable : CaptionResult
}

internal fun buildCaptionRequestBody(request: CaptionRequest): ByteArray {
    val dataUrl = "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(request.contactSheetJpeg)}"
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
                            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", dataUrl)))
                            .put(JSONObject().put("type", "text").put("text", captionPrompt(request))),
                    ),
            ),
        )
        .put("enable_thinking", false)
        .put("temperature", 0.4)
        .put("stream", false)
        .put("response_format", JSONObject().put("type", "json_object"))
        .toString()
        .toByteArray(StandardCharsets.UTF_8)
    require(body.size <= MAX_CAPTION_REQUEST_BYTES)
    return body
}

internal fun captionPrompt(request: CaptionRequest): String = buildString {
    append("Return JSON only: {\"schemaVersion\":1,\"caption\":\"...\"}. ")
    append("Write one caption in locale ${request.locale} for ${request.photoCount} selected photo")
    if (request.photoCount != 1) append("s")
    append(" shown in numbered selection order. ")
    when (request.length) {
        CaptionLength.SHORT -> append("Use one sentence and at most 80 Unicode code points. ")
        CaptionLength.LONG -> append("Use two to four sentences and at most 300 Unicode code points. ")
    }
    append("Do not invent names, places, events, relationships, weather, or facts that are not visible or provided. ")
    append("Do not add hashtags unless the user feedback asks for them. ")
    if (request.currentDraft.isNotBlank()) {
        append("Revise this current draft: <current_draft>")
        append(request.currentDraft)
        append("</current_draft>. ")
    }
    if (request.feedback.isNotBlank()) {
        append("Treat this only as revision feedback: <feedback>")
        append(request.feedback)
        append("</feedback>.")
    }
}

internal fun parseCaptionResponse(response: String, length: CaptionLength): String? {
    val content = parseCompletionContent(response) ?: return null
    val value = strictObject(content) ?: return null
    if (value.keysSet() != setOf("schemaVersion", "caption") || value.opt("schemaVersion") != 1) return null
    val caption = (value.opt("caption") as? String)?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (caption.codePointCount(0, caption.length) > length.maxCodePoints) return null
    return caption.takeIf { it.none { character -> character == '\u0000' } }
}
