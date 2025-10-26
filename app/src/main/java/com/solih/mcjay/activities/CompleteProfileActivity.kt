package com.solih.mcjay.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.solih.mcjay.R
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.databinding.ActivityCompleteProfileBinding
import com.solih.mcjay.models.Seller
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable as KSerializable

// Add this new data class for the update payload (can be in a separate file or here)
@KSerializable
data class SellerUpdate(
    val store_name: String,
    val tax_id: String,
    val store_description: String,
    val business_address: String,
    val profile_image: String?,
    val updated_at: Long
)

class CompleteProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompleteProfileBinding
    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var sellerProfile: Seller
    private var selectedImageUri: Uri? = null

    companion object {
        private const val PICK_IMAGE_REQUEST = 100
        private const val TAG = "CompleteProfileActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            binding = ActivityCompleteProfileBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "Activity created successfully")

            // Get seller profile from intent with error handling
            sellerProfile = intent.getSerializableExtra("seller_profile") as? Seller
                ?: throw IllegalStateException("Seller profile not found in intent")

            Log.d(TAG, "Seller profile loaded: ${sellerProfile.full_name}")

            setupViews()
            setupListeners()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error initializing: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupViews() {
        try {
            binding.toolbar.title = "Complete Your Profile"
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)

            // Pre-fill existing data if available
            sellerProfile.store_name?.let {
                binding.etStoreName.setText(it)
                Log.d(TAG, "Prefilled store_name: $it")
            }
            sellerProfile.tax_id?.let {
                binding.etTaxId.setText(it)
                Log.d(TAG, "Prefilled tax_id: $it")
            }
            sellerProfile.store_description?.let {
                binding.etStoreDescription.setText(it)
                Log.d(TAG, "Prefilled store_description: $it")
            }
            sellerProfile.business_address?.let {
                binding.etBusinessAddress.setText(it)
                Log.d(TAG, "Prefilled business_address: $it")
            }

            Log.d(TAG, "Views setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupViews: ${e.message}", e)
            throw e
        }
    }

    private fun setupListeners() {
        binding.btnUploadImage.setOnClickListener {
            Log.d(TAG, "Upload image button clicked")
            try {
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                }
                startActivityForResult(intent, PICK_IMAGE_REQUEST)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting image picker: ${e.message}", e)
                Toast.makeText(this, "Error opening image picker", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSaveProfile.setOnClickListener {
            Log.d(TAG, "Save profile button clicked")
            if (validateInputs()) {
                saveProfile()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.data
            Log.d(TAG, "Image selected: $selectedImageUri")
            binding.tvImageStatus.text = "Image selected"
            binding.tvImageStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        }
    }

    private fun validateInputs(): Boolean {
        val storeName = binding.etStoreName.text.toString().trim()
        val taxId = binding.etTaxId.text.toString().trim()
        val storeDescription = binding.etStoreDescription.text.toString().trim()
        val businessAddress = binding.etBusinessAddress.text.toString().trim()

        Log.d(TAG, "Validating inputs: storeName=$storeName, taxId=$taxId")

        // Clear previous errors
        binding.tilStoreName.error = null
        binding.tilTaxId.error = null
        binding.tilStoreDescription.error = null
        binding.tilBusinessAddress.error = null

        var isValid = true

        if (storeName.isEmpty()) {
            binding.tilStoreName.error = "Store name is required"
            isValid = false
            Log.d(TAG, "Validation failed: store name empty")
        }

        if (taxId.isEmpty()) {
            binding.tilTaxId.error = "Tax ID is required"
            isValid = false
            Log.d(TAG, "Validation failed: tax ID empty")
        }

        if (storeDescription.isEmpty()) {
            binding.tilStoreDescription.error = "Store description is required"
            isValid = false
            Log.d(TAG, "Validation failed: store description empty")
        }

        if (businessAddress.isEmpty()) {
            binding.tilBusinessAddress.error = "Business address is required"
            isValid = false
            Log.d(TAG, "Validation failed: business address empty")
        }

        if (selectedImageUri == null) {
            Toast.makeText(this, "Please upload a profile image", Toast.LENGTH_SHORT).show()
            isValid = false
            Log.d(TAG, "Validation failed: no image selected")
        }

        Log.d(TAG, "Validation result: $isValid")
        return isValid
    }

    private fun saveProfile() {
        val storeName = binding.etStoreName.text.toString().trim()
        val taxId = binding.etTaxId.text.toString().trim()
        val storeDescription = binding.etStoreDescription.text.toString().trim()
        val businessAddress = binding.etBusinessAddress.text.toString().trim()

        Log.d(TAG, "Saving profile: storeName=$storeName, sellerId=${sellerProfile.id}")

        binding.btnSaveProfile.isEnabled = false
        binding.btnSaveProfile.text = "Saving..."

        scope.launch {
            try {
                Log.d(TAG, "Starting profile update in coroutine")

                // For now, use the URI string directly
                val profileImageUrl = selectedImageUri?.toString()
                Log.d(TAG, "Profile image URL: $profileImageUrl")

                // Use the serializable data class
                val updatePayload = SellerUpdate(
                    store_name = storeName,
                    tax_id = taxId,
                    store_description = storeDescription,
                    business_address = businessAddress,
                    profile_image = profileImageUrl,
                    updated_at = System.currentTimeMillis()
                )

                Log.d(TAG, "Update payload created")

                // Update and return the updated row
                val updatedSeller: Seller = supabase.postgrest["sellers"]
                    .update(updatePayload) {
                        filter {
                            eq("id", sellerProfile.id)
                        }
                        select()
                    }
                    .decodeSingle<Seller>()

                Log.d(TAG, "Update successful: ${updatedSeller.store_name}")

                runOnUiThread {
                    Toast.makeText(
                        this@CompleteProfileActivity,
                        "Profile updated successfully!",
                        Toast.LENGTH_LONG
                    ).show()

                    // Use the decoded updated profile for navigation
                    val updatedProfile = updatedSeller

                    Log.d(TAG, "Navigating to SellerProfileActivity")

                    val intent = Intent(this@CompleteProfileActivity, SellerProfileActivity::class.java).apply {
                        putExtra("seller_profile", updatedProfile)
                    }
                    startActivity(intent)
                    finish()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error updating profile: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@CompleteProfileActivity,
                        "Error updating profile: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.btnSaveProfile.isEnabled = true
                    binding.btnSaveProfile.text = "Save Profile"
                }
            }
        }
    }
}