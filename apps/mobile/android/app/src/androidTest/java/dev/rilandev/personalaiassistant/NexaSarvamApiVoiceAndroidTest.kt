package dev.rilandev.personalaiassistant

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Native instrumentation for BYOK realtime model-protocol parameters.
 * Runs completely offline; no key, audio, LLM request or microphone starts.
 */
@RunWith(AndroidJUnit4::class)
class NexaSarvamApiVoiceAndroidTest {
    @Test fun saarasV3RealtimeUsesCorrectPersistentWebSocketProtocol() {
        val uri=Uri.parse(SarvamApiVoiceTransport.sttUrl())
        assertEquals("https",uri.scheme)
        assertEquals("api.sarvam.ai",uri.host)
        assertEquals("/speech-to-text-realtime/ws",uri.path)
        assertEquals("saaras:v3-realtime",uri.getQueryParameter("model"))
        assertEquals("auto",uri.getQueryParameter("language_code"))
        assertEquals("fast",uri.getQueryParameter("stream_type"))
        assertEquals("linear16",uri.getQueryParameter("encoding"))
        assertEquals("16000",uri.getQueryParameter("sample_rate"))
        assertEquals("vad",uri.getQueryParameter("endpointing"))
        assertEquals(16000,SarvamApiVoiceTransport.RATE)
    }

    @Test fun bulbulStreamsLinearPcmWithoutSeparateRecordings() {
        val uri=Uri.parse(SarvamApiVoiceTransport.ttsUrl())
        assertEquals("wss",uri.scheme)
        assertEquals("api.sarvam.ai",uri.host)
        assertEquals("/text-to-speech/ws",uri.path)
        assertEquals("bulbul:v4-flash",uri.getQueryParameter("model"))
        val message=SarvamApiVoiceTransport.ttsConfig()
        assertEquals("config",message.getString("type"))
        val cfg=message.getJSONObject("data")
        assertEquals("linear16",cfg.getString("output_audio_codec"))
        assertEquals(16000,cfg.getInt("speech_sample_rate"))
        assertEquals("sarvam-105b-conversations",SarvamApiVoiceTransport.CHAT_MODEL)
    }
}
