package com.looper.vic.adapter

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.forEach
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.looper.vic.MyApp
import com.looper.vic.R
import com.looper.vic.util.ChatUtils

class ChatsAdapter(
    private val activity: AppCompatActivity
) : RecyclerView.Adapter<ViewHolder>() {

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
        val layoutItemChat: LinearLayout = holder.itemView.findViewById(R.id.layout_item_chat)
        val chatTitleView: MaterialTextView = holder.itemView.findViewById(android.R.id.text1)

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(chatId)
        }

        layoutItemChat.setOnCreateContextMenuListener { menu, _, _ ->
            val inflater: MenuInflater = activity.menuInflater
            inflater.inflate(R.menu.fragment_chats_popup_menu, menu)
            menu?.forEach {
                it.intent = Intent().apply {
                    putExtra("chatId", chatId)
                    putExtra("position", position)
                }
            }
        }

        chatTitleView.text = ChatUtils.getChatTitle(chatId)
    }
}