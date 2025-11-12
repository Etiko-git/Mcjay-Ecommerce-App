package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.solih.mcjay.R
import com.solih.mcjay.adapters.TransactionAdapter
import com.solih.mcjay.databinding.ActivitySellerEarningsBinding
import com.solih.mcjay.models.Transaction
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SellerEarningsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySellerEarningsBinding
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var transactionAdapter: TransactionAdapter
    private var sellerId: String = ""
    private val transactions = mutableListOf<Transaction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySellerEarningsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        getCurrentSeller()
        setupRecyclerView()
        setupClickListeners()
        loadEarningsData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Earnings & Balance"
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun getCurrentSeller() {
        val currentUser = supabase.auth.currentUserOrNull()
        if (currentUser != null) {
            sellerId = currentUser.id
        } else {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(transactions)
        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(this@SellerEarningsActivity)
            adapter = transactionAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnWithdraw.setOnClickListener {
            val intent = Intent(this, WithdrawalActivity::class.java)
            startActivity(intent)
        }
        binding.btnViewWithdrawalHistory.setOnClickListener {
            val intent = Intent(this, TransactionHistoryActivity::class.java).apply {
                putExtra("show_withdrawals_only", true)
            }
            startActivity(intent)
        }
    }

    private fun loadEarningsData() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvEmpty.visibility = android.view.View.GONE
        scope.launch {
            try {
                // Load total balance and pending amount
                loadBalanceData()
                // Load monthly earnings
                loadMonthlyEarnings()
                // Load recent transactions
                loadRecentTransactions()
            } catch (e: Exception) {
                Log.e("SellerEarnings", "Error loading earnings data: ${e.message}", e)
                Toast.makeText(this@SellerEarningsActivity, "Error loading data", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private suspend fun loadBalanceData() {
        try {
            // Calculate total balance from delivered orders
            val deliveredOrders = withContext(Dispatchers.IO) {
                supabase.postgrest.from("order_items")
                    .select {
                        filter {
                            eq("seller_id", sellerId)
                            eq("item_status", "Delivered")
                        }
                    }
                    .decodeList<Map<String, Any>>()
            }
            val totalBalance = deliveredOrders.sumOf { it["subtotal"] as? Double ?: 0.0 }
            // Calculate pending balance from non-delivered orders
            val pendingOrders = withContext(Dispatchers.IO) {
                supabase.postgrest.from("order_items")
                    .select {
                        filter {
                            eq("seller_id", sellerId)
                            neq("item_status", "Delivered") // Use neq instead of not
                        }
                    }
                    .decodeList<Map<String, Any>>()
            }
            val pendingBalance = pendingOrders.sumOf { it["subtotal"] as? Double ?: 0.0 }
            runOnUiThread {
                binding.tvTotalBalance.text = "$${String.format("%.2f", totalBalance)}"
                binding.tvPendingBalance.text = "Pending: $${String.format("%.2f", pendingBalance)}"
            }
        } catch (e: Exception) {
            Log.e("SellerEarnings", "Error loading balance data: ${e.message}")
        }
    }

    private suspend fun loadMonthlyEarnings() {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
            val currentMonth = dateFormat.format(Date())
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -1)
            val lastMonth = dateFormat.format(calendar.time)
            // This month earnings
            val thisMonthEarnings = withContext(Dispatchers.IO) {
                supabase.postgrest.from("order_items")
                    .select {
                        filter {
                            eq("seller_id", sellerId)
                            eq("item_status", "Delivered")
                            like("created_at", "$currentMonth%")
                        }
                    }
                    .decodeList<Map<String, Any>>()
            }.sumOf { it["subtotal"] as? Double ?: 0.0 }
            // Last month earnings
            val lastMonthEarnings = withContext(Dispatchers.IO) {
                supabase.postgrest.from("order_items")
                    .select {
                        filter {
                            eq("seller_id", sellerId)
                            eq("item_status", "Delivered")
                            like("created_at", "$lastMonth%")
                        }
                    }
                    .decodeList<Map<String, Any>>()
            }.sumOf { it["subtotal"] as? Double ?: 0.0 }
            runOnUiThread {
                binding.tvThisMonthEarnings.text = "$${String.format("%.2f", thisMonthEarnings)}"
                binding.tvLastMonthEarnings.text = "$${String.format("%.2f", lastMonthEarnings)}"
            }
        } catch (e: Exception) {
            Log.e("SellerEarnings", "Error loading monthly earnings: ${e.message}")
        }
    }

    private suspend fun loadRecentTransactions() {
        try {
            val recentTransactions = withContext(Dispatchers.IO) {
                supabase.postgrest.from("transactions")
                    .select {
                        filter {
                            eq("seller_id", sellerId)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(10)
                    }
                    .decodeList<Transaction>()
            }
            transactions.clear()
            transactions.addAll(recentTransactions)
            transactionAdapter.notifyDataSetChanged()
            runOnUiThread {
                if (transactions.isEmpty()) {
                    binding.tvEmpty.visibility = android.view.View.VISIBLE
                } else {
                    binding.tvEmpty.visibility = android.view.View.GONE
                }
            }
        } catch (e: Exception) {
            Log.e("SellerEarnings", "Error loading transactions: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        loadEarningsData()
    }
}