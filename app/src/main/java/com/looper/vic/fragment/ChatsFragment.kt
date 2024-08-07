package com.looper.vic.fragment

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.looper.android.support.util.DialogUtils
import com.looper.vic.MyApp
import com.looper.vic.R
import com.looper.vic.adapter.ChatsAdapter
import com.looper.vic.util.ChatUtils
import java.lang.reflect.Field

class ChatsFragment : Fragment(), MenuProvider {
    // Declare variables.
    private lateinit var chatsAdapter: ChatsAdapter
    private lateinit var navController: NavController
    private lateinit var recyclerView: RecyclerView
    private lateinit var recyclerViewLayoutManager: RecyclerView.LayoutManager
    private lateinit var cardViewNoChats: MaterialCardView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup MenuProvider.
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        // Initialize variables.
        navController = view.findNavController()
        recyclerView = view.findViewById(R.id.recycler_view_chats)
        cardViewNoChats = view.findViewById(R.id.card_view_no_chats)
        chatsAdapter = ChatsAdapter(activity as AppCompatActivity)

        // If no chats are available, then make recycler view invisible and show card view.
        if (chatsAdapter.itemCount == 0) {
            recyclerView.visibility = View.GONE
            cardViewNoChats.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            cardViewNoChats.visibility = View.GONE
        }

        chatsAdapter.onItemClick = {
            navController.popBackStack(R.id.fragment_chats, true)
            navController.navigate(
                R.id.fragment_chat,
                Bundle().apply { putInt("chatId", it) },
                NavOptions.Builder().setPopUpTo(R.id.fragment_chat, true).build()
            )
        }

        // Set up RecyclerView.
        recyclerView.adapter = chatsAdapter
        recyclerViewLayoutManager = LinearLayoutManager(context)
        recyclerView.layoutManager = recyclerViewLayoutManager
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.fragment_chats_menu, menu)

        val searchIcon = menu.findItem(R.id.search_chat)
        val deleteAllChatsItem = menu.findItem(R.id.delete_all_chats)
        val searchView: SearchView = searchIcon.actionView as SearchView

        // If no chats are available, then hide search menu and delete all chats item.
        searchIcon.isVisible = chatsAdapter.itemCount != 0
        deleteAllChatsItem.isVisible = chatsAdapter.itemCount != 0

        // Set search hint.
        searchView.queryHint = getString(R.string.search_chat)

        // Remove search hint icon.
        try {
            val mDrawable: Field = SearchView::class.java.getDeclaredField("mSearchHintIcon")
            mDrawable.isAccessible = true
            (mDrawable.get(searchView) as Drawable).setBounds(0, 0, 0, 0)
        } catch (e: Exception) {
            Log.e(ChatsFragment::class.simpleName, e.toString())
        }

        // Remove search plate.
        val searchPlate = searchView.findViewById(androidx.appcompat.R.id.search_plate) as? View
        searchPlate?.background = null

        // Set query listener.
        searchView.setOnQueryTextListener(object : OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                chatsAdapter.filter(query.orEmpty())
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                chatsAdapter.filter(newText)
                return true
            }
        })
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.delete_all_chats -> {
                deleteAllConversations()
                true
            }

            else -> false
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val chatId = item.intent!!.getIntExtra("chatId", -1)
        val position = item.intent!!.getIntExtra("position", -1)

        return when (item.itemId) {
            R.id.edit_chat_title -> {
                displayEditChatTitleDialog(chatId)
                true
            }

            R.id.delete_chat -> {
                deleteConversation(chatId, position)
                true
            }

            else -> super.onContextItemSelected(item)
        }
    }

    private fun deleteConversation(chatId: Int, position: Int) {
        DialogUtils.displayActionConfirmDialog(
            context = requireContext(),
            title = getString(R.string.delete_chat),
            onPositiveAction = {
                // Delete conversation.
                ChatUtils.deleteAllThreadsOfChat(chatId)

                // Notify change.
                chatsAdapter.notifyItemRemoved(position)
                activity?.recreate()

                // Show a toast message.
                Toast.makeText(
                    context,
                    requireContext().getString(R.string.chat_toast_delete_conversation),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    private fun deleteAllConversations() {
        DialogUtils.displayActionConfirmDialog(
            context = requireContext(),
            title = getString(R.string.delete_all_chats),
            onPositiveAction = {
                // Delete all conversations.
                ChatUtils.deleteAllChats()
                activity?.recreate()

                // Show a toast message.
                Toast.makeText(
                    context,
                    getString(R.string.all_conversations_deleted),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    private fun displayEditChatTitleDialog(chatId: Int) {
        val chat = MyApp.chatDao().getChat(chatId)

        DialogUtils.displayEditTextDialog(
            context = requireContext(),
            title = getString(R.string.edit_chat_title),
            initialInput = ChatUtils.getChatTitle(chatId, false),
            onPositiveAction = { input ->
                // Set the new chat title.
                val newChatTitle = input.text.toString().trim()
                if (newChatTitle.isNotEmpty()) {
                    ChatUtils.setChatTitle(
                        activity as AppCompatActivity?,
                        chatId,
                        chat?.tool,
                        newChatTitle
                    )
                    activity?.recreate()
                }
            }
        )
    }
}