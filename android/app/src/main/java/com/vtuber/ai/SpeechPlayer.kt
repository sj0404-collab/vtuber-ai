package com.vtuber.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.concurrent.thread

class SpeechPlayer(private val context: Context) {

    interface Listener {
        fun onSpeechStart()
        fun onLevel(level: Float)
        fun onSpeechDone()
    }

    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var voiceReady = false
    private var player: MediaPlayer? = null
    private var levelTask: Runnable? = null
    private var watchdog: Runnable? = null
    private var pendingListener: Listener? = null
    private var currentText: String = ""

    @Volatile
    private var envelope = FloatArray(0)

    @Volatile
    private var synthesizing = false

    fun init(onReady: () -> Unit = {}, onFailure: (String) -> Unit) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            voiceReady = status == TextToSpeech.SUCCESS
            if (!voiceReady) {
                onFailure("Синтез речи недоступен")
                return@TextToSpeech
            }
            onReady()
            val engine = tts ?: return@TextToSpeech
            engine.language = Locale("ru", "RU")
            engine.setSpeechRate(1.02f)
            engine.setPitch(1.08f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId == FILE_ID) synthesizing = true
                }

                override fun onDone(utteranceId: String?) {
                    when (utteranceId) {
                        FILE_ID -> handler.post { playFile() }
                        else -> if (utteranceId?.startsWith(DIRECT_PREFIX) == true) finish()
                    }
                }

                @Deprecated("Required by the platform interface")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == FILE_ID) handler.post { speakDirectly() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == FILE_ID) handler.post { speakDirectly() }
                }
            })
        }
    }

    fun speak(text: String, listener: Listener) {
        val engine = tts
        if (text.isBlank() || engine == null || !voiceReady) return
        stopPlayback()
        cancelWatchdog()
        tts?.stop()
        currentText = text
        pendingListener = listener
        synthesizing = false
        envelope = FloatArray(0)
        listener.onSpeechStart()

        val file = File(context.cacheDir, "reply.wav")
        file.delete()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, FILE_ID)
        }
        val queued = try {
            engine.synthesizeToFile(text, params, file, FILE_ID)
        } catch (e: Exception) {
            Log.e(TAG, "synthesizeToFile failed", e)
            TextToSpeech.ERROR
        }
        if (queued != TextToSpeech.SUCCESS) {
            speakDirectly()
            return
        }
        watchdog = Runnable {
            if (!synthesizing) {
                Log.w(TAG, "synthesis is too slow, speaking directly")
                speakDirectly()
            }
        }.also { handler.postDelayed(it, WATCHDOG_MS) }
    }

    private fun speakDirectly() {
        cancelWatchdog()
        val engine = tts ?: return
        val utteranceId = DIRECT_PREFIX + System.currentTimeMillis()
        try {
            engine.speak(currentText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "speak failed", e)
            finish()
        }
    }

    private fun playFile() {
        cancelWatchdog()
        val file = File(context.cacheDir, "reply.wav")
        if (!file.exists() || file.length() < 64) {
            speakDirectly()
            return
        }
        thread(name = "tts-envelope") {
            envelope = analyze(file)
        }
        try {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener { finish() }
                setOnErrorListener { _, _, _ ->
                    finish()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "playback failed", e)
            speakDirectly()
            return
        }
        val task = object : Runnable {
            override fun run() {
                val current = player ?: return
                if (!current.isPlaying) return
                val position = current.currentPosition
                val levels = envelope
                val level = if (levels.isEmpty()) {
                    0.35f
                } else {
                    levels[(position / FRAME_MS).toInt().coerceIn(0, levels.size - 1)]
                }
                pendingListener?.onLevel(level)
                handler.postDelayed(this, FRAME_MS)
            }
        }
        levelTask = task
        handler.postDelayed(task, FRAME_MS)
    }

    private fun analyze(file: File): FloatArray {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(12)
                raf.readFully(header)
                var rate = 22050
                var channels = 1
                var bits = 16
                var dataSize = 0L
                while (raf.filePointer + 8 <= raf.length()) {
                    val chunk = ByteArray(8)
                    raf.readFully(chunk)
                    val id = String(chunk, 0, 4, Charsets.US_ASCII)
                    val size = (chunk[4].toLong() and 0xFF) or
                        ((chunk[5].toLong() and 0xFF) shl 8) or
                        ((chunk[6].toLong() and 0xFF) shl 16) or
                        ((chunk[7].toLong() and 0xFF) shl 24)
                    if (id == "fmt ") {
                        val fmt = ByteArray(16)
                        raf.readFully(fmt, 0, min(fmt.size, size.toInt().coerceAtLeast(1)))
                        channels = ((fmt[2].toInt() and 0xFF) or (fmt[3].toInt() shl 8)).coerceAtLeast(1)
                        rate = ((fmt[4].toInt() and 0xFF) or (fmt[5].toInt() shl 8) or
                            (fmt[6].toInt() shl 16) or (fmt[7].toInt() shl 24)).coerceIn(8000, 48000)
                        bits = ((fmt[14].toInt() and 0xFF) or (fmt[15].toInt() shl 8)).coerceAtLeast(8)
                    } else if (id == "data") {
                        dataSize = size
                        break
                    } else {
                        raf.seek(raf.filePointer + size + (size and 1L))
                    }
                }
                if (dataSize <= 0) {
                    return@use FloatArray(0)
                }
                val bytesPerSample = (bits / 8).coerceAtLeast(1)
                val bytesPerFrame = (rate / 1000 * FRAME_MS * channels * bytesPerSample).toInt().coerceAtLeast(2)
                val frameCount = (dataSize / bytesPerFrame).toInt().coerceAtLeast(1)
                val levels = FloatArray(frameCount)
                val buffer = ByteArray(min(bytesPerFrame, 1 shl 16))
                var peak = 0f
                for (index in 0 until frameCount) {
                    val read = raf.read(buffer, 0, min(buffer.size, bytesPerFrame))
                    if (read <= 1) break
                    var sum = 0.0
                    var samples = 0
                    var offset = 0
                    while (offset + 1 < read) {
                        val sample = ((buffer[offset + 1].toInt() shl 8) or
                            (buffer[offset].toInt() and 0xFF)) / 32768.0
                        sum += sample * sample
                        samples++
                        offset += 2
                    }
                    val rms = if (samples == 0) 0f else sqrt(sum / samples).toFloat()
                    levels[index] = rms
                    if (rms > peak) peak = rms
                }
                if (peak > 0.001f) {
                    for (index in levels.indices) {
                        levels[index] = (0.15f + 0.85f * (levels[index] / peak)).coerceIn(0f, 1f)
                    }
                }
                levels
            }
        } catch (e: Exception) {
            Log.e(TAG, "envelope analysis failed", e)
            FloatArray(0)
        }
    }

    private fun stopPlayback() {
        levelTask?.let { handler.removeCallbacks(it) }
        levelTask = null
        player?.let { media ->
            runCatching { media.stop() }
            media.release()
        }
        player = null
    }

    fun stop() {
        cancelWatchdog()
        stopPlayback()
        runCatching { tts?.stop() }
        envelope = FloatArray(0)
        val listener = pendingListener
        pendingListener = null
        listener?.onLevel(0f)
        listener?.onSpeechDone()
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        voiceReady = false
    }

    private fun finish() {
        cancelWatchdog()
        stopPlayback()
        envelope = FloatArray(0)
        val listener = pendingListener
        pendingListener = null
        listener?.onLevel(0f)
        listener?.onSpeechDone()
    }

    private fun cancelWatchdog() {
        watchdog?.let { handler.removeCallbacks(it) }
        watchdog = null
    }

    companion object {
        private const val TAG = "SpeechPlayer"
        private const val FILE_ID = "vtuber-file"
        private const val DIRECT_PREFIX = "vtuber-direct-"
        private const val FRAME_MS = 45L
        private const val WATCHDOG_MS = 3500L
    }
}
