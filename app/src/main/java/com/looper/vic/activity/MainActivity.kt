package com.looper.vic.activity

import android.os.Bundle
import androidx.navigation.NavController
import com.google.android.material.appbar.AppBarLayout
import com.looper.android.support.activity.DrawerNavigationActivity
import com.looper.vic.R

class MainActivity : DrawerNavigationActivity() {

    private val destinationChangeListener =
        NavController.OnDestinationChangedListener { _, destination, _ ->
            val appbar: AppBarLayout = findViewById(com.looper.android.support.R.id.app_bar_layout)

            when (destination.id) {
                R.id.fragment_chat -> {
                    appbar.liftOnScrollTargetViewId = R.id.recycler_view_chat
                }

                R.id.fragment_chats -> {
                    appbar.liftOnScrollTargetViewId = R.id.recycler_view_chats
                }

                R.id.fragment_tools -> {
                    appbar.liftOnScrollTargetViewId = R.id.recycler_view_tools
                }

                R.id.fragment_about -> {
                    appbar.liftOnScrollTargetViewId = R.id.about_scroll_view
                }

                R.id.fragment_select_text -> {
                    appbar.liftOnScrollTargetViewId = R.id.select_text_scroll_view
                }

                R.id.fragment_voice_assistant -> {
                    supportActionBar?.setDisplayHomeAsUpEnabled(false)
                }

                else -> {
                    appbar.liftOnScrollTargetViewId = android.R.id.content
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup navigation.
        setupNavigation(R.navigation.mobile_navigation, R.menu.activity_main_drawer)
    }

    override fun onStart() {
        super.onStart()
        navController.addOnDestinationChangedListener(destinationChangeListener)
    }

    override fun onStop() {
        super.onStop()
        navController.removeOnDestinationChangedListener(destinationChangeListener)
    }

    override fun getContentView(): Int {
        return R.layout.activity_main
    }
}