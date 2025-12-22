package com.romeo.jarvis.utils

import okhttp3.*
import java.io.IOException

object XTTSClient {

    private val client = OkHttpClient()

    fun speak(text: String, onAudio: (ByteArray) -> Unit) {
        val json = """
        {
          "text": "$text",
          "speaker": "venom",
          "language": "ur"
        }
        """.trimIndent()

        val req = Request.Builder()
            .url("http://127.0.0.1:5002/tts") // XTTS local server
            .post(RequestBody.create(MediaType.parse("application/json"), json))
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, res: Response) {
                res.body()?.bytes()?.let { onAudio(it) }
            }
        })
    }
}