package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.databinding.ActivityRegisterBinding
import com.solih.mcjay.models.User
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.registerButton.setOnClickListener {
            if (validateInputs()) {
                registerUser()
            }
        }

        binding.loginTextView.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun validateInputs(): Boolean {
        val name = binding.nameEditText.text.toString().trim()
        val username = binding.usernameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val mobile = binding.mobileEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()
        val confirmPassword = binding.confirmPasswordEditText.text.toString().trim()

        if (name.isEmpty()) {
            binding.nameInputLayout.error = "Please enter your name"
            return false
        }
        binding.nameInputLayout.error = null

        if (username.isEmpty()) {
            binding.usernameInputLayout.error = "Please enter a username"
            return false
        }
        binding.usernameInputLayout.error = null

        if (email.isEmpty()) {
            binding.emailInputLayout.error = "Please enter your email"
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = "Please enter a valid email"
            return false
        }
        binding.emailInputLayout.error = null

        if (mobile.isEmpty()) {
            binding.mobileInputLayout.error = "Please enter your mobile number"
            return false
        }
        binding.mobileInputLayout.error = null

        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Please enter a password"
            return false
        }
        val passwordPattern = Pattern.compile(
            "^(?=.*[A-Z])(?=.*[0-9])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$"
        )
        if (!passwordPattern.matcher(password).matches()) {
            binding.passwordInputLayout.error = "Password must start with capital letter, contain special character and number, and be at least 8 characters"
            return false
        }
        binding.passwordInputLayout.error = null

        if (confirmPassword.isEmpty()) {
            binding.confirmPasswordInputLayout.error = "Please confirm your password"
            return false
        }
        if (password != confirmPassword) {
            binding.confirmPasswordInputLayout.error = "Passwords don't match"
            return false
        }
        binding.confirmPasswordInputLayout.error = null

        return true
    }

    private fun registerUser() {
        val name = binding.nameEditText.text.toString().trim()
        val username = binding.usernameEditText.text.toString().trim()
        val email = binding.emailEditText.text.toString().trim()
        val mobile = binding.mobileEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString().trim()

        scope.launch {
            try {
                // Step 1: Sign up with Supabase Auth
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }

                // Step 2: Get the signed-up user
                val user = supabase.auth.currentUserOrNull()

                if (user != null) {
                    val userId = user.id

                    // Step 3: Save user info to "users" table
                    val newUser = User(userId, name, username, email, mobile)
                    supabase.postgrest["users"].insert(newUser)

                    Toast.makeText(
                        this@RegisterActivity,
                        "Registration successful! Please check your email to confirm.",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.d("Supabase", "User saved: $newUser")
                    finish()
                } else {
                    Toast.makeText(this@RegisterActivity, "Sign-up failed. Try again.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("Supabase", "Registration error", e)
                Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
