package com.solih.mcjay.activities

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.solih.mcjay.R
import com.solih.mcjay.adapters.TransactionAdapter
import com.solih.mcjay.databinding.ActivityTransactionHistoryBinding
import com.solih.mcjay.models.Transaction
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTransactionHistoryBinding
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var transactionAdapter: TransactionAdapter
    private var sellerId: String = ""
    private val allTransactions = mutableListOf<Transaction>()
    private val displayedTransactions = mutableListOf<Transaction>()
    private var currentFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        getCurrentSeller()
        setupRecyclerView()
        setupFilterButtons()
        loadTransactions()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Transaction History"
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
        transactionAdapter = TransactionAdapter(displayedTransactions)
        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(this@TransactionHistoryActivity)
            adapter = transactionAdapter
        }
    }

    private fun setupFilterButtons() {
        binding.btnAll.setOnClickListener { setFilter("all") }
        binding.btnWithdrawals.setOnClickListener { setFilter("withdrawal") }
        binding.btnSales.setOnClickListener { setFilter("sale") }
        // Check if we should show withdrawals only
        if (intent.getBooleanExtra("show_withdrawals_only", false)) {
            setFilter("withdrawal")
        }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        // Update button states
        updateButtonAppearance(binding.btnAll, filter == "all")
        updateButtonAppearance(binding.btnWithdrawals, filter == "withdrawal")
        updateButtonAppearance(binding.btnSales, filter == "sale")
        applyFilter()
    }

    private fun updateButtonAppearance(button: com.google.android.material.button.MaterialButton, isSelected: Boolean) {
        if (isSelected) {
            button.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500))
            button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        } else {
            button.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
            button.setTextColor(ContextCompat.getColor(this, R.color.purple_500))
        }
    }

    private fun applyFilter() {
        displayedTransactions.clear()
        when (currentFilter) {
            "all" -> displayedTransactions.addAll(allTransactions)
            "withdrawal" -> displayedTransactions.addAll(allTransactions.filter { it.type == "withdrawal" })
            "sale" -> displayedTransactions.addAll(allTransactions.filter { it.type == "sale" })
        }
        transactionAdapter.notifyDataSetChanged()
        if (displayedTransactions.isEmpty()) {
            binding.tvEmpty.visibility = android.view.View.VISIBLE
        } else {
            binding.tvEmpty.visibility = android.view.View.GONE
        }
    }

    private fun loadTransactions() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.tvEmpty.visibility = android.view.View.GONE
        scope.launch {
            try {
                val transactions = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("transactions")
                        .select {
                            filter {
                                eq("seller_id", sellerId)
                            }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<Transaction>()
                }
                allTransactions.clear()
                allTransactions.addAll(transactions)
                applyFilter()
            } catch (e: Exception) {
                Log.e("TransactionHistory", "Error loading transactions: ${e.message}", e)
                Toast.makeText(this@TransactionHistoryActivity, "Error loading transactions", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }
}