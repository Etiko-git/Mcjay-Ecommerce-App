package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.solih.mcjay.R
import com.solih.mcjay.models.Transaction
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private var transactions: List<Transaction>
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)

        fun bind(transaction: Transaction) {
            // Set amount with color based on type
            val amountText = if (transaction.type == "sale") {
                "+₹${String.format("%.2f", transaction.amount)}"
            } else {
                "-₹${String.format("%.2f", transaction.amount)}"
            }

            tvAmount.text = amountText
            tvAmount.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (transaction.type == "sale") R.color.green_600 else R.color.red_600
                )
            )

            // Set description
            tvDescription.text = transaction.description

            // Format and set date
            val date = formatDate(transaction.created_at)
            tvDate.text = date

            // Set status with color
            tvStatus.text = transaction.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val statusColor = when (transaction.status.lowercase()) {
                "completed" -> R.color.green_600
                "pending" -> R.color.orange
                "failed" -> R.color.red_600
                else -> R.color.gray
            }
            tvStatus.setTextColor(ContextCompat.getColor(itemView.context, statusColor))
        }

        private fun formatDate(dateString: String?): String {
            if (dateString.isNullOrEmpty()) return "Unknown date"
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                dateString
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    fun updateTransactions(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }
}