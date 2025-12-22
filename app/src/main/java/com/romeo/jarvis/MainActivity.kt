package com.romeo.jarvis

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.romeo.jarvis.services.JarvisService
import com.romeo.jarvis.utils.ChatRequest
import com.romeo.jarvis.utils.ChatResponse
import com.romeo.jarvis.utils.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    // UI Components (Naye XML IDs ke mutabiq)
    private lateinit var aiOrb: ImageView
    private lateinit var btnMic: ImageButton
    private lateinit var txtPrompt: TextView
    private lateinit var statusLabel: TextView // "ONLINE" label ke liye
    
    // Layout Containers
    private lateinit var homeLayout: RelativeLayout
    private lateinit var fragmentContainer: FrameLayout

    // Navigation Icons
    private lateinit var navVoice: ImageView
    private lateinit var navChat: ImageView
    private lateinit var navData: ImageView
    private lateinit var navSettings: ImageView

    // Animation Object
    private var rotateAnim: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Views Initialization
        initializeViews()

        // 2. Start Iron Man Rotation Animation
        startOrbAnimation()

        // 3. Permissions Check (Tumhara purana logic)
        checkAndRequestPermissions()

        // 4. Navigation Clicks Setup
        setupNavigation()

        // 5. Mic Button Logic (Touch & Animation)
        setupMicButton()
    }

    private fun initializeViews() {
        // Main Display
        aiOrb = findViewById(R.id.aiOrb)
        btnMic = findViewById(R.id.btnMic)
        txtPrompt = findViewById(R.id.txtPrompt)
        statusLabel = findViewById(R.id.statusLabel) // Agar XML me id nahi di, to wahan id add karlena
        
        // Containers
        homeLayout = findViewById(R.id.homeLayout)
        fragmentContainer = findViewById(R.id.fragmentContainer)

        // Nav Icons
        navVoice = findViewById(R.id.navVoice)
        navChat = findViewById(R.id.navChat)
        navData = findViewById(R.id.navData)
        navSettings = findViewById(R.id.navSettings)
    }

    // ================= ANIMATIONS =================

    private fun startOrbAnimation() {
        // Ye circle ko infinite ghumata rahega
        rotateAnim = ObjectAnimator.ofFloat(aiOrb, "rotation", 0f, 360f).apply {
            duration = 8000 // 8 seconds per round (Slow & Techy)
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    // ================= MIC & TOUCH LOGIC =================

    private fun setupMicButton() {
        btnMic.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Button dabane par thoda bada ho (Scale Up)
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).start()
                    
                    statusLabel.text = "JARVIS • LISTENING"
                    statusLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                    txtPrompt.text = "Listening..."
                    
                    // Service start (Background processing)
                    startJarvisService()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Button wapis normal ho (Scale Down)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    
                    statusLabel.text = "JARVIS • PROCESSING"
                    statusLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                    
                    // Backend Request
                    connectToBackend("Hello Jarvis, testing new UI!") 
                    true
                }
                else -> false
            }
        }
    }

    // ================= NAVIGATION SYSTEM =================

    private fun setupNavigation() {
        navVoice.setOnClickListener { updateUIState("Voice") }
        navChat.setOnClickListener { updateUIState("Chat") }
        navData.setOnClickListener { updateUIState("Data") }
        navSettings.setOnClickListener { updateUIState("Settings") }
    }

    private fun updateUIState(state: String) {
        // Pehle sab icons ko dim (transparent) karo
        navVoice.alpha = 0.5f
        navChat.alpha = 0.5f
        navData.alpha = 0.5f
        navSettings.alpha = 0.5f

        // Colors reset (Default White)
        val defaultColor = ContextCompat.getColor(this, android.R.color.white)
        val goldColor = 0xFFD700.toInt() // Gold Color manually or resource se le lo

        navVoice.setColorFilter(defaultColor)
        navChat.setColorFilter(defaultColor)
        navData.setColorFilter(defaultColor)
        navSettings.setColorFilter(defaultColor)

        // Ab active tab ko highlight karo
        when (state) {
            "Voice" -> {
                navVoice.alpha = 1f
                navVoice.setColorFilter(goldColor)
                homeLayout.visibility = View.VISIBLE // Orb wapis dikhao
                txtPrompt.text = "How may I assist you, sir?"
            }
            "Chat" -> {
                navChat.alpha = 1f
                navChat.setColorFilter(goldColor)
                homeLayout.visibility = View.GONE // Orb chupao taake chat dikhe
                // Yahan tum ChatFragment load karoge (Future Step)
                Toast.makeText(this, "Opening Secure Chat...", Toast.LENGTH_SHORT).show()
            }
            "Data" -> {
                navData.alpha = 1f
                navData.setColorFilter(goldColor)
                Toast.makeText(this, "Accessing System Data...", Toast.LENGTH_SHORT).show()
            }
            "Settings" -> {
                navSettings.alpha = 1f
                navSettings.setColorFilter(goldColor)
                Toast.makeText(this, "Opening Configuration...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ================= BACKEND CONNECTION =================
    
    private fun connectToBackend(message: String) {
        txtPrompt.text = "Thinking..." // Update Prompt
        
        val request = ChatRequest(message = message, mode = "voice")
        
        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val aiReply = response.body()!!.reply
                    
                    // UI Update
                    txtPrompt.text = aiReply
                    statusLabel.text = "JARVIS • ONLINE"
                    statusLabel.setTextColor(0xFF00FF00.toInt()) // Green Color
                    
                    Log.d("JarvisAI", "Reply: $aiReply")
                    // TTS logic yahan ayegi
                } else {
                    txtPrompt.text = "Server Error: ${response.code()}"
                    statusLabel.text = "JARVIS • ERROR"
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                txtPrompt.text = "Connection Failed"
                statusLabel.text = "JARVIS • OFFLINE"
                Log.e("JarvisAI", "Error: ${t.message}")
            }
        })
    }

    // ================= PERMISSIONS & SERVICE (SAME AS BEFORE) =================

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        } else {
            checkAdvancedPermissions()
        }
    }

    private fun checkAdvancedPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
    }

    private fun startJarvisService() {
        val intent = Intent(this, JarvisService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            checkAdvancedPermissions()
        }
    }
}
