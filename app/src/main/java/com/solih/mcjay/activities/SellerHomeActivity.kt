package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.solih.mcjay.R
import com.solih.mcjay.SupabaseClientInstance
import com.solih.mcjay.databinding.ActivitySellerHomeBinding
import com.solih.mcjay.models.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SellerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySellerHomeBinding
    private val supabase = SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private var sellerId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        getCurrentSeller()
        setupListeners()

        Log.d("SellerHome", "Activity created successfully")
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun getCurrentSeller() {
        val currentUser = supabase.auth.currentUserOrNull()
        if (currentUser != null) {
            sellerId = currentUser.id
            setupWelcomeMessage()
            loadDashboardData()
        } else {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupWelcomeMessage() {
        val fullName = intent.getStringExtra("full_name") ?: "Seller"
        binding.tvSellerName.text = fullName
        binding.tvWelcome.text = "Welcome Back,"
    }

    private fun loadDashboardData() {
        scope.launch {
            try {
                loadProductsCount()
                loadTodaysOrders()
                loadLowStockCount()
                loadTotalRevenue()
            } catch (e: Exception) {
                Log.e("SellerHome", "Error loading dashboard data: ${e.message}", e)
            }
        }
    }

    private suspend fun loadProductsCount() {
        try {
            val products = supabase.postgrest.from("products")
                .select {
                    filter {
                        eq("seller_id", sellerId)
                    }
                }
                .decodeList<Product>()

            runOnUiThread {
                binding.tvProductsCount.text = products.size.toString()
            }
        } catch (e: Exception) {
            Log.e("SellerHome", "Error loading products count: ${e.message}")
            runOnUiThread {
                binding.tvProductsCount.text = "0"
            }
        }
    }

    private suspend fun loadTodaysOrders() {
        try {
            // Get today's date range
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            // Start of today
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = dateFormat.format(calendar.time) + " 00:00:00"

            // End of today
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val endOfDay = dateFormat.format(calendar.time) + " 23:59:59"

            // Use range query for timestamp filtering
            val orderItems = supabase.postgrest.from("order_items")
                .select {
                    filter {
                        eq("seller_id", sellerId)
                        gte("created_at", startOfDay)
                        lte("created_at", endOfDay)
                    }
                }
                .decodeList<OrderItem>()

            runOnUiThread {
                binding.tvTodaysOrders.text = orderItems.size.toString()
            }
        } catch (e: Exception) {
            Log.e("SellerHome", "Error loading today's orders: ${e.message}")
            runOnUiThread {
                binding.tvTodaysOrders.text = "0"
            }
        }
    }

    private suspend fun loadLowStockCount() {
        try {
            val lowStockProducts = supabase.postgrest.from("products")
                .select {
                    filter {
                        eq("seller_id", sellerId)
                        lte("stock_quantity", 3)
                    }
                }
                .decodeList<Product>()

            runOnUiThread {
                binding.tvLowStockCount.text = lowStockProducts.size.toString()
            }
        } catch (e: Exception) {
            Log.e("SellerHome", "Error loading low stock count: ${e.message}")
            runOnUiThread {
                binding.tvLowStockCount.text = "0"
            }
        }
    }

    private suspend fun loadTotalRevenue() {
        try {
            // Get total earnings from seller_balance table
            val sellerBalance = supabase.postgrest.from("seller_balance")
                .select {
                    filter {
                        eq("seller_id", sellerId)
                    }
                }
                .decodeSingleOrNull<SellerBalance>()

            val totalEarnings = sellerBalance?.total_earnings ?: 0.0

            runOnUiThread {
                binding.tvTotalRevenue.text = "₹${String.format("%.2f", totalEarnings)}"
            }
        } catch (e: Exception) {
            Log.e("SellerHome", "Error loading total revenue: ${e.message}")
            runOnUiThread {
                binding.tvTotalRevenue.text = "₹0.00"
            }
        }
    }

    private fun setupListeners() {
        // Card click listeners
        binding.cardProducts.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, SellerProductsActivity::class.java)
            startActivity(intent)
        }

        binding.cardOrders.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, SellerOrdersActivity::class.java)
            startActivity(intent)
        }

        binding.cardLowStock.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, SellerProductsActivity::class.java).apply {
                putExtra("show_low_stock", true)
            }
            startActivity(intent)
        }

        binding.cardRevenue.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, SellerEarningsActivity::class.java)
            startActivity(intent)
        }

        // Quick Actions
        binding.btnViewOrders.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, SellerOrdersActivity::class.java)
            startActivity(intent)
        }

        binding.btnAddProduct.setOnClickListener {
            showAddProductDialog()
        }

        binding.btnViewProducts.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, SellerProductsActivity::class.java)
            startActivity(intent)
        }

        // Analytics & Reports
        binding.btnSalesReports.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, SalesReportsActivity::class.java)
            startActivity(intent)
        }

        // Payment & Earnings
        binding.btnTransactionHistory.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, TransactionHistoryActivity::class.java)
            startActivity(intent)
        }

        binding.btnEarnings.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, SellerEarningsActivity::class.java)
            startActivity(intent)
        }

        binding.btnWithdraw.setOnClickListener {
            val intent = Intent(this@SellerHomeActivity, WithdrawalActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showAddProductDialog() {
        // Use the same dialog approach as in SellerProductsActivity
        scope.launch {
            try {
                val currentUser = supabase.auth.currentUserOrNull()
                if (currentUser != null) {
                    // Check if seller profile is complete
                    val sellerProfile = getSellerProfile(currentUser.id)
                    if (sellerProfile != null && isProfileComplete(sellerProfile)) {
                        // Profile is complete, navigate to add product
                        val intent = Intent(this@SellerHomeActivity, SellerProductsActivity::class.java)
                        startActivity(intent)
                    } else {
                        // Profile incomplete, show message
                        runOnUiThread {
                            Toast.makeText(
                                this@SellerHomeActivity,
                                "Please complete your profile first",
                                Toast.LENGTH_LONG
                            ).show()
                            navigateToProfile()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SellerHome", "Error checking profile: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@SellerHomeActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.seller_home_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_profile -> {
                navigateToProfile()
                true
            }
            R.id.menu_logout -> {
                logoutSeller()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun navigateToProfile() {
        Log.d("SellerHome", "Starting profile navigation")

        scope.launch {
            try {
                Log.d("SellerHome", "Getting current user")
                val currentUser = supabase.auth.currentUserOrNull()

                if (currentUser != null) {
                    Log.d("SellerHome", "Current user found: ${currentUser.id}")

                    // Check if profile is complete
                    val sellerProfile = getSellerProfile(currentUser.id)

                    if (sellerProfile != null) {
                        Log.d("SellerHome", "Seller profile loaded: ${sellerProfile.full_name}")

                        if (isProfileComplete(sellerProfile)) {
                            Log.d("SellerHome", "Profile is complete, navigating to SellerProfileActivity")
                            // Profile is complete, show profile details
                            val intent = Intent(this@SellerHomeActivity, SellerProfileActivity::class.java).apply {
                                putExtra("seller_profile", sellerProfile)
                            }
                            startActivity(intent)
                        } else {
                            Log.d("SellerHome", "Profile is incomplete, navigating to CompleteProfileActivity")
                            // Profile is incomplete, show completion form
                            val intent = Intent(this@SellerHomeActivity, CompleteProfileActivity::class.java).apply {
                                putExtra("seller_profile", sellerProfile)
                            }
                            startActivity(intent)
                        }
                    } else {
                        Log.e("SellerHome", "Seller profile is null")
                        runOnUiThread {
                            Toast.makeText(this@SellerHomeActivity, "Error loading profile", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.e("SellerHome", "Current user is null")
                    runOnUiThread {
                        Toast.makeText(this@SellerHomeActivity, "No user logged in", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SellerHome", "Error in navigateToProfile: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@SellerHomeActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun getSellerProfile(userId: String): Seller? {
        return try {
            Log.d("SellerHome", "Fetching seller profile for user: $userId")
            val result = supabase.postgrest["sellers"]
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<Seller>()

            Log.d("SellerHome", "Profile fetch result: $result")
            result
        } catch (e: Exception) {
            Log.e("SellerHome", "Error fetching seller profile: ${e.message}", e)
            null
        }
    }

    private fun isProfileComplete(profile: Seller): Boolean {
        val isComplete = !profile.store_name.isNullOrEmpty() &&
                !profile.tax_id.isNullOrEmpty() &&
                !profile.store_description.isNullOrEmpty() &&
                !profile.business_address.isNullOrEmpty() &&
                !profile.profile_image.isNullOrEmpty()

        Log.d("SellerHome", "Profile completeness check: $isComplete")
        Log.d("SellerHome", "store_name: ${profile.store_name}")
        Log.d("SellerHome", "tax_id: ${profile.tax_id}")
        Log.d("SellerHome", "store_description: ${profile.store_description}")
        Log.d("SellerHome", "business_address: ${profile.business_address}")
        Log.d("SellerHome", "profile_image: ${profile.profile_image}")

        return isComplete
    }

    private fun logoutSeller() {
        scope.launch {
            try {
                supabase.auth.signOut()
                runOnUiThread {
                    Toast.makeText(this@SellerHomeActivity, "Logged out successfully", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@SellerHomeActivity, SellerLoginActivity::class.java)
                    startActivity(intent)
                    finishAffinity()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@SellerHomeActivity, "Logout failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this activity
        loadDashboardData()
    }
}