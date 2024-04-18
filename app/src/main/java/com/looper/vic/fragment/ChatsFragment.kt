package com.looper.vic.fragment

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.looper.vic.R
import com.looper.vic.adapter.ChatsAdapter
import java.lang.reflect.Field


/**
 * A simple [Fragment] subclass.
 * Use the [ChatsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ChatsFragment : Fragment() {
    // Declare variables.
    private lateinit var navController: NavController
    private lateinit var recyclerView: RecyclerView
    private lateinit var recyclerViewLayoutManager: RecyclerView.LayoutManager
    private lateinit var cardViewNoChats: MaterialCardView
    private val chatsAdapter = ChatsAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_chats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)

        // Initialize variables and views.
        navController = view.findNavController()

        // Find views by their IDs.
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

    @SuppressLint("DiscouragedApi")
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.fragment_chats_menu, menu)

        val searchIcon = menu.findItem(R.id.search_chat)
        val searchView: SearchView = searchIcon.actionView as SearchView
        searchView.queryHint = getString(R.string.search_chat)

        try {
            val mDrawable: Field = SearchView::class.java.getDeclaredField("mSearchHintIcon")
            mDrawable.isAccessible = true
            (mDrawable.get(searchView) as Drawable).setBounds(0, 0, 0, 0)
        } catch (e: Exception) {
            Log.e(ChatsFragment::class.simpleName, e.toString())
        }

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

        // If no chats are available, then hide search menu item.
        searchIcon.isVisible = chatsAdapter.itemCount != 0

        super.onCreateOptionsMenu(menu, inflater)
    }

    companion object {
        @JvmStatic
        fun newInstance() = ChatsFragment()
    }
}