package com.romeo.jarvis.utils

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import java.io.File

object SystemController {

    // ================= TERMUX =================
    fun runTermuxCommand(context: Context, command: String) {
        try {
            val intent = Intent("com.termux.action.RUN_COMMAND")
            intent.setClassName("com.termux", "com.termux.app.TermuxService")
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ================= FILE CONTROL =================
    fun deleteFile(path: String) {
        try {
            File(path).deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ================= CALL =================
    fun callNumber(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:$number")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    // ================= WHATSAPP CALL =================
    fun callWhatsApp(context: Context, number: String) {
        val uri = Uri.parse("https://wa.me/$number")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.whatsapp")
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    // ================= OPEN APP =================
    fun openApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.let {
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(it)
        }
    }

    // ================= MUSIC =================
    fun playMusic(context: Context) {
        val intent = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun musicControl(context: Context, keyCode: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        )
        audio.dispatchMediaKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        )
    }
}