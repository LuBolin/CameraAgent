package com.bolin.photohelper.visual

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.bolin.photohelper.capture.FaceObservation
import java.io.ByteArrayOutputStream

/** Input-only selection cue. The semantic response still contains no geometry. */
internal fun markCompositionMembers(jpeg: ByteArray, members: List<FaceObservation>): ByteArray? {
    val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
    val marked = decoded.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
    decoded.recycle()
    if (marked == null) return null
    return try {
        val canvas = Canvas(marked)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            style = Paint.Style.STROKE
            strokeWidth = maxOf(2f, marked.width / 160f)
        }
        members.forEach { face ->
            canvas.drawRect(face.left * marked.width, face.top * marked.height,
                face.right * marked.width, face.bottom * marked.height, paint)
        }
        ByteArrayOutputStream().use { output ->
            if (!marked.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, output)) return null
            output.toByteArray().takeIf { it.size <= MAX_OBSERVATION_JPEG_BYTES }
        }
    } finally {
        marked.recycle()
    }
}
