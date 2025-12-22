package com.romeo.jarvis.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

object SystemController {

    // --- Termux Command Execution ---
    // Boss, ye function seedha Termux ko order deta hai
    fun runTermuxCommand(context: Context, command: String) {
        val intent = Intent("com.termux.action.RUN_COMMAND")
        intent.setClassName("com.termux", "com.termux.app.TermuxService")
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
        
        // Ye command ko execute karega aur background mein rakhega
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true) 
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0") 

        try {
            context.startService(intent)
            Log.d("JarvisController", "Command sent to Termux: $command")
        } catch (e: Exception) {
            Log.e("JarvisController", "Termux not found or permission missing")
        }
    }

    // --- File System Control ---
    // Boss, ye kisi bhi file ya folder ko delete kar sakta hai (Handle with care!)
    fun deleteFileOrFolder(path: String): String {
        val file = File(path)
        return if (file.exists()) {
            val deleted = file.deleteRecursively() // Folder ke andar sab kuch uda dega
            if (deleted) "File deleted successfully, Sir." else "Unable to delete file."
        } else {
            "File not found at $path"
        }
    }

    // --- Create File/Code ---
    // Agar aap bolen "Jarvis write python code for calculator", ye file bana dega
    fun writeCodeToFile(fileName: String, content: String) {
        val path = "/sdcard/JarvisProjects/$fileName"
        try {
            val file = File(path)
            file.parentFile?.mkdirs() // Folder nahi hai tu bana dega
            file.writeText(content)
            Log.d("JarvisController", "Code written to $path")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

