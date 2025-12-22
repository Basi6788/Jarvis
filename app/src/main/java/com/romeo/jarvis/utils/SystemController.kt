package com.romeo.jarvis.utils

import android.content.Context
import android.content.Intent
import java.io.File

object SystemController {
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

    fun deleteFile(path: String) {
        try {
            File(path).deleteRecursively()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
