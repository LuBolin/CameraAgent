package com.bolin.photohelper.visual

import com.bolin.photohelper.gallery.CaptionLength
import com.bolin.photohelper.gallery.PreparedImage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryAiContractsTest {
    @Test
    fun `follow-up edit sends original first and working image last`() {
        val body = buildImageEditRequestBody(
            ImageEditRequest(
                images = listOf(
                    PreparedImage(byteArrayOf(1, 2), 1200, 1600, "image/jpeg"),
                    PreparedImage(byteArrayOf(3, 4), 1000, 1500, "image/jpeg"),
                ),
                instruction = "Remove the bird",
            ),
        )
        val root = JSONObject(body.toString(Charsets.UTF_8))
        val content = root.getJSONObject("input").getJSONArray("messages").getJSONObject(0).getJSONArray("content")

        assertTrue(content.getJSONObject(0).getString("image").endsWith("AQI="))
        assertTrue(content.getJSONObject(1).getString("image").endsWith("AwQ="))
        assertTrue(content.getJSONObject(2).getString("text").contains("Image 2 is the Working Asset"))
        assertEquals("1008*1504", root.getJSONObject("parameters").getString("size"))
        assertFalse(root.getJSONObject("parameters").getBoolean("prompt_extend"))
        assertFalse(root.getJSONObject("parameters").getBoolean("watermark"))
    }

    @Test
    fun `edit output size upscales small photos within the provider range`() {
        assertEquals("512*512", imageEditOutputSize(100, 100))
        assertTrue(runCatching { imageEditOutputSize(100, 1000) }.isFailure)
    }

    @Test
    fun `image result requires a successful strict https payload`() {
        val valid = """{
            "request_id":"req-1",
            "output":{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":[
                {"image":"https://result.example.com/edit.png?token=1"}
            ]}}]}
        }""".trimIndent()
        assertEquals("https://result.example.com/edit.png?token=1", parseImageEditResponse(valid)?.url)

        assertNull(parseImageEditResponse(valid.replace("https://", "http://")))
        assertNull(parseImageEditResponse(valid.replace("result.example.com", "127.0.0.1")))
        assertNull(parseImageEditResponse(valid.replace("\"stop\"", "\"length\"")))
    }

    @Test
    fun `caption parser keeps only a valid bounded caption`() {
        assertEquals("A quiet afternoon", parseCaptionResponse(completion("A quiet afternoon"), CaptionLength.SHORT))
        assertNull(parseCaptionResponse(completion("x".repeat(81)), CaptionLength.SHORT))
        assertNull(parseCaptionResponse(completion(""), CaptionLength.SHORT))
    }

    private fun completion(caption: String): String = JSONObject()
        .put("id", "completion-1")
        .put("object", "chat.completion")
        .put("model", QWEN_MODEL)
        .put(
            "choices",
            org.json.JSONArray().put(
                JSONObject()
                    .put("finish_reason", "stop")
                    .put(
                        "message",
                        JSONObject()
                            .put("role", "assistant")
                            .put("content", JSONObject().put("schemaVersion", 1).put("caption", caption).toString()),
                    ),
            ),
        )
        .toString()
}
