package com.romeo.jarvis.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.romeo.jarvis.R
import com.romeo.jarvis.utils.SystemController
import java.util.*

class JarvisService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent
    private lateinit var tts: TextToSpeech
    private val WAKE_WORD = "jarvis"

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "JarvisChannel"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(channelId, "Jarvis", NotificationManager.IMPORTANCE_LOW))
        
        // Using ic_launcher_background because it definitely exists now
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis AI")
            .setContentText("Listening...")
            .setSmallIcon(R.drawable.ic_launcher_background) 
            .build()
        startForeground(1, notification)
        
        try { speechRecognizer.startListening(speechIntent) } catch(e: Exception){}
        return START_STICKY
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0].lowercase()
            if (text.contains(WAKE_WORD)) {
                val cmd = text.substringAfter(WAKE_WORD).trim()
                if (cmd.startsWith("run")) SystemController.runTermuxCommand(this, cmd.removePrefix("run"))
                else if (cmd.contains("delete")) SystemController.deleteFileOrFolder("/sdcard/Test")
                tts.speak("Executed: $cmd", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
        speechRecognizer.startListening(speechIntent)
    }

    override fun onError(error: Int) {
        speechRecognizer.startListening(speechIntent)
    }

    override fun onInit(status: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
