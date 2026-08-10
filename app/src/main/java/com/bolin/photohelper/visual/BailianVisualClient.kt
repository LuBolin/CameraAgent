package com.bolin.photohelper.visual

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import kotlin.math.max
import kotlin.math.min

internal fun visualFailureForHttpStatus(status: Int): VisualResult =
    if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
        VisualResult.CredentialsRejected
    } else {
        VisualResult.Unavailable
    }

class BailianVisualClient internal constructor(
    private val connectionFactory: (URL) -> HttpsURLConnection,
    private val callLimiter: VisualCallLimiter = VisualCallLimiter(),
) {
    constructor() : this(
        connectionFactory = { it.openConnection() as HttpsURLConnection },
        callLimiter = PROCESS_CALL_LIMITER,
    )

    suspend fun interpret(request: VisualRequest, apiKey: CharArray): VisualResult {
        val focusGuide = request.focusGrid?.let { createFocusGridGuide(request.observationJpeg, it) }
        if (request.family == com.bolin.photohelper.coach.VisualFamily.OBJECT_FOCUS && focusGuide == null) {
            apiKey.fill('\u0000')
            return VisualResult.Unavailable
        }
        val timeoutMs = if (focusGuide == null) VISUAL_NETWORK_TIMEOUT_MS else OBJECT_FOCUS_NETWORK_TIMEOUT_MS
        val result = try {
            call(apiKey, timeoutMs) { buildVisualRequestBody(request, focusGuide) }
        } finally {
            focusGuide?.fill(0)
        }
        return when (result) {
            is ProviderCall.Available -> parseVisualResponse(result.response, request.family, request.focusGrid)
                ?.let(VisualResult::Available) ?: VisualResult.Unavailable
            ProviderCall.CredentialsRejected -> VisualResult.CredentialsRejected
            ProviderCall.Unavailable -> VisualResult.Unavailable
        }
    }

    suspend fun classify(request: ComplaintRequest, apiKey: CharArray): ComplaintResult =
        when (val result = call(apiKey, COMPLAINT_NETWORK_TIMEOUT_MS) { buildComplaintRequestBody(request) }) {
            is ProviderCall.Available -> parseComplaintResponse(result.response)
                ?.let(ComplaintResult::Available) ?: ComplaintResult.Unavailable
            ProviderCall.CredentialsRejected -> ComplaintResult.CredentialsRejected
            ProviderCall.Unavailable -> ComplaintResult.Unavailable
        }

    private suspend fun call(
        apiKey: CharArray,
        timeoutMs: Long,
        buildBody: () -> ByteArray,
    ): ProviderCall {
        val authorization = try {
            apiKey.takeIf(::isValidApiKey)?.concatToString()?.let { "Bearer $it" }
        } finally {
            apiKey.fill('\u0000')
        }
        if (authorization == null) return ProviderCall.Unavailable
        val body = try {
            buildBody()
        } catch (_: IllegalArgumentException) {
            return ProviderCall.Unavailable
        } catch (_: JSONException) {
            return ProviderCall.Unavailable
        }
        if (!callLimiter.tryAcquire()) {
            body.fill(0)
            return ProviderCall.Unavailable
        }

        return try {
            withTimeout(timeoutMs) {
                runInterruptible(Dispatchers.IO) {
                    execute(body, authorization, timeoutMs)
                }
            }
        } catch (_: TimeoutCancellationException) {
            ProviderCall.Unavailable
        } catch (_: IOException) {
            ProviderCall.Unavailable
        } finally {
            body.fill(0)
        }
    }

    private fun execute(
        body: ByteArray,
        authorization: String,
        timeoutMs: Long,
    ): ProviderCall {
        val connection = connectionFactory(URL(BAILIAN_ENDPOINT))
        return try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = timeoutMs.toInt()
            connection.readTimeout = timeoutMs.toInt()
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty("Authorization", authorization)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }

            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                connection.errorStream?.close()
                return if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
                    ProviderCall.CredentialsRejected
                } else {
                    ProviderCall.Unavailable
                }
            }
            val response = connection.inputStream.use(::readLimited) ?: return ProviderCall.Unavailable
            ProviderCall.Available(response.toString(StandardCharsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        return try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_HTTP_RESPONSE_BYTES) return null
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } finally {
            buffer.fill(0)
        }
    }

    private companion object {
        const val VISUAL_NETWORK_TIMEOUT_MS = 5_000L
        const val OBJECT_FOCUS_NETWORK_TIMEOUT_MS = 20_000L
        const val COMPLAINT_NETWORK_TIMEOUT_MS = 15_000L
        const val MAX_HTTP_RESPONSE_BYTES = 64 * 1024
        val PROCESS_CALL_LIMITER = VisualCallLimiter()
    }
}

internal fun createFocusGridGuide(observationJpeg: ByteArray, grid: FocusGrid): ByteArray? {
    val source = BitmapFactory.decodeByteArray(observationJpeg, 0, observationJpeg.size) ?: return null
    val guide = try {
        source.copy(Bitmap.Config.ARGB_8888, true)
    } finally {
        source.recycle()
    }
    return try {
        val canvas = Canvas(guide)
        val cellWidth = guide.width.toFloat() / grid.columns
        val cellHeight = guide.height.toFloat() / grid.rows
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 213, 79)
            style = Paint.Style.STROKE
            strokeWidth = max(2f, min(guide.width, guide.height) / 240f)
        }
        for (column in 0..grid.columns) {
            val x = column * cellWidth
            canvas.drawLine(x, 0f, x, guide.height.toFloat(), linePaint)
        }
        for (row in 0..grid.rows) {
            val y = row * cellHeight
            canvas.drawLine(0f, y, guide.width.toFloat(), y, linePaint)
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = max(14f, min(cellWidth, cellHeight) * .30f)
            typeface = Typeface.DEFAULT_BOLD
            strokeJoin = Paint.Join.ROUND
        }
        val padding = max(2f, labelPaint.textSize * .14f)
        for (row in 0 until grid.rows) {
            for (column in 0 until grid.columns) {
                val label = "$column,$row"
                val x = column * cellWidth + padding
                val y = row * cellHeight + labelPaint.textSize + padding
                labelPaint.style = Paint.Style.STROKE
                labelPaint.strokeWidth = max(2f, labelPaint.textSize * .22f)
                labelPaint.color = Color.BLACK
                canvas.drawText(label, x, y, labelPaint)
                labelPaint.style = Paint.Style.FILL
                labelPaint.color = Color.rgb(255, 213, 79)
                canvas.drawText(label, x, y, labelPaint)
            }
        }

        val output = ByteArrayOutputStream()
        for (quality in intArrayOf(88, 78, 68, 58)) {
            output.reset()
            if (guide.compress(Bitmap.CompressFormat.JPEG, quality, output) &&
                output.size() <= MAX_FOCUS_GUIDE_JPEG_BYTES
            ) return output.toByteArray()
        }
        null
    } finally {
        guide.recycle()
    }
}

private sealed interface ProviderCall {
    data class Available(val response: String) : ProviderCall
    data object CredentialsRejected : ProviderCall
    data object Unavailable : ProviderCall
}
