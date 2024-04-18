package com.looper.vic.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.looper.vic.model.Chat
import com.looper.vic.model.ChatThread

@Dao
interface ChatDao {
    @Query("SELECT * FROM chatthread WHERE chat_id = :chatId")
    fun getAllThreads(chatId: Int): List<ChatThread>

    @Query("SELECT * FROM chatthread WHERE chat_id = :chatId AND id < :upToThreadId ORDER BY id ASC")
    fun getAllThreadsBeforeThread(chatId: Int, upToThreadId: Int): List<ChatThread>

    @Query("SELECT DISTINCT chat_id FROM chatthread")
    fun getAllChatIds(): List<Int>

    @Query("SELECT chat_title FROM chat WHERE chatId = :chatId")
    fun getChatTitleSQL(chatId: Int): String

    @Query("SELECT chat_tool FROM chat WHERE chatId = :chatId")
    fun getToolType(chatId: Int): String?

    @Query("SELECT * FROM chat")
    fun getAllChats(): List<Chat>

    @Query("SELECT * FROM chat WHERE chatId = :chatId")
    fun getChat(chatId: Int): Chat?

    @Insert(ChatThread::class)
    fun insertAllThreadsSQL(vararg threads: ChatThread)

    @Insert(Chat::class)
    fun insertChat(chat: Chat)

    @Update(ChatThread::class)
    fun updateThread(thread: ChatThread)

    @Update(Chat::class)
    fun updateChat(chat: Chat)

    @Delete(ChatThread::class)
    fun deleteThreadSQL(thread: ChatThread)

    @Delete(Chat::class)
    fun deleteChat(chat: Chat)

    @Query("DELETE FROM chatthread WHERE chat_id = :chatId")
    fun deleteAllThreadsOfChatSQL(chatId: Int)
}