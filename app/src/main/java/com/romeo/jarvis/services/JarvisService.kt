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
import com.romeo.jarvis.utils.SystemController
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
    
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        setupSpeech()
    }

    private fun setupSpeech() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "JarvisServiceChannel"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channelId, "Jarvis", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis AI")
            .setContentText("Connected to Brain...")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .build()
        startForeground(1, notification)
        
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        if (!isListening) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Ye important hai taake wo urdu/english mix samajh sake
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK") 
            try { 
                speechRecognizer.startListening(intent)
                isListening = true
            } catch(e: Exception){
                isListening = false
            }
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val userVoice = matches[0]
            Log.d("Jarvis", "Heard: $userVoice")

            // 1. LOCAL COMMANDS (Fast Action)
            if (userVoice.lowercase().contains("open")) {
                 // Open App logic here
                 speak("Opening app")
            } 
            else if (userVoice.lowercase().contains("stop")) {
                speak("Stopping service")
                stopSelf()
            }
            // 2. BACKEND QUERY (AI Research/Chat)
            else {
                askBackend(userVoice)
            }
        } else {
            startListening()
        }
    }

    private fun askBackend(query: String) {
        // AI se pucho
        RetrofitClient.instance.chatWithAI(ChatRequest(query)).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val aiReply = response.body()!!.reply
                    speak(aiReply) // AI ka jawab bolo
                } else {
                    speak("Server error sir.")
                }
                startListening() // Jawab dene ke baad wapis sunna shuru
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                speak("Internet connection failed.")
                startListening()
            }
        })
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onError(error: Int) { 
        isListening = false
        startListening() 
    }
    
    override fun onInit(status: Int) { 
        if (status == TextToSpeech.SUCCESS) tts.language = Locale("ur") // Urdu/Hindi accent koshish karega
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
