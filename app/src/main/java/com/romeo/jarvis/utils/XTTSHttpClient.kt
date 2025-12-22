package com.romeo.jarvis.utils

import okhttp3.*

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

        val req = Request.Builder()
            .url(url) // e.g. https://romeo-backend.vercel.app/api/tts
            .post(RequestBody.create("application/json".toMediaType(), json))
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) = onFail()
            override fun onResponse(call: Call, res: Response) {
                if (!res.isSuccessful) { onFail(); return }
                onBytes(res.body?.bytes() ?: run { onFail(); return })
            }
        })
    }
}