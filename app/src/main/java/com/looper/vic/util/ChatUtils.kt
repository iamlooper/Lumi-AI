package com.looper.vic.util

import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import com.looper.vic.MyApp
import com.looper.vic.R
import com.looper.vic.activity.MainActivity
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

    fun getChatTitle(chatId: Int, retWithToolType: Boolean = true): String {
        val retValue: String
        val title = MyApp.chatDao().getChatTitleSQL(chatId)
        retValue = if (!title.isNullOrEmpty()) {
            title
        } else {
            val list = MyApp.chatDao().getAllThreads(chatId)
            if (list.isNotEmpty()) {
                list.first().userContent
            } else {
                MyApp.getAppContext()!!.getString(R.string.title_chat)
            }
        }

        var addition = ""
        if (retWithToolType) {
            val toolType = MyApp.chatDao().getToolType(chatId)
            if (toolType != null) {
                val toolTypeEntry = MyApp.allCategoryCache.find { it.first == toolType }
                if (toolTypeEntry != null) {
                    addition = " (${toolTypeEntry.second})"
                }
            }
        }

        return "$retValue$addition".trim()
    }

    fun insertAllThreads(vararg threads: ChatThread) {
        MyApp.chatDao().insertAllThreadsSQL(*threads)

        val chats = MyApp.chatDao().getAllChats().toMutableList()
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
        MyApp.chatDao().deleteThread(thread)
        if (MyApp.chatDao().getAllThreads(thread.chatId).isEmpty()) {
            deleteAllThreadsOfChat(thread.chatId)
        }
    }

    fun deleteAllThreadsOfChat(chatId: Int) {
        MyApp.chatDao().deleteAllThreadsOfChat(chatId)
        MyApp.chatDao().deleteChat(MyApp.chatDao().getChat(chatId)!!)
    }

    fun deleteAllChats() {
        val chatIds = MyApp.chatDao().getAllChatIds()
        for (chatId in chatIds) {
            MyApp.chatDao().deleteAllThreadsOfChat(chatId)
            MyApp.chatDao().deleteChat(MyApp.chatDao().getChat(chatId)!!)
        }
    }

    fun setChatTitle(
        activity: AppCompatActivity?,
        chatId: Int,
        toolType: String?,
        title: String?
    ) {
        val actualTitle: String

        if (title != null) {
            var chat = MyApp.chatDao().getChat(chatId)
            if (chat == null) {
                chat = Chat(
                    chatId = chatId,
                    chatTitle = title,
                    tool = toolType,
                )
                MyApp.chatDao().insertChat(chat)
            } else {
                chat.chatTitle = title
                chat.tool = toolType
                MyApp.chatDao().updateChat(chat)
            }

            actualTitle = getChatTitle(chatId)
        } else {
            actualTitle = getChatTitle(chatId)
        }

        if (activity != null) {
            val navHostFragment = activity
                .supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val navController = navHostFragment.navController

            // Set title on the navigation drawer.
            (activity as MainActivity).setDrawerFragmentTitle(
                R.id.fragment_chat,
                actualTitle
            )

            // Set title to action bar if the chat fragment is being shown.
            if (navController.currentDestination?.id == R.id.fragment_chat) {
                activity.supportActionBar?.title = actualTitle
            }
        }
    }
}