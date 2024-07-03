package com.looper.vic.util

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
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

    fun getChatTitle(chatId: Int): String {
        val retValue: String
        val title = MyApp.chatDao().getChatTitleSQL(chatId)
        retValue = if (!title.isNullOrEmpty()) {
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

    fun setChatTitle(
        activity: AppCompatActivity?,
        fragment: Fragment,
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
                .findFragmentById(com.looper.android.support.R.id.fragment_container_view_content_main) as NavHostFragment

            // Set title on the navigation drawer.
            (activity as MainActivity).setDrawerFragmentTitle(
                R.id.fragment_chat,
                actualTitle
            )

            // Set title to action bar if the current fragment is being shown.
            if (navHostFragment.childFragmentManager.primaryNavigationFragment == fragment) {
                activity.supportActionBar?.title = actualTitle
            }
        }
    }
}