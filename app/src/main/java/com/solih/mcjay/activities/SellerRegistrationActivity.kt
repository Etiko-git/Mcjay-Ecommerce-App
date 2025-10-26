package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.databinding.ActivitySellerRegistrationBinding
import com.solih.mcjay.models.Seller
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class SellerRegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerRegistrationBinding
    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            if (validateInputs()) {
                registerSeller()
            }
        }

        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, SellerLoginActivity::class.java))
            finish()
        }
    }

    private fun validateInputs(): Boolean {
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val mobile = binding.etMobile.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        val agreedToTerms = binding.termsCheckBox.isChecked

        // Full Name validation
        if (fullName.isEmpty()) {
            binding.nameInputLayout.error = "Please enter your full name"
            return false
        }
        binding.nameInputLayout.error = null

        // Email validation
        if (email.isEmpty()) {
            binding.emailInputLayout.error = "Please enter your email"
            return false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = "Please enter a valid email"
            return false
        }
        binding.emailInputLayout.error = null

        // Mobile validation
        if (mobile.isEmpty()) {
            binding.mobileInputLayout.error = "Please enter your mobile number"
            return false
        }
        binding.mobileInputLayout.error = null

        // Password validation
        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Please enter a password"
            return false
        } else {
            val passwordPattern = Pattern.compile(
                "^(?=.*[A-Z])(?=.*[0-9])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$"
            )
            if (!passwordPattern.matcher(password).matches()) {
                binding.passwordInputLayout.error = "Password must contain uppercase letter, special character and number, and be at least 8 characters"
                return false
            }
        }
        binding.passwordInputLayout.error = null

        // Confirm Password validation
        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordInputLayout.error = "Please confirm your password"
            return false
        } else if (password != confirmPassword) {
            binding.confirmPasswordInputLayout.error = "Passwords don't match"
            return false
        }
        binding.confirmPasswordInputLayout.error = null

        // Terms and Conditions validation
        if (!agreedToTerms) {
            Toast.makeText(this, "Please agree to Terms and Conditions", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun registerSeller() {
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val mobile = binding.etMobile.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        scope.launch {
            try {
                // Step 1: Sign up with Supabase Auth
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                // Step 2: Get the signed-up user (since no email verification, user is auto-logged in)
                val user = supabase.auth.currentUserOrNull()

                if (user != null) {
                    val userId = user.id

                    // Step 3: Save seller info to "sellers" table
                    val newSeller = Seller(
                        id = userId,
                        full_name = fullName,
                        email = email,
                        mobile_number = mobile,
                        user_type = "seller",
                        created_at = System.currentTimeMillis(),
                        is_verified = false,
                        updated_at = System.currentTimeMillis()
                    )
                    supabase.postgrest["sellers"].insert(newSeller)

                    Toast.makeText(
                        this@SellerRegistrationActivity,
                        "Seller registration successful! Welcome.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.d("SellerRegistration", "Seller saved: $newSeller")

                    // Navigate directly to seller dashboard since auto-logged in
                    val intent = Intent(this@SellerRegistrationActivity, SellerLoginActivity::class.java)
                    intent.putExtra("userId", userId)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@SellerRegistrationActivity, "Sign-up failed. Try again.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("SellerRegistration", "Registration error", e)
                Toast.makeText(this@SellerRegistrationActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}