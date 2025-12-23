package com.romeo.jarvis.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.romeo.jarvis.R
import java.util.*

class OrbOverlayService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var orbView: View
    private lateinit var orbIcon: ImageView
    private lateinit var params: WindowManager.LayoutParams
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var textToSpeech: TextToSpeech? = null
    private var audioManager: AudioManager? = null

    // Logic States
    private var isWakeWordMode = true 
    private val WAKE_WORD = "jarvis"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        textToSpeech = TextToSpeech(this, this)
        
        initializeOrb()
        setupSpeechRecognizer()
    }

    private fun initializeOrb() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        orbView = LayoutInflater.from(this).inflate(R.layout.layout_orb_overlay, null)
        orbIcon = orbView.findViewById(R.id.orbIcon)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        // Bottom Center Position
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 150 
        
        orbView.visibility = View.GONE
        windowManager.addView(orbView, params)
    }

    // --- SPEECH RECOGNITION ---
    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK") 
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                if (!isWakeWordMode) {
                    val scale = 1f + (rmsdB / 20f)
                    orbIcon.scaleX = scale
                    orbIcon.scaleY = scale
                }
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { restartListening() }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    handleVoiceInput(matches[0].lowercase())
                } else {
                    restartListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty() && isWakeWordMode) {
                    if (matches[0].lowercase().contains(WAKE_WORD)) {
                        speechRecognizer?.stopListening()
                        activateAssistant()
                    }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        
        speechRecognizer?.startListening(speechIntent)
    }

    private fun restartListening() {
        Handler(Looper.getMainLooper()).postDelayed({
            try { speechRecognizer?.startListening(speechIntent) } catch (e: Exception) {}
        }, 500)
    }

    private fun activateAssistant() {
        isWakeWordMode = false
        // Orb Show Animation
        Handler(Looper.getMainLooper()).post {
            orbView.visibility = View.VISIBLE
            orbIcon.scaleX = 0f; orbIcon.scaleY = 0f
            orbIcon.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
        }
        speak("Ji sir?")
        restartListening()
    }

    private fun handleVoiceInput(cmd: String) {
        if (isWakeWordMode) {
            if (cmd.contains(WAKE_WORD)) activateAssistant() else restartListening()
        } else {
            processCommand(cmd)
        }
    }

    // --- COMMAND LOGIC ---
    private fun processCommand(cmd: String) {
        var shouldHide = true

        when {
            // Media Controls
            cmd.contains("play") || cmd.contains("chalao") -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            cmd.contains("pause") || cmd.contains("ruko") || cmd.contains("stop") -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            cmd.contains("next") || cmd.contains("agla") -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            cmd.contains("previous") || cmd.contains("pichla") -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            
            // Apps
            cmd.contains("open") || cmd.contains("kholo") -> {
                val appName = cmd.replace("open", "").replace("kholo", "").replace("jarvis", "").trim()
                if(openApp(appName)) speak("Opening $appName") else speak("App nahi mili")
            }

            // Casual
            cmd.contains("kaise ho") -> {
                speak("Main theek hoon sir, hukam karein.")
                shouldHide = false // Conversation continue rakho
            }
            
            // Close
            cmd.contains("cancel") || cmd.contains("kuch nahi") -> speak("Theek hai")
            
            else -> {
                speak("Samajh nahi aya")
                shouldHide = false // Dobara suno
            }
        }

        if (shouldHide) {
            Handler(Looper.getMainLooper()).postDelayed({
                isWakeWordMode = true
                orbView.visibility = View.GONE
                restartListening()
            }, 4000)
        } else {
            restartListening()
        }
    }

    // --- MEDIA CONTROL HELPER ---
    private fun dispatchMediaKey(keyCode: Int) {
        val eventTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        
        audioManager?.dispatchMediaKeyEvent(downEvent)
        audioManager?.dispatchMediaKeyEvent(upEvent)
        
        speak("Done sir")
    }

    private fun openApp(appName: String): Boolean {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        packages.forEach {
            val label = pm.getApplicationLabel(it).toString().lowercase()
            if (label.contains(appName) || it.packageName.contains(appName)) {
                try {
                    val intent = pm.getLaunchIntentForPackage(it.packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    return true
                } catch (e: Exception) { return false }
            }
        }
        return false
    }

    // --- TTS & AUDIO DUCKING ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setPitch(0.6f) // Venom Voice
        }
    }

    private fun speak(text: String) {
        // Music volume low karo (Band nahi)
        audioManager?.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
        
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "messageID")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "messageID")
        
        // Bolne ke baad volume wapis normal karne ke liye delay
        Handler(Looper.getMainLooper()).postDelayed({
            audioManager?.abandonAudioFocus(null)
        }, 3000)
    }

    private fun createNotification(): Notification {
        val channelId = "JarvisService"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Jarvis Background", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis Active")
            .setSmallIcon(R.drawable.ic_mic) // Ensure icon exists
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        if (::windowManager.isInitialized) windowManager.removeView(orbView)
    }
}
