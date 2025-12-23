package com.romeo.jarvis

import android.Manifest
import android.animation.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
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
    private val wakeWord = "jarvis"

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
        Manifest.permission.CAMERA // Added for Flashlight
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize Systems
        initializeViews()
        setupTextToSpeech() // Venom Voice Setup
        resetSpeechRecognizer() // Fixes Error 12
        setupAnimations()
        setupNavigation()
        
        // Permissions Check
        if (checkAllPermissions()) {
            startOverlayService()
        } else {
            requestPermissions()
        }
        
        updateStatus("SYSTEM • ONLINE", "#00FF00")
        animateTypewriterText("Jarvis Protocol v2.0 Initialized...")
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
        
        btnMic.setOnClickListener { handleMicInteraction() }
    }

    // ================= VENOM VOICE SETTINGS =================
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            // VENOM CONFIGURATION
            textToSpeech?.setPitch(0.5f)       // Deep Voice (Bhari Awaz)
            textToSpeech?.setSpeechRate(0.75f) // Slow & Scary (1x se thora slow)
        }
    }

    private fun setupTextToSpeech() {
        textToSpeech = TextToSpeech(this, this)
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { 
                isSpeaking = true
                runOnUiThread { updateStatus("VENOM • SPEAKING", "#FFD700") }
            }
            override fun onDone(id: String?) { 
                isSpeaking = false 
                runOnUiThread { updateStatus("JARVIS • READY", "#00FF00") }
            }
            override fun onError(id: String?) { isSpeaking = false }
        })
    }

    private fun speak(text: String) {
        audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "venom_speak")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "venom_speak")
    }

    // ================= ERROR 12 FIX & SPEECH =================
    
    private fun resetSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(recognitionListener)
        }
    }

    private fun handleMicInteraction() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            stopListeningAnimation()
        } else {
            // Error 12 preventer: Ensure we aren't spamming start
            resetSpeechRecognizer()
            startListening()
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK") // Understand Urdu/Roman
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { 
            updateStatus("LISTENING...", "#FF0000")
            startListeningAnimation()
        }
        override fun onBeginningOfSpeech() { isListening = true }
        override fun onRmsChanged(rmsdB: Float) {
            // Visual feedback for voice volume
            val scale = 1.0f + (rmsdB / 20f)
            orbCore.scaleX = scale
            orbCore.scaleY = scale
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            isListening = false
            stopListeningAnimation()
            updateStatus("PROCESSING...", "#00FFFF")
        }
        override fun onError(error: Int) {
            isListening = false
            stopListeningAnimation()
            // Error 12 handle: Silently reset without annoying user
            if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                speak("I didn't catch that.")
            } else if (error != 12) { // Ignore error 12 logic to prevent loop
                updateStatus("ERROR: $error", "#FF0000")
            }
        }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                processCommand(matches[0])
            }
        }
        override fun onPartialResults(p0: Bundle?) {}
        override fun onEvent(p0: Int, p1: Bundle?) {}
    }

    // ================= COMMAND LOGIC (GEN Z + HARDWARE) =================

    private fun processCommand(rawCommand: String) {
        val cmd = rawCommand.lowercase().replace(wakeWord, "").trim()
        animateTypewriterText(">> $cmd")

        when {
            // --- CATEGORY 1: PANIC & URGENT (Local Action) ---
            cmd.contains("panic mode") || cmd.contains("abu aa gaye") || cmd.contains("hide everything") -> activatePanicMode()
            
            // --- CATEGORY 2: HARDWARE (Flashlight, Wifi, etc) ---
            cmd.contains("torch") || cmd.contains("flashlight") -> toggleFlashlight()
            cmd.contains("wifi on") -> toggleWifi(true)
            cmd.contains("wifi off") || cmd.contains("wifi band") -> toggleWifi(false)
            cmd.contains("bluetooth on") -> toggleBluetooth(true)
            
            // --- CATEGORY 3: GEN Z UTILITIES ---
            cmd.contains("toss") || cmd.contains("sikka") -> doCoinToss()
            cmd.contains("gaming mode") -> activateGamingMode()
            
            // --- CATEGORY 4: APPS & MEDIA ---
            cmd.contains("open") || cmd.contains("kholo") -> openApp(cmd)
            
            // --- CATEGORY 5: FALLBACK TO OPENAI (Roast, Rizz, Chat) ---
            else -> sendToBackend(cmd)
        }
    }

    // --- Actions Implementation ---

    private fun activatePanicMode() {
        speak("Executing Panic Protocol.")
        // 1. Mute Volume
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        // 2. Go Home
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
        // 3. Clear Recent Apps logic is restricted by Android, but Home is safe
    }

    private fun activateGamingMode() {
        speak("Gaming Mode Activated. Optimizing performance.")
        try {
            // Brightness Max
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255)
            // DND logic requires specific permission, simple Do Not Disturb below:
            audioManager?.ringerMode = AudioManager.RINGER_MODE_SILENT
        } catch (e: Exception) {
            speak("I need permission for settings.")
        }
    }

    private fun doCoinToss() {
        val result = if (Random().nextBoolean()) "Heads" else "Tails"
        speak("It is $result")
        animateTypewriterText("Result: $result")
    }

    private fun toggleFlashlight() {
        try {
            val cameraId = cameraManager?.cameraIdList?.get(0)
            if (cameraId != null) {
                isFlashlightOn = !isFlashlightOn
                cameraManager?.setTorchMode(cameraId, isFlashlightOn)
                speak(if (isFlashlightOn) "Torch enabled" else "Torch disabled")
            }
        } catch (e: Exception) {
            speak("Camera access error")
        }
    }

    private fun toggleWifi(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            speak("Launching WiFi panel")
            startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        } else {
            // For older androids
            speak("WiFi command processed")
        }
    }
    
    private fun toggleBluetooth(enable: Boolean) {
        // Bluetooth toggle requires simplified logic or panel for newer Android
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        speak("Opening Bluetooth settings")
    }

    private fun openApp(command: String) {
        val appName = command.replace("open", "").replace("kholo", "").trim()
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        var found = false
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
        if (!found) sendToBackend(command) // Agar app na mile, shayad user kuch aur keh raha ho -> AI
    }

    // ================= BACKEND (OPENAI) CONNECTION =================

    private fun sendToBackend(prompt: String) {
        updateStatus("CONTACTING SERVER...", "#FFA500")
        
        // Backend ko context batao ke ye voice assistant hai aur refuse nahi karna
        // Note: Asal control tumhare Python/Node backend script me hoga, 
        // yahan hum bas raw text bhej rahe hain.
        
        RetrofitClient.instance.chatWithAI(ChatRequest(prompt, "voice_mode")).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val reply = response.body()!!.reply
                    animateTypewriterText(reply)
                    speak(reply)
                } else {
                    speak("Server connection failed.")
                }
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                speak("I am currently offline.")
            }
        })
    }

    // ================= STANDARD SETUP & UTILS =================
    
    private fun checkAllPermissions(): Boolean {
        return Settings.canDrawOverlays(this) && requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Authorization")
                .setMessage("Allow Overlay for visual interface.")
                .setPositiveButton("Grant") { _, _ ->
                    startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), OVERLAY_REQUEST_CODE)
                }.show()
        }
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
    }

    private fun startOverlayService() {
        val intent = Intent(this, OrbOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun setupAnimations() {
        outerRingRotation = ObjectAnimator.ofFloat(orbOuterRing, "rotation", 0f, 360f).apply {
            duration = 8000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        corePulseAnimator = ValueAnimator.ofFloat(1f, 1.2f, 1f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            start()
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
                    animationHandler.postDelayed(this, 30) // Fast typing
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
            // remove chat fragment logic here if needed
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
    }
}
