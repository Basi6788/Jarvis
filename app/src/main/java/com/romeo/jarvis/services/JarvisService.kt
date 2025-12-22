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
import com.romeo.jarvis.utils.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class JarvisService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    private lateinit var recognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech

    private var isListening = false
    private var isAwake = false

    override fun onBind(intent: Intent?): IBinder? = null

    // ================= LIFECYCLE =================

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        // Speech Recognizer setup
        setupRecognizer()
    }

    private fun setupRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            recognizer.setRecognitionListener(this)
        } else {
            Log.e("Jarvis", "Speech Recognition not available")
        }
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
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Jarvis Service", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val n = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.app_icon) // Make sure ye icon drawables me ho
            .setContentTitle("Jarvis Active")
            .setContentText("Listening for wake word...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, n)
    }

    // ================= LISTENING =================

    private fun startListening() {
        if (isListening) return
        
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK") // Urdu Pakistan
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            recognizer.startListening(i)
            isListening = true
        } catch (e: Exception) {
            isListening = false
            Log.e("Jarvis", "Mic Error: ${e.message}")
            // Agar crash ho to thora ruk kar restart karo
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                setupRecognizer()
                startListening()
            }, 2000)
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

        // ---------- 1. WAKE WORD (Jagne ka process) ----------
        if (!isAwake) {
            if (heard.contains("jarvis") || heard.contains("hello")) {
                isAwake = true
                speak("Jee boss, hukum karein")
                showOrbListening()
            }
            startListening() // Wapis sunne lag jao
            return
        }

        // ---------- 2. HARDCODED COMMANDS (Local Actions) ----------
        
        // Call Logic
        if (heard.startsWith("call")) {
            val name = heard.replace("jarvis", "").replace("call", "").trim()
            val number = ContactResolver.resolveNumber(this, name)
            
            if (number != null) {
                speak("$name ko call mila raha hun")
                SystemController.callNumber(this, number)
            } else {
                speak("Contact list mein $name nahi mila")
            }
            resetAfterAction()
            return
        }

        // Music Logic
        if (heard.contains("music") || heard.contains("gana")) {
            SystemController.playMusic(this)
            speak("Music chala diya")
            resetAfterAction()
            return
        }

        // Termux Logic (Hacking)
        if (heard.startsWith("run")) {
            val cmd = heard.replace("run", "").trim()
            SystemController.runTermuxCommand(this, cmd)
            speak("Command execute ho gayi")
            resetAfterAction()
            return
        }

        // Stop Logic
        if (heard.contains("band") || heard.contains("chup") || heard.contains("stop")) {
            speak("Allah Hafiz")
            isAwake = false
            showOrbIdle()
            startListening()
            return
        }

        // ---------- 3. AI BRAIN (Agar kuch samajh na aye to Server se pucho) ----------
        
        // Pehle hum keh rahe thay "Samajh nahi aya", ab hum server bhejenge
        askJarvisBrain(heard)
    }

    // ================= AI CONNECTION =================

    private fun askJarvisBrain(question: String) {
        showOrbListening() // User ko dikhao ke process ho raha hai
        
        val request = ChatRequest(message = question, mode = "voice")
        
        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val reply = response.body()!!.reply
                    speak(reply) // AI ka jawab bolo
                } else {
                    speak("Server se jawab nahi aya, error code ${response.code()}")
                }
                resetAfterAction()
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                speak("Internet disconnect lag raha hai boss")
                resetAfterAction()
            }
        })
    }

    private fun resetAfterAction() {
        isAwake = false
        showOrbIdle()
        // Thora sa delay taake khud ki awaz na sun le
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startListening()
        }, 1500)
    }

    // ================= TTS & AUDIO (Existing Logic) =================

    private fun speak(text: String) {
        showOrbListening()

        // Aapka existing XTTS logic
        XTTSHttpClient.fetchWav(
            url = "https://romeo-backend.vercel.app/api/tts",
            text = text,
            lang = "ur",
            onBytes = { wav ->
                val player = XTTSPlayer(
                    onLevel = { level ->
                        startService(Intent(this, OrbOverlayService::class.java).putExtra("level", level))
                    },
                    onDone = { showOrbIdle() }
                )
                player.playWavBytes(wav)
            },
            onFail = {
                // Agar internet na ho to local TTS
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fallback")
            }
        )
    }

    // ================= ORB & INIT =================

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("ur", "PK")
        }
    }

    private fun showOrbIdle() {
        startService(Intent(this, OrbOverlayService::class.java).putExtra("state", "idle"))
    }

    private fun showOrbListening() {
        startService(Intent(this, OrbOverlayService::class.java).putExtra("state", "listening"))
    }

    override fun onError(error: Int) {
        // Error aye to dobara sunna shuru karo (Loop)
        isListening = false
        startListening()
    }

    override fun onDestroy() {
        try {
            recognizer.destroy()
            tts.shutdown()
        } catch (e: Exception) {}
        super.onDestroy()
    }

    // Unused overrides
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
