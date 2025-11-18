package com.solih.mcjay.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.solih.mcjay.R
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.databinding.ActivitySellerProfileBinding
import com.solih.mcjay.models.Seller
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

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
        setupProfileImageSize() // Add this line
        displayProfileData()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Profile"
    }

    private fun displayProfileData() {
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

        // Load profile image from storage
        loadProfileImage()
    }

    private fun loadProfileImage() {
        scope.launch {
            try {
                val imageUrl = getProfileImageUrl(sellerProfile.id)
                runOnUiThread {
                    if (imageUrl != null) {
                        Glide.with(this@SellerProfileActivity)
                            .load(imageUrl)
                            .override(300, 300) // Reduced from 500 to match smaller circle
                            .circleCrop()
                            .placeholder(R.drawable.ic_store)
                            .error(R.drawable.ic_store)
                            .into(binding.ivProfileImage)
                    } else {
                        binding.ivProfileImage.setImageResource(R.drawable.ic_store)
                        binding.ivProfileImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.ivProfileImage.setImageResource(R.drawable.ic_store)
                    binding.ivProfileImage.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            }
        }
    }

    private fun setupProfileImageSize() {
        // Set smaller dimensions - reduced from 180dp to 120dp
        val sizeInPixels = (120 * resources.displayMetrics.density).toInt()
        binding.ivProfileImage.layoutParams.width = sizeInPixels
        binding.ivProfileImage.layoutParams.height = sizeInPixels

        // Remove any padding that might create space
        binding.ivProfileImage.setPadding(0, 0, 0, 0)

        // Make it perfectly circular
        binding.ivProfileImage.clipToOutline = true

        // Use center crop to fill the entire circle without spaces
        binding.ivProfileImage.scaleType = ImageView.ScaleType.CENTER_CROP

        // Set background only if you want a border, otherwise remove it
        // binding.ivProfileImage.background = resources.getDrawable(R.drawable.circle_background, null)
    }

    private suspend fun getProfileImageUrl(sellerId: String): String? {
        return try {
            // Try to get the public URL for the profile image
            // Note: This assumes the image follows the pattern {sellerId}/profile_{timestamp}.{extension}
            // We need to list files in the seller's folder and get the latest one
            val files = supabase.storage.from("seller-profiles").list(sellerId)
            val profileImage = files.find { it.name.startsWith("profile_") }

            if (profileImage != null) {
                supabase.storage.from("seller-profiles").publicUrl("$sellerId/${profileImage.name}")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
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

        // Add click listener for profile image to allow uploading new image
        binding.ivProfileImage.setOnClickListener {
            // You can implement image picker here
            Toast.makeText(this, "Image upload feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun uploadProfileImage(imageUri: Uri): Boolean {
        return try {
            val sellerId = sellerProfile.id

            // Generate unique file name with timestamp
            val fileExtension = getFileExtensionFromUri(imageUri)
            val fileName = "profile_${System.currentTimeMillis()}.$fileExtension"
            val fullPath = "$sellerId/$fileName" // It's good practice to define the full path

            // Read the image as ByteArray from Uri
            val inputStream = contentResolver.openInputStream(imageUri)
            val fileBytes = inputStream?.readBytes()
            inputStream?.close()

            if (fileBytes == null) {
                return false
            }

            // ✅ FIX: Correctly call the upload function with the options lambda
            supabase.storage.from("seller-profiles")
                .upload(fullPath, fileBytes) {
                    upsert = true
                }

            true
        } catch (e: Exception) {
            // It's helpful to log the error to understand what went wrong
            // Log.e("SellerProfileActivity", "Image upload failed", e)
            false
        }
    }

    private fun getFileExtensionFromUri(uri: Uri): String {
        return contentResolver.getType(uri)?.substringAfterLast("/") ?: "jpg"
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

    override fun onResume() {
        super.onResume()
        // Refresh profile data when returning from edit profile
        refreshProfile()
    }
}