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
import androidx.appcompat.widget.PopupMenu
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
import com.looper.vic.R
import com.looper.vic.adapter.ChatsAdapter
import com.looper.vic.util.ChatUtils
import java.lang.reflect.Field

class ChatsFragment : Fragment(), MenuProvider {
    // Declare variables.
    private val chatsAdapter = ChatsAdapter()
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

        chatsAdapter.onItemLongClick = { chatId: Int, position: Int ->
            val popupMenu = PopupMenu(requireContext(), requireView())
            popupMenu.menuInflater.inflate(R.menu.fragment_chats_popup_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.delete_chat -> deleteConversation(chatId, position)
                }
                true
            }
            popupMenu.show()
            true
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
        val searchView: SearchView = searchIcon.actionView as SearchView

        // If no chats are available, then hide search menu item.
        searchIcon.isVisible = chatsAdapter.itemCount != 0

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
        return false
    }

    private fun deleteConversation(chatId: Int, position: Int) {
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
}