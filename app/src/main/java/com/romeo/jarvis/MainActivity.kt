package com.romeo.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.romeo.jarvis.services.JarvisService
// Apni Utils files import karein (Retrofit wali)
import com.romeo.jarvis.utils.RetrofitClient
import com.romeo.jarvis.utils.ChatRequest
import com.romeo.jarvis.utils.ChatResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var orbGlow: ImageView
    private lateinit var orbCore: ImageView
    private lateinit var micBtn: ImageButton
    private lateinit var statusText: TextView

    private lateinit var idleAnim: Animation
    private lateinit var listenAnim: Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Views setup
        orbGlow = findViewById(R.id.orbGlow)
        orbCore = findViewById(R.id.orbCore)
        micBtn = findViewById(R.id.btnMic)
        statusText = findViewById(R.id.greetingText)

        // Animations setup
        idleAnim = AnimationUtils.loadAnimation(this, R.anim.orb_idle)
        listenAnim = AnimationUtils.loadAnimation(this, R.anim.orb_listening)

        startIdleState()

        // Permissions check (Sequence mein taake black screen na aye)
        checkAndRequestPermissions()

        // ================= MIC BUTTON LOGIC =================
        micBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startListeningState()
                    // Service start karein (Background processing ke liye)
                    startJarvisService()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopListeningState()
                    
                    // Jese hi button chora, Backend ko request bhejein
                    // Filhal testing ke liye "Hello Jarvis" bhej rahe hain
                    // Baad mein yahan Voice-to-Text ka result ayega
                    connectToBackend("Hello Jarvis, main online hun!") 
                    true
                }
                else -> false
            }
        }
    }

    // ================= BACKEND CONNECTION (NEW) =================
    
    private fun connectToBackend(message: String) {
        statusText.text = "Thinking..."
        
        val request = ChatRequest(message = message, mode = "voice")
        
        RetrofitClient.instance.chatWithAI(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val aiReply = response.body()!!.reply
                    
                    // UI Update karein
                    statusText.text = aiReply
                    Log.d("JarvisAI", "Reply: $aiReply")
                    
                    // Yahan aap TextToSpeech (TTS) laga sakte hain taake wo bol kar jawab de
                    Toast.makeText(applicationContext, aiReply, Toast.LENGTH_LONG).show()
                } else {
                    statusText.text = "Server Error: ${response.code()}"
                }
            }

            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                statusText.text = "Connection Failed"
                Log.e("JarvisAI", "Error: ${t.message}")
            }
        })
    }

    // ================= PERMISSIONS (FIXED) =================

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        // Basic Permissions
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }
        // Storage Permissions (Android version ke hisab se)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        } else {
            // Agar basic permissions mil gayi hain, tab advanced check karein
            checkAdvancedPermissions()
        }
    }

    private fun checkAdvancedPermissions() {
        // Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return // Return kar dein taake aik waqt me aik hi screen khule
        }

        // All Files Access (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
    }

    // ================= STATES =================

    private fun startIdleState() {
        orbGlow.clearAnimation()
        orbCore.clearAnimation()
        orbGlow.startAnimation(idleAnim)
        orbCore.startAnimation(idleAnim)
        // Greeting wapis set na karein agar AI soch raha hai
        if (statusText.text == "Listening...") {
             statusText.text = "Touch to talk"
        }
    }

    private fun startListeningState() {
        orbGlow.clearAnimation()
        orbCore.clearAnimation()
        orbGlow.startAnimation(listenAnim)
        orbCore.startAnimation(listenAnim)
        statusText.text = "Listening..."
    }

    private fun stopListeningState() {
        startIdleState()
    }

    // ================= SERVICE =================

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
            // Jab basic permission mil jayen, tab advanced check karo
            checkAdvancedPermissions()
        }
    }
}
