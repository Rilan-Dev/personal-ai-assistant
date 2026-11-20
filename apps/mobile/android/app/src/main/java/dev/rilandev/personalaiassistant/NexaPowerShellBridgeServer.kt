package dev.rilandev.personalaiassistant

import android.net.LocalServerSocket
import android.net.LocalSocket
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Debug-only HTTP-like RPC over an Android abstract local socket. */
internal class NexaPowerShellBridgeServer(
    private val context: ReactApplicationContext
) : Closeable {
    companion object {
        val SOCKET_NAME = "nexa-qa-control-${BuildConfig.APPLICATION_ID.substringAfterLast('.')}"
        const val EVENT_NAME = "NexaPowerShellRequest"
        private const val MAX_LINE_BYTES = 2_048
        private const val MAX_HEADER_BYTES = 8_192
        private const val MAX_BODY_BYTES = 16_384
        private const val MAX_RESPONSE_BYTES = 2 * 1_024 * 1_024
        private const val RESPONSE_TIMEOUT_SECONDS = 36L
        private val requestIdPattern = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            RegexOption.IGNORE_CASE
        )
    }

    private data class PendingReply(
        val latch: CountDownLatch = CountDownLatch(1),
        @Volatile var json: String? = null
    )

    @Volatile private var running = false
    @Volatile private var serverSocket: LocalServerSocket? = null
    private val pending = java.util.concurrent.ConcurrentHashMap<String, PendingReply>()
    private val workers = Executors.newSingleThreadExecutor { task ->
        Thread(task, "NexaPowerShellRpcWorker").apply { isDaemon = true }
    }
    private val seenLock = Any()
    private val seenRequestIds = LinkedHashSet<String>()

    @Synchronized fun start(): Boolean {
        if (running && serverSocket != null) return true
        if (!BuildConfig.DEBUG) return false
        return try {
            val socket = LocalServerSocket(SOCKET_NAME)
            serverSocket = socket
            running = true
            Thread({ acceptLoop(socket) }, "NexaPowerShellRpcAccept").apply {
                isDaemon = true
                start()
            }
            true
        } catch (_: Exception) {
            running = false
            serverSocket = null
            false
        }
    }

    private fun acceptLoop(socket: LocalServerSocket) {
        while (running) {
            val client = try { socket.accept() } catch (_: Exception) { break }
            try {
                workers.execute { handle(client) }
            } catch (_: Exception) {
                try { client.close() } catch (_: Exception) {}
            }
        }
    }

    private fun handle(socket: LocalSocket) {
        socket.use { client ->
            try {
                client.setSoTimeout(40_000)
                val request = readRequest(client.inputStream)
                if (request.error != null) {
                    writeResponse(client, request.status, errorJson(request.error))
                    return
                }
                val id = request.requestId ?: run {
                    writeResponse(client, 400, errorJson("INVALID_REQUEST_ID"))
                    return
                }
                synchronized(seenLock) {
                    if (id in seenRequestIds) {
                        writeResponse(client, 409, errorJson("REQUEST_REPLAYED"))
                        return
                    }
                    if (seenRequestIds.size >= 2_048) {
                        writeResponse(client, 429, errorJson("REQUEST_BUDGET_EXHAUSTED"))
                        return
                    }
                    seenRequestIds.add(id)
                }
                val rawBody = request.body.toString(StandardCharsets.UTF_8)
                val body = try {
                    if (rawBody.isBlank()) JSONObject() else JSONObject(rawBody)
                } catch (_: Exception) {
                    writeResponse(client, 400, errorJson("INVALID_JSON"))
                    return
                }
                val envelope = JSONObject()
                    .put("requestId", id)
                    .put("method", request.method)
                    .put("path", request.path)
                    .put("body", body)
                val result = dispatchToReact(id, envelope.toString())
                if (result == null) {
                    writeResponse(client, 504, errorJson("APP_REQUEST_TIMEOUT"))
                    return
                }
                if (result.toByteArray(StandardCharsets.UTF_8).size > MAX_RESPONSE_BYTES) {
                    writeResponse(client, 500, errorJson("RESPONSE_TOO_LARGE"))
                    return
                }
                writeResponse(client, 200, result)
            } catch (_: Exception) {
                try { writeResponse(client, 400, errorJson("MALFORMED_REQUEST")) }
                catch (_: Exception) {}
            }
        }
    }

    private data class ParsedRequest(
        val method: String? = null,
        val path: String? = null,
        val requestId: String? = null,
        val body: ByteArray = byteArrayOf(),
        val status: Int = 400,
        val error: String? = null
    )

    private fun readRequest(input: InputStream): ParsedRequest {
        val requestLine = readBoundedLine(input, MAX_LINE_BYTES)
            ?: return ParsedRequest(error = "EMPTY_REQUEST")
        val pieces = requestLine.toString(StandardCharsets.US_ASCII).split(' ')
        if (pieces.size != 3 || pieces[2] !in setOf("HTTP/1.0", "HTTP/1.1") ||
            pieces[0] !in setOf("GET", "POST") ||
            !pieces[1].matches(Regex("^/v1/[a-z]+$")))
            return ParsedRequest(error = "INVALID_REQUEST_LINE")

        val headers = linkedMapOf<String, String>()
        var consumed = requestLine.size + 2
        var count = 0
        while (true) {
            val line = readBoundedLine(input, MAX_LINE_BYTES)
                ?: return ParsedRequest(error = "INCOMPLETE_HEADERS")
            consumed += line.size + 2
            if (consumed > MAX_HEADER_BYTES) return ParsedRequest(status = 413, error = "HEADERS_TOO_LARGE")
            if (line.isEmpty()) break
            if (++count > 32) return ParsedRequest(status = 413, error = "TOO_MANY_HEADERS")
            val text = line.toString(StandardCharsets.US_ASCII)
            val colon = text.indexOf(':')
            if (colon <= 0) return ParsedRequest(error = "INVALID_HEADER")
            val name = text.substring(0, colon).trim().lowercase()
            val value = text.substring(colon + 1).trim()
            if (name in headers) return ParsedRequest(error = "DUPLICATE_HEADER")
            headers[name] = value
        }

        if (headers.containsKey("transfer-encoding"))
            return ParsedRequest(error = "TRANSFER_ENCODING_NOT_ALLOWED")
        val id = headers["x-nexa-request-id"]
        if (id == null || !requestIdPattern.matches(id))
            return ParsedRequest(error = "INVALID_REQUEST_ID")
        val contentLength = try { (headers["content-length"] ?: "0").toInt() }
            catch (_: Exception) { return ParsedRequest(error = "INVALID_CONTENT_LENGTH") }
        if (contentLength < 0) return ParsedRequest(error = "INVALID_CONTENT_LENGTH")
        if (contentLength > MAX_BODY_BYTES)
            return ParsedRequest(status = 413, error = "BODY_TOO_LARGE")
        if (pieces[0] == "POST" &&
            headers["content-type"]?.substringBefore(';')?.trim()?.lowercase() != "application/json")
            return ParsedRequest(status = 415, error = "JSON_REQUIRED")
        val body = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = input.read(body, offset, contentLength - offset)
            if (read <= 0) return ParsedRequest(error = "INCOMPLETE_BODY")
            offset += read
        }
        return ParsedRequest(pieces[0], pieces[1], id, body)
    }

    private fun readBoundedLine(input: InputStream, maxBytes: Int): ByteArray? {
        val bytes = ByteArrayOutputStream()
        var previous = -1
        while (bytes.size() <= maxBytes) {
            val next = input.read()
            if (next < 0) return if (bytes.size() == 0) null else null
            if (previous == '\r'.code && next == '\n'.code) {
                val current = bytes.toByteArray()
                return if (current.isNotEmpty() && current.last() == '\r'.code.toByte())
                    current.copyOf(current.size - 1) else current
            }
            bytes.write(next)
            previous = next
        }
        throw IllegalArgumentException("line too long")
    }

    private fun dispatchToReact(id: String, envelopeJson: String): String? {
        if (!running || !BuildConfig.DEBUG) return errorJson("DEBUG_BRIDGE_UNAVAILABLE")
        val reply = PendingReply()
        if (pending.putIfAbsent(id, reply) != null) return errorJson("REQUEST_REPLAYED")
        try {
            val event = Arguments.createMap()
                .apply {
                    putString("requestId", id)
                    putString("envelopeJson", envelopeJson)
                }
            context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(EVENT_NAME, event)
            if (!reply.latch.await(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) return null
            return reply.json
        } catch (_: Exception) {
            return errorJson("APP_DISPATCH_FAILED")
        } finally {
            pending.remove(id)
        }
    }

    fun reply(id: String, json: String): Boolean {
        if (!requestIdPattern.matches(id) || json.toByteArray(StandardCharsets.UTF_8).size > MAX_RESPONSE_BYTES)
            return false
        try { JSONObject(json) } catch (_: Exception) { return false }
        val item = pending[id] ?: return false
        if (item.json != null) return false
        item.json = json
        item.latch.countDown()
        return true
    }

    private fun writeResponse(socket: LocalSocket, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            409 -> "Conflict"
            413 -> "Payload Too Large"
            415 -> "Unsupported Media Type"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            504 -> "Gateway Timeout"
            else -> "Error"
        }
        val headers = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        socket.outputStream.write(headers.toByteArray(StandardCharsets.US_ASCII))
        socket.outputStream.write(bytes)
        socket.outputStream.flush()
    }

    private fun errorJson(code: String): String =
        JSONObject().put("ok", false).put("error", code).toString()

    override fun close() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        workers.shutdownNow()
        pending.values.forEach { it.latch.countDown() }
        pending.clear()
    }
}
