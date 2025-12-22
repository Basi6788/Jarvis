package com.romeo.jarvis.utils

import java.util.Locale

sealed class JarvisCommand {
    data class Call(val name: String, val viaWhatsApp: Boolean = false) : JarvisCommand()
    data class OpenApp(val app: String) : JarvisCommand()
    object PlayMusic : JarvisCommand()
    object PauseMusic : JarvisCommand()
    object NextMusic : JarvisCommand()
    object PrevMusic : JarvisCommand()
    object StopListening : JarvisCommand()
    object Unknown : JarvisCommand()
}

object OfflineCommandParser {

    fun parse(input: String): JarvisCommand {
        val text = input.lowercase(Locale.getDefault()).trim()

        // ================= CALL =================
        // "jarvis call ali"
        // "call ammi"
        // "jarvis call ahmed on whatsapp"
        val callRegex = Regex("call\\s+(\\w+)(\\s+on\\s+whatsapp)?")
        val callMatch = callRegex.find(text)
        if (callMatch != null) {
            val name = callMatch.groupValues[1]
            val viaWhatsapp = callMatch.groupValues[2].isNotEmpty()
            return JarvisCommand.Call(name, viaWhatsapp)
        }

        // ================= OPEN APP =================
        // "open whatsapp", "kholo camera"
        if (text.contains("open") || text.contains("kholo")) {
            val app = text.replace("open", "")
                .replace("kholo", "")
                .trim()
            return JarvisCommand.OpenApp(app)
        }

        // ================= MUSIC =================
        if (text.contains("play music") || text.contains("music chalao")) {
            return JarvisCommand.PlayMusic
        }

        if (text.contains("pause music") || text.contains("music roko")) {
            return JarvisCommand.PauseMusic
        }

        if (text.contains("next song") || text.contains("agla gana")) {
            return JarvisCommand.NextMusic
        }

        if (text.contains("previous song") || text.contains("pichla gana")) {
            return JarvisCommand.PrevMusic
        }

        // ================= STOP =================
        if (text.contains("band ho jao") || text.contains("stop listening")) {
            return JarvisCommand.StopListening
        }

        return JarvisCommand.Unknown
    }
}