package com.bolin.photohelper.capture

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.bolin.photohelper.coach.*
import com.bolin.photohelper.visual.*
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Exports real app requests; validates real provider envelopes returned by the host runner. */
class CompositionQwenTest {
    @Test fun realProviderComposition() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("compositionQwen")
        assumeTrue(phase == "export" || phase == "refresh" || phase == "replay")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        val directory = File(context.getExternalFilesDir(null), args.getString("compositionDirectory") ?: "composition-qwen")
        val report = JSONArray()
        val files = args.getString("compositionFiles")?.split(",")
            ?: listOf("bad-focus-1-500x373.jpg", "bad-crop.jpg", "so-close.jpg", "bad-composition.jpg")
        for (filename in files) {
            val photo = File(directory, filename)
            val name = photo.nameWithoutExtension
            assertTrue(photo.exists())
            val camera = CameraXSession(context)
            try {
                if (phase == "export") {
                    val samples = mutableListOf<FrameObservation>()
                    repeat(8) { index ->
                        if (index > 0) delay(250)
                        samples += camera.createReviewObservation(Uri.fromFile(photo), null, null)
                    }
                    val observation = samples.last()
                    val automatic = automaticCompositionMembers(samples.takeLast(3))
                    val members = automatic ?: observation.faces
                    val faces = JSONArray()
                    members.forEach { face -> faces.put(JSONObject().put("id", face.trackingId)
                        .put("left", face.left).put("top", face.top).put("right", face.right)
                        .put("bottom", face.bottom).put("visibleFraction", face.visibleFraction)) }
                    File(directory, "$name-observation.json").writeText(JSONObject()
                        .put("width", observation.sourceWidth).put("height", observation.sourceHeight)
                        .put("automaticSelection", automatic != null).put("faces", faces)
                        .put("detectedCounts", JSONArray(samples.map { it.faces.size })).toString(2))
                    val bitmap = BitmapFactory.decodeFile(photo.absolutePath)
                    val jpeg = try { camera.encodeObservationJpeg(bitmap)!! } finally { bitmap.recycle() }
                    File(directory, "$name-submitted.jpg").writeBytes(jpeg)
                    jpeg.fill(0)
                }
                if (phase == "export" || phase == "refresh") {
                    val snapshot = JSONObject(File(directory, "$name-observation.json").readText())
                    val jpeg = File(directory, "$name-submitted.jpg").readBytes()
                    val members = (0 until snapshot.getJSONArray("faces").length()).map { index ->
                        val face = snapshot.getJSONArray("faces").getJSONObject(index)
                        FaceObservation(if (face.has("id")) face.getInt("id") else null,
                            face.getDouble("left").toFloat(), face.getDouble("top").toFloat(),
                            face.getDouble("right").toFloat(), face.getDouble("bottom").toFloat(),
                            face.getDouble("visibleFraction").toFloat())
                    }
                    val selectedWidth = faceUnion(members)?.widthFraction
                    val request = VisualRequest(VisualFamily.COMPOSITION,
                        "Suggest a composition for ${snapshot.getJSONArray("faces").length()} selected people, or scene advice if none. " +
                            (selectedWidth?.let { "Selected people width is ${(it * 100).toInt()}% of the image. " } ?: "") +
                            "All detected people are selected. Do not suggest selecting other people.", jpeg)
                    File(directory, "$name-request.json").writeBytes(buildVisualRequestBody(request))
                    jpeg.fill(0)
                } else {
                    val snapshot = JSONObject(File(directory, "$name-observation.json").readText())
                    val faces = snapshot.getJSONArray("faces")
                    val members = (0 until faces.length()).map { index ->
                        val face = faces.getJSONObject(index)
                        FaceObservation(if (face.has("id")) face.getInt("id") else null,
                            face.getDouble("left").toFloat(), face.getDouble("top").toFloat(),
                            face.getDouble("right").toFloat(), face.getDouble("bottom").toFloat(),
                            face.getDouble("visibleFraction").toFloat())
                    }
                    val observation = FrameObservation(1, 0, .5f, 0f, 0f, faces = members,
                        sourceWidth = snapshot.getInt("width"), sourceHeight = snapshot.getInt("height"))
                    val responseFile = File(directory, "$name-response.json")
                    val hint = if (responseFile.exists()) parseVisualResponse(responseFile.readText(), VisualFamily.COMPOSITION)
                        as? VisualHint.CompositionPlan else null
                    val network = JSONArray(File(directory, "network-results.json").readText())
                    val timing = (0 until network.length()).map { network.getJSONObject(it) }
                        .single { it.getString("case") == name }
                    val usable = hint != null && timing.optBoolean("withinAppDeadline") && timing.optInt("status") == 200
                    val plan = compileComposition(if (usable) hint!!.intent else CompositionIntent(), members)
                    val measured = measureGuidance(plan.targets, observation.copy(faces = members), distanceMovement = plan.distanceMovement)
                    val position = measureGuidance(plan.targets.filter {
                        it is VerificationTarget.FacePosition || it is VerificationTarget.GroupPosition
                    }, observation)
                    val baseline = compileComposition(CompositionIntent(), members)
                    val governor = GuidanceGovernor(0)
                    var result: GovernedGuidance? = null
                    if (measured != null) for (time in listOf(0L, 250L, 500L, 750L)) result = governor.update(measured, time)
                    report.put(JSONObject().put("image", "$name-submitted.jpg")
                        .put("faces", members.size).put("acceptedByAppParser", hint != null)
                        .put("withinAppDeadline", timing.optBoolean("withinAppDeadline"))
                        .put("elapsedMs", timing.optInt("elapsedMs"))
                        .put("localFallback", !usable).put("strategy", plan.intent.strategy.name)
                        .put("framing", plan.intent.framing.name).put("placement", plan.intent.placement.name)
                        .put("mode", plan.guidanceMode.name).put("targets", plan.targets.toString())
                        .put("positionOnlyDiagnostic", position?.correction?.phoneInstruction())
                        .put("sameTargetsAsDefault", plan.targets == baseline.targets)
                        .put("adjustment", plan.intent.adjustment?.toString())
                        .put("distanceMovement", plan.distanceMovement)
                        .put("instruction", result?.correction?.phoneInstruction() ?: plan.advice))
                }
            } finally { camera.close() }
        }
        if (phase == "replay") File(directory, "app-results.json").writeText(report.toString(2))
    }
}
