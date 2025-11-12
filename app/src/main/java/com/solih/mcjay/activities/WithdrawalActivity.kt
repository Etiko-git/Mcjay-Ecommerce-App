package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.solih.mcjay.R
import com.solih.mcjay.databinding.ActivityWithdrawalBinding
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WithdrawalActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWithdrawalBinding
    private val supabase = com.solih.mcjay.SupabaseClientInstance.client
    private val scope = CoroutineScope(Dispatchers.Main)
    private var sellerId: String = ""
    private var availableBalance: Double = 0.0
    private var pendingBalance: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWithdrawalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        getCurrentSeller()
        setupClickListeners()
        loadBalanceData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Withdraw Funds"
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

    private fun setupClickListeners() {
        // Payment method selection
        binding.rgPaymentMethod.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbBankTransfer -> binding.bankDetails.visibility = android.view.View.VISIBLE
                else -> binding.bankDetails.visibility = android.view.View.GONE
            }
        }
        // Withdraw button
        binding.btnWithdraw.setOnClickListener {
            requestWithdrawal()
        }
        // View history button
        binding.btnViewWithdrawalHistory.setOnClickListener {
            val intent = Intent(this, TransactionHistoryActivity::class.java).apply {
                putExtra("show_withdrawals_only", true)
            }
            startActivity(intent)
        }
    }

    private fun loadBalanceData() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        scope.launch {
            try {
                // Calculate available balance from delivered orders
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
                availableBalance = deliveredOrders.sumOf { it["subtotal"] as? Double ?: 0.0 }
                // Calculate pending balance from non-delivered orders
                val pendingOrders = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("order_items")
                        .select {
                            filter {
                                eq("seller_id", sellerId)
                                neq("item_status", "Delivered")
                            }
                        }
                        .decodeList<Map<String, Any>>()
                }
                pendingBalance = pendingOrders.sumOf { it["subtotal"] as? Double ?: 0.0 }
                runOnUiThread {
                    binding.tvAvailableBalance.text = "$${String.format("%.2f", availableBalance)}"
                    binding.tvPendingBalance.text = "Pending: $${String.format("%.2f", pendingBalance)}"
                    binding.progressBar.visibility = android.view.View.GONE
                }
            } catch (e: Exception) {
                Log.e("Withdrawal", "Error loading balance data: ${e.message}")
                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this@WithdrawalActivity, "Error loading balance", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestWithdrawal() {
        val amountText = binding.etAmount.text.toString()
        if (amountText.isEmpty()) {
            Toast.makeText(this, "Please enter amount", Toast.LENGTH_SHORT).show()
            return
        }
        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Please enter valid amount", Toast.LENGTH_SHORT).show()
            return
        }
        if (amount > availableBalance) {
            Toast.makeText(this, "Insufficient balance", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedPaymentMethod = when (binding.rgPaymentMethod.checkedRadioButtonId) {
            R.id.rbBankTransfer -> "bank_transfer"
            R.id.rbPaypal -> "paypal"
            R.id.rbStripe -> "stripe"
            else -> {
                Toast.makeText(this, "Please select payment method", Toast.LENGTH_SHORT).show()
                return
            }
        }
        // Validate bank details if bank transfer is selected
        if (selectedPaymentMethod == "bank_transfer") {
            val accountNumber = binding.etAccountNumber.text.toString()
            val routingNumber = binding.etRoutingNumber.text.toString()
            val bankName = binding.etBankName.text.toString()
            if (accountNumber.isEmpty() || routingNumber.isEmpty() || bankName.isEmpty()) {
                Toast.makeText(this, "Please fill all bank details", Toast.LENGTH_SHORT).show()
                return
            }
        }
        binding.progressBar.visibility = android.view.View.VISIBLE
        scope.launch {
            try {
                // Create withdrawal record
                val withdrawalData = mapOf(
                    "seller_id" to sellerId,
                    "amount" to amount,
                    "payment_method" to selectedPaymentMethod,
                    "status" to "pending",
                    "created_at" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                )
                withContext(Dispatchers.IO) {
                    supabase.postgrest.from("withdrawals")
                        .insert(withdrawalData)
                }
                // Create transaction record
                val transactionData = mapOf(
                    "seller_id" to sellerId,
                    "amount" to amount,
                    "type" to "withdrawal",
                    "description" to "Withdrawal via $selectedPaymentMethod",
                    "status" to "pending",
                    "created_at" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                )
                withContext(Dispatchers.IO) {
                    supabase.postgrest.from("transactions")
                        .insert(transactionData)
                }
                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this@WithdrawalActivity, "Withdrawal request submitted successfully", Toast.LENGTH_SHORT).show()
                    // Clear form
                    binding.etAmount.setText("")
                    binding.rgPaymentMethod.clearCheck()
                    binding.bankDetails.visibility = android.view.View.GONE
                    // Reload balance
                    loadBalanceData()
                }
            } catch (e: Exception) {
                Log.e("Withdrawal", "Error requesting withdrawal: ${e.message}")
                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this@WithdrawalActivity, "Error submitting withdrawal request", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadBalanceData()
    }
}