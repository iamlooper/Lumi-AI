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
import com.looper.vic.model.Chat
import com.looper.vic.model.ChatThread
import com.looper.vic.util.FileUtils
import com.looper.vic.util.MarkwonUtils
import okhttp3.internal.filterList
import org.json.JSONArray
import org.json.JSONObject
import com.looper.vic.util.ChatUtils

class ChatAdapter(
    private val chatId: Int,
    private val speechUtils: SpeechUtils,
    private val onRegenerateResponse: (ChatThread) -> Unit
) :
    RecyclerView.Adapter<ViewHolder>() {

    fun getThreadsOfChat(): List<ChatThread> {
        val dao = MyApp.chatDao()
        if (dao.getAllChatIds().contains(chatId)) {
            return dao.getAllThreads(chatId)
        }

        return mutableListOf()
    }

    private fun getThreadsOfChatBeforeThread(thread: ChatThread): List<ChatThread> {
        val dao = MyApp.chatDao()
        if (dao.getAllChatIds().contains(chatId)) {
            return dao.getAllThreadsBeforeThread(chatId, thread.id)
        }

        return mutableListOf()
    }

    fun getThreadIndexById(threadId: Int): Int {
        return getThreadsOfChat().indexOfFirst { it.id == threadId }
    }

    override fun getItemCount() = getThreadsOfChat().size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_chat_thread, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentThread = getThreadsOfChat()[position]

        // If the thread is visible, proceed with binding data
        holder.itemView.visibility = View.VISIBLE
        val userChat = holder.itemView.findViewById<TextView>(R.id.text_view_user_chat)
        val aiChatLoading = holder.itemView.findViewById<LoadingDots>(R.id.loading_dots_ai_chat)
        val aiChat = holder.itemView.findViewById<TextView>(R.id.text_view_ai_chat)

        // Set user/ai icon background alpha
        val userIcon = holder.itemView.findViewById<AppCompatImageView>(R.id.image_view_user_icon)
        val aiIcon = holder.itemView.findViewById<AppCompatImageView>(R.id.image_view_ai_icon)
        userIcon.background.alpha = 0x4F
        aiIcon.background.alpha = 0x4F

        val userLayout = holder.itemView.findViewById<LinearLayout>(R.id.layout_user)
        val userPopupMenu = View.OnLongClickListener {
            val popupMenu = PopupMenu(holder.itemView.context, it)
            popupMenu.menuInflater.inflate(R.menu.fragment_chat_user_popup_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.copy_clipboard -> SystemServiceUtils.copyToClipboard(
                        holder.itemView.context,
                        currentThread.userContent
                    )
                }
                true
            }
            popupMenu.show()
            true
        }
        userChat.setOnLongClickListener(userPopupMenu)
        userLayout.setOnLongClickListener(userPopupMenu)

        val aiLayout = holder.itemView.findViewById<LinearLayout>(R.id.layout_ai)
        val aiPopupMenu = View.OnLongClickListener {
            val popupMenu = PopupMenu(holder.itemView.context, it)
            popupMenu.menuInflater.inflate(R.menu.fragment_chat_ai_popup_menu, popupMenu.menu)

            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.copy_clipboard -> SystemServiceUtils.copyToClipboard(
                        holder.itemView.context,
                        currentThread.aiContent
                    )

                    R.id.regenerate_response -> onRegenerateResponse(currentThread)
                    R.id.speak -> speak(currentThread)
                }
                true
            }
            popupMenu.show()
            true
        }
        aiChat.setOnLongClickListener(aiPopupMenu)
        aiLayout.setOnLongClickListener(aiPopupMenu)

        if (currentThread.isVoiceInput) {
            userIcon.setImageResource(R.drawable.ic_wave)
        }

        val markwon = MarkwonUtils.getMarkdownParser(holder.itemView.context, aiChat.textSize)
        userChat.text = currentThread.userContent

        if (currentThread.isPending) {
            aiChatLoading.visibility = View.VISIBLE
            aiChat.visibility = View.GONE
        } else {
            aiChatLoading.visibility = View.GONE
            aiChat.visibility = View.VISIBLE
            markwon.setMarkdown(aiChat, currentThread.aiContent)
        }

        val filesContainer = holder.itemView.findViewById<LinearLayout>(R.id.scroll_view_files)
        filesContainer.removeAllViews() // Clear previous files views.

        if (currentThread.filesNames.isNotEmpty()) {
            for (file in currentThread.filesNames) {
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

    fun hasPendingQuery(thread: ChatThread): Boolean {
        return thread.isPending
    }

    fun hasConversation(): Boolean {
        val threads = getThreadsOfChat()
        return threads.isNotEmpty()
    }

    fun getLastUserQuery(): String {
        val threads = getThreadsOfChat()
        return threads.last().userContent
    }

    fun cancelPendingQuery(thread: ChatThread, cancelResponse: String) {
        if (hasPendingQuery(thread)) {
            thread.isPending = false
            thread.aiContent = cancelResponse
            thread.isCancelled = true
            MyApp.chatDao().updateThread(thread)
            notifyItemChanged(getThreadIndexById(thread.id))
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun deleteConversation() {
        ChatUtils.deleteAllThreadsOfChat(chatId)
        notifyDataSetChanged()
    }

    fun getChatHistory(thread: ChatThread): JSONArray {
        val threads = getThreadsOfChatBeforeThread(thread).filterList { !isCancelled }
        val conversationArray = JSONArray()
        for (i in threads.indices) {
            if (threads[i].webSearchResults.isNotEmpty()) {
                val webSearchResultsObject1 = JSONObject()
                    .put("role", "user")
                    .put("content", "")
                val webSearchResultsObject2 = JSONObject()
                    .put("role", "ai")
                    .put("content", threads[i].webSearchResults)
                conversationArray.put(webSearchResultsObject1)
                conversationArray.put(webSearchResultsObject2)
            }

            if (threads[i].filesContents.isNotEmpty()) {
                val filesContentsObject1 = JSONObject()
                    .put("role", "user")
                    .put("content", "")
                val filesContentsObject2 = JSONObject()
                    .put("role", "ai")
                    .put("content", threads[i].filesContents)
                conversationArray.put(filesContentsObject1)
                conversationArray.put(filesContentsObject2)
            }

            val userMessageObject = JSONObject()
                .put("role", "user")
                .put("content", threads[i].userContent)
            val aiMessageObject = JSONObject()
                .put("role", "ai")
                .put("content", threads[i].aiContent)

            conversationArray.put(userMessageObject)
            conversationArray.put(aiMessageObject)
        }
        return conversationArray
    }

    fun updateThreadWithFilesNames(thread: ChatThread, files: List<String>) {
        if (hasConversation()) {
            thread.filesNames = files
            MyApp.chatDao().updateThread(thread)
            notifyItemChanged(getThreadIndexById(thread.id))
        }
    }

    fun updateThreadWithAIResponse(
        chatId: Int,
        toolType: String?,
        thread: ChatThread,
        aiResponse: String,
        responseJson: JSONObject
    ) {
        if (hasConversation()) {
            if (responseJson.has("title")) {
                val title = responseJson.getString("title")
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
            }

            if (responseJson.has("web_search_results")) {
                val webSearchResults = responseJson.getString("web_search_results")
                thread.webSearchResults = webSearchResults
            }

            if (responseJson.has("files_contents")) {
                val filesContents = responseJson.getString("files_contents")
                thread.filesContents = filesContents
            }

            thread.isPending = false
            thread.aiContent = aiResponse
            MyApp.chatDao().updateThread(thread)
            notifyItemChanged(getThreadIndexById(thread.id))
        }
    }

    private fun speak(thread: ChatThread) {
        speechUtils.speak(thread.aiContent)
    }
}
