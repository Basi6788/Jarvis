package com.romeo.jarvis

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Basic Permissions
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        ActivityCompat.requestPermissions(this, permissions, 101)

        // 2. Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        // 3. Storage Permission (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
             startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        }

        findViewById<ImageButton>(R.id.btnMic).setOnClickListener {
            Toast.makeText(this, "Jarvis Activated", Toast.LENGTH_SHORT).show()
            startForegroundService(Intent(this, com.romeo.jarvis.services.JarvisService::class.java))
        }
    }
}
