package com.looper.vic.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.looper.vic.R
import com.looper.vic.util.CategoryUtils

class ToolsAdapter(private val context: Context) : RecyclerView.Adapter<ViewHolder>() {
    private var categoryTypeIndex: Int = 0 // ALL
    private var categoryCache: Pair<Int, List<Triple<String, String, String>>>? = null

    var onItemClick: ((String) -> Unit)? = null

    private fun getCategoryItems(): List<Triple<String, String, String>> {
        if (categoryCache != null && categoryTypeIndex == categoryCache!!.first) {
            return categoryCache!!.second
        }

        categoryCache = Pair(categoryTypeIndex, CategoryUtils.getCategoryItems(categoryTypeIndex))
        return categoryCache!!.second
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
        val description = items[position].third

        val titleView = holder.itemView.findViewById<TextView>(android.R.id.text1)
        titleView.text = title

        val descView = holder.itemView.findViewById<TextView>(android.R.id.text2)
        descView.text = description

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(key)
        }
    }
}