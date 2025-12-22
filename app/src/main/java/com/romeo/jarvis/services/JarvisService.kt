package com.romeo.jarvis.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.os.Bundle
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.romeo.jarvis.R
import com.romeo.jarvis.utils.RetrofitClient
import com.romeo.jarvis.utils.ChatRequest
import com.romeo.jarvis.utils.ChatResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class JarvisService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech

    private var isListening = false
    private var isAwake = false   // 🔥 WAKE WORD STATE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        setupSpeechRecognizer()
    }

    // ================= FOREGROUND =================

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val channelId = "JarvisServiceChannel"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "Jarvis AI",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis Active")
            .setContentText("Always listening for wake word")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .build()

        startForeground(1, notification)

        startListening()
        return START_STICKY
    }

    // ================= SPEECH =================

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
    }

    private fun startListening() {
        if (isListening) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            // Roman Urdu + English
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            speechRecognizer.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            isListening = false
        }
    }

    // ================= RESULTS =================

    override fun onResults(results: Bundle?) {
        isListening = false

        val matches =
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

        if (matches.isNullOrEmpty()) {
            startListening()
            return
        }

        val heard = matches[0].lowercase(Locale.getDefault())
        Log.d("Jarvis", "Heard: $heard")

        // ================= WAKE WORD =================
        if (!isAwake) {
            if (heard.contains("jarvis")) {
                isAwake = true
                speak("Yes sir")
            }
            startListening()
            return
        }

        // ================= OFFLINE COMMANDS =================
        when {
            heard.contains("call") -> {
                speak("Calling now")
                // NEXT STEP: contact intent
                isAwake = false
            }

            heard.contains("open") -> {
                speak("Opening application")
                // NEXT STEP: accessibility open app
                isAwake = false
            }

            heard.contains("read screen") -> {
                speak("Reading screen")
                // NEXT STEP: accessibility read
                isAwake = false
            }

            heard.contains("stop listening") -> {
                speak("Going idle")
                isAwake = false
            }

            else -> {
                // ================= ONLINE AI =================
                askBackend(heard)
            }
        }
    }

    // ================= BACKEND =================

    private fun askBackend(query: String) {
        RetrofitClient.instance
            .chatWithAI(ChatRequest(query))
            .enqueue(object : Callback<ChatResponse> {

                override fun onResponse(
                    call: Call<ChatResponse>,
                    response: Response<ChatResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        speak(response.body()!!.reply)
                    } else {
                        speak("I could not process that.")
                    }
                    isAwake = false
                    startListening()
                }

                override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                    speak("Offline mode. Command not supported.")
                    isAwake = false
                    startListening()
                }
            })
    }

    // ================= TTS =================

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_tts")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setPitch(0.7f)        // 🔥 DEEPER VOICE
            tts.setSpeechRate(0.85f)  // 🔥 SLOWER
        }
    }

    // ================= ERRORS =================

    override fun onError(error: Int) {
        isListening = false
        startListening()
    }

    // ================= BOILERPLATE =================
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
    override fun onDestroy() {
        speechRecognizer.destroy()
        tts.shutdown()
        super.onDestroy()
    }
}