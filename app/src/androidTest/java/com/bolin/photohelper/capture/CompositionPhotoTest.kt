package com.bolin.photohelper.capture

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bolin.photohelper.coach.CompositionIntent
import com.bolin.photohelper.coach.GuidanceGovernor
import com.bolin.photohelper.coach.automaticCompositionMembers
import com.bolin.photohelper.coach.compileComposition
import com.bolin.photohelper.coach.measureGuidance
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in still-photo probe. No network calls or synthetic face boxes. */
@RunWith(AndroidJUnit4::class)
class CompositionPhotoTest {
    @Test fun downloadedPhotosThroughLocalComposition() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("compositionPhotos") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "composition-online")
        val photos = directory.listFiles { file -> file.extension == "jpg" }.orEmpty().sortedBy { it.name }
        assertTrue("Push photos into $directory before running", photos.isNotEmpty())
        val report = JSONArray()
        try {
            for (photo in photos) {
                // The live detector tracks across frames. Unrelated stills need independent sessions.
                val camera = CameraXSession(context)
                val observation = try {
                    camera.createReviewObservation(Uri.fromFile(photo), null, null)
                } finally { camera.close() }
                // A still tests geometry, not temporal tracking. Repeat it only to exercise proposal/dwell.
                val samples = listOf(0L, 250L, 500L).map { observation.copy(id = it, timestampMs = it) }
                val automatic = automaticCompositionMembers(samples)
                val members = automatic ?: observation.faces // Explicit simulated "Everyone" correction.
                val selected = observation.copy(faces = members)
                val plan = compileComposition(CompositionIntent(), members)
                val measurement = measureGuidance(plan.targets, selected)
                val governor = GuidanceGovernor(0)
                val instructions = JSONArray()
                if (measurement != null) {
                    for (time in listOf(0L, 250L, 500L, 750L)) {
                        val result = governor.update(measurement, time)
                        instructions.put(JSONObject().put("timeMs", time)
                            .put("instruction", result.correction.phoneInstruction())
                            .put("complete", result.complete))
                    }
                }
                val boxes = JSONArray()
                for (face in observation.faces) boxes.put(JSONObject()
                    .put("left", face.left).put("top", face.top)
                    .put("right", face.right).put("bottom", face.bottom)
                    .put("visibleFraction", face.visibleFraction))
                report.put(JSONObject().put("file", photo.name)
                    .put("width", observation.sourceWidth).put("height", observation.sourceHeight)
                    .put("detectedFaces", boxes).put("automaticSelection", automatic != null)
                    .put("selectedCount", members.size).put("mode", plan.guidanceMode.name)
                    .put("targets", plan.targets.toString()).put("advice", plan.advice)
                    .put("correction", measurement?.correction?.name ?: JSONObject.NULL)
                    .put("instruction", measurement?.correction?.phoneInstruction() ?: plan.advice)
                    .put("withinTolerance", measurement?.satisfied ?: false)
                    .put("governor", instructions))
            }
        } finally {
            File(directory, "results.json").writeText(report.toString(2))
        }
        val distant = (0 until report.length()).map { report.getJSONObject(it) }
            .single { it.getString("file") == "bad-focus-1-500x373.jpg" }
        assertEquals(1, distant.getInt("selectedCount"))
        assertEquals("Zoom in slightly", distant.getString("instruction"))
        val group = (0 until report.length()).map { report.getJSONObject(it) }
            .single { it.getString("file") == "so-close.jpg" }
        assertEquals(5, group.getInt("selectedCount"))
        assertEquals("Aim the phone slightly right", group.getString("instruction"))
    }
}
