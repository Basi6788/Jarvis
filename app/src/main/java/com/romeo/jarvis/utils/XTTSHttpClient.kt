package com.romeo.jarvis.utils

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object XTTSHttpClient {
    private val client = OkHttpClient()

    fun fetchWav(
        url: String,
        text: String,
        lang: String = "ur",
        onBytes: (ByteArray) -> Unit,
        onFail: () -> Unit
    ) {
        val json = """
          {"text":"$text","language":"$lang","speaker":"venom"}
        """.trimIndent()

        val mediaType = "application/json".toMediaType()  // Correct MediaType
        val body = json.toRequestBody(mediaType)  // Correct RequestBody

        val req = Request.Builder()
            .url(url) // e.g. https://romeo-backend.vercel.app/api/tts
            .post(body)
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace() // Log the error for debugging
                onFail()
            }

            override fun onResponse(call: Call, res: Response) {
                if (!res.isSuccessful) {
                    println("Error: ${res.code}") // Fix: Use response.code instead of code()
                    onFail()
                    return
                }
                // Only proceed if body is not null
                res.body?.bytes()?.let { 
                    onBytes(it)
                } ?: onFail()
            }
        })
    }
}