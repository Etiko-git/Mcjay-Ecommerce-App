package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.databinding.ActivityAdminHomeBinding
import com.solih.mcjay.models.Product
import com.solih.mcjay.models.Seller
import com.solih.mcjay.models.User
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class AdminHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminHomeBinding
    private val supabase = SupabaseClientInstance.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adminName = intent.getStringExtra("admin_name") ?: "Admin"
        val adminEmail = intent.getStringExtra("admin_email") ?: ""

        setupUI(adminName, adminEmail)
        setupListeners()
        loadDashboardData()
    }

    private fun setupUI(adminName: String, adminEmail: String) {
        binding.tvWelcome.text = "Welcome, $adminName"
        binding.tvAdminEmail.text = adminEmail
    }

    private fun setupListeners() {
        binding.btnManageProducts.setOnClickListener {
            val intent = Intent(this, ManageProductsActivity::class.java)
            startActivity(intent)
        }

        binding.btnAnalytics.setOnClickListener {
            Toast.makeText(this, "Analytics & Reporting", Toast.LENGTH_SHORT).show()
            // Start AnalyticsActivity
             val intent = Intent(this, AnalyticsActivity::class.java)
             startActivity(intent)
        }

        binding.btnManageUsers.setOnClickListener {
            val intent = Intent(this, ManageUsersActivity::class.java)
            startActivity(intent)
        }

        binding.btnManageSellers.setOnClickListener {
            val intent = Intent(this, ManageSellersActivity::class.java)
            startActivity(intent)
        }

        binding.btnManageOrders.setOnClickListener {
            Toast.makeText(this, "Manage Orders", Toast.LENGTH_SHORT).show()
            // Start ManageOrdersActivity
             val intent = Intent(this, ManageOrdersActivity::class.java)
             startActivity(intent)
        }

        binding.btnLogout.setOnClickListener {
            logoutAdmin()
        }
    }

    private fun loadDashboardData() {
        lifecycleScope.launch {
            try {
                // Load total users count
                val users = supabase.postgrest["users"]
                    .select()
                    .decodeList<User>()
                binding.tvTotalUsers.text = users.size.toString()

                // Load total products count
                val products = supabase.postgrest["products"]
                    .select()
                    .decodeList<Product>()
                binding.tvTotalProducts.text = products.size.toString()

            } catch (e: Exception) {
                Toast.makeText(this@AdminHomeActivity, "Error loading dashboard data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logoutAdmin() {
        val intent = Intent(this, AdminLoginActivity::class.java)
        startActivity(intent)
        finish()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }
}