package com.looper.vic.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.eyalbira.loadingdots.LoadingDots
import com.looper.android.support.util.SpeechUtils
import com.looper.android.support.util.SystemServiceUtils
import com.looper.vic.MyApp
import com.looper.vic.R
import com.looper.vic.model.ChatThread
import com.looper.vic.util.ChatUtils
import com.looper.vic.util.FileUtils
import com.looper.vic.util.MarkwonUtils

class ChatAdapter(
    private val chatId: Int,
    private val speechUtils: SpeechUtils,
    private val onRegenerateResponse: (ChatThread) -> Unit
): RecyclerView.Adapter<ViewHolder>() {

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
            val aiChat = holder.itemView.findViewById<TextView>(R.id.text_view_ai_chat)
            val aiChatLoading = holder.itemView.findViewById<LoadingDots>(R.id.loading_dots_ai_chat)
            val filesContainer = holder.itemView.findViewById<LinearLayout>(R.id.scroll_view_files)

            for (payload in payloads) {
                when (payload) {
                    UPDATE_AI_CONTENT -> {
                        val markwon = MarkwonUtils.getMarkdownParser(holder.itemView.context, aiChat.textSize)

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
                }
            }
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentThread = getCurrentThread(position)

        // Initialize views.
        val userChat = holder.itemView.findViewById<TextView>(R.id.text_view_user_chat)
        val aiChatLoading = holder.itemView.findViewById<LoadingDots>(R.id.loading_dots_ai_chat)
        val aiChat = holder.itemView.findViewById<TextView>(R.id.text_view_ai_chat)
        val userIcon = holder.itemView.findViewById<AppCompatImageView>(R.id.image_view_user_icon)
        val aiIcon = holder.itemView.findViewById<AppCompatImageView>(R.id.image_view_ai_icon)
        val filesContainer = holder.itemView.findViewById<LinearLayout>(R.id.scroll_view_files)

        // Set background alpha to user and AI icon.
        userIcon.background.alpha = 0x4F
        aiIcon.background.alpha = 0x4F

        // Set popup menu to user layout and chat.
        val userLayout = holder.itemView.findViewById<LinearLayout>(R.id.layout_user)
        val userPopupMenu = View.OnLongClickListener {
            val popupMenu = PopupMenu(holder.itemView.context, it)
            popupMenu.menuInflater.inflate(R.menu.fragment_chat_user_popup_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { menuItem ->
                val userContent = getCurrentThread(position).userContent

                when (menuItem.itemId) {
                    R.id.copy_clipboard -> SystemServiceUtils.copyToClipboard(
                        holder.itemView.context,
                        userContent
                    )
                }
                true
            }
            popupMenu.show()
            true
        }
        userChat.setOnLongClickListener(userPopupMenu)
        userLayout.setOnLongClickListener(userPopupMenu)

        // Set popup menu to AI layout and chat.
        val aiLayout = holder.itemView.findViewById<LinearLayout>(R.id.layout_ai)
        val aiPopupMenu = View.OnLongClickListener {
            val popupMenu = PopupMenu(holder.itemView.context, it)
            popupMenu.menuInflater.inflate(R.menu.fragment_chat_ai_popup_menu, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                val cThread = getCurrentThread(position)
                val aiContent = cThread.aiContent

                when (menuItem.itemId) {
                    R.id.copy_clipboard -> SystemServiceUtils.copyToClipboard(
                        holder.itemView.context,
                        aiContent
                    )

                    R.id.regenerate_response -> onRegenerateResponse(cThread)

                    R.id.speak -> speechUtils.speak(aiContent)
                }
                true
            }
            popupMenu.show()
            true
        }
        aiChat.setOnLongClickListener(aiPopupMenu)
        aiLayout.setOnLongClickListener(aiPopupMenu)

        // Set voice icon if it is a voice input.
        if (currentThread.isVoiceInput) {
            userIcon.setImageResource(R.drawable.ic_wave)
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

    fun getThreadIndexById(threadId: Int): Int = getThreadsOfChat().indexOfFirst { it.id == threadId }

    private fun getCurrentThread(position: Int): ChatThread = getThreadsOfChat()[position]
}