package com.bolin.photohelper.visual

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
        val authorization = try {
            apiKey.takeIf(::isValidApiKey)?.concatToString()?.let { "Bearer $it" }
        } finally {
            apiKey.fill('\u0000')
        }
        if (authorization == null) return VisualResult.Unavailable
        val body = try {
            buildVisualRequestBody(request)
        } catch (_: IllegalArgumentException) {
            return VisualResult.Unavailable
        } catch (_: JSONException) {
            return VisualResult.Unavailable
        }
        if (!callLimiter.tryAcquire()) {
            body.fill(0)
            return VisualResult.Unavailable
        }

        return try {
            withTimeout(NETWORK_TIMEOUT_MS) {
                runInterruptible(Dispatchers.IO) {
                    execute(request, body, authorization)
                }
            }
        } catch (_: TimeoutCancellationException) {
            VisualResult.Unavailable
        } catch (_: IOException) {
            VisualResult.Unavailable
        } finally {
            body.fill(0)
        }
    }

    private fun execute(
        request: VisualRequest,
        body: ByteArray,
        authorization: String,
    ): VisualResult {
        val connection = connectionFactory(URL(BAILIAN_ENDPOINT))
        return try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = NETWORK_TIMEOUT_MS.toInt()
            connection.readTimeout = NETWORK_TIMEOUT_MS.toInt()
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
                return visualFailureForHttpStatus(status)
            }
            val response = connection.inputStream.use(::readLimited) ?: return VisualResult.Unavailable
            parseVisualResponse(
                response = response.toString(StandardCharsets.UTF_8),
                family = request.family,
            )?.let { VisualResult.Available(it) } ?: VisualResult.Unavailable
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
        const val NETWORK_TIMEOUT_MS = 5_000L
        const val MAX_HTTP_RESPONSE_BYTES = 64 * 1024
        val PROCESS_CALL_LIMITER = VisualCallLimiter()
    }
}
