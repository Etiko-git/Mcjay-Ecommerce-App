package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.solih.mcjay.R
import com.solih.mcjay.SharedPrefManager
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.databinding.LoginBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: LoginBinding
    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var sharedPrefManager: SharedPrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPrefManager = SharedPrefManager.getInstance(this)

        // REMOVED: checkExistingSession() - Don't auto-redirect

        setupListeners()
    }

    // REMOVE this entire method or keep it for manual checking
    /*
    private fun checkExistingSession() {
        // Check if user is already logged in via MyID
        if (sharedPrefManager.isLoggedIn() && sharedPrefManager.getAuthType() == "myid") {
            Log.d("LoginActivity", "User already logged in via MyID, redirecting to Home")
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        // Check Supabase session
        scope.launch {
            try {
                val session = supabase.auth.currentSessionOrNull()
                if (session != null) {
                    Log.d("LoginActivity", "User already logged in via Supabase, redirecting to Home")
                    // Save Supabase session to SharedPreferences for consistency
                    sharedPrefManager.saveSupabaseAuth()
                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "Error checking Supabase session: ${e.message}")
            }
        }
    }
    */

    private fun setupListeners() {
        binding.loginButton.setOnClickListener {
            if (validateInputs()) {
                loginUser()
            }
        }

        binding.loginTextView.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        binding.myIDButton.setOnClickListener {
            try {
                val intent = Intent(this, MyIDLoginActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to open myID: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateInputs(): Boolean {
        val email = binding.inputField.text.toString().trim()
        val password = binding.passwordField.text.toString().trim()

        if (email.isEmpty()) {
            binding.inputLayout.error = "Please enter your email, username, or mobile"
            return false
        }

        // Check if input is email, username, or mobile number
        val isEmail = Patterns.EMAIL_ADDRESS.matcher(email).matches()
        val isMobile = Patterns.PHONE.matcher(email).matches()

        if (!isEmail && !isMobile && email.length < 3) {
            binding.inputLayout.error = "Please enter a valid email, username, or mobile number"
            return false
        }
        binding.inputLayout.error = null

        if (password.isEmpty()) {
            binding.passwordLayout.error = "Please enter your password"
            return false
        }

        if (password.length < 6) {
            binding.passwordLayout.error = "Password must be at least 6 characters"
            return false
        }
        binding.passwordLayout.error = null

        return true
    }

    private fun loginUser() {
        val email = binding.inputField.text.toString().trim()
        val password = binding.passwordField.text.toString().trim()

        // Show loading state
        showLoading(true)

        scope.launch {
            try {
                // Login with Supabase
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                val user = supabase.auth.currentUserOrNull()

                if (user != null) {
                    Log.d("Supabase", "Logged in as: ${user.email}")

                    // Save Supabase session to SharedPreferences
                    sharedPrefManager.saveSupabaseAuth()

                    // Show success message and redirect
                    showRedirecting()

                    // Delay a bit to show the redirecting message
                    kotlinx.coroutines.delay(1000)

                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Login successful!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                        finish()
                    }
                } else {
                    runOnUiThread {
                        showLoading(false)
                        Toast.makeText(this@LoginActivity, "Invalid credentials. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Supabase", "Login error", e)
                runOnUiThread {
                    showLoading(false)
                    val errorMessage = when {
                        e.message?.contains("Invalid login credentials") == true ->
                            "Invalid email or password"
                        e.message?.contains("Email not confirmed") == true ->
                            "Please verify your email before logging in"
                        else -> "Login failed: ${e.message}"
                    }
                    Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.loginButton.isEnabled = !isLoading
        binding.loginButton.alpha = if (isLoading) 0.7f else 1.0f

        // Change button text during loading if needed
        if (isLoading) {
            binding.loginButton.text = "Logging in..."
        } else {
            binding.loginButton.text = "Login"
        }
    }

    private fun showRedirecting() {
        runOnUiThread {
            binding.redirectText.visibility = View.VISIBLE
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up any coroutines if needed
    }
}