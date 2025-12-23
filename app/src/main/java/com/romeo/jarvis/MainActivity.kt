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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.romeo.jarvis.fragments.ChatFragment
import com.romeo.jarvis.services.OrbOverlayService // Import fix kiya hai
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
    
    // Overlay Service
    private val OVERLAY_REQUEST_CODE = 102
    private var overlayServiceIntent: Intent? = null
    
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
        Manifest.permission.BLUETOOTH_CONNECT
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize systems in order
        initializeViews()
        createNotificationChannel() // Critical for service
        setupTextToSpeech()
        setupSpeechRecognizer()
        setupAnimations()
        setupNavigation()
        checkAllPermissions()
        
        // Status update
        updateStatus("JARVIS • ONLINE", "#00FF00")
        animateTypewriterText("System initialized. How may I assist you, sir?")
        
        // Start overlay if permission granted
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        }
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

    // ================= OVERLAY SERVICE =================

    private fun checkAllPermissions() {
        // Runtime permissions
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        }
        
        // Overlay permission (critical for floating orb)
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
        }
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Overlay Permission Required")
            .setMessage("JARVIS needs overlay permission to show the floating orb. This allows quick access from any screen.")
            .setPositiveButton("Grant") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                    Uri.parse("package:$packageName"))
                startActivityForResult(intent, OVERLAY_REQUEST_CODE)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Floating orb will not be available without overlay permission", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Overlay permission granted!", Toast.LENGTH_SHORT).show()
                startOverlayService()
            } else {
                Toast.makeText(this, "Overlay permission denied. Floating orb disabled.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startOverlayService() {
        overlayServiceIntent = Intent(this, OrbOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(overlayServiceIntent)
        } else {
            startService(overlayServiceIntent)
        }
        Toast.makeText(this, "Floating orb activated", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlayService() {
        overlayServiceIntent?.let {
            stopService(it)
            Toast.makeText(this, "Floating orb deactivated", Toast.LENGTH_SHORT).show()
        }
    }

    // ================= NOTIFICATION CHANNEL =================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "jarvis_service_channel"
            val channelName = "JARVIS AI Service"
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = "JARVIS AI background voice service"
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
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
        btnMic.animate().scaleX(1f).scaleY(1f).duration = 0
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
                val fade = ObjectAnimator.ofFloat(orbCore, "alpha", 0.8f, 0f).apply {
                    duration = 800
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.RESTART
                }
                playTogether(ripple1, ripple2, fade)
                start()
            }

            btnMic.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300).start()
        }
    }

    private fun stopListeningAnimation() {
        listeningRippleAnimator?.cancel()
        setupOrbAnimations()
        btnMic.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
    }

    private fun updateStatus(status: String, colorHex: String) {
        statusLabel.text = status
        statusLabel.setTextColor(Color.parseColor(colorHex))
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
                orbCore.alpha = 0.5f + (rmsdB / 10).coerceIn(0f, 0.5f)
            }

            // FIXED: Added missing method here
            override fun onBufferReceived(buffer: ByteArray?) {
                // Buffer received logic (optional)
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
            actualCommand.startsWith("open") -> openApp(actualCommand.removePrefix("open").trim())
            actualCommand.startsWith("send message") -> parseSendMessage(actualCommand)
            actualCommand.startsWith("call") -> makeCall(actualCommand.removePrefix("call").trim())
            actualCommand.contains(Regex("play|pause|next|previous")) -> controlMedia(actualCommand)
            actualCommand.contains("volume") -> controlVolume(actualCommand)
            actualCommand.contains(Regex("turn (on|off) wifi")) -> toggleWifi(actualCommand)
            actualCommand.contains(Regex("turn (on|off) bluetooth")) -> toggleBluetooth(actualCommand)
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
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        
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
                speak("I could not find $appName")
            }
        } catch (e: Exception) {
            animateTypewriterText("Error opening app")
            Log.e("JarvisApp", "Error: ${e.message}")
        }
        isProcessing = false
    }

    private fun parseSendMessage(command: String) {
        val regex = """send message to (\w+) (.+)""".toRegex()
        val match = regex.find(command)
        
        if (match != null) {
            sendMessage(match.groupValues[1], match.groupValues[2])
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
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$phoneNumber")
                    putExtra("sms_body", message)
                }
                startActivity(smsIntent)
                animateTypewriterText("Message sent to $contactName")
                speak("Message sent to $contactName")
            } else {
                animateTypewriterText("Contact not found")
                speak("Contact $contactName not found")
            }
        } else {
            animateTypewriterText("Contact permission needed")
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 102) // Fixed param
        }
        isProcessing = false
    }

    private fun getContactsByName(name: String): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )
        
        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            
            if (nameIndex != -1 && numberIndex != -1) {
                while (it.moveToNext()) {
                    val contactName = it.getString(nameIndex).lowercase()
                    if (contactName.contains(name.lowercase())) {
                        contacts.add(Contact(it.getString(nameIndex), it.getString(numberIndex)))
                    }
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
                speak("Contact $contactName not found")
            }
        } else {
            animateTypewriterText("Call permission needed")
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 103) // Fixed param
        }
        isProcessing = false
    }

    private fun controlMedia(command: String) {
        when {
            command.contains("play") -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
            command.contains("pause") -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
            command.contains("next") -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
            command.contains("previous") -> sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        animateTypewriterText("Media command executed")
        speak("Media control activated")
        isProcessing = false
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        try {
            val eventTime = SystemClock.uptimeMillis()
            val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            Log.e("JarvisMedia", "Error: ${e.message}")
        }
    }

    private fun controlVolume(command: String) {
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: return
        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: return
        
        when {
            command.contains("up") || command.contains("increase") -> {
                val newVolume = (current + 2).coerceAtMost(maxVolume)
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                animateTypewriterText("Volume: $newVolume/$maxVolume")
                speak("Volume up")
            }
            command.contains("down") || command.contains("decrease") -> {
                val newVolume = (current - 2).coerceAtLeast(0)
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                animateTypewriterText("Volume: $newVolume/$maxVolume")
                speak("Volume down")
            }
            command.contains("max") -> {
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                animateTypewriterText("Maximum volume")
                speak("Volume maximum")
            }
            command.contains("min") || command.contains("mute") -> {
                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                animateTypewriterText("Muted")
                speak("Volume muted")
            }
        }
        isProcessing = false
    }

    private fun toggleWifi(command: String) {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val enable = command.contains("on")
            wifiManager.isWifiEnabled = enable
            animateTypewriterText("WiFi ${if(enable) "enabled" else "disabled"}")
            speak("WiFi ${if(enable) "enabled" else "disabled"}")
        } catch (e: SecurityException) {
            animateTypewriterText("System permission required")
            openSystemSettings()
        } catch (e: Exception) {
            Log.e("JarvisWiFi", "Error: ${e.message}")
        }
        isProcessing = false
    }

    private fun toggleBluetooth(command: String) {
        try {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            val enable = command.contains("on")
            if (enable) bluetoothAdapter?.enable() else bluetoothAdapter?.disable()
            animateTypewriterText("Bluetooth ${if(enable) "enabled" else "disabled"}")
            speak("Bluetooth ${if(enable) "enabled" else "disabled"}")
        } catch (e: SecurityException) {
            animateTypewriterText("System permission required")
            openSystemSettings()
        } catch (e: Exception) {
            Log.e("JarvisBluetooth", "Error: ${e.message}")
        }
        isProcessing = false
    }

    private fun openSystemSettings() {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    // ================= BACKEND INTEGRATION =================

    private fun connectToBackend(message: String) {
        animateTypewriterText("Processing...")
        
        RetrofitClient.instance.chatWithAI(ChatRequest(message, "voice")).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val aiReply = response.body()!!.reply
                    animateTypewriterText(aiReply) { speak(aiReply) }
                } else {
                    animateTypewriterText("Backend error")
                    speak("I encountered an error")
                }
                isProcessing = false
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                animateTypewriterText("Offline mode")
                speak("I am offline")
                Log.e("JarvisBackend", "Error: ${t.message}")
                isProcessing = false
            }
        })
    }

    // ================= SOUND EFFECTS =================

    private fun playActivationSound() {
        try {
            val mediaPlayer = MediaPlayer.create(this, R.raw.ting_sound)
            mediaPlayer.setOnCompletionListener { it.release() }
            mediaPlayer.start()
        } catch (e: Exception) {
            // Fallback system beep
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100).startTone(ToneGenerator.TONE_PROP_BEEP)
            Log.e("JarvisSound", "Add 'ting_sound.mp3' to res/raw/")
        }
    }

    // ================= NAVIGATION =================

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.navVoice)?.setOnClickListener { updateUIState("Voice") }
        findViewById<ImageView>(R.id.navChat)?.setOnClickListener { updateUIState("Chat") }
        findViewById<ImageView>(R.id.navData)?.setOnClickListener { updateUIState("Data") }
        findViewById<ImageView>(R.id.navSettings)?.setOnClickListener { updateUIState("Settings") }
    }

    private fun updateUIState(state: String) {
        listOf(R.id.navVoice, R.id.navChat, R.id.navData, R.id.navSettings).forEach { id ->
            findViewById<ImageView>(id)?.apply {
                alpha = 0.5f
                setColorFilter(Color.WHITE)
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
                // FIXED: homeLayout ID doesn't exist, using orbContainer and txtPrompt
                orbContainer.visibility = View.VISIBLE
                txtPrompt.visibility = View.VISIBLE
                
                animateTypewriterText("How may I assist you, sir?")
            }
            "Chat" -> {
                findViewById<ImageView>(R.id.navChat)?.apply {
                    alpha = 1f
                    setColorFilter(Color.parseColor("#FFD700"))
                }
                // FIXED: homeLayout ID doesn't exist, hiding orbContainer
                orbContainer.visibility = View.GONE
                txtPrompt.visibility = View.GONE

                if (supportFragmentManager.findFragmentByTag("CHAT_TAG") == null) {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        // FIXED: fragmentContainer ID doesn't exist, using mainContent
                        .add(R.id.mainContent, ChatFragment(), "CHAT_TAG")
                        .commit()
                }
            }
            "Data" -> Toast.makeText(this, getSystemInfo(), Toast.LENGTH_LONG).show()
            "Settings" -> openSystemSettings()
        }
    }

    private fun getSystemInfo(): String {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
        val maxMemory = runtime.maxMemory() / 1048576L
        val battery = getBatteryLevel()
        return "Memory: ${usedMemory}MB/${maxMemory}MB | Battery: $battery%"
    }
    
    private fun getBatteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    // ================= LIFECYCLE =================

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        outerRingRotation?.cancel()
        corePulseAnimator?.cancel()
        listeningRippleAnimator?.cancel()
        typewriterRunnable?.let { animationHandler.removeCallbacks(it) }
        stopOverlayService()
    }

    override fun onPause() {
        super.onPause()
        if (isListening) stopVoiceRecognition()
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission missing. Floating orb disabled.", Toast.LENGTH_SHORT).show()
        }
    }
}
