package com.looper.vic

import android.annotation.SuppressLint
import com.google.android.material.color.DynamicColors
import com.looper.android.support.App
import com.looper.vic.db.AppDatabase
import com.looper.vic.util.CategoryUtils

class MyApp : App() {
    companion object {
        private lateinit var database: AppDatabase

        @SuppressLint("StaticFieldLeak") lateinit var context: MyApp
        lateinit var allCategoryCache: List<Triple<String, String, String>>

        fun chatDao() = database.chatDao()
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize variables.
        context = this
        database = AppDatabase.getDatabase(this)
        allCategoryCache = CategoryUtils.getCategoryItems(CategoryUtils.CATEGORY_ALL)

        // Apply dynamic colors.
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}