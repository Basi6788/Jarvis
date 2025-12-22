package com.romeo.jarvis.utils

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// ================= REQUEST =================

data class ChatContext(
    val wake: Boolean = true,
    val offlineHandled: Boolean = false
)

data class ChatRequest(
    val message: String,
    val lang: String = "ur",
    val mode: String = "voice",
    val context: ChatContext = ChatContext()
)

// ================= RESPONSE =================

data class ChatResponse(
    val reply: String,
    val confidence: Double? = null,
    val type: String? = null, // chat | action | error
    val short: Boolean? = true
)

// ================= API =================

interface ApiService {

    @POST("/api/chat")
    fun chatWithAI(@Body request: ChatRequest): Call<ChatResponse>
}

// ================= RETROFIT =================

object RetrofitClient {

    // ⚠️ Slash zaroori hai
    private const val BASE_URL = "https://romeo-backend.vercel.app/"

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}