package dev.rilandev.personalaiassistant

import android.content.Context
import android.net.Uri
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * Owner-invoked, read-only Voice Agent preflight. Fetches Sarvam's signed call
 * URL using credentials decrypted ONLY inside Android Keystore-backed storage.
 *
 * Does not connect a WebSocket, record audio, expose the signed URL to React
 * Native, or print credentials / response bodies. Validates that a committed
 * agent is addressable before the user starts a microphone foreground service.
 */
internal object SarvamVoiceReadiness {
    data class Result(val ready: Boolean, val code: String, val explanation: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun check(context: Context, respond: (Result) -> Unit) {
        val cfg = try {
            AgentJournalModule.openVoiceCredentials(context)
        } catch (_: Exception) { null }
        if (cfg == null || cfg.provider != "sarvam") {
            respond(Result(false, "MISSING_CONFIG",
                "Save a new Sarvam API key and the exact Organization, Workspace and published Agent App IDs in Nexa Settings."))
            return
        }
        val url = Uri.parse("https://apps.sarvam.ai/api/app-runtime/")
            .buildUpon()
            .appendEncodedPath("orgs/${cfg.orgId}/workspaces/${cfg.workspaceId}/apps/${cfg.appId}/url")
            .appendQueryParameter("interaction_type", "call")
            .build().toString()
        val request = Request.Builder().url(url)
            .header("X-API-Key", cfg.apiKey).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                respond(Result(false, "NETWORK",
                    "Could not reach Sarvam's authorization endpoint. Check network access."))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) {
                            val (code, explanation) = when (it.code) {
                                401, 403 -> "AUTH" to "Sarvam rejected this key or workspace access. Rotate and re-enter the key."
                                404 -> "AGENT_NOT_FOUND" to "Agent ID, workspace, organization or committed version not found. Copy the full Agent App ID and publish the agent."
                                422, 400 -> "INVALID_AGENT" to "Sarvam did not accept the Voice Agent configuration or call parameters."
                                429 -> "RATE_LIMIT" to "Sarvam is rate-limiting Voice Agent authorization."
                                else -> "PROVIDER_ERROR" to "Sarvam could not authorize the voice session (HTTP ${it.code})."
                            }
                            respond(Result(false, code, explanation))
                            return
                        }
                        val body = it.body?.string().orEmpty()
                        if (body.length !in 2..8192) {
                            respond(Result(false, "MALFORMED", "Sarvam returned an invalid signed-session response."))
                            return
                        }
                        val json = JSONObject(body)
                        val referenceId = json.optString("reference_id")
                        val signed = json.optString("url")
                        val uri = Uri.parse(signed)
                        val host = uri.host.orEmpty().lowercase()
                        val trusted = uri.scheme == "wss" &&
                            (host == "sarvam.ai" || host.endsWith(".sarvam.ai")) &&
                            (uri.port == -1 || uri.port == 443) &&
                            uri.encodedAuthority?.contains("@") != true
                        if (!trusted || referenceId.isBlank() || referenceId.length > 128) {
                            respond(Result(false, "BAD_SIGNED_URL",
                                "Sarvam did not return a valid trusted signed WebSocket connection."))
                            return
                        }
                        respond(Result(true, "AUTHORIZED",
                            "Sarvam issued a signed Voice Agent WebSocket URL. This verifies authorization, not microphone/audio playback."))
                    }
                } catch (_: Exception) {
                    respond(Result(false, "MALFORMED", "Sarvam Voice Agent authorization response could not be processed."))
                }
            }
        })
    }
}
