package com.romeo.jarvis

import android.Manifest
import android.animation.*
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.Uri
import android.os.*
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
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

    // Animation Controllers
    private var outerRingRotation: ObjectAnimator? = null
    private var corePulseAnimator: ValueAnimator? = null
    private var listeningRippleAnimator: AnimatorSet? = null
    private val animationHandler = Handler(Looper.getMainLooper())
    private var typewriterRunnable: Runnable? = null

    // Voice Systems
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    
    // Service & Permissions
    private val OVERLAY_REQUEST_CODE = 102
    private val PERMISSION_REQUEST_CODE = 101
    
    // State Management
    private var isListening = false
    private var isProcessing = false
    private var isSpeaking = false
    private val wakeWord = "jarvis"
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize systems
        initializeViews()
        createNotificationChannel()
        setupTextToSpeech()
        setupSpeechRecognizer()
        setupAnimations()
        setupNavigation()
        
        // Check Permissions & Start Service
        if (checkAllPermissions()) {
            startOverlayService()
        } else {
            requestPermissions()
        }
        
        // Initial Status
        updateStatus("JARVIS • ONLINE", "#00FF00")
        animateTypewriterText("System initialized. Background services active.")
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
        
        btnMic.setOnClickListener { handleMicButtonClick() }
    }

    // ================= PERMISSIONS & SERVICE =================

    private fun checkAllPermissions(): Boolean {
        val overlayGranted = Settings.canDrawOverlays(this)
        val runtimeGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        return overlayGranted && runtimeGranted
    }

    private fun requestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
        }
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Floating Orb ke liye permission chahiye.")
            .setPositiveButton("Grant") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                    Uri.parse("package:$packageName"))
                startActivityForResult(intent, OVERLAY_REQUEST_CODE)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startOverlayService()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQUEST_CODE && Settings.canDrawOverlays(this)) {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val serviceIntent = Intent(this, OrbOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        // App ko minimize nahi kar rahe taake aap animations enjoy kar sakein
    }

    // ================= ANIMATIONS =================

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
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                orbCore.scaleX = scale
                orbCore.scaleY = scale
                orbCore.alpha = 0.6f + (scale - 1f) * 0.4f
            }
            start()
        }

        ObjectAnimator.ofFloat(orbAiText, "alpha", 1f, 0.7f, 1f).apply {
            duration = 3000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun startListeningAnimation() {
        runOnUiThread {
            corePulseAnimator?.cancel()
            listeningRippleAnimator = AnimatorSet().apply {
                val ripple1 = ObjectAnimator.ofFloat(orbCore, "scaleX", 1f, 1.5f).apply {
                    duration = 800
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.RESTART
                }
                val ripple2 = ObjectAnimator.ofFloat(orbCore, "scaleY", 1f, 1.5f).apply {
                    duration = 800
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.RESTART
                }
                playTogether(ripple1, ripple2)
                start()
            }
            btnMic.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300).start()
        }
    }

    private fun stopListeningAnimation() {
        listeningRippleAnimator?.cancel()
        setupAnimations() // Restart normal pulse
        btnMic.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
    }

    private fun updateStatus(status: String, colorHex: String) {
        statusLabel.text = status
        statusLabel.setTextColor(Color.parseColor(colorHex))
    }

    private fun animateTypewriterText(text: String, onComplete: (() -> Unit)? = null) {
        typewriterRunnable?.let { animationHandler.removeCallbacks(it) }
        txtPrompt.text = ""
        val words = text.split(" ")
        var wordIndex = 0
        typewriterRunnable = object : Runnable {
            override fun run() {
                if (wordIndex < words.size) {
                    txtPrompt.append("${words[wordIndex]} ")
                    wordIndex++
                    animationHandler.postDelayed(this, 100)
                } else {
                    onComplete?.invoke()
                }
            }
        }
        animationHandler.post(typewriterRunnable!!)
    }

    // ================= TEXT TO SPEECH (VENOM STYLE) =================

    private fun setupTextToSpeech() {
        textToSpeech = TextToSpeech(this, this)
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                updateStatus("JARVIS • SPEAKING", "#FFD700")
            }
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                updateStatus("JARVIS • ONLINE", "#00FF00")
            }
            override fun onError(utteranceId: String?) { isSpeaking = false }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setPitch(0.6f) // Deep Voice
            textToSpeech?.setSpeechRate(0.8f) // Slower
        }
    }

    private fun speak(text: String) {
        // Audio Ducking (Music slow, not stop)
        audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "jarvis_speak")
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "jarvis_speak")
        
        Handler(Looper.getMainLooper()).postDelayed({
            audioManager?.abandonAudioFocus(null)
        }, 4000)
    }

    // ================= SPEECH RECOGNITION =================

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(createRecognitionListener())
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { updateStatus("JARVIS • READY", "#00FFFF") }
            override fun onBeginningOfSpeech() {
                isListening = true
                updateStatus("JARVIS • LISTENING", "#FF0000")
                startListeningAnimation()
            }
            override fun onRmsChanged(rmsdB: Float) {
                orbCore.alpha = 0.5f + (rmsdB / 15).coerceIn(0f, 0.5f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                stopListeningAnimation()
                updateStatus("JARVIS • PROCESSING", "#FFA500")
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    processVoiceCommand(matches[0].lowercase())
                }
            }
            override fun onError(error: Int) {
                isListening = false
                stopListeningAnimation()
                animateTypewriterText("Error code: $error")
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK") // Urdu Enabled
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun handleMicButtonClick() {
        playActivationSound()
        if (isListening) speechRecognizer?.stopListening() else startVoiceRecognition()
    }

    // ================= COMMAND PROCESSING =================

    private fun processVoiceCommand(command: String) {
        animateTypewriterText(command) // Show what user said
        isProcessing = true
        
        // Remove wake words
        val cleanCmd = command.replace(wakeWord, "").trim()

        when {
            // Media Controls (Added as requested)
            cleanCmd.contains("play") || cleanCmd.contains("chalao") -> controlMedia(KeyEvent.KEYCODE_MEDIA_PLAY)
            cleanCmd.contains("pause") || cleanCmd.contains("ruko") -> controlMedia(KeyEvent.KEYCODE_MEDIA_PAUSE)
            cleanCmd.contains("next") || cleanCmd.contains("agla") -> controlMedia(KeyEvent.KEYCODE_MEDIA_NEXT)
            cleanCmd.contains("volume") -> controlVolume(cleanCmd)
            
            // App Opening
            cleanCmd.contains("open") || cleanCmd.contains("kholo") -> {
                val appName = cleanCmd.replace("open", "").replace("kholo", "").trim()
                openApp(appName)
            }
            
            // Utilities
            cleanCmd.contains("wifi") -> toggleWifi(cleanCmd.contains("on") || cleanCmd.contains("chalao"))
            cleanCmd.contains("bluetooth") -> toggleBluetooth(cleanCmd.contains("on"))
            cleanCmd.contains("call") || cleanCmd.contains("milao") -> makeCall(cleanCmd.replace("call", "").replace("milao", "").trim())
            cleanCmd.contains("message") -> parseSendMessage(cleanCmd)
            
            // AI Chat
            else -> connectToBackend(cleanCmd)
        }
    }

    // ================= FUNCTIONS =================

    private fun controlMedia(keyCode: Int) {
        val eventTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager?.dispatchMediaKeyEvent(downEvent)
        audioManager?.dispatchMediaKeyEvent(upEvent)
        speak("Media command executed")
        isProcessing = false
    }
    
    private fun controlVolume(command: String) {
        val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 5
        if (command.contains("up") || command.contains("ziada")) {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, (current + 2).coerceAtMost(max), 0)
            speak("Volume increased")
        } else {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, (current - 2).coerceAtLeast(0), 0)
            speak("Volume decreased")
        }
    }

    private fun openApp(appName: String) {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var found = false
        for (pkg in packages) {
            val label = pm.getApplicationLabel(pkg).toString().lowercase()
            if (label.contains(appName.lowercase()) || pkg.packageName.contains(appName.lowercase())) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName)
                if (launchIntent != null) {
                    speak("Opening $label")
                    startActivity(launchIntent)
                    found = true
                    break
                }
            }
        }
        if (!found) speak("App not found")
        isProcessing = false
    }

    private fun toggleWifi(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            speak("Opening settings")
        } else {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            wifiManager.isWifiEnabled = enable
            speak("WiFi ${if(enable) "enabled" else "disabled"}")
        }
        isProcessing = false
    }

    private fun toggleBluetooth(enable: Boolean) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (enable) adapter?.enable() else adapter?.disable()
            speak("Bluetooth ${if(enable) "enabled" else "disabled"}")
        } else {
            speak("Permission denied")
        }
        isProcessing = false
    }

    private fun makeCall(name: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_DIAL) // Safer than ACTION_CALL for testing
            intent.data = Uri.parse("tel:") 
            // Real contact search logic requires heavy code, using dialer for now
            startActivity(intent)
            speak("Opening dialer for $name")
        } else {
            requestPermissions()
        }
        isProcessing = false
    }

    private fun parseSendMessage(command: String) {
        speak("Opening messages")
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_APP_MESSAGING)
        startActivity(intent)
        isProcessing = false
    }

    // ================= BACKEND =================

    private fun connectToBackend(message: String) {
        RetrofitClient.instance.chatWithAI(ChatRequest(message, "voice")).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val reply = response.body()!!.reply
                    animateTypewriterText(reply) { speak(reply) }
                } else {
                    speak("Server error")
                }
                isProcessing = false
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                speak("I am offline")
                isProcessing = false
            }
        })
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("jarvis_service_channel", "Jarvis Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun playActivationSound() {
        try {
            MediaPlayer.create(this, R.raw.ting_sound).start()
        } catch (e: Exception) {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(ToneGenerator.TONE_PROP_BEEP)
        }
    }
    
    private fun setupNavigation() {
        // Navigation Logic (Same as before)
        findViewById<ImageView>(R.id.navVoice)?.setOnClickListener { 
            orbContainer.visibility = View.VISIBLE
            txtPrompt.visibility = View.VISIBLE
            supportFragmentManager.findFragmentByTag("CHAT_TAG")?.let { supportFragmentManager.beginTransaction().remove(it).commit() }
        }
        findViewById<ImageView>(R.id.navChat)?.setOnClickListener {
            orbContainer.visibility = View.GONE
            txtPrompt.visibility = View.GONE
            supportFragmentManager.beginTransaction().add(R.id.mainContent, ChatFragment(), "CHAT_TAG").commit()
        }
        findViewById<ImageView>(R.id.navSettings)?.setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        // Note: OverlayService ko stop nahi kar rahe taake wo chalta rahe
    }
}
