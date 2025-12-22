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
import com.romeo.jarvis.utils.ContactResolver
import com.romeo.jarvis.utils.SystemController
import java.util.*

class JarvisService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    private lateinit var recognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech

    private var isListening = false
    private var isAwake = false

    override fun onBind(intent: Intent?): IBinder? = null

    // -------------------- LIFECYCLE --------------------

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotif()
        startListening()
        showOrbIdle()
        return START_STICKY
    }

    private fun startForegroundNotif() {
        val channelId = "jarvis_channel"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Jarvis", NotificationManager.IMPORTANCE_LOW)
        )

        val n = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle("Jarvis Active")
            .setContentText("Listening for wake word")
            .build()

        startForeground(1, n)
    }

    // -------------------- LISTENING --------------------

    private fun startListening() {
        if (isListening) return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Roman Urdu + English works best like this
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try {
            recognizer.startListening(i)
            isListening = true
        } catch (_: Exception) {
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

        // ---------- WAKE WORD ----------
        if (!isAwake) {
            if (heard.contains("jarvis")) {
                isAwake = true
                speak("Haan boliye")
                showOrbListening()
            }
            startListening()
            return
        }

        // ---------- CALL (NORMAL / WHATSAPP) ----------
        if (heard.startsWith("call")) {
            val name = heard
                .replace("jarvis", "")
                .replace("call", "")
                .replace("on whatsapp", "")
                .replace("whatsapp par", "")
                .trim()

            val number = ContactResolver.resolveNumber(this, name)
            if (number != null) {
                if (heard.contains("whatsapp")) {
                    speak("$name ko WhatsApp par call kar raha hoon")
                    SystemController.callWhatsApp(this, number)
                } else {
                    speak("$name ko call kar raha hoon")
                    SystemController.callNumber(this, number)
                }
            } else {
                speak("$name ka number nahi mila")
            }
            resetAfterAction()
            return
        }

        // ---------- MUSIC ----------
        if (heard.contains("music") || heard.contains("gana")) {
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
            resetAfterAction()
            return
        }

        // ---------- ACCESSIBILITY ----------
        when {
            heard.contains("back") -> {
                JarvisAccessibilityHolder.service?.back()
                speak("Wapas")
                resetAfterAction(); return
            }
            heard.contains("home") -> {
                JarvisAccessibilityHolder.service?.home()
                speak("Home")
                resetAfterAction(); return
            }
            heard.contains("scroll down") -> {
                JarvisAccessibilityHolder.service?.scrollDown()
                speak("Neeche")
                resetAfterAction(); return
            }
            heard.contains("read screen") -> {
                val text = JarvisAccessibilityHolder.service?.readScreen().orEmpty()
                speak(if (text.isNotBlank()) text else "Screen khali hai")
                resetAfterAction(); return
            }
        }

        // ---------- TERMUX ----------
        if (heard.startsWith("run")) {
            val cmd = heard.replace("run", "").trim()
            if (cmd.isNotEmpty()) {
                SystemController.runTermuxCommand(this, cmd)
                speak("Command chala raha hoon")
            } else {
                speak("Command batao")
            }
            resetAfterAction()
            return
        }

        // ---------- STOP ----------
        if (heard.contains("band") || heard.contains("stop listening")) {
            speak("Theek hai")
            resetAfterAction()
            return
        }

        // ---------- FALLBACK ----------
        speak("Samajh nahi aaya, dobara bolo")
        resetAfterAction()
    }

    private fun resetAfterAction() {
        isAwake = false
        showOrbIdle()
        startListening()
    }

    // -------------------- TTS --------------------

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("ur", "PK")
            tts.setPitch(0.7f)       // deeper
            tts.setSpeechRate(0.85f) // slower
        }
    }

    // -------------------- ORB HOOKS --------------------

    private fun showOrbIdle() {
        startService(Intent(this, OrbOverlayService::class.java).apply {
            putExtra("state", "idle")
        })
    }

    private fun showOrbListening() {
        startService(Intent(this, OrbOverlayService::class.java).apply {
            putExtra("state", "listening")
        })
    }

    // -------------------- ERRORS / CLEANUP --------------------

    override fun onError(error: Int) {
        isListening = false
        startListening()
    }

    override fun onDestroy() {
        recognizer.destroy()
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