package com.looper.vic.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ChatThread(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "chat_id") val chatId: Int,
    @ColumnInfo(name = "user_content") val userContent: String,
    @ColumnInfo(name = "ai_content") var aiContent: String,
    @ColumnInfo(name = "pending") var isPending: Boolean = false,
    @ColumnInfo(name = "voice") var isVoiceInput: Boolean = false,
    @ColumnInfo(name = "cancelled") var isCancelled: Boolean = false,
    @ColumnInfo(name = "files_names") var filesNames: List<String> = listOf(),
    @ColumnInfo(name = "web_search_results") var webSearchResults: String = "",
    @ColumnInfo(name = "files_contents") var filesContents: String = ""
)