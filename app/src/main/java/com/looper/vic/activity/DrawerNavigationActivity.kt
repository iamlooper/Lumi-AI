package com.looper.vic.activity

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.looper.android.support.activity.BaseActivity
import com.looper.android.support.util.SystemServiceUtils
import com.looper.vic.R

open class DrawerNavigationActivity : BaseActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    protected lateinit var navController: NavController
    protected lateinit var navView: NavigationView
    protected lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set content view.
        setContentView(getContentView())

        // Initialize and set up the toolbar as the action bar.
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Find the navigation controller.
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Get the view of drawer.
        navView = findViewById(R.id.nav_view)

        // Resolve visual overlap of drawer.
        ViewCompat.setOnApplyWindowInsetsListener(navView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply the insets as a margin to the view.
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                topMargin = insets.top
                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) bottomMargin =
                    insets.bottom
                rightMargin = insets.right
            }

            // Don't pass down window insets to descendant views.
            WindowInsetsCompat.CONSUMED
        }

        // Consumes extra top padding caused by system windows.
        val navHostFragmentParent: ConstraintLayout = findViewById(R.id.nav_host_fragment_parent)
        navHostFragmentParent.viewTreeObserver.addOnGlobalLayoutListener {
            navHostFragmentParent.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = -navHostFragmentParent.paddingTop
            }
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                navHostFragmentParent.setPadding(
                    0,
                    navHostFragmentParent.paddingTop,
                    navHostFragmentParent.paddingRight,
                    0
                )
            }
        }
    }

    protected open fun setupNavigation(navGraphId: Int, menuId: Int) {
        // Inflate the navigation graph.
        val navInflater = navController.navInflater
        val navGraph = navInflater.inflate(navGraphId)

        // Set the inflated graph as the graph for the navigation  controller.
        navController.graph = navGraph

        // Inflate the menu for the drawer.
        navView.inflateMenu(menuId)

        // Find the drawer layout.
        drawerLayout = findViewById(R.id.drawer_layout)

        // Fix the drawer header logo height.
        drawerLayout.addDrawerListener(object : DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

            override fun onDrawerOpened(drawerView: View) {}

            override fun onDrawerClosed(drawerView: View) {}

            override fun onDrawerStateChanged(newState: Int) {
                if (newState != DrawerLayout.STATE_IDLE) {
                    SystemServiceUtils.hideKeyboard(this@DrawerNavigationActivity)
                }
            }
        })

        // Setup app bar with navigation controller.
        appBarConfiguration = AppBarConfiguration(navView.menu, drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Set up drawer with navigation controller.
        navView.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    protected open fun getContentView(): Int {
        return -1
    }

    fun setDrawerFragmentTitle(fragmentId: Int, title: String) {
        navView.menu.findItem(fragmentId).title = title
    }
}