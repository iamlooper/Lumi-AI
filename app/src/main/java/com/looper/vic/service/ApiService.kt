package com.looper.vic.service

import com.looper.vic.model.ChatResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("v2/chat")
    fun chat(@Body requestString: String): Call<ChatResponse>

    @POST("v1/title")
    fun getChatTitle(@Body requestString: String): Call<ResponseBody>
}
