package com.solih.mcjay.fragments

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.solih.mcjay.R
import com.solih.mcjay.databinding.FragmentProfileBinding
import com.solih.mcjay.databinding.DialogEditProfileBinding
import com.solih.mcjay.models.User
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentUser: User? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadProfileImage(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        loadUserProfile()
    }

    private fun setupViews() {
        // Profile image click
        binding.profileImage.setOnClickListener {
            showImagePickerOptions()
        }

        // Upload photo button
        binding.btnUploadPhoto.setOnClickListener {
            showImagePickerOptions()
        }

        // Edit profile button
        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        // Complete profile card
        binding.completeProfileCard.setOnClickListener {
            showEditProfileDialog()
        }

        // Action buttons
        binding.btnChangePassword.setOnClickListener {
            changePassword()
        }

        binding.btnOrderHistory.setOnClickListener {
            // Navigate to order history
            Toast.makeText(requireContext(), "Order History", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun loadUserProfile() {
        scope.launch {
            try {
                showLoading(true)

                val user = supabase.auth.currentUserOrNull()
                if (user == null) {
                    showLoginRequired()
                    return@launch
                }

                currentUser = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("users")
                        .select {
                            // Use the filter function with eq
                            filter {
                                eq("id", user.id)
                            }
                        }
                        .decodeSingle<User>()
                }

                currentUser?.let { updateUI(it) }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("ProfileFragment", "Error loading profile", e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updateUI(user: User) {
        // Profile image
        if (!user.profile_image.isNullOrEmpty()) {
            Glide.with(this)
                .load(user.profile_image)
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(binding.profileImage)
        } else {
            binding.profileImage.setImageResource(R.drawable.ic_profile_placeholder)
        }

        // User info
        binding.tvUserName.text = user.name ?: "No Name Set"
        binding.tvUserEmail.text = user.email ?: "No email set"
        binding.tvPhone.text = user.mobile ?: "Not set"
        binding.tvAddress.text = user.address ?: "Not set"

        // Show/hide complete profile card
        if (user.isProfileComplete()) {
            binding.completeProfileCard.visibility = View.GONE
            binding.btnEditProfile.visibility = View.VISIBLE
        } else {
            binding.completeProfileCard.visibility = View.VISIBLE
            binding.btnEditProfile.visibility = View.GONE
        }
    }

    private fun showLoginRequired() {
        // Since we removed the login required view from XML, show a dialog instead
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Login Required")
            .setMessage("Please login to view and manage your profile")
            .setPositiveButton("Login") { dialog, _ ->
                // Navigate to login activity
                Toast.makeText(requireContext(), "Please login", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showImagePickerOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Profile Picture")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> chooseFromGallery()
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun takePhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        imagePickerLauncher.launch(intent)
    }

    private fun chooseFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun uploadProfileImage(imageUri: Uri) {
        scope.launch {
            try {
                showUploadProgress(true)

                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch

                val imageUrl = withContext(Dispatchers.IO) {
                    // Generate unique file name
                    val fileName = "profile_${userId}_${System.currentTimeMillis()}.jpg"

                    // Read the image file
                    val inputStream = requireContext().contentResolver.openInputStream(imageUri)
                    val bytes = inputStream?.use { it.readBytes() }
                        ?: throw Exception("Could not read image file")

                    // Upload to Supabase storage with progress tracking
                    supabase.storage.from("profile-images").upload(fileName, bytes) {
                        upsert = true
                    }

                    // Get public URL
                    supabase.storage.from("profile-images").publicUrl(fileName)
                }

                // Update user profile with new image URL
                withContext(Dispatchers.IO) {
                    supabase.postgrest.from("users").update(
                        mapOf(
                            "profile_image" to imageUrl
                        )
                    ) {
                        // Use filter with eq for update as well
                        filter { eq("id", userId) }
                    }
                }

                // Reload profile
                loadUserProfile()
                Toast.makeText(requireContext(), "Profile picture updated", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error uploading image: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("ProfileFragment", "Error uploading image", e)
            } finally {
                showUploadProgress(false)
            }
        }
    }

    private fun showEditProfileDialog() {
        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)
        val user = currentUser ?: return

        // Pre-fill current data
        dialogBinding.nameEditText.setText(user.name ?: "")
        dialogBinding.emailEditText.setText(user.email ?: "")
        dialogBinding.mobileEditText.setText(user.mobile ?: "")
        dialogBinding.addressEditText.setText(user.address ?: "")

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Profile")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { dialog, _ ->
                val name = dialogBinding.nameEditText.text.toString()
                val email = dialogBinding.emailEditText.text.toString()
                val mobile = dialogBinding.mobileEditText.text.toString()
                val address = dialogBinding.addressEditText.text.toString()

                updateUserProfile(name, email, mobile, address)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun updateUserProfile(name: String, email: String, mobile: String, address: String) {
        scope.launch {
            try {
                showLoading(true)

                val userId = supabase.auth.currentUserOrNull()?.id ?: return@launch

                withContext(Dispatchers.IO) {
                    supabase.postgrest.from("users").update(
                        mapOf(
                            "name" to name.ifEmpty { null },
                            "email" to email.ifEmpty { null },
                            "mobile" to mobile.ifEmpty { null },
                            "address" to address.ifEmpty { null },
                            "updated_at" to "now()"
                        )
                    ) {
                        // Use filter with eq for update
                        filter { eq("id", userId) }
                    }
                }

                loadUserProfile()
                Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("ProfileFragment", "Error updating profile", e)
            } finally {
                showLoading(false)
            }
        }
    }

    private fun changePassword() {
        // Implement password change logic
        // This could open a dialog or navigate to a password change screen
        Toast.makeText(requireContext(), "Password change feature coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun logout() {
        scope.launch {
            try {
                supabase.auth.signOut()
                Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
                // You might want to navigate to login screen or refresh the UI
                loadUserProfile()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error logging out: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("ProfileFragment", "Error logging out", e)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.profileProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showUploadProgress(show: Boolean) {
        binding.uploadProgressSection.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.uploadProgressBar.progress = 0
            binding.uploadProgressText.text = "0%"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}