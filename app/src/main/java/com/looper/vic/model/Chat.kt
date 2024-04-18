package com.looper.vic.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Chat(
    @PrimaryKey val chatId: Int,
    @ColumnInfo(name = "chat_title") var chatTitle: String,
    @ColumnInfo(name = "chat_tool") var tool: String?,
)