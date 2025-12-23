package com.romeo.jarvis

import android.Manifest
import android.animation.*
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.view.animation.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.romeo.jarvis.fragments.ChatFragment
import com.romeo.jarvis.utils.ChatRequest
import com.romeo.jarvis.utils.ChatResponse
import com.romeo.jarvis.utils.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.*
import kotlin.collections.ArrayList

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
    
    // State Management
    private var isListening = false
    private var isProcessing = false
    private var isSpeaking = false
    private val wakeWord = "jarvis"
    
    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize systems in order
        initializeViews()
        setupTextToSpeech()
        setupSpeechRecognizer()
        setupAnimations()
        setupNavigation()
        checkPermissions()
        
        // Status update
        updateStatus("JARVIS • ONLINE", "#00FF00")
        animateTypewriterText("System initialized. How may I assist you, sir?")
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
        
        // Mic button click listener
        btnMic.setOnClickListener { handleMicButtonClick() }
    }

    // ================= ANIMATION SYSTEM =================

    private fun setupAnimations() {
        setupOrbAnimations()
        setupMicButtonAnimations()
    }

    private fun setupOrbAnimations() {
        // 1. Outer Ring Rotation (Continuous)
        outerRingRotation = ObjectAnimator.ofFloat(orbOuterRing, "rotation", 0f, 360f).apply {
            duration = 8000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // 2. Core Pulsing Glow
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

        // 3. AI Text Flicker (Subtle)
        ObjectAnimator.ofFloat(orbAiText, "alpha", 1f, 0.7f, 1f).apply {
            duration = 3000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun setupMicButtonAnimations() {
        // Default state - subtle breathing effect
        btnMic.animate().scaleX(1f).scaleY(1f).duration = 0
    }

    private fun startListeningAnimation() {
        runOnUiThread {
            // Stop default animations
            corePulseAnimator?.cancel()
            
            // Start ripple effect
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
                val fade = ObjectAnimator.ofFloat(orbCore, "alpha", 0.8f, 0f).apply {
                    duration = 800
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.RESTART
                }
                playTogether(ripple1, ripple2, fade)
                start()
            }

            // Mic button pulse
            btnMic.animate().scaleX(1.2f).scaleY(1.2f).duration = 300
        }
    }

    private fun stopListeningAnimation() {
        listeningRippleAnimator?.cancel()
        setupOrbAnimations() // Restart default animations
        btnMic.animate().scaleX(1f).scaleY(1f).duration = 300
    }

    private fun updateStatus(status: String, colorHex: String) {
        statusLabel.text = status
        statusLabel.setTextColor(android.graphics.Color.parseColor(colorHex))
    }

    // ================= TYPEWRITER ANIMATION =================

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

    // ================= TEXT-TO-SPEECH =================

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
                runOnUiThread { animateTypewriterText("How may I assist you, sir?") }
            }
            
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setPitch(1.1f)
            textToSpeech?.setSpeechRate(0.9f)
        }
    }

    private fun speak(text: String) {
        if (textToSpeech?.isSpeaking == false) {
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "jarvis_speak")
            }
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "jarvis_speak")
        }
    }

    // ================= SPEECH RECOGNITION =================

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(createRecognitionListener())
            }
        } else {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_LONG).show()
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateStatus("JARVIS • READY", "#00FFFF")
            }

            override fun onBeginningOfSpeech() {
                isListening = true
                updateStatus("JARVIS • LISTENING", "#FF0000")
                animateTypewriterText("Listening...")
                startListeningAnimation()
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Could animate orb based on volume level
                orbCore.alpha = 0.5f + (rmsdB / 10).coerceIn(0f, 0.5f)
            }

            override fun onEndOfSpeech() {
                isListening = false
                stopListeningAnimation()
                updateStatus("JARVIS • PROCESSING", "#FFA500")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0].lowercase(Locale.ROOT)
                    processVoiceCommand(command)
                }
            }

            override fun onError(error: Int) {
                isListening = false
                stopListeningAnimation()
                handleSpeechError(error)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopVoiceRecognition() {
        speechRecognizer?.stopListening()
        isListening = false
        stopListeningAnimation()
    }

    private fun handleMicButtonClick() {
        playActivationSound()
        
        if (isListening) {
            stopVoiceRecognition()
        } else {
            startVoiceRecognition()
        }
    }

    // ================= COMMAND PROCESSOR =================

    private fun processVoiceCommand(command: String) {
        Log.d("JarvisCommand", "Received: $command")
        
        if (!command.contains(wakeWord)) {
            animateTypewriterText("Please say 'jarvis' before your command.")
            return
        }

        isProcessing = true
        val actualCommand = command.replace(wakeWord, "").trim()

        when {
            // App Control
            actualCommand.startsWith("open") -> openApp(actualCommand.removePrefix("open").trim())
            
            // Messaging
            actualCommand.startsWith("send message") -> parseSendMessage(actualCommand)
            
            // Calls
            actualCommand.startsWith("call") -> makeCall(actualCommand.removePrefix("call").trim())
            
            // Media Control
            actualCommand.contains("play music") || actualCommand.contains("pause music") -> controlMedia(actualCommand)
            actualCommand.contains("next song") || actualCommand.contains("previous song") -> controlMedia(actualCommand)
            
            // Volume Control
            actualCommand.contains("volume") -> controlVolume(actualCommand)
            
            // System Commands
            actualCommand.contains("turn on wifi") || actualCommand.contains("turn off wifi") -> toggleWifi(actualCommand)
            actualCommand.contains("turn on bluetooth") || actualCommand.contains("turn off bluetooth") -> toggleBluetooth(actualCommand)
            
            // General Query
            else -> connectToBackend(actualCommand)
        }
    }

    private fun handleSpeechError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions needed"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "System busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            else -> "Unknown error"
        }
        animateTypewriterText(message)
    }

    // ================= ACTION EXECUTORS =================

    private fun openApp(appName: String) {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        
        try {
            val apps = pm.queryIntentActivities(mainIntent, 0)
            val targetApp = apps.find {
                it.loadLabel(pm).toString().lowercase().contains(appName.lowercase()) ||
                it.activityInfo.packageName.lowercase().contains(appName.lowercase())
            }
            
            if (targetApp != null) {
                val launchIntent = pm.getLaunchIntentForPackage(targetApp.activityInfo.packageName)
                startActivity(launchIntent)
                animateTypewriterText("Opening ${appName.capitalize()}")
                speak("Opening ${appName.capitalize()}")
            } else {
                animateTypewriterText("App not found: $appName")
                speak("I could not find $appName on your device.")
            }
        } catch (e: Exception) {
            animateTypewriterText("Error opening app")
        }
        isProcessing = false
    }

    private fun parseSendMessage(command: String) {
        // Expected: "send message to [contact] [message]"
        val regex = """send message to (\w+) (.+)""".toRegex()
        val match = regex.find(command)
        
        if (match != null) {
            val contact = match.groupValues[1]
            val message = match.groupValues[2]
            sendMessage(contact, message)
        } else {
            animateTypewriterText("Say: 'send message to [contact] [message]'")
            speak("Please specify contact and message")
        }
    }

    private fun sendMessage(contactName: String, message: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            val contacts = getContactsByName(contactName)
            if (contacts.isNotEmpty()) {
                val phoneNumber = contacts[0].phoneNumber
                try {
                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:$phoneNumber")
                        putExtra("sms_body", message)
                    }
                    startActivity(smsIntent)
                    animateTypewriterText("Message sent to $contactName")
                    speak("Message sent to $contactName")
                } catch (e: Exception) {
                    animateTypewriterText("Failed to send message")
                }
            } else {
                animateTypewriterText("Contact not found")
                speak("I could not find $contactName in your contacts")
            }
        } else {
            animateTypewriterText("Contact permission needed")
            requestPermissions()
        }
        isProcessing = false
    }

    private fun getContactsByName(name: String): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val contentResolver = contentResolver
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )
        
        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            while (it.moveToNext()) {
                val contactName = it.getString(nameIndex).lowercase()
                if (contactName.contains(name.lowercase())) {
                    contacts.add(
                        Contact(
                            name = it.getString(nameIndex),
                            phoneNumber = it.getString(numberIndex)
                        )
                    )
                }
            }
        }
        return contacts
    }

    data class Contact(val name: String, val phoneNumber: String)

    private fun makeCall(contactName: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val contacts = getContactsByName(contactName)
            if (contacts.isNotEmpty()) {
                val phoneNumber = contacts[0].phoneNumber
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                startActivity(callIntent)
                animateTypewriterText("Calling ${contacts[0].name}")
                speak("Calling ${contacts[0].name}")
            } else {
                animateTypewriterText("Contact not found")
            }
        } else {
            animateTypewriterText("Call permission needed")
            requestPermissions()
        }
        isProcessing = false
    }

    private fun controlMedia(command: String) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        when {
            command.contains("play") -> {
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
            }
            command.contains("pause") -> {
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
            }
            command.contains("next") -> {
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            command.contains("previous") -> {
                sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
        }
        
        animateTypewriterText("Command executed: $command")
        speak("Media control activated")
        isProcessing = false
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        try {
            val eventTime = System.currentTimeMillis()
            val downEvent = android.view.KeyEvent(eventTime, eventTime, android.view.KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = android.view.KeyEvent(eventTime, eventTime, android.view.KeyEvent.ACTION_UP, keyCode, 0)
            
            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            Log.e("JarvisMedia", "Error sending media key event", e)
        }
    }

    private fun controlVolume(command: String) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        
        when {
            command.contains("up") || command.contains("increase") -> {
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (current + 2).coerceAtMost(maxVolume), 0)
                animateTypewriterText("Volume increased")
            }
            command.contains("down") || command.contains("decrease") -> {
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (current - 2).coerceAtLeast(0), 0)
                animateTypewriterText("Volume decreased")
            }
            command.contains("max") || command.contains("maximum") -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                animateTypewriterText("Volume set to maximum")
            }
            command.contains("min") || command.contains("minimum") || command.contains("mute") -> {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                animateTypewriterText("Volume muted")
            }
        }
        isProcessing = false
    }

    private fun toggleWifi(command: String) {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            if (command.contains("on")) {
                wifiManager.isWifiEnabled = true
                animateTypewriterText("WiFi turned on")
                speak("WiFi enabled")
            } else {
                wifiManager.isWifiEnabled = false
                animateTypewriterText("WiFi turned off")
                speak("WiFi disabled")
            }
        } catch (e: Exception) {
            animateTypewriterText("WiFi control failed")
            openSystemSettings()
        }
        isProcessing = false
    }

    private fun toggleBluetooth(command: String) {
        try {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (command.contains("on")) {
                bluetoothAdapter.enable()
                animateTypewriterText("Bluetooth turned on")
                speak("Bluetooth enabled")
            } else {
                bluetoothAdapter.disable()
                animateTypewriterText("Bluetooth turned off")
                speak("Bluetooth disabled")
            }
        } catch (e: Exception) {
            animateTypewriterText("Bluetooth control failed")
            openSystemSettings()
        }
        isProcessing = false
    }

    private fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS)
        startActivity(intent)
    }

    // ================= BACKEND INTEGRATION =================

    private fun connectToBackend(message: String) {
        animateTypewriterText("Processing...")
        
        val request = ChatRequest(message = message, mode = "voice")
        
        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val aiReply = response.body()!!.reply
                    animateTypewriterText(aiReply) { speak(aiReply) }
                } else {
                    animateTypewriterText("Error: Backend response failed")
                    speak("I encountered an error processing your request.")
                }
                isProcessing = false
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                animateTypewriterText("Connection error. Using offline mode.")
                speak("I am offline. Limited functionality available.")
                isProcessing = false
            }
        })
    }

    // ================= PERMISSIONS =================

    private fun checkPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            val denied = grantResults.indices.filter { grantResults[it] != PackageManager.PERMISSION_GRANTED }
            if (denied.isNotEmpty()) {
                Toast.makeText(this, "Some features may not work without all permissions", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ================= SOUND EFFECTS =================

    private fun playActivationSound() {
        try {
            val mediaPlayer = MediaPlayer.create(this, R.raw.ting_sound)
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            Log.e("JarvisSound", "Sound file not found. Add 'ting_sound.mp3' to res/raw/")
        }
    }

    // ================= NAVIGATION (FIXED) =================

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.navVoice)?.setOnClickListener { updateUIState("Voice") }
        findViewById<ImageView>(R.id.navChat)?.setOnClickListener { updateUIState("Chat") }
        findViewById<ImageView>(R.id.navData)?.setOnClickListener { updateUIState("Data") }
        findViewById<ImageView>(R.id.navSettings)?.setOnClickListener { updateUIState("Settings") }
    }

    private fun updateUIState(state: String) {
        // Reset all nav icons
        listOf(R.id.navVoice, R.id.navChat, R.id.navData, R.id.navSettings).forEach { id ->
            findViewById<ImageView>(id)?.apply {
                alpha = 0.5f
                setColorFilter(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            }
        }

        when (state) {
            "Voice" -> {
                findViewById<ImageView>(R.id.navVoice)?.apply {
                    alpha = 1f
                    setColorFilter(Color.parseColor("#FFD700"))
                }
                supportFragmentManager.findFragmentByTag("CHAT_TAG")?.let {
                    supportFragmentManager.beginTransaction().remove(it).commit()
                }
                findViewById<View>(R.id.homeLayout)?.visibility = View.VISIBLE
                animateTypewriterText("How may I assist you, sir?")
            }
            "Chat" -> {
                findViewById<ImageView>(R.id.navChat)?.apply {
                    alpha = 1f
                    setColorFilter(Color.parseColor("#FFD700"))
                }
                findViewById<View>(R.id.homeLayout)?.visibility = View.GONE
                if (supportFragmentManager.findFragmentByTag("CHAT_TAG") == null) {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .add(R.id.fragmentContainer, ChatFragment(), "CHAT_TAG")
                        .commit()
                }
            }
            "Data" -> Toast.makeText(this, "System Data: ${getSystemInfo()}", Toast.LENGTH_LONG).show()
            "Settings" -> openSystemSettings()
        }
    }

    private fun getSystemInfo(): String {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
        val maxMemory = runtime.maxMemory() / 1048576L
        return "Memory: ${usedMemory}MB / ${maxMemory}MB"
    }

    // ================= LIFECYCLE =================

    override fun onDestroy() {
        super.onDestroy()
        // Clean up all resources
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        outerRingRotation?.cancel()
        corePulseAnimator?.cancel()
        listeningRippleAnimator?.cancel()
        typewriterRunnable?.let { animationHandler.removeCallbacks(it) }
    }

    override fun onPause() {
        super.onPause()
        if (isListening) stopVoiceRecognition()
    }
}
