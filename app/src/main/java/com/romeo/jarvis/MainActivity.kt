package com.romeo.jarvis

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.romeo.jarvis.services.JarvisService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ), 1)

        findViewById<ImageButton>(R.id.btnMic).setOnClickListener {
            Toast.makeText(this, "Starting Jarvis...", Toast.LENGTH_SHORT).show()
            startForegroundService(Intent(this, JarvisService::class.java))
        }
    }
}
