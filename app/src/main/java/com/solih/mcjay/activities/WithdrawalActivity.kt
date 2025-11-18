package com.solih.mcjay.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.solih.mcjay.R
import com.solih.mcjay.databinding.ActivityWithdrawalBinding
import com.solih.mcjay.models.SellerBalance
import com.solih.mcjay.models.Transaction
import com.solih.mcjay.models.Withdrawal
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
        // Withdraw button
        binding.btnWithdraw.setOnClickListener {
            validateAndShowConfirmation()
        }

        // View history button
        binding.btnViewWithdrawalHistory.setOnClickListener {
            val intent = Intent(this, TransactionHistoryActivity::class.java).apply {
                putExtra("show_withdrawals_only", true)
            }
            startActivity(intent)
        }
    }

    private fun validateAndShowConfirmation() {
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
            R.id.rbBank -> "bank"
            R.id.rbCard -> "card"
            else -> {
                Toast.makeText(this, "Please select payment method", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Show confirmation dialog
        showConfirmationDialog(amount, selectedPaymentMethod)
    }

    private fun showConfirmationDialog(amount: Double, paymentMethod: String) {
        val paymentMethodText = when (paymentMethod) {
            "bank" -> "Bank Transfer"
            "card" -> "Card"
            else -> paymentMethod
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Confirm Withdrawal")
            .setMessage("Are you sure you want to withdraw ₹${String.format("%.2f", amount)} via $paymentMethodText?")
            .setPositiveButton("Yes") { dialog, _ ->
                dialog.dismiss()
                processWithdrawal(amount, paymentMethod)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun processWithdrawal(amount: Double, paymentMethod: String) {
        binding.progressBar.visibility = android.view.View.VISIBLE
        scope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    processWithdrawalInBackground(amount, paymentMethod)
                }

                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    if (success) {
                        Toast.makeText(this@WithdrawalActivity, "Withdrawal processed successfully", Toast.LENGTH_SHORT).show()
                        // Clear form
                        binding.etAmount.setText("")
                        binding.rgPaymentMethod.clearCheck()
                        // Reload balance
                        loadBalanceData()
                    } else {
                        Toast.makeText(this@WithdrawalActivity, "Error processing withdrawal", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Withdrawal", "Error processing withdrawal: ${e.message}", e)
                runOnUiThread {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this@WithdrawalActivity, "Error processing withdrawal", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun processWithdrawalInBackground(amount: Double, paymentMethod: String): Boolean {
        return try {
            // 1. Get current balance
            val currentBalance = getSellerBalance()

            if (currentBalance != null && currentBalance.balance >= amount) {
                val currentTime = getCurrentTime()

                // 2. Create updated seller balance object
                val updatedBalance = SellerBalance(
                    id = currentBalance.id,
                    seller_id = sellerId,
                    balance = currentBalance.balance - amount,
                    total_earnings = currentBalance.total_earnings,
                    created_at = currentBalance.created_at,
                    updated_at = currentTime
                )

                // Update seller balance using upsert (insert or update)
                supabase.postgrest.from("seller_balance")
                    .upsert(updatedBalance)

                // 3. Create withdrawal record
                val withdrawal = Withdrawal(
                    seller_id = sellerId,
                    amount = amount,
                    payment_method = paymentMethod,
                    status = "completed",
                    created_at = currentTime,
                    updated_at = currentTime
                )

                supabase.postgrest.from("withdrawals")
                    .insert(withdrawal)

                // 4. Create transaction record
                val paymentMethodText = when (paymentMethod) {
                    "bank" -> "Bank Transfer"
                    "card" -> "Card"
                    else -> paymentMethod
                }

                val transaction = Transaction(
                    seller_id = sellerId,
                    amount = amount,
                    type = "withdrawal",
                    description = "Withdrawal via $paymentMethodText",
                    status = "completed",
                    created_at = currentTime
                )

                supabase.postgrest.from("transactions")
                    .insert(transaction)

                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("Withdrawal", "Error in background withdrawal processing: ${e.message}", e)
            false
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private suspend fun getSellerBalance(): SellerBalance? {
        return try {
            supabase.postgrest.from("seller_balance")
                .select {
                    filter {
                        eq("seller_id", sellerId)
                    }
                }
                .decodeSingleOrNull<SellerBalance>()
        } catch (e: Exception) {
            null
        }
    }

    private fun loadBalanceData() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        scope.launch {
            try {
                // Get balance from seller_balance table
                val sellerBalance = withContext(Dispatchers.IO) {
                    getSellerBalance()
                }

                availableBalance = sellerBalance?.balance ?: 0.0

                runOnUiThread {
                    binding.tvAvailableBalance.text = "₹${String.format("%.2f", availableBalance)}"
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

    override fun onResume() {
        super.onResume()
        loadBalanceData()
    }
}