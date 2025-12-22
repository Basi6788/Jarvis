package com.romeo.jarvis.services

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.app.Service
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.content.pm.PackageManager
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
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Notification for Foreground Service
        val notification = NotificationCompat.Builder(this, "JarvisChannel")
            .setContentTitle("Jarvis AI")
            .setContentText("Online & Listening")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .build()
        startForeground(1, notification)
        
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        try { speechRecognizer.startListening(speechIntent) } catch(e: Exception){ restartListening() }
    }
    
    private fun restartListening() {
        speechRecognizer.stopListening()
        Thread.sleep(100)
        startListening()
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0].lowercase()
            Log.d("Jarvis", "Heard: $text")

            if (text.contains(WAKE_WORD)) {
                val cmd = text.substringAfter(WAKE_WORD).trim()
                executeCommand(cmd)
            }
        }
        startListening()
    }

    private fun executeCommand(command: String) {
        when {
            // CALL LOGIC
            command.startsWith("call") -> {
                val number = command.replace("call", "").trim()
                speak("Calling $number")
                val intent = Intent(Intent.ACTION_CALL)
                intent.data = Uri.parse("tel:$number")
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            
            // WHATSAPP LOGIC
            command.contains("open whatsapp") -> {
                speak("Opening WhatsApp")
                launchApp("com.whatsapp")
            }
            
            // APP OPEN LOGIC (Universal)
            command.startsWith("open") -> {
                val appName = command.replace("open", "").trim()
                speak("Opening $appName")
                openAppByName(appName)
            }

            // TERMUX LOGIC
            command.startsWith("run") -> {
                val tCmd = command.removePrefix("run").trim()
                speak("Running command")
                SystemController.runTermuxCommand(this, tCmd)
            }

            else -> speak("I didn't understand: $command")
        }
    }

    private fun openAppByName(appName: String) {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var found = false
        for (pkg in packages) {
            val label = pm.getApplicationLabel(pkg).toString().lowercase()
            if (label.contains(appName)) {
                val intent = pm.getLaunchIntentForPackage(pkg.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    found = true
                    break
                }
            }
        }
        if (!found) speak("App not found")
    }

    // TTS & Boilerplate
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts.language = Locale.US }
    private fun speak(text: String) { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }
    
    override fun onError(error: Int) { restartListening() }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
