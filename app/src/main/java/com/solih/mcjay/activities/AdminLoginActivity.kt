package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.data.models.AdminProfile
import com.solih.mcjay.databinding.ActivityAdminLoginBinding
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class AdminLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminLoginBinding
    private val supabase = SupabaseClientInstance.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setDefaultCredentials() // Remove in production
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            if (validateInputs()) {
                loginAdmin()
            }
        }

        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Admin password reset - Contact system administrator", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setDefaultCredentials() {
        // For testing - remove this in production
        binding.etEmail.setText("admin@mcjay.com")
        binding.etPassword.setText("admin123")
    }

    private fun validateInputs(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Clear previous errors
        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null
        binding.tvError.visibility = android.view.View.GONE

        var isValid = true

        // Email validation
        if (email.isEmpty()) {
            binding.emailInputLayout.error = "Please enter admin email"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = "Please enter a valid email"
            isValid = false
        }

        // Password validation
        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Please enter admin password"
            isValid = false
        } else if (password.length < 6) {
            binding.passwordInputLayout.error = "Password must be at least 6 characters"
            isValid = false
        }

        return isValid
    }

    private fun loginAdmin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Show loading state
        setLoadingState(true)

        lifecycleScope.launch {
            try {
                Log.d("AdminLogin", "Attempting admin login for: $email")

                // Direct validation against admins table (no Supabase Auth needed)
                val adminProfile = verifyAdminCredentials(email, password)

                if (adminProfile != null) {
                    // Success - navigate to admin home
                    handleLoginSuccess(adminProfile.full_name, adminProfile.user_type)
                } else {
                    // Invalid credentials
                    handleLoginError(Exception("Invalid email or password"))
                }

            } catch (e: Exception) {
                Log.e("AdminLogin", "Login error: ${e.message}", e)
                handleLoginError(e)
            }
        }
    }

    private suspend fun verifyAdminCredentials(email: String, password: String): AdminProfile? {
        return try {
            // Query the admins table with email and password
            val response = supabase.postgrest["admins"]
                .select {
                    filter {
                        eq("email", email)
                        eq("password", password)
                    }
                }
                .decodeSingleOrNull<AdminProfile>()

            Log.d("AdminLogin", "Admin credentials verification result: $response")
            response
        } catch (e: Exception) {
            Log.e("AdminLogin", "Error verifying admin credentials: ${e.message}")
            null
        }
    }

    private fun handleLoginSuccess(fullName: String, userType: String) {
        runOnUiThread {
            Toast.makeText(
                this@AdminLoginActivity,
                "Admin access granted! Welcome, $fullName ($userType)",
                Toast.LENGTH_SHORT
            ).show()

            // Navigate to admin home page
            val intent = Intent(this@AdminLoginActivity, AdminHomeActivity::class.java).apply {
                putExtra("admin_name", fullName)
                putExtra("admin_email", binding.etEmail.text.toString().trim())
                putExtra("admin_type", userType)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun handleLoginError(e: Exception) {
        runOnUiThread {
            val errorMessage = when {
                e.message?.contains("Invalid email or password", ignoreCase = true) == true ->
                    "Invalid email or password. Please try again."
                e.message?.contains("network", ignoreCase = true) == true ->
                    "Network error. Please check your internet connection."
                e.message?.contains("not authorized", ignoreCase = true) == true ||
                        e.message?.contains("Access denied", ignoreCase = true) == true ->
                    "Access denied. Administrator privileges required."
                e.message?.contains("admin profile not found", ignoreCase = true) == true ->
                    "Admin profile not found. Please contact system administrator."
                else -> "Login failed: ${e.message ?: "Please try again later."}"
            }

            binding.tvError.text = errorMessage
            binding.tvError.visibility = android.view.View.VISIBLE

            setLoadingState(false)
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) "Authenticating..." else "Login as Admin"
        binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE

        if (isLoading) {
            binding.tvError.visibility = android.view.View.GONE
        }
    }
}