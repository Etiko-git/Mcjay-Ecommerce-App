package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.solih.mcjay.R
import com.solih.mcjay.models.Order
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AllOrdersAdapter(
    private val orders: List<Order>,
    private val onOrderClick: (Order) -> Unit
) : RecyclerView.Adapter<AllOrdersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Assuming your admin_item_order.xml has these IDs
        val tvOrderNumber: TextView = view.findViewById(R.id.tvOrderNumber)
        val tvOrderDate: TextView = view.findViewById(R.id.tvOrderDate)
        val tvTotalAmount: TextView = view.findViewById(R.id.tvTotalAmount)
        val tvOrderStatus: TextView = view.findViewById(R.id.tvOrderStatus)
        val tvPaymentStatus: TextView = view.findViewById(R.id.tvPaymentStatus)

        // If your layout has different IDs, adjust these accordingly
        // Example: val tvOrderNumber = view.findViewById<TextView>(R.id.your_order_number_id)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Using admin_item_order.xml layout
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.admin_item_order, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val order = orders[position]

        holder.tvOrderNumber.text = order.order_number ?: "N/A"
        holder.tvOrderDate.text = formatDate(order.created_at ?: "")
        holder.tvTotalAmount.text = "$${order.total_amount}"
        holder.tvOrderStatus.text = order.order_status
        holder.tvPaymentStatus.text = order.payment_status

        // Update status colors
        updateStatusBackground(holder.tvOrderStatus, order.order_status)
        updateStatusBackground(holder.tvPaymentStatus, order.payment_status)

        holder.itemView.setOnClickListener {
            onOrderClick(order)
        }
    }

    override fun getItemCount() = orders.size

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            // Try alternative format if the first one fails
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } catch (e2: Exception) {
                dateString
            }
        }
    }

    private fun updateStatusBackground(textView: TextView, status: String) {
        val backgroundRes = when (status.lowercase()) {
            "completed", "paid", "delivered" -> R.drawable.bg_status_completed
            "pending", "processing" -> R.drawable.bg_status_pending
            "cancelled", "failed", "refunded" -> R.drawable.bg_status_cancelled
            "shipped" -> R.drawable.bg_status_shipped
            else -> R.drawable.bg_status_pending
        }

        textView.setBackgroundResource(backgroundRes)
    }
}