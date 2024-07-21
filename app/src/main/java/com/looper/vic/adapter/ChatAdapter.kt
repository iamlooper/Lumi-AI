package com.looper.vic.adapter

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.forEach
import androidx.recyclerview.widget.RecyclerView
import com.eyalbira.loadingdots.LoadingDots
import com.google.android.material.textview.MaterialTextView
import com.looper.android.support.util.SpeechUtils
import com.looper.vic.MyApp
import com.looper.vic.R
import com.looper.vic.model.ChatThread
import com.looper.vic.util.ChatUtils
import com.looper.vic.util.FileUtils
import com.looper.vic.util.MarkwonUtils

class ChatAdapter(
    private val activity: AppCompatActivity,
    private val chatId: Int,
    private val speechUtils: SpeechUtils
) : RecyclerView.Adapter<ViewHolder>() {

    companion object {
        const val UPDATE_AI_CONTENT = 1
        const val UPDATE_LOCAL_FILES = 2
    }

    override fun getItemCount() = getThreadsOfChat().size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_chat_thread, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty()) {
            val currentThread = getCurrentThread(position)

            // Initialize views.
            val aiChat: MaterialTextView = holder.itemView.findViewById(R.id.text_view_ai_chat)
            val aiChatLoading: LoadingDots = holder.itemView.findViewById(R.id.loading_dots_ai_chat)
            val filesContainer: LinearLayout = holder.itemView.findViewById(R.id.scroll_view_files)

            for (payload in payloads) {
                when (payload) {
                    UPDATE_AI_CONTENT -> {
                        val markwon =
                            MarkwonUtils.getMarkdownParser(holder.itemView.context, aiChat.textSize)

                        // If current thread is pending then show loading dots and hide AI chat else show AI chat and set AI content.
                        if (currentThread.isPending) {
                            aiChatLoading.visibility = View.VISIBLE
                            aiChat.visibility = View.GONE
                        } else {
                            aiChatLoading.visibility = View.GONE
                            aiChat.visibility = View.VISIBLE
                            markwon.setMarkdown(aiChat, currentThread.aiContent)
                        }
                    }

                    UPDATE_LOCAL_FILES -> {
                        // Add local files to the container for current thread.
                        if (!currentThread.localFiles.isNullOrEmpty()) {
                            for (file in currentThread.localFiles) {
                                val fileItem = LayoutInflater.from(holder.itemView.context)
                                    .inflate(
                                        R.layout.item_chat_file_md,
                                        filesContainer,
                                        false
                                    ) as ViewGroup
                                (fileItem.layoutParams as LinearLayout.LayoutParams).leftMargin = 0
                                fileItem.findViewById<TextView>(R.id.text_view_file_name).text =
                                    file
                                fileItem.findViewById<TextView>(R.id.text_view_file_size).text =
                                    FileUtils.getFileUriFromCache(holder.itemView.context, file)
                                        ?.let {
                                            FileUtils.formatFileSize(
                                                FileUtils.getFileSize(
                                                    holder.itemView.context.contentResolver,
                                                    it
                                                )
                                            )
                                        }
                                filesContainer.addView(fileItem)
                            }
                        }
                    }
                }
            }
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentThread = getCurrentThread(position)

        // Initialize views.
        val userChat: MaterialTextView = holder.itemView.findViewById(R.id.text_view_user_chat)
        val userLayout: LinearLayout = holder.itemView.findViewById(R.id.layout_user)
        val aiChatLoading: LoadingDots = holder.itemView.findViewById(R.id.loading_dots_ai_chat)
        val aiChat: MaterialTextView = holder.itemView.findViewById(R.id.text_view_ai_chat)
        val aiLayout: LinearLayout = holder.itemView.findViewById(R.id.layout_ai)
        val userIcon: AppCompatImageView = holder.itemView.findViewById(R.id.image_view_user_icon)
        val aiIcon: AppCompatImageView = holder.itemView.findViewById(R.id.image_view_ai_icon)
        val filesContainer: LinearLayout = holder.itemView.findViewById(R.id.scroll_view_files)

        // Set background alpha to user and AI icon.
        userIcon.background.alpha = 0x4F
        aiIcon.background.alpha = 0x4F

        // Setup context menu for user and ai layout.
        userLayout.setOnCreateContextMenuListener { menu, view, _ ->
            val inflater: MenuInflater = activity.menuInflater
            inflater.inflate(R.menu.fragment_chat_user_popup_menu, menu)
            menu?.forEach {
                it.intent = Intent().apply {
                    putExtra("position", position)
                    putExtra("viewId", view.id)
                }
            }
        }
        aiLayout.setOnCreateContextMenuListener { menu, view, _ ->
            val inflater: MenuInflater = activity.menuInflater
            inflater.inflate(R.menu.fragment_chat_ai_popup_menu, menu)
            menu?.forEach {
                it.intent = Intent().apply {
                    putExtra("position", position)
                    putExtra("viewId", view.id)
                }
            }
        }

        // Get markdown parser and set user content.
        val markwon = MarkwonUtils.getMarkdownParser(holder.itemView.context, aiChat.textSize)
        userChat.text = currentThread.userContent

        // If current thread is pending then show loading dots and hide AI chat else show AI chat and set AI content.
        if (currentThread.isPending) {
            aiChatLoading.visibility = View.VISIBLE
            aiChat.visibility = View.GONE
        } else {
            aiChatLoading.visibility = View.GONE
            aiChat.visibility = View.VISIBLE
            markwon.setMarkdown(aiChat, currentThread.aiContent)
        }

        // Clear previous views in the container.
        filesContainer.removeAllViews()

        // Add local files to the container for current thread.
        if (!currentThread.localFiles.isNullOrEmpty()) {
            for (file in currentThread.localFiles) {
                val fileItem = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_chat_file_md, filesContainer, false) as ViewGroup
                (fileItem.layoutParams as LinearLayout.LayoutParams).leftMargin = 0
                fileItem.findViewById<TextView>(R.id.text_view_file_name).text = file
                fileItem.findViewById<TextView>(R.id.text_view_file_size).text =
                    FileUtils.getFileUriFromCache(holder.itemView.context, file)?.let {
                        FileUtils.formatFileSize(
                            FileUtils.getFileSize(
                                holder.itemView.context.contentResolver,
                                it
                            )
                        )
                    }
                filesContainer.addView(fileItem)
            }
        }
    }

    fun addThread(thread: ChatThread) {
        ChatUtils.insertAllThreads(thread)
        notifyItemInserted(itemCount - 1)
    }

    fun hasConversation(): Boolean {
        val threads = getThreadsOfChat()
        return threads.isNotEmpty()
    }

    fun cancelPendingQuery(thread: ChatThread, cancelResponse: String) {
        if (thread.isPending) {
            thread.isPending = false
            thread.isCancelled = true
            thread.aiContent = cancelResponse
            MyApp.chatDao().updateThread(thread)
            notifyItemChanged(getThreadIndexById(thread.id))
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun deleteConversation() {
        ChatUtils.deleteAllThreadsOfChat(chatId)
        notifyDataSetChanged()
    }

    fun getChatHistory(thread: ChatThread): List<Map<String, Any>> {
        val threads = getThreadsOfChatBeforeThread(thread).filter { !it.isCancelled }
        val conversationList = mutableListOf<Map<String, Any>>()

        for (i in threads.indices) {
            val userMessageObject = mutableMapOf<String, Any>(
                "role" to "user",
                "content" to threads[i].userContent
            )

            if (threads[i].aiFiles.isNotEmpty()) {
                userMessageObject["files"] = threads[i].aiFiles
            }

            val aiMessageObject = mapOf(
                "role" to "ai",
                "content" to threads[i].aiContent
            )

            conversationList.add(userMessageObject)
            conversationList.add(aiMessageObject)
        }

        return conversationList
    }

    fun updateThreadWithLocalFiles(thread: ChatThread, files: List<String>) {
        if (hasConversation()) {
            thread.localFiles = files
            MyApp.chatDao().updateThread(thread)
            notifyItemChanged(getThreadIndexById(thread.id), UPDATE_LOCAL_FILES)
        }
    }

    fun updateThreadWithAIFiles(thread: ChatThread, files: List<String> = emptyList()) {
        if (hasConversation()) {
            if (files.isNotEmpty()) {
                thread.aiFiles = files
                MyApp.chatDao().updateThread(thread)
            }
        }
    }

    fun updateThreadWithResponse(
        thread: ChatThread,
        response: String,
        files: List<String> = emptyList()
    ) {
        if (hasConversation()) {
            // Set AI files.
            updateThreadWithAIFiles(thread, files)

            // Set AI response.
            thread.isPending = false
            thread.aiContent = response
            MyApp.chatDao().updateThread(thread)
            notifyItemChanged(getThreadIndexById(thread.id), UPDATE_AI_CONTENT)
        }
    }

    fun updateThreadWithResponsePart(
        thread: ChatThread,
        responsePart: String
    ) {
        if (hasConversation()) {
            // Set AI response part.
            thread.isPending = false
            thread.aiContent += responsePart
            MyApp.chatDao().updateThread(thread)
            notifyItemChanged(getThreadIndexById(thread.id), UPDATE_AI_CONTENT)
        }
    }

    fun getThreadsOfChat(): List<ChatThread> {
        if (MyApp.chatDao().getAllChatIds().contains(chatId)) {
            return MyApp.chatDao().getAllThreads(chatId)
        }

        return mutableListOf()
    }

    private fun getThreadsOfChatBeforeThread(thread: ChatThread): List<ChatThread> {
        if (MyApp.chatDao().getAllChatIds().contains(chatId)) {
            return MyApp.chatDao().getAllThreadsBeforeThread(chatId, thread.id)
        }

        return mutableListOf()
    }

    fun getThreadIndexById(threadId: Int): Int =
        getThreadsOfChat().indexOfFirst { it.id == threadId }

    fun getCurrentThread(position: Int): ChatThread = getThreadsOfChat()[position]
}