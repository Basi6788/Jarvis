package com.romeo.jarvis.utils

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// JSON format jo bhejna hai
data class ChatRequest(val message: String)
// JSON format jo wapis ayega
data class ChatResponse(val reply: String)

interface ApiService {
    @POST("/api/chat") // Apka endpoint
    fun chatWithAI(@Body request: ChatRequest): Call<ChatResponse>
}

object RetrofitClient {
    private const val BASE_URL = "https://romeo-backend.vercel.app/" // Apka URL

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
