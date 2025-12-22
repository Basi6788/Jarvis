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
import com.romeo.jarvis.fragments.ChatFragment
import com.romeo.jarvis.services.JarvisService
import com.romeo.jarvis.utils.ChatRequest
import com.romeo.jarvis.utils.ChatResponse
import com.romeo.jarvis.utils.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var aiOrb: ImageView
    private lateinit var btnMic: ImageButton
    private lateinit var txtPrompt: TextView
    private lateinit var statusLabel: TextView

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

        // 1. Initialize Views (IDs check kar lena XML se match honi chahiye)
        try {
            initializeViews()
            
            // 2. Start Animations
            startOrbAnimation()

            // 3. Setup Navigation Logic
            setupNavigation()

            // 4. Setup Mic Button
            setupMicButton()

            // 5. Check Permissions
            checkAndRequestPermissions()
            
        } catch (e: Exception) {
            Log.e("JarvisMain", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing Jarvis", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeViews() {
        // Main Display
        aiOrb = findViewById(R.id.aiOrb)
        btnMic = findViewById(R.id.btnMic)
        txtPrompt = findViewById(R.id.txtPrompt)
        
        // Note: XML me agar statusLabel ka ID nahi hai to add karlena, filhal main avoid crash ke liye check laga raha hun
        statusLabel = findViewById(R.id.statusLabel) ?: TextView(this) 
        
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
        if (::aiOrb.isInitialized) {
            rotateAnim = ObjectAnimator.ofFloat(aiOrb, "rotation", 0f, 360f).apply {
                duration = 8000 // Slow rotation
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    // ================= MIC LOGIC =================

    private fun setupMicButton() {
        btnMic.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Button Scale Up Animation
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).start()
                    
                    statusLabel.text = "JARVIS • LISTENING"
                    statusLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                    txtPrompt.text = "Listening..."
                    
                    // Service Start
                    startJarvisService()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Button Scale Down
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    
                    statusLabel.text = "JARVIS • PROCESSING"
                    statusLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                    
                    // Backend Request (Test Message)
                    connectToBackend("Hello Jarvis") 
                    true
                }
                else -> false
            }
        }
    }

    // ================= NAVIGATION SYSTEM (FIXED) =================

    private fun setupNavigation() {
        navVoice.setOnClickListener { updateUIState("Voice") }
        navChat.setOnClickListener { updateUIState("Chat") }
        navData.setOnClickListener { updateUIState("Data") }
        navSettings.setOnClickListener { updateUIState("Settings") }
    }

    private fun updateUIState(state: String) {
        // 1. Reset Icons Alpha (Dim look)
        navVoice.alpha = 0.5f
        navChat.alpha = 0.5f
        navData.alpha = 0.5f
        navSettings.alpha = 0.5f

        // 2. Reset Icons Color (White)
        val whiteColor = ContextCompat.getColor(this, android.R.color.white)
        val goldColor = 0xFFD700.toInt() // Gold

        navVoice.setColorFilter(whiteColor)
        navChat.setColorFilter(whiteColor)
        navData.setColorFilter(whiteColor)
        navSettings.setColorFilter(whiteColor)

        // 3. Handle States
        when (state) {
            "Voice" -> {
                navVoice.alpha = 1f
                navVoice.setColorFilter(goldColor)
                
                // Show Home, Remove Chat Fragment
                homeLayout.visibility = View.VISIBLE
                val fragment = supportFragmentManager.findFragmentByTag("CHAT_TAG")
                if (fragment != null) {
                    supportFragmentManager.beginTransaction().remove(fragment).commit()
                }
                txtPrompt.text = "How may I assist you, sir?"
                statusLabel.text = "JARVIS • ONLINE"
            }
            "Chat" -> {
                navChat.alpha = 1f
                navChat.setColorFilter(goldColor)
                
                // Hide Home, Show Chat Fragment
                homeLayout.visibility = View.GONE
                
                // Check if fragment is already added to avoid overlap
                val existingFragment = supportFragmentManager.findFragmentByTag("CHAT_TAG")
                if (existingFragment == null) {
                    supportFragmentManager.beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .add(R.id.fragmentContainer, ChatFragment(), "CHAT_TAG")
                        .commit()
                }
            }
            "Data" -> {
                navData.alpha = 1f
                navData.setColorFilter(goldColor)
                Toast.makeText(this, "System Data Access: Denied", Toast.LENGTH_SHORT).show()
            }
            "Settings" -> {
                navSettings.alpha = 1f
                navSettings.setColorFilter(goldColor)
                Toast.makeText(this, "Opening Settings...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ================= BACKEND CONNECTION =================
    
    private fun connectToBackend(message: String) {
        txtPrompt.text = "Thinking..."
        
        val request = ChatRequest(message = message, mode = "voice")
        
        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val aiReply = response.body()!!.reply
                    
                    txtPrompt.text = aiReply
                    statusLabel.text = "JARVIS • ONLINE"
                    statusLabel.setTextColor(0xFF00FF00.toInt()) // Green
                    
                } else {
                    txtPrompt.text = "Error: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                txtPrompt.text = "Connection Failed"
                statusLabel.text = "JARVIS • OFFLINE"
            }
        })
    }

    // ================= PERMISSIONS & SERVICE =================

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
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
            // Toast remove kar diya taake bar bar popup na aye
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
