package com.solih.mcjay.fragments

import ProfileData
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.activities.LoginActivity
import com.solih.mcjay.databinding.FragmentProfileBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var selectedImage: Uri? = null
    private val supabase = SupabaseClientInstance.client

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadProfile()
        setupClickListeners()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                val userId = session.user?.id ?: return@launch
                val userEmail = session.user?.email ?: ""

                try {
                    val profiles = supabase
                        .from("profiles")
                        .select(columns = Columns.list("*")) {
                            filter { eq("id", userId) }
                        }
                        .decodeList<ProfileData>()

                    val profile = if (profiles.isEmpty()) {
                        // Create a blank profile if not found
                        supabase.from("profiles").insert(
                            ProfileData(
                                id = userId,
                                name = "",
                                email = userEmail,
                                profile_complete = false
                            )
                        )
                        ProfileData(
                            id = userId,
                            name = "",
                            email = userEmail,
                            profile_complete = false
                        )
                    } else {
                        profiles.first()
                    }

                    // Update UI
                    binding.tvUserName.text = profile.name
                    binding.tvUserEmail.text = profile.email
                    binding.tvPhone.text = profile.mobile ?: ""
                    binding.tvAddress.text = profile.address ?: ""
                    if (!profile.profile_image_url.isNullOrBlank()) {
                        binding.profileImage.load(profile.profile_image_url)
                    }

                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnUploadPhoto.setOnClickListener { pickImage() }
        binding.btnSubmitProfile.setOnClickListener {
            val name = binding.editName.text.toString()
            val username = binding.editUsername.text.toString()
            val phone = binding.editPhone.text.toString()
            val address = binding.editAddress.text.toString()

            if (name.isBlank() || username.isBlank() || phone.isBlank() || address.isBlank()) {
                Toast.makeText(requireContext(), "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                updateProfile(name, username, phone, address, selectedImage)
            }
        }
        binding.btnLogout.setOnClickListener {
            lifecycleScope.launch {
                supabase.auth.signOut()
                startActivity(Intent(requireContext(), LoginActivity::class.java))
                requireActivity().finish()
            }
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            selectedImage = data?.data
            binding.profileImage.setImageURI(selectedImage)
        }
    }

    private suspend fun updateProfile(name: String, username: String, phone: String, address: String, imageUri: Uri?) {
        val session = supabase.auth.currentSessionOrNull() ?: return
        val userId = session.user?.id ?: return

        var imageUrl: String? = null
        if (imageUri != null) {
            imageUrl = uploadImage(userId, imageUri)
        }

        try {
            supabase.from("profiles").update({
                set("name", name)
                set("username", username)
                set("mobile", phone)
                set("address", address)
                set("profile_image_url", imageUrl)
                set("profile_complete", true)
            }) {
                filter { eq("id", userId) }
            }

            Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show()
            loadProfile()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun uploadImage(userId: String, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@withContext null

                val path = "profiles/$userId.jpg"
                supabase.storage.from("profile_pics").upload(
                    path = path,
                    data = bytes
                ) {
                    upsert = true
                }

                supabase.storage.from("profile_pics").publicUrl(path)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error uploading image: ${e.message}", Toast.LENGTH_SHORT).show()
                null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
