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

        val mediaType = "application/json".toMediaType()
        val body = json.toRequestBody(mediaType)

        val req = Request.Builder()
            .url("http://127.0.0.1:5002/tts") // XTTS local server
            .post(body)
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace() // Handle the error
            }

            override fun onResponse(call: Call, res: Response) {
                if (res.isSuccessful) {
                    res.body()?.bytes()?.let { onAudio(it) }
                } else {
                    // Handle the failure case
                    println("Error: ${res.code()}")
                }
            }
        })
    }
}