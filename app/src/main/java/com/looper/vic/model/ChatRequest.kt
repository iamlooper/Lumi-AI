package com.looper.vic.model

data class ChatRequest(
    val time: Int,
    val sign: String,
    val query: String,
    val history: List<Map<String, Any>>,
    val persona: String,
    val style: String,
    val web_search: Boolean,
    val tool: String?,
    val custom_instructions: String,
    val stream: Boolean,
    val files: List<Map<String, String>>
)