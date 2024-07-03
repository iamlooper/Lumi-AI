package com.looper.vic.model

data class ChatResponseStream(
    val chunk: String?,
    val files: List<String>?
)