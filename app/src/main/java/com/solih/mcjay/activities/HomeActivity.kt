package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.solih.mcjay.R
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.SharedPrefManager
import io.github.jan.supabase.auth.auth
import com.solih.mcjay.fragments.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var sharedPrefManager: SharedPrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home)

        sharedPrefManager = SharedPrefManager.getInstance(this)

        checkUserSession()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment())
                R.id.nav_profile -> loadFragment(ProfileFragment())
                R.id.nav_favorites -> loadFragment(FavoriteFragment())
                R.id.nav_cart -> loadFragment(CartFragment())
                // add other nav items if needed
            }
            true
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        }
    }

    // In HomeActivity's checkUserSession method
    private fun checkUserSession() {
        scope.launch {
            // Check if user is logged in via any method
            if (!sharedPrefManager.isLoggedIn()) {
                // Check Supabase session as fallback
                val session = supabase.auth.currentSessionOrNull()
                if (session == null) {
                    // Not logged in via any method, redirect to login
                    redirectToLogin()
                } else {
                    // Supabase session exists, save it
                    sharedPrefManager.saveSupabaseAuth()
                }
            } else {
                // User is logged in via MyID or Supabase
                val authType = sharedPrefManager.getAuthType()
                val authToken = sharedPrefManager.getAuthToken()
                val userName = sharedPrefManager.getUserName()

                Log.d("HomeActivity", "User logged in via: $authType, name: $userName")

                if (authType == "supabase") {
                    // Verify Supabase session is still valid
                    val session = supabase.auth.currentSessionOrNull()
                    if (session == null) {
                        // Supabase session expired
                        sharedPrefManager.clearAuth()
                        redirectToLogin()
                    }
                }
                // For MyID, we trust the shared preferences
            }
        }
    }

    private fun redirectToLogin() {
        startActivity(Intent(this@HomeActivity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onStart() {
        super.onStart()
        // Only check session on start if we're not already checking
        if (!sharedPrefManager.isLoggedIn()) {
            checkUserSession()
        }
    }
}