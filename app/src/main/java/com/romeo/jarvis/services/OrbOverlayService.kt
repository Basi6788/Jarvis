package com.romeo.jarvis.services

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.ActivityCompat
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

    // States
    private var isWakeWordMode = true // True = Listening for "Jarvis", False = Listening for commands
    private val WAKE_WORD = "jarvis"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // Foreground Service Notification (Android Requirement)
        startForeground(1, createNotification())

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        textToSpeech = TextToSpeech(this, this)
        
        initializeOrb()
        setupSpeechRecognizer()
    }

    // --- ORB UI SETUP ---
    private fun initializeOrb() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Layout Inflate
        orbView = LayoutInflater.from(this).inflate(R.layout.layout_orb_overlay, null)
        orbIcon = orbView.findViewById(R.id.orbIcon)

        // Initial Params: HIDDEN (Invisible lekin background me active)
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

        // Position: Bottom Center (Nav bar se thora oopar)
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 120 // Pixels from bottom
        
        // Shuru me Orb Gayab rahay ga
        orbView.visibility = View.GONE 
        windowManager.addView(orbView, params)
    }

    private fun showOrb() {
        Handler(Looper.getMainLooper()).post {
            orbView.visibility = View.VISIBLE
            // Animation: Pop up
            orbIcon.scaleX = 0f
            orbIcon.scaleY = 0f
            orbIcon.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
        }
    }

    private fun hideOrb() {
        Handler(Looper.getMainLooper()).post {
            orbIcon.animate().scaleX(0f).scaleY(0f).setDuration(300).withEndAction {
                orbView.visibility = View.GONE
            }.start()
        }
    }

    // --- SPEECH RECOGNITION LOOP ---
    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK") // Urdu + English Mix
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // Agar Orb visible hai to usay animate karo
                if (!isWakeWordMode) {
                    val scale = 1f + (rmsdB / 20f)
                    orbIcon.scaleX = scale
                    orbIcon.scaleY = scale
                }
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            
            override fun onError(error: Int) {
                // Loop: Agar error aye to dobara sunna shuru karo
                restartListening()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0].lowercase()
                    handleVoiceInput(text)
                } else {
                    restartListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Wake word jaldi pakarne ke liye partial results check karo
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0].lowercase()
                    if (isWakeWordMode && text.contains(WAKE_WORD)) {
                        speechRecognizer?.stopListening() // Roko taake final result process ho
                        activateAssistant()
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Start Loop
        speechRecognizer?.startListening(speechIntent)
    }

    private fun restartListening() {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                speechRecognizer?.startListening(speechIntent)
            } catch (e: Exception) { e.printStackTrace() }
        }, 500)
    }

    // --- LOGIC HANDLING ---
    
    private fun handleVoiceInput(text: String) {
        if (isWakeWordMode) {
            if (text.contains(WAKE_WORD)) {
                activateAssistant()
            } else {
                restartListening() // Sahi lafz nahi tha, wapis suno
            }
        } else {
            // Command Mode
            processCommand(text)
        }
    }

    private fun activateAssistant() {
        isWakeWordMode = false // Ab command sunni hai
        showOrb() // Orb zahir karo
        speak("Ji boss?") // Feedback
        restartListening() // Ab command sunnay ke liye ready
    }

    private fun processCommand(cmd: String) {
        Log.d("Jarvis", "Command: $cmd")
        
        // Command execute karne ke baad wapis sleep mode me jana hai
        val shouldSleep = true 

        when {
            // 1. APPS
            cmd.contains("open") || cmd.contains("kholo") || cmd.contains("chalao") -> {
                val appName = cmd.replace("open", "").replace("kholo", "").replace("chalao", "").replace("jarvis", "").trim()
                if (openApp(appName)) speak("Opening $appName") else speak("$appName nahi mili")
            }
            
            // 2. FLASHLIGHT (Torch)
            cmd.contains("torch") || cmd.contains("flashlight") -> {
                toggleFlashlight(cmd.contains("on") || cmd.contains("jalao"))
            }

            // 3. WIFI
            cmd.contains("wifi") -> {
                toggleWifi(cmd.contains("on") || cmd.contains("active"))
            }

            // 4. BLUETOOTH
            cmd.contains("bluetooth") -> {
                toggleBluetooth(cmd.contains("on") || cmd.contains("connect"))
            }

            // 5. CALLS (Urdu/Eng)
            cmd.contains("call") || cmd.contains("milao") -> {
                val name = cmd.replace("call", "").replace("milao", "").replace("ko", "").trim()
                makeCall(name)
            }

            // 6. SMS
            cmd.contains("message") || cmd.contains("msg") -> {
                // Simple parser logic for now
                speak("Message feature activate kar raha hoon")
                // Yahan SMS logic ayegi (complex parsing required)
            }

            // 7. TIME
            cmd.contains("time") || cmd.contains("waqt") -> {
                val time = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                speak("Abhi $time bajay hain")
            }
            
            // 8. CLOSE / CANCEL
            cmd.contains("band karo") || cmd.contains("cancel") || cmd.contains("nothing") -> {
                speak("Theek hai")
            }

            else -> {
                speak("Samajh nahi aya, dobara bolain")
                restartListening() // Sleep mode me mat jao, dobara suno
                return 
            }
        }

        if (shouldSleep) {
            Handler(Looper.getMainLooper()).postDelayed({
                isWakeWordMode = true
                hideOrb()
                restartListening()
            }, 3000) // 3 second baad gayab
        }
    }

    // --- UTILITIES (Actions) ---

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

    private fun toggleFlashlight(state: Boolean) {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, state)
            speak(if (state) "Torch on kar di" else "Torch band")
        } catch (e: Exception) { speak("Torch access nahi ho rahi") }
    }

    private fun toggleWifi(state: Boolean) {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            // Android 10+ me ye direct kaam nahi karta, Panel kholna parta hai
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                wifiManager.isWifiEnabled = state
                speak("Wifi ${if(state) "on" else "off"} kar diya")
            } else {
                startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                speak("Internet settings khol di hain")
            }
        } catch (e: Exception) { speak("Error aya hai") }
    }

    private fun toggleBluetooth(state: Boolean) {
        // Permission check zaroori hai
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (state) adapter?.enable() else adapter?.disable()
            speak("Bluetooth ${if(state) "on" else "off"} ho gaya")
        } else {
            speak("Bluetooth permission nahi hai")
        }
    }

    private fun makeCall(name: String) {
        // Contacts search logic (simplified)
        // Real project me ContentResolver use kar ke number nikalna parega
        speak("$name ko call mila raha hoon")
        // Demo purpose ke liye Dial action:
        val intent = Intent(Intent.ACTION_DIAL)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    // --- TTS & INIT ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.US
            textToSpeech?.setPitch(0.7f) // Thora bhari awaz
        }
    }

    private fun speak(text: String) {
        audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_speak")
    }
    
    private fun createNotification(): Notification {
        val channelId = "JarvisService"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Jarvis Background", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis Active")
            .setContentText("Listening for 'Jarvis'...")
            .setSmallIcon(R.drawable.ic_mic) // Ensure you have this icon
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
        if (::windowManager.isInitialized) windowManager.removeView(orbView)
    }
}
