package com.romeo.jarvis.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
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
    private var isListening = false
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "JarvisChannel")
            .setContentTitle("Jarvis AI")
            .setContentText("Listening for commands...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        startForeground(1, notification)
        
        startListening()
        return START_STICKY
    }

    private fun startListening() {
        if (!isListening) {
            try {
                speechRecognizer.startListening(speechIntent)
                isListening = true
            } catch (e: Exception) {
                restartListening()
            }
        }
    }

    private fun restartListening() {
        isListening = false
        speechRecognizer.stopListening()
        Thread.sleep(100)
        startListening()
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val spokenText = matches[0].lowercase()
            Log.d("Jarvis", "Heard: $spokenText")

            if (spokenText.contains(WAKE_WORD)) {
                val command = spokenText.substringAfter(WAKE_WORD).trim()
                executeCommand(command)
            }
        }
        restartListening()
    }

    override fun onError(error: Int) {
        restartListening()
    }

    private fun executeCommand(command: String) {
        when {
            // 1. App Launching
            command.startsWith("open") -> {
                val appName = command.removePrefix("open").trim()
                speak("Opening $appName")
                launchApp(appName)
            }
            
            // 2. Termux Commands (e.g. "Jarvis run update")
            command.startsWith("run") -> {
                val termuxCmd = command.removePrefix("run").trim()
                speak("Executing command: $termuxCmd")
                SystemController.runTermuxCommand(this, termuxCmd)
            }
            
            // 3. File Deletion (e.g. "Jarvis delete file movies")
            command.startsWith("delete file") || command.startsWith("delete folder") -> {
                val path = command.replace("delete file", "").replace("delete folder", "").trim()
                val result = SystemController.deleteFileOrFolder("/sdcard/$path")
                speak(result)
            }
            
            // 4. Write Code (e.g. "Jarvis write code hello.py print hello")
            command.startsWith("write code") -> {
                // Format: write code [filename] [content]
                val parts = command.removePrefix("write code").trim().split(" ", limit = 2)
                if (parts.size == 2) {
                    val fileName = parts[0]
                    val content = parts[1]
                    SystemController.writeCodeToFile(fileName, content)
                    speak("Code written to $fileName")
                } else {
                    speak("Please specify filename and content.")
                }
            }

            // 5. Basic Utilities
            command.contains("time") -> {
                val time = java.text.SimpleDateFormat("hh:mm a", Locale.US).format(Date())
                speak("It is $time")
            }
            
            command.isEmpty() -> speak("Yes Boss?")
            
            else -> speak("Command not recognized: $command")
        }
    }

    private fun launchApp(appName: String) {
        // Simple logic to find app by name (Case sensitive match is tricky, needs Loop)
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var found = false
        
        for (packageInfo in packages) {
            val label = pm.getApplicationLabel(packageInfo).toString().lowercase()
            if (label.contains(appName)) {
                val launchIntent = pm.getLaunchIntentForPackage(packageInfo.packageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                found = true
                break
            }
        }
        if (!found) speak("I couldn't find $appName installed.")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            speak("Jarvis is online.")
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            "JarvisChannel",
            "Jarvis Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}

