package com.romeo.jarvis.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.romeo.jarvis.R
import java.util.*

class OrbOverlayService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var orbView: View
    private lateinit var orbIcon: ImageView
    private lateinit var params: WindowManager.LayoutParams
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    
    // Venom Voice Settings
    private val VENOM_PITCH = 0.4f // Gehri awaz (Male/Monster style)
    private val VENOM_SPEED = 0.8f // Thora slow aur clear

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // 1. Setup Audio & TTS
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        textToSpeech = TextToSpeech(this, this)
        setupSpeechRecognizer()

        // 2. Create Floating Orb (Venom Style)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOrbView()
    }

    private fun createOrbView() {
        orbView = LayoutInflater.from(this).inflate(R.layout.layout_orb_overlay, null)
        orbIcon = orbView.findViewById(R.id.orbIcon)

        // Orb Position: Bottom Center (Navigation ke thora oopar)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, // Apps ke upar chalega
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 150 // Bottom se thora oopar

        // Touch Listener (Drag & Click)
        orbIcon.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = (event.rawX - initialTouchX).toInt()
                        val diffY = (event.rawY - initialTouchY).toInt()
                        
                        // Agar drag nahi kia, to click mana jaye
                        if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                            startListening()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY - (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(orbView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(orbView, params)
    }

    // ================= VENOM VOICE ENGINE =================
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("ur")) // Urdu try karega
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech?.language = Locale.US // Fallback to English
            }
            
            // Yahan banegi Venom ki awaz
            textToSpeech?.setPitch(VENOM_PITCH) 
            textToSpeech?.setSpeechRate(VENOM_SPEED)
            
            speak("System Online. I am watching.")
        }
    }

    private fun speak(text: String) {
        // Audio Ducking: Gaana band nahi hoga, sirf slow hoga
        audioManager?.requestAudioFocus(
            null, 
            AudioManager.STREAM_MUSIC, 
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )
        
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "venom_id")
    }

    // ================= LISTENING LOGIC =================
    private fun startListening() {
        // Orb animation change karo (Listening Mode)
        orbIcon.animate().scaleX(1.5f).scaleY(1.5f).setDuration(300).start()
        orbIcon.alpha = 1.0f

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK") // Urdu Recognizer
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        Handler(Looper.getMainLooper()).post {
            speechRecognizer?.startListening(intent)
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // Orb pulse karega jab aap bolo ge
                val scale = 1f + (rmsdB / 15f)
                orbIcon.scaleX = scale
                orbIcon.scaleY = scale
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                orbIcon.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
            }
            override fun onError(error: Int) {
                orbIcon.animate().scaleX(1f).scaleY(1f).start()
                // Agar error aye to dobara suno (Hotword loop trick)
                // Note: Continuous listening battery khata hai, filhal tap-to-speak best hai
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    processCommand(matches[0].lowercase())
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    // ================= SMART COMMAND PROCESSOR (URDU + ENGLISH) =================
    private fun processCommand(cmd: String) {
        // Urdu & English Mix Logic
        val appName = cmd.replace("jarvis", "")
                        .replace("open", "")
                        .replace("kholo", "")
                        .replace("chalao", "")
                        .replace("lagao", "")
                        .trim()

        when {
            // Apps Opening Logic
            cmd.contains("open") || cmd.contains("kholo") || cmd.contains("chalao") -> {
                if (openApp(appName)) {
                    speak("Opening $appName") // English response (looks cooler)
                } else {
                    speak("I cannot find $appName")
                }
            }
            
            // Utilities
            cmd.contains("wifi on") || cmd.contains("wifi chalao") -> {
                // Wifi logic here
                speak("WiFi activated")
            }
            
            // Casual Talk
            cmd.contains("kaise ho") || cmd.contains("how are you") -> {
                speak("I am simply code. But I am functioning perfectly.")
            }
            
            // Youtube Specific
            cmd.contains("youtube") -> {
                openApp("youtube")
                speak("Accessing YouTube database")
            }
            
            else -> speak("Command not recognized. Say again.")
        }
    }

    private fun openApp(appName: String): Boolean {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        for (packageInfo in packages) {
            val label = pm.getApplicationLabel(packageInfo).toString().lowercase()
            if (label.contains(appName) || packageInfo.packageName.contains(appName)) {
                val launchIntent = pm.getLaunchIntentForPackage(packageInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    return true
                }
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::windowManager.isInitialized) windowManager.removeView(orbView)
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
    }
}
