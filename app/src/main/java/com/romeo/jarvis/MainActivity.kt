package com.romeo.jarvis

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.romeo.jarvis.services.JarvisService

class MainActivity : AppCompatActivity() {

    private lateinit var micBtn: ImageButton
    private lateinit var statusText: TextView

    private lateinit var idleAnim: Animation
    private lateinit var listenAnim: Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI - Removed aiAvatar reference
        micBtn = findViewById(R.id.btnMic)
        statusText = findViewById(R.id.greetingText)

        // Animations
        idleAnim = AnimationUtils.loadAnimation(this, R.anim.orb_idle)
        listenAnim = AnimationUtils.loadAnimation(this, R.anim.orb_listening)

        startIdleState()

        // ================= PERMISSIONS =================
        requestPermissions()

        // Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        // All Files Access (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        // ================= MIC BUTTON LOGIC =================
        micBtn.setOnTouchListener { _, event ->
            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    startListeningState()
                    startJarvisService()
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopListeningState()
                    true
                }

                else -> false
            }
        }
    }

    // ================= PERMISSIONS =================

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ),
            101
        )
    }

    // ================= STATES =================

    private fun startIdleState() {
        statusText.text = "Touch mic to talk with Jarvis"
    }

    private fun startListeningState() {
        statusText.text = "Listening..."
    }

    private fun stopListeningState() {
        startIdleState()
    }

    // ================= SERVICE =================

    private fun startJarvisService() {
        Toast.makeText(this, "Jarvis online", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, JarvisService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // ================= PERMISSIONS RESULT HANDLING =================

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissions granted, proceed with your actions
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}