package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.solih.mcjay.R
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.databinding.ActivitySellerProfileBinding
import com.solih.mcjay.models.Seller
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class SellerProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerProfileBinding
    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var sellerProfile: Seller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get seller profile from intent
        sellerProfile = intent.getSerializableExtra("seller_profile") as Seller

        setupToolbar()
        displayProfileData()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Profile"
    }

    private fun displayProfileData() {
        // Display profile image
        sellerProfile.profile_image?.let { imageUrl ->
            if (imageUrl.isNotEmpty()) {
                try {
                    Glide.with(this)
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_store)
                        .into(binding.ivProfileImage)
                } catch (e: Exception) {
                    binding.ivProfileImage.setImageResource(R.drawable.ic_store)
                }
            }
        }

        // Display profile information
        binding.tvFullName.text = sellerProfile.full_name
        binding.tvEmail.text = sellerProfile.email
        binding.tvMobile.text = sellerProfile.mobile_number
        binding.tvStoreName.text = sellerProfile.store_name ?: "Not set"
        binding.tvTaxId.text = sellerProfile.tax_id ?: "Not set"
        binding.tvStoreDescription.text = sellerProfile.store_description ?: "Not set"
        binding.tvBusinessAddress.text = sellerProfile.business_address ?: "Not set"

        // Display verification status
        binding.tvVerificationStatus.text = if (sellerProfile.is_verified) "Verified" else "Not Verified"
        binding.tvVerificationStatus.setTextColor(
            if (sellerProfile.is_verified) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_red_dark)
        )

        // Display registration date
        val registrationDate = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(sellerProfile.created_at))
        binding.tvRegistrationDate.text = registrationDate
    }

    private fun setupListeners() {
        binding.btnEditProfile.setOnClickListener {
            val intent = Intent(this, CompleteProfileActivity::class.java).apply {
                putExtra("seller_profile", sellerProfile)
            }
            startActivity(intent)
        }

        binding.btnChangePassword.setOnClickListener {
            Toast.makeText(this, "Change password feature coming soon", Toast.LENGTH_SHORT).show()
        }

        binding.btnStoreSettings.setOnClickListener {
            Toast.makeText(this, "Store settings feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.profile_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.menu_refresh -> {
                refreshProfile()
                true
            }
            R.id.menu_settings -> {
                Toast.makeText(this, "Settings feature coming soon", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refreshProfile() {
        scope.launch {
            try {
                val currentUser = supabase.auth.currentUserOrNull()
                if (currentUser != null) {
                    val updatedProfile = getSellerProfile(currentUser.id)
                    if (updatedProfile != null) {
                        sellerProfile = updatedProfile
                        runOnUiThread {
                            displayProfileData()
                            Toast.makeText(this@SellerProfileActivity, "Profile refreshed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@SellerProfileActivity, "Error refreshing profile", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getSellerProfile(userId: String): Seller? {
        return try {
            supabase.postgrest["sellers"]
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<Seller>()
        } catch (e: Exception) {
            null
        }
    }
}