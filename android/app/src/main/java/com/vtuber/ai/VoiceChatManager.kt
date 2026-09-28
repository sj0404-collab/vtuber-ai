package com.vtuber.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat

object VoiceChatManager {

    interface Listener {
        fun onMood(mood: String)
        fun onCaption(text: String)
        fun onLevel(level: Float)
        fun onHeard(text: String)
        fun onError(message: String)
    }

    private val TAG = "VoiceChat"
    private var recognizer: SpeechRecognizer? = null
    private var speech: SpeechPlayer? = null
    private val chat = ChatClient()

    @Volatile
    private var listening = false

    @Volatile
    private var speaking = false

    var listener: Listener? = null

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun isListening(): Boolean = listening

    fun isSpeaking(): Boolean = speaking

    fun init(context: Context) {
        if (speech == null) {
            speech = SpeechPlayer(context.applicationContext).apply {
                init(onFailure = { message -> listener?.onError(message) })
            }
        }
    }

    fun toggle(context: Context) {
        if (listening || speaking) {
            stop()
        } else {
            start(context)
        }
    }

    fun start(context: Context) {
        if (listening || speaking) return
        if (!hasPermission(context)) {
            listener?.onError("Нет доступа к микрофону")
            return
        }
        val app = context.applicationContext
        init(app)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
        }
        if (speech == null) return
        stopSpeech()
        val instance = recognizer ?: SpeechRecognizer.createSpeechRecognizer(app).also {
            recognizer = it
        }
        instance.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() {
                listener?.onMood("listening")
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                listener?.onMood("thinking")
            }

            override fun onError(error: Int) {
                listening = false
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    listener?.onMood("idle")
                } else {
                    listener?.onError(errorText(error))
                    listener?.onMood("idle")
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isBlank()) {
                    listener?.onMood("idle")
                } else {
                    ask(app, text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { listener?.onCaption(it) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        listening = true
        instance.startListening(intent)
    }

    private fun ask(context: Context, text: String) {
        val prefs = Prefs(context)
        listener?.onMood("thinking")
        listener?.onCaption("")
        listener?.onHeard(text)
        chat.send(prefs, text, object : ChatClient.Listener {
            override fun onReply(reply: String) {
                prefs.addTurn("user", text)
                prefs.addTurn("assistant", reply)
                speak(reply)
            }

            override fun onFailure(message: String) {
                Log.e(TAG, "model failure: $message")
                listener?.onError("Модель молчит: $message")
                listener?.onCaption("Не получилось получить ответ")
                listener?.onMood("idle")
            }
        })
    }

    private fun speak(reply: String) {
        val player = speech ?: return
        speaking = true
        player.speak(reply, object : SpeechPlayer.Listener {
            override fun onSpeechStart() {
                listener?.onMood("speaking")
                listener?.onCaption(reply)
            }

            override fun onLevel(level: Float) {
                listener?.onLevel(level)
            }

            override fun onSpeechDone() {
                speaking = false
                listener?.onLevel(0f)
                listener?.onCaption("")
                listener?.onMood("idle")
            }
        })
    }

    fun stop() {
        listening = false
        runCatching { recognizer?.cancel() }
        stopSpeech()
        listener?.onLevel(0f)
        listener?.onMood("idle")
    }

    private fun stopSpeech() {
        speaking = false
        speech?.stop()
    }

    fun destroy() {
        stop()
        runCatching { recognizer?.destroy() }
        recognizer = null
        speech?.shutdown()
        speech = null
    }

    private fun errorText(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи звука"
        SpeechRecognizer.ERROR_CLIENT -> "Ошибка распознавания"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону"
        SpeechRecognizer.ERROR_NETWORK -> "Нет сети для распознавания"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Сеть не отвечает"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
        SpeechRecognizer.ERROR_SERVER -> "Сервер распознавания недоступен"
        else -> "Распознавание не удалось"
    }
}
