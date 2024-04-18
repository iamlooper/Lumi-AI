package com.looper.vic.util

import com.looper.vic.MyApp
import com.looper.vic.R
import com.looper.vic.model.Chat
import com.looper.vic.model.ChatThread
import kotlin.random.Random

object ChatUtils {
    fun generateNewChatId(): Int {
        val chatIds = MyApp.chatDao().getAllChatIds()
        var randomNumber: Int

        do {
            randomNumber = Random.nextInt(10000000, 99999999)
        } while (chatIds.contains(randomNumber))

        return randomNumber
    }

    fun getChatTitle(chatId: Int): String {
        var retValue = ""
        val title = MyApp.chatDao().getChatTitleSQL(chatId)
        retValue = if (title != null && title.isNotEmpty()) {
            title
        } else {
            val list = MyApp.chatDao().getAllThreads(chatId)
            if (list.isNotEmpty()) {
                list.first().userContent
            } else {
                MyApp.context.getString(R.string.title_chat)
            }
        }

        val toolType = MyApp.chatDao().getToolType(chatId)
        var addition = ""
        if (toolType != null) {
            val toolTypeEntry = MyApp.allCategoryCache.find { it.first == toolType }
            if (toolTypeEntry != null) {
                addition = " (${toolTypeEntry.second})"
            }
        }

        return "$retValue$addition".trim()
    }

    fun getChatFiles(chatId: Int): Set<String> {
        val threads = MyApp.chatDao().getAllThreads(chatId)
        val out = mutableListOf<String>()

        for (thread in threads) {
            out += thread.filesNames
        }

        return out.toSet()
    }

    fun insertAllThreads(vararg threads: ChatThread) {
        MyApp.chatDao().insertAllThreadsSQL(*threads)

        val chats = ArrayList<Chat>(MyApp.chatDao().getAllChats())
        for (thread in threads) {
            if (chats.find { it.chatId == thread.chatId } == null) {
                val newChat = Chat(
                    chatId = thread.chatId,
                    chatTitle = "",
                    tool = null,
                )
                MyApp.chatDao().insertChat(newChat)
                chats.add(newChat)
            }
        }
    }

    fun deleteThread(thread: ChatThread) {
        MyApp.chatDao().deleteThreadSQL(thread)
        if (MyApp.chatDao().getAllThreads(thread.chatId).isEmpty()) {
            deleteAllThreadsOfChat(thread.chatId)
        }
    }

    fun deleteAllThreadsOfChat(chatId: Int) {
        MyApp.chatDao().deleteAllThreadsOfChatSQL(chatId)
        MyApp.chatDao().deleteChat(MyApp.chatDao().getChat(chatId)!!)
    }
}