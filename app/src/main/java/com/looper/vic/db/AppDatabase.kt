package com.looper.vic.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.looper.vic.db.dao.ChatDao
import com.looper.vic.model.Chat
import com.looper.vic.model.ChatThread

@Database(entities = [Chat::class, ChatThread::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}