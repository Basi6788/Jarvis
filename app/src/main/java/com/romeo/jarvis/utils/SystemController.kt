package com.romeo.jarvis.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

object SystemController {
    fun runTermuxCommand(context: Context, command: String) {
        val intent = Intent("com.termux.action.RUN_COMMAND")
        intent.setClassName("com.termux", "com.termux.app.TermuxService")
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/sh")
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true) 
        intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0") 
        try {
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteFileOrFolder(path: String): String {
        val file = File(path)
        return if (file.exists()) {
            file.deleteRecursively()
            "Deleted $path"
        } else {
            "File not found"
        }
    }

    fun writeCodeToFile(fileName: String, content: String) {
        val path = "/sdcard/JarvisProjects/$fileName"
        try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
