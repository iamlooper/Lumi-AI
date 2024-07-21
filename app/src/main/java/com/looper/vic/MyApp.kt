package com.looper.vic

import android.content.Context
import com.google.android.material.color.DynamicColors
import com.looper.android.support.App
import com.looper.vic.db.AppDatabase
import com.looper.vic.util.CategoryUtils

class MyApp : App() {
    companion object {
        private lateinit var database: AppDatabase
        lateinit var allCategoryCache: List<Pair<String, String>>
        fun chatDao() = database.chatDao()
        fun getAppContext(): Context? = App.getAppContext()
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize variables.
        database = AppDatabase.getDatabase(this)
        allCategoryCache = CategoryUtils.getCategoryItems(CategoryUtils.CATEGORY_ALL)

        // Apply dynamic colors.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}