package com.looper.vic.activity

import android.Manifest
import android.os.Bundle
import androidx.navigation.NavController
import com.looper.android.support.activity.DrawerNavigationActivity
import com.looper.android.support.util.PermissionUtils
import com.looper.vic.R

class MainActivity : DrawerNavigationActivity() {

    private val destinationChangeListener = NavController.OnDestinationChangedListener { _, destination, _ ->
        if (destination.id == R.id.fragment_voice_assistant) {
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup mobile_navigation.xml.
        setupNavigation(R.navigation.mobile_navigation, R.menu.main_drawer_menu)

        // Grant necessary permissions.
        PermissionUtils.request(this@MainActivity, this, Manifest.permission.RECORD_AUDIO)
    }

    override fun onStart() {
        super.onStart()
        navController.addOnDestinationChangedListener(destinationChangeListener)
    }

    override fun onStop() {
        super.onStop()
        navController.removeOnDestinationChangedListener(destinationChangeListener)
    }
}