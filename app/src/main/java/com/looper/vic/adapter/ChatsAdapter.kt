package com.looper.vic.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.looper.vic.MyApp
import com.looper.vic.R
import com.looper.vic.util.ChatUtils

class ChatsAdapter : RecyclerView.Adapter<ViewHolder>() {

    private var chatIds: List<Int> = emptyList()
    private var filteredChatIds: List<Int> = emptyList()

    var onItemClick: ((Int) -> Unit)? = null

    init {
        chatIds = MyApp.chatDao().getAllChatIds().reversed()
        filteredChatIds = chatIds
    }

    @SuppressLint("NotifyDataSetChanged")
    fun filter(query: String) {
        filteredChatIds = if (query.isBlank()) {
            chatIds
        } else {
            chatIds.filter { chatId ->
                val chatTitle = ChatUtils.getChatTitle(chatId)
                chatTitle.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = filteredChatIds.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chatId = filteredChatIds[position]

        val chatTitleView = holder.itemView.findViewById<TextView>(android.R.id.text1)

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(chatId)
        }

        chatTitleView.text = ChatUtils.getChatTitle(chatId)
    }
}