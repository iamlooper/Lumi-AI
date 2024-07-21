package com.looper.vic.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.looper.vic.R
import com.looper.vic.util.CategoryUtils

class ToolsAdapter(private val context: Context) : RecyclerView.Adapter<ViewHolder>() {
    private var categoryTypeIndex: Int = 0 // ALL
    private var categoryCache: Pair<Int, List<Pair<String, String>>>? = null
    var onItemClick: ((String) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val rootView = LayoutInflater.from(context)
            .inflate(R.layout.item_tool, parent, false)
        return ViewHolder(rootView)
    }

    override fun getItemCount() = getCategoryItems().size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val items = getCategoryItems()
        val key = items[position].first
        val title = items[position].second

        val titleView: MaterialTextView = holder.itemView.findViewById(android.R.id.text1)
        titleView.text = title

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(key)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun changeCategory(categoryNumber: Int) {
        if (categoryNumber < CategoryUtils.CATEGORY_ALL ||
            categoryNumber > CategoryUtils.CATEGORY_OTHERS ||
            categoryNumber == categoryTypeIndex
        ) {
            return
        }

        categoryTypeIndex = categoryNumber
        notifyDataSetChanged()
    }

    private fun getCategoryItems(): List<Pair<String, String>> {
        if (categoryCache != null && categoryTypeIndex == categoryCache!!.first) {
            return categoryCache!!.second
        }

        categoryCache = Pair(categoryTypeIndex, CategoryUtils.getCategoryItems(categoryTypeIndex))
        return categoryCache!!.second
    }
}