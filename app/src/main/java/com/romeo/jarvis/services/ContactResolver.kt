package com.romeo.jarvis.services

import android.app.*
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.*
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.romeo.jarvis.R
import com.romeo.jarvis.utils.SystemController
import java.util.*

class JarvisService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech

    private var isListening = false
    private var isAwake = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "jarvis_channel"

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Jarvis", NotificationManager.IMPORTANCE_LOW)
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis Active")
            .setContentText("Listening for wake word")
            .setSmallIcon(R.drawable.app_icon)
            .build()

        startForeground(1, notification)
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        if (isListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)

        try {
            speechRecognizer.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            isListening = false
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (list.isNullOrEmpty()) {
            startListening()
            return
        }

        val heard = list[0].lowercase(Locale.getDefault())
        Log.d("Jarvis", "Heard: $heard")

        // ================= WAKE WORD =================
        if (!isAwake) {
            if (heard.contains("jarvis")) {
                isAwake = true
                speak("Haan boliye")
            }
            startListening()
            return
        }

        // ================= CALL =================
        if (heard.startsWith("call")) {
            val nameOrNumber = heard.replace("call", "").replace("kar do", "").trim()

            if (heard.contains("whatsapp")) {
                speak("$nameOrNumber ko WhatsApp par call kar raha hoon")
                SystemController.callWhatsApp(this, nameOrNumber)
            } else {
                speak("$nameOrNumber ko call kar raha hoon")
                SystemController.callNumber(this, nameOrNumber)
            }
            isAwake = false
        }

        // ================= MUSIC =================
        else if (heard.contains("music")) {
            when {
                heard.contains("play") || heard.contains("chalao") ->
                    SystemController.playMusic(this)
                heard.contains("pause") || heard.contains("roko") ->
                    SystemController.musicControl(this, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
                heard.contains("next") || heard.contains("agla") ->
                    SystemController.musicControl(this, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                heard.contains("previous") || heard.contains("pichla") ->
                    SystemController.musicControl(this, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            speak("Theek hai")
            isAwake = false
        }

        // ================= TERMUX =================
        else if (heard.startsWith("run")) {
            val cmd = heard.replace("run", "").trim()
            SystemController.runTermuxCommand(this, cmd)
            speak("Command execute kar raha hoon")
            isAwake = false
        }

        // ================= STOP =================
        else if (heard.contains("band")) {
            speak("Main chup ho gaya")
            isAwake = false
        }

        startListening()
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("ur", "PK")
            tts.setPitch(0.7f)
            tts.setSpeechRate(0.85f)
        }
    }

    override fun onError(error: Int) {
        isListening = false
        startListening()
    }

    override fun onDestroy() {
        speechRecognizer.destroy()
        tts.shutdown()
        super.onDestroy()
    }

    // Boilerplate
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}