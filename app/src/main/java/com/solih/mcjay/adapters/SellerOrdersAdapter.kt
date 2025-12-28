package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.solih.mcjay.R
import com.solih.mcjay.models.Order
import com.solih.mcjay.models.OrderItem
import com.solih.mcjay.models.User
import java.text.SimpleDateFormat
import java.util.*

class SellerOrdersAdapter(
    private var orders: List<Order>,
    private val orderItemsMap: Map<Int, List<OrderItem>>,
    private var customerMap: Map<String, User>, // Change from 'val' to 'var'
    private val onOrderClick: (Order) -> Unit
) : RecyclerView.Adapter<SellerOrdersAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOrderNumber: TextView = itemView.findViewById(R.id.tvOrderNumber)
        val tvOrderDate: TextView = itemView.findViewById(R.id.tvOrderDate)
        val tvCustomerName: TextView = itemView.findViewById(R.id.tvCustomerName)
        val tvTotalAmount: TextView = itemView.findViewById(R.id.tvTotalAmount)
        val tvItemCount: TextView = itemView.findViewById(R.id.tvItemCount)
        val tvOrderStatus: TextView = itemView.findViewById(R.id.tvOrderStatus)
        val tvShippingAddress: TextView = itemView.findViewById(R.id.tvShippingAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_seller_order, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val order = orders[position]
        val orderItems = orderItemsMap[order.order_id] ?: emptyList()
        val customer = customerMap[order.user_id] // Get customer from customerMap

        // Set basic order information
        holder.tvOrderNumber.text = "Order #${order.order_number ?: "N/A"}"
        holder.tvOrderDate.text = formatDateSimple(order.created_at)
        holder.tvTotalAmount.text = "₹${String.format("%.2f", order.total_amount)}"
        holder.tvItemCount.text = "${orderItems.size} items"
        holder.tvShippingAddress.text = order.shipping_address ?: "No address"

        // Customer info - now using actual customer data
        holder.tvCustomerName.text = if (customer != null) {
            "${customer.name ?: "Customer"} (${customer.email ?: "No email"})"
        } else {
            "Customer ID: ${order.user_id?.take(8) ?: "Unknown"}..."
        }

        // Order status with simple color coding
        holder.tvOrderStatus.text = order.order_status
        setSimpleOrderStatusStyle(holder.tvOrderStatus, order.order_status)

        holder.itemView.setOnClickListener {
            onOrderClick(order)
        }
    }

    override fun getItemCount(): Int = orders.size

    fun updateOrders(newOrders: List<Order>) {
        orders = newOrders
        notifyDataSetChanged()
    }

    fun updateCustomerMap(newCustomerMap: Map<String, User>) {
        customerMap = newCustomerMap
        notifyDataSetChanged()
    }

    private fun setSimpleOrderStatusStyle(textView: TextView, status: String) {
        val colorRes = when (status.lowercase()) {
            "pending" -> R.color.orange
            "confirmed" -> R.color.blue
            "processing" -> R.color.purple_700
            "shipped" -> android.R.color.holo_blue_light
            "delivered" -> android.R.color.holo_green_dark
            "cancelled" -> android.R.color.holo_red_dark
            else -> android.R.color.darker_gray
        }

        textView.setTextColor(ContextCompat.getColor(textView.context, colorRes))
    }

    private fun formatDateSimple(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return "No date"

        return try {
            // Simple format that works with most date strings
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

            // Extract just the date part if it's a full timestamp
            val datePart = dateString.substring(0, minOf(10, dateString.length))
            val date = inputFormat.parse(datePart)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            "Invalid date"
        }
    }
}