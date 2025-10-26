package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.databinding.ActivitySellerLoginBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SellerLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerLoginBinding
    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            if (validateInputs()) {
                loginSeller()
            }
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, SellerRegistrationActivity::class.java))
            finish()
        }

        binding.tvForgotPassword.setOnClickListener {
            // Implement forgot password functionality
            Toast.makeText(this, "Forgot password feature coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateInputs(): Boolean {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Clear previous errors
        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null

        var isValid = true

        // Email validation
        if (email.isEmpty()) {
            binding.emailInputLayout.error = "Please enter your email"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = "Please enter a valid email"
            isValid = false
        }

        // Password validation
        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Please enter your password"
            isValid = false
        }

        return isValid
    }

    private fun loginSeller() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Show loading state
        setLoadingState(true)

        scope.launch {
            try {
                Log.d("SellerLogin", "Attempting login for: $email")

                // Step 1: Sign in with Supabase Auth
                val authResult = supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                Log.d("SellerLogin", "Login auth result received")

                // Step 2: Get current user session
                val session = supabase.auth.currentSessionOrNull()
                val user = supabase.auth.currentUserOrNull()

                if (session != null && user != null) {
                    Log.d("SellerLogin", "Login successful for user: ${user.id}")

                    // Step 3: Verify if user is a seller
                    val sellerProfile = verifySellerProfile(user.id)

                    if (sellerProfile != null) {
                        // Success - navigate to seller home
                        handleLoginSuccess(sellerProfile.full_name)
                    } else {
                        // User exists but not registered as seller
                        handleLoginError(Exception("User is not registered as a seller"))
                    }

                } else {
                    Log.e("SellerLogin", "No session or user after login")
                    handleLoginError(Exception("Login failed. Please check your credentials."))
                }

            } catch (e: Exception) {
                Log.e("SellerLogin", "Login error: ${e.message}", e)
                handleLoginError(e)
            }
        }
    }

    private suspend fun verifySellerProfile(userId: String): SellerProfile? {
        return try {
            val sellers = supabase.postgrest["sellers"]
                .select()
                .decodeList<SellerProfile>()

            // Manually filter in code
            sellers.find { it.id == userId }
        } catch (e: Exception) {
            Log.e("SellerLogin", "Error verifying seller profile: ${e.message}")
            null
        }
    }

    private fun handleLoginSuccess(fullName: String) {
        runOnUiThread {
            Toast.makeText(
                this@SellerLoginActivity,
                "Login successful! Welcome back, $fullName",
                Toast.LENGTH_SHORT
            ).show()

            // Navigate to seller home page
            val intent = Intent(this@SellerLoginActivity, SellerHomeActivity::class.java).apply {
                putExtra("full_name", fullName)
            }
            startActivity(intent)
            finish()
        }
    }

    private fun handleLoginError(e: Exception) {
        runOnUiThread {
            val errorMessage = when {
                e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                    "Invalid email or password. Please try again."
                e.message?.contains("Email not confirmed", ignoreCase = true) == true ->
                    "Please confirm your email before logging in."
                e.message?.contains("network", ignoreCase = true) == true ->
                    "Network error. Please check your internet connection."
                e.message?.contains("not registered as a seller", ignoreCase = true) == true ->
                    "User is not registered as a seller. Please register first."
                else -> "Login failed: ${e.message ?: "Please try again later."}"
            }

            Toast.makeText(
                this@SellerLoginActivity,
                errorMessage,
                Toast.LENGTH_LONG
            ).show()

            setLoadingState(false)
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnLogin.text = if (isLoading) "Logging in..." else "Login as Seller"
        binding.progressBar.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
    }

    // Data class for seller profile
    @kotlinx.serialization.Serializable
    data class SellerProfile(
        val id: String,
        val full_name: String,
        val email: String,
        val mobile_number: String,
        val user_type: String
    )
}