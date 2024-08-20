package com.looper.vic.model

data class ChatResponse(
    val chunk: String?,
    val files: List<String>?,
    val title: String?
)