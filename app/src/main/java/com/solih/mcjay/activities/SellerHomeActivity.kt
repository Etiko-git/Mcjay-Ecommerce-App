package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.solih.mcjay.R
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.databinding.ActivitySellerHomeBinding
import com.solih.mcjay.models.Seller
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SellerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerHomeBinding
    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupWelcomeMessage()
        setupListeners()

        Log.d("SellerHome", "Activity created successfully")
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun setupWelcomeMessage() {
        val fullName = intent.getStringExtra("full_name") ?: "Seller"
        binding.tvSellerName.text = fullName
        binding.tvWelcome.text = "Welcome Back,"
    }

    private fun setupListeners() {


        binding.btnViewOrders.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, SellerOrdersActivity::class.java)
            startActivity(intent)
        }

        binding.btnAddProduct.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, AddProductActivity::class.java)
            startActivity(intent)
        }

        binding.btnViewProducts.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, SellerProductsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.seller_home_menu, menu) // Make sure this matches your XML filename
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_profile -> {  // This must match the XML id
                navigateToProfile()
                true
            }
            R.id.menu_logout -> {   // This must match the XML id
                logoutSeller()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun navigateToProfile() {
        Log.d("SellerHome", "Starting profile navigation")

        scope.launch {
            try {
                Log.d("SellerHome", "Getting current user")
                val currentUser = supabase.auth.currentUserOrNull()

                if (currentUser != null) {
                    Log.d("SellerHome", "Current user found: ${currentUser.id}")

                    // Check if profile is complete
                    val sellerProfile = getSellerProfile(currentUser.id)

                    if (sellerProfile != null) {
                        Log.d("SellerHome", "Seller profile loaded: ${sellerProfile.full_name}")

                        if (isProfileComplete(sellerProfile)) {
                            Log.d("SellerHome", "Profile is complete, navigating to SellerProfileActivity")
                            // Profile is complete, show profile details
                            val intent = Intent(this@SellerHomeActivity, SellerProfileActivity::class.java).apply {
                                putExtra("seller_profile", sellerProfile)
                            }
                            startActivity(intent)
                        } else {
                            Log.d("SellerHome", "Profile is incomplete, navigating to CompleteProfileActivity")
                            // Profile is incomplete, show completion form
                            val intent = Intent(this@SellerHomeActivity, CompleteProfileActivity::class.java).apply {
                                putExtra("seller_profile", sellerProfile)
                            }
                            startActivity(intent)
                        }
                    } else {
                        Log.e("SellerHome", "Seller profile is null")
                        runOnUiThread {
                            Toast.makeText(this@SellerHomeActivity, "Error loading profile", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("SellerHome", "Current user is null")
                    runOnUiThread {
                        Toast.makeText(this@SellerHomeActivity, "No user logged in", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SellerHome", "Error in navigateToProfile: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@SellerHomeActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getSellerProfile(userId: String): Seller? {
        return try {
            Log.d("SellerHome", "Fetching seller profile for user: $userId")
            val result = supabase.postgrest["sellers"]
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<Seller>()

            Log.d("SellerHome", "Profile fetch result: $result")
            result
        } catch (e: Exception) {
            Log.e("SellerHome", "Error fetching seller profile: ${e.message}", e)
            null
        }
    }

    private fun isProfileComplete(profile: Seller): Boolean {
        val isComplete = !profile.store_name.isNullOrEmpty() &&
                !profile.tax_id.isNullOrEmpty() &&
                !profile.store_description.isNullOrEmpty() &&
                !profile.business_address.isNullOrEmpty() &&
                !profile.profile_image.isNullOrEmpty()

        Log.d("SellerHome", "Profile completeness check: $isComplete")
        Log.d("SellerHome", "store_name: ${profile.store_name}")
        Log.d("SellerHome", "tax_id: ${profile.tax_id}")
        Log.d("SellerHome", "store_description: ${profile.store_description}")
        Log.d("SellerHome", "business_address: ${profile.business_address}")
        Log.d("SellerHome", "profile_image: ${profile.profile_image}")

        return isComplete
    }

    private fun logoutSeller() {
        scope.launch {
            try {
                supabase.auth.signOut()
                runOnUiThread {
                    Toast.makeText(this@SellerHomeActivity, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@SellerHomeActivity, SellerLoginActivity::class.java)
                    startActivity(intent)
                    finishAffinity()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@SellerHomeActivity, "Logout failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}