package dev.rilandev.personalaiassistant

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioManager
import android.media.MediaRecorder
import android.content.pm.PackageManager
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Base64

/** Explicit, single-turn Sarvam Saaras STT and Bulbul TTS.
 * Entire PCM/WAV and text are memory-only. No raw audio files, logs,
 * background capture, fallback to unrelated providers, or device tools.
 * Live full-duplex calls use SarvamAudioTransport instead.
 */
class SarvamSpeechModule(private val context:ReactApplicationContext):
    ReactContextBaseJavaModule(context){
    override fun getName()="PAASarvamSpeech"
    private val client=OkHttpClient.Builder()
        .connectTimeout(12,java.util.concurrent.TimeUnit.SECONDS)
        // Never forward a private subscription key to a redirected host.
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(35,java.util.concurrent.TimeUnit.SECONDS).build()
    private val capturing=AtomicBoolean(false)
    private val playing=AtomicBoolean(false)
    private val cancelCapture=AtomicBoolean(false)
    private val cancelPlayback=AtomicBoolean(false)
    @Volatile private var sttCall:Call?=null
    @Volatile private var ttsCall:Call?=null
    @Volatile private var recorder:AudioRecord?=null
    @Volatile private var output:AudioTrack?=null

    private fun key():String? =
        try {AgentJournalModule.openSarvamApiKey(context)}catch(_:Exception){null}

    private fun writeInt(w:ByteArrayOutputStream,i:Int){
        for(n in 0..3)w.write((i ushr (8*n)) and 255)
    }
    private fun writeShort(w:ByteArrayOutputStream,i:Int){
        w.write(i and 255);w.write((i ushr 8) and 255)
    }
    internal fun wav(pcm:ByteArray):ByteArray {
        val w=ByteArrayOutputStream(pcm.size+44)
        w.write("RIFF".toByteArray(Charsets.US_ASCII));writeInt(w,pcm.size+36)
        w.write("WAVEfmt ".toByteArray(Charsets.US_ASCII));writeInt(w,16)
        writeShort(w,1);writeShort(w,1);writeInt(w,16000)
        writeInt(w,32000);writeShort(w,2);writeShort(w,16)
        w.write("data".toByteArray(Charsets.US_ASCII));writeInt(w,pcm.size)
        w.write(pcm);return w.toByteArray()
    }
    private fun usableForeground():Boolean=
        context.currentActivity?.hasWindowFocus()==true

    @ReactMethod fun listenOnce(p:Promise){
        if(!usableForeground()){p.reject("FOREGROUND_REQUIRED","Open Voice screen before recording");return}
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            p.reject("MIC_REQUIRED","Microphone permission not granted");return
        }
        if(RealtimeVoiceService.status=="listening" || RealtimeVoiceService.status=="connecting"){
            p.reject("LIVE_VOICE_ACTIVE","Stop live voice before dictation");return
        }
        val secret=key()
        if(secret==null){p.reject("SARVAM_KEY_REQUIRED","Select Sarvam AI and save a key");return}
        if(playing.get()) {
            p.reject("PLAYBACK_ACTIVE","Stop speech playback before dictation");return
        }
        if(!capturing.compareAndSet(false,true)){
            p.reject("RECORDING_ACTIVE","Already capturing");return
        }
        cancelCapture.set(false)
        Thread({
            var record:AudioRecord?=null
            try {
                if(cancelCapture.get())throw InterruptedException()
                val bufferSize=maxOf(AudioRecord.getMinBufferSize(16000,
                    AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT),2048)
                record=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,
                    AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,bufferSize*2)
                if(record.state!=AudioRecord.STATE_INITIALIZED)throw IllegalStateException()
                recorder=record
                record.startRecording()
                if(record.recordingState!=AudioRecord.RECORDSTATE_RECORDING)
                    throw IllegalStateException()
                val pcm=ByteArrayOutputStream()
                val data=ByteArray(1280)
                val start=android.os.SystemClock.elapsedRealtime()
                var voiced=false
                var lastVoice=start
                while(capturing.get() && !cancelCapture.get() &&
                    android.os.SystemClock.elapsedRealtime()-start<9500){
                    val got=record.read(data,0,data.size)
                    if(got<=0)break
                    pcm.write(data,0,got)
                    var peak=0
                    var i=0
                    while(i+1<got){
                        val v=(data[i].toInt() and 255) or (data[i+1].toInt() shl 8)
                        val abs=kotlin.math.abs(v.toShort().toInt())
                        if(abs>peak)peak=abs
                        i+=2
                    }
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(peak>650){voiced=true;lastVoice=now}
                    if(voiced && now-lastVoice>1100 && now-start>900)break
                }
                record.stop();record.release();record=null;recorder=null
                if(cancelCapture.get()) {p.reject("CANCELLED","Voice dictation cancelled");return@Thread}
                val bytes=pcm.toByteArray()
                if(bytes.size<1600){p.reject("EMPTY_AUDIO","No speech detected");return@Thread}
                val body=MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("model","saaras:v4")
                    .addFormDataPart("file","voice.wav",wav(bytes)
                        .toRequestBody("audio/wav".toMediaType())).build()
                val req=Request.Builder().url("https://api.sarvam.ai/speech-to-text")
                    .header("api-subscription-key",secret).post(body).build()
                if(cancelCapture.get()) {p.reject("CANCELLED","Voice dictation cancelled");return@Thread}
                val call=client.newCall(req)
                sttCall=call
                if(cancelCapture.get()){
                    call.cancel();p.reject("CANCELLED","Voice dictation cancelled");return@Thread
                }
                call.execute().use { response ->
                    if(cancelCapture.get()){p.reject("CANCELLED","Voice dictation cancelled");return@Thread}
                    if(!response.isSuccessful){
                        p.reject("SARVAM_STT_HTTP","Sarvam speech recognition rejected request")
                        return@Thread
                    }
                    val raw=response.body?.string().orEmpty()
                    if(raw.length>12000)throw IllegalStateException()
                    val transcript=JSONObject(raw).optString("transcript").trim()
                    if(transcript.isBlank() || transcript.length>2000){
                        p.reject("EMPTY_TRANSCRIPT","No usable speech recognized")
                    }else p.resolve(transcript)
                }
            }catch(_:Exception){
                if(cancelCapture.get())p.reject("CANCELLED","Voice dictation cancelled")
                else p.reject("SARVAM_STT_FAILED","Sarvam recognition unavailable")
            }finally{
                sttCall=null
                capturing.set(false)
                try{record?.stop();record?.release()}catch(_:Exception){}
                recorder=null
            }
        },"sarvam-one-turn-stt").start()
    }

    @ReactMethod fun cancelListening(p:Promise){
        cancelCapture.set(true)
        sttCall?.cancel()
        try{recorder?.stop()}catch(_:Exception){}
        // Keep capturing=true until the worker's finally block, so a new
        // session cannot race an old network callback into the new editor.
        p.resolve(true)
    }

    internal fun pcmFromWav(bytes:ByteArray):ByteArray? {
        if(bytes.size<44 || String(bytes,0,4,Charsets.US_ASCII)!="RIFF" ||
            String(bytes,8,4,Charsets.US_ASCII)!="WAVE")return null
        val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var offset=12
        var formatValid=false
        while(offset+8<=bytes.size){
            val id=String(bytes,offset,4,Charsets.US_ASCII)
            val size=buffer.getInt(offset+4)
            if(size<0 || size>bytes.size-offset-8)return null
            if(id=="fmt " && size>=16) {
                formatValid=buffer.getShort(offset+8).toInt()==1 &&
                    buffer.getShort(offset+10).toInt()==1 &&
                    buffer.getInt(offset+12)==16000 &&
                    buffer.getShort(offset+22).toInt()==16
            }
            if(id=="data" && formatValid && size>0 && size<=2_000_000)
                return bytes.copyOfRange(offset+8,offset+8+size)
            offset+=8+size+(size and 1)
        }
        return null
    }

    @ReactMethod fun speak(text:String,language:String,p:Promise){
        if(!usableForeground()){p.reject("FOREGROUND_REQUIRED","Open app for TTS");return}
        if(text.isBlank() || text.length>1000){p.reject("INVALID_TEXT","Text limit 1000");return}
        if(language !in listOf("en-IN","hi-IN","ta-IN","te-IN","kn-IN","bn-IN","gu-IN","mr-IN","pa-IN")){
            p.reject("INVALID_LANGUAGE","Select a supported Bulbul language");return
        }
        if(RealtimeVoiceService.status=="listening" ||
            RealtimeVoiceService.status=="connecting"){
            p.reject("LIVE_VOICE_ACTIVE","Stop live voice before TTS");return
        }
        val secret=key()
        if(secret==null){p.reject("SARVAM_KEY_REQUIRED","Select Sarvam AI and save a key");return}
        if(capturing.get()){
            p.reject("RECORDING_ACTIVE","Stop voice dictation before speech playback");return
        }
        if(!playing.compareAndSet(false,true)){
            p.reject("PLAYBACK_ACTIVE","Already speaking");return
        }
        cancelPlayback.set(false)
        Thread({
            var track:AudioTrack?=null
            try {
                val body=JSONObject().put("text",text)
                    .put("model","bulbul:v4-flash")
                    .put("language_code",language)
                    .put("speech_sample_rate",16000)
                    .put("output_audio_codec","wav").toString()
                val request=Request.Builder().url("https://api.sarvam.ai/text-to-speech")
                    .header("api-subscription-key",secret)
                    .post(body.toRequestBody("application/json".toMediaType())).build()
                if(cancelPlayback.get()){p.resolve(false);return@Thread}
                val call=client.newCall(request)
                ttsCall=call
                if(cancelPlayback.get()){
                    call.cancel();p.resolve(false);return@Thread
                }
                val audio=call.execute().use { response->
                    if(!response.isSuccessful)throw IllegalStateException()
                    val raw=response.body?.string().orEmpty()
                    if(raw.length>3_500_000)throw IllegalStateException()
                    val base64=JSONObject(raw).getJSONArray("audios").getString(0)
                    if(base64.length>2_800_000)throw IllegalStateException()
                    val decoded=Base64.decode(base64,Base64.DEFAULT)
                    pcmFromWav(decoded) ?: throw IllegalStateException()
                }
                if(cancelPlayback.get()){p.resolve(false);return@Thread}
                val min=AudioTrack.getMinBufferSize(16000,AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT)
                track=AudioTrack(AudioManager.STREAM_MUSIC,16000,
                    AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(min,4096),AudioTrack.MODE_STREAM)
                if(track.state!=AudioTrack.STATE_INITIALIZED)throw IllegalStateException()
                output=track
                track.play()
                var offset=0
                while(!cancelPlayback.get() && offset<audio.size){
                    val size=minOf(3200,audio.size-offset)
                    val n=track.write(audio,offset,size)
                    if(n<=0)break
                    offset+=n
                }
                // write() finishing only means AudioTrack queued PCM. Do not
                // release or report success until actual playback head drains.
                val deadline=android.os.SystemClock.elapsedRealtime()+3500L
                val totalFrames=audio.size/2
                while(!cancelPlayback.get() && offset==audio.size &&
                    track.playbackHeadPosition.toLong()<totalFrames &&
                    android.os.SystemClock.elapsedRealtime()<deadline) {
                    Thread.sleep(35)
                }
                p.resolve(offset==audio.size && !cancelPlayback.get() &&
                    track.playbackHeadPosition.toLong()>=totalFrames)
            }catch(_:Exception){
                if(cancelPlayback.get())p.resolve(false)
                else p.reject("SARVAM_TTS_FAILED","Sarvam voice playback unavailable")
            }
            finally{
                ttsCall=null
                playing.set(false)
                try{track?.stop();track?.release()}catch(_:Exception){}
                output=null
            }
        },"sarvam-one-turn-tts").start()
    }
    @ReactMethod fun stopSpeaking(p:Promise){
        cancelPlayback.set(true)
        ttsCall?.cancel()
        try{output?.pause();output?.flush()}catch(_:Exception){}
        // Keep playing=true until worker cleanup, avoiding overlapping audio
        // sessions and late audio playback after explicit cancellation.
        p.resolve(true)
    }
}
