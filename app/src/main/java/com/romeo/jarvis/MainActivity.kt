package com.romeo.jarvis

import android.Manifest
import android.animation.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.animation.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.romeo.jarvis.fragments.ChatFragment
import com.romeo.jarvis.services.OrbOverlayService
import com.romeo.jarvis.utils.ChatRequest
import com.romeo.jarvis.utils.ChatResponse
import com.romeo.jarvis.utils.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // UI Components
    private lateinit var orbContainer: FrameLayout
    private lateinit var orbOuterRing: View
    private lateinit var orbCore: View
    private lateinit var orbAiText: TextView
    private lateinit var btnMic: ImageButton
    private lateinit var txtPrompt: TextView
    private lateinit var statusLabel: TextView

    // Animation & Handlers
    private var outerRingRotation: ObjectAnimator? = null
    private var corePulseAnimator: ValueAnimator? = null
    private var listeningRippleAnimator: AnimatorSet? = null
    private val animationHandler = Handler(Looper.getMainLooper())
    private var typewriterRunnable: Runnable? = null

    // System Managers
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var cameraManager: CameraManager? = null
    
    // Flags & State
    private var isListening = false
    private var isSpeaking = false
    private var isFlashlightOn = false
    private val wakeWord = "jarvis" // English
    private val wakeWordUrdu = "جاروس" // Urdu Script

    // Permissions List
    private val OVERLAY_REQUEST_CODE = 102
    private val PERMISSION_REQUEST_CODE = 101
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        setupTextToSpeech() // Venom Setup
        setupAnimations()
        setupNavigation()
        
        // Mute Beep Sound fix
        muteSystemBeep() 

        if (checkAllPermissions()) {
            startOverlayService()
        } else {
            requestPermissions()
        }
        
        // Start Listening Automatically (Fixing Error 12 loop inside)
        resetSpeechRecognizer()
        
        updateStatus("JARVIS • ONLINE", "#00FF00")
        animateTypewriterText("System Online. Waiting for 'Jarvis'...")
    }

    private fun initializeViews() {
        orbContainer = findViewById(R.id.orbContainer)
        orbOuterRing = findViewById(R.id.orbOuterRing)
        orbCore = findViewById(R.id.orbCore)
        orbAiText = findViewById(R.id.orbAiText)
        btnMic = findViewById(R.id.btnMic)
        txtPrompt = findViewById(R.id.txtPrompt)
        statusLabel = findViewById(R.id.statusLabel)
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        btnMic.setOnClickListener { 
            if(isListening) stopListening() else startListening()
        }
    }

    // ================= SOUND & BEEP CONTROL =================
    
    private fun muteSystemBeep() {
        // "Karwaon" awaz kam karne ke liye
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0)
        } catch (e: Exception) { e.printStackTrace() }
    }

    // ================= VENOM VOICE =================
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setPitch(0.6f)       
            textToSpeech?.setSpeechRate(0.85f) 
        }
    }

    private fun setupTextToSpeech() {
        textToSpeech = TextToSpeech(this, this)
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { 
                isSpeaking = true
                runOnUiThread { updateStatus("SPEAKING...", "#FFD700") }
            }
            override fun onDone(id: String?) { 
                isSpeaking = false 
                runOnUiThread { 
                    updateStatus("LISTENING...", "#FF0000")
                    startListening() // Auto restart listening after speaking
                }
            }
            override fun onError(id: String?) { isSpeaking = false }
        })
    }

    private fun speak(text: String) {
        if (isListening) speechRecognizer?.stopListening()
        
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "venom_id")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "venom_id")
    }

    // ================= SPEECH RECOGNIZER (THE FIX) =================
    
    private fun resetSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(recognitionListener)
            startListening()
        }
    }

    private fun startListening() {
        if (isSpeaking) return // Don't listen while speaking
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK") // Urdu Optimization
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        
        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            startListeningAnimation()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
        stopListeningAnimation()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            // Mute beep again just in case
            muteSystemBeep()
        }
        override fun onBeginningOfSpeech() { 
            isListening = true 
            updateStatus("LISTENING...", "#FF0000")
        }
        override fun onRmsChanged(rmsdB: Float) {
            val scale = 1.0f + (rmsdB / 20f)
            orbCore.scaleX = scale
            orbCore.scaleY = scale
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            isListening = false
        }
        override fun onError(error: Int) {
            isListening = false
            // Silent Retry (No Beep, No Toast)
            Handler(Looper.getMainLooper()).postDelayed({
                resetSpeechRecognizer()
            }, 1000)
        }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val command = matches[0]
                // Yahan check hoga ke "Jarvis" bola gaya hai ya nahi
                processCommand(command)
            } else {
                startListening()
            }
        }
        override fun onPartialResults(p0: Bundle?) {}
        override fun onEvent(p0: Int, p1: Bundle?) {}
    }

    // ================= LOGIC: URDU + ENGLISH HANDLING =================

    private fun processCommand(rawCommand: String) {
        val lowerCmd = rawCommand.lowercase()
        animateTypewriterText(">> $rawCommand")

        // 1. WAKE WORD CHECK (Sabse Zaroori)
        // Agar user ne "Jarvis" nahi bola, to kuch mat karo aur wapis sunna shuru karo
        if (!lowerCmd.contains(wakeWord) && !lowerCmd.contains(wakeWordUrdu) && !lowerCmd.contains("service")) {
            startListening()
            return
        }

        // 2. CLEANUP (Naam hatao taake command bache)
        val cleanCmd = lowerCmd
            .replace(wakeWord, "")
            .replace(wakeWordUrdu, "") // "جاروس"
            .replace("جارویس", "")
            .trim()

        // 3. EXECUTION
        when {
            // Panic Mode
            cleanCmd.contains("panic") || cleanCmd.contains("hide") || cleanCmd.contains("chupa") -> activatePanicMode()
            
            // Flashlight
            cleanCmd.contains("torch") || cleanCmd.contains("flashlight") || cleanCmd.contains("ٹارچ") || cleanCmd.contains("لائٹ") -> toggleFlashlight()
            
            // Apps (Urdu & English support added)
            cleanCmd.contains("open") || cleanCmd.contains("kholo") || cleanCmd.contains("کھولو") || cleanCmd.contains("چلاؤ") -> {
                val appName = cleanCmd
                    .replace("open", "")
                    .replace("kholo", "")
                    .replace("کھولو", "")
                    .replace("چلاؤ", "")
                    .trim()
                openApp(appName)
            }
            
            // Media
            cleanCmd.contains("play") || cleanCmd.contains("song") || cleanCmd.contains("gana") || cleanCmd.contains("گانا") -> {
                sendToBackend(cleanCmd) // Spotify integration complex hai, AI ko handle karne do
            }
            
            // Default AI Chat
            else -> sendToBackend(cleanCmd)
        }
    }

    // ================= ACTIONS =================

    private fun toggleFlashlight() {
        try {
            val cameraId = cameraManager?.cameraIdList?.get(0)
            if (cameraId != null) {
                isFlashlightOn = !isFlashlightOn
                cameraManager?.setTorchMode(cameraId, isFlashlightOn)
                speak(if (isFlashlightOn) "Torch ON" else "Torch OFF")
            }
        } catch (e: Exception) { speak("Hardware error") }
    }

    private fun activatePanicMode() {
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
        speak("Panic mode executed.")
    }

    private fun openApp(appName: String) {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var found = false
        
        // Fuzzy Search for Apps (Like "Whats" -> "WhatsApp")
        for (pkg in packages) {
            val label = pm.getApplicationLabel(pkg).toString().lowercase()
            if (label.contains(appName)) {
                val intent = pm.getLaunchIntentForPackage(pkg.packageName)
                if (intent != null) {
                    speak("Opening $label")
                    startActivity(intent)
                    found = true
                    break
                }
            }
        }
        if (!found) {
            speak("App nahi mili: $appName")
            // Retry listening
            Handler(Looper.getMainLooper()).postDelayed({ startListening() }, 2000)
        }
    }

    private fun sendToBackend(prompt: String) {
        updateStatus("AI PROCESSING...", "#FFA500")
        RetrofitClient.instance.chatWithAI(ChatRequest(prompt, "voice")).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val reply = response.body()!!.reply
                    animateTypewriterText(reply)
                    speak(reply)
                } else {
                    speak("Server error")
                    startListening()
                }
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                speak("Internet connection check karo.")
                startListening()
            }
        })
    }
    
    // ================= UTILS =================

    private fun checkAllPermissions(): Boolean {
        return Settings.canDrawOverlays(this) && requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
    }

    private fun startOverlayService() {
        // Overlay Service Background ke liye zaroori hai
        val intent = Intent(this, OrbOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun setupAnimations() {
        outerRingRotation = ObjectAnimator.ofFloat(orbOuterRing, "rotation", 0f, 360f).apply {
            duration = 8000; repeatCount = ObjectAnimator.INFINITE; interpolator = LinearInterpolator(); start()
        }
        corePulseAnimator = ValueAnimator.ofFloat(1f, 1.2f, 1f).apply {
            duration = 2000; repeatCount = ObjectAnimator.INFINITE; start()
        }
    }

    private fun startListeningAnimation() {
        runOnUiThread {
            corePulseAnimator?.cancel()
            listeningRippleAnimator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(orbCore, "scaleX", 1f, 1.5f).apply { duration=800; repeatCount=ObjectAnimator.INFINITE; repeatMode=ObjectAnimator.RESTART },
                    ObjectAnimator.ofFloat(orbCore, "scaleY", 1f, 1.5f).apply { duration=800; repeatCount=ObjectAnimator.INFINITE; repeatMode=ObjectAnimator.RESTART }
                )
                start()
            }
            btnMic.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300).start()
        }
    }

    private fun stopListeningAnimation() {
        listeningRippleAnimator?.cancel()
        setupAnimations() 
        btnMic.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
    }

    private fun updateStatus(text: String, color: String) {
        statusLabel.text = text
        statusLabel.setTextColor(Color.parseColor(color))
    }

    private fun animateTypewriterText(text: String) {
        typewriterRunnable?.let { animationHandler.removeCallbacks(it) }
        txtPrompt.text = ""
        var i = 0
        typewriterRunnable = object : Runnable {
            override fun run() {
                if (i < text.length) {
                    txtPrompt.append(text[i].toString())
                    i++
                    animationHandler.postDelayed(this, 30)
                }
            }
        }
        animationHandler.post(typewriterRunnable!!)
    }
    
    private fun setupNavigation() {
        findViewById<ImageView>(R.id.navChat)?.setOnClickListener { 
            orbContainer.visibility = View.GONE; txtPrompt.visibility = View.GONE
            supportFragmentManager.beginTransaction().replace(R.id.mainContent, ChatFragment()).commit()
        }
        findViewById<ImageView>(R.id.navVoice)?.setOnClickListener { 
            orbContainer.visibility = View.VISIBLE; txtPrompt.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
    }
}
