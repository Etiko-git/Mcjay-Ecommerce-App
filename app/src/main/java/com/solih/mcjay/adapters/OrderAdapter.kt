package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.solih.mcjay.R
import com.solih.mcjay.models.Order
import java.text.SimpleDateFormat
import java.util.*

class OrderAdapter(
    private var orders: List<Order>,
    private val onOrderClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    inner class OrderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val orderCard: MaterialCardView = itemView.findViewById(R.id.orderCard)
        val tvOrderNumber: TextView = itemView.findViewById(R.id.tvOrderNumber)
        val tvOrderDate: TextView = itemView.findViewById(R.id.tvOrderDate)
        val chipStatus: Chip = itemView.findViewById(R.id.chipStatus)
        val tvItemsPreview: TextView = itemView.findViewById(R.id.tvItemsPreview)
        val tvOrderTotal: TextView = itemView.findViewById(R.id.tvOrderTotal)
        val btnViewDetails: MaterialButton = itemView.findViewById(R.id.btnViewDetails)
        val btnTrackOrder: MaterialButton = itemView.findViewById(R.id.btnTrackOrder)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]
        val context = holder.itemView.context

        // Set order details using string resources
        holder.tvOrderNumber.text = context.getString(R.string.order_number, order.order_number ?: "N/A")
        holder.tvOrderDate.text = formatDate(order.created_at, context)
        holder.tvOrderTotal.text = context.getString(R.string.order_total, String.format("%.2f", order.total_amount))

        // Set status chip
        holder.chipStatus.text = order.order_status
        setStatusChipStyle(holder.chipStatus, order.order_status)

        // Set items preview placeholder
        holder.tvItemsPreview.text = context.getString(R.string.order_items_loading)

        // Show/hide track order button based on status
        when (order.order_status) {
            "Shipped", "Processing" -> {
                holder.btnTrackOrder.visibility = View.VISIBLE
                holder.btnTrackOrder.setOnClickListener {
                    val message = context.getString(R.string.track_order_toast, order.order_number ?: "")
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                holder.btnTrackOrder.visibility = View.GONE
            }
        }

        // Set button texts
        holder.btnViewDetails.text = context.getString(R.string.view_details)
        holder.btnTrackOrder.text = context.getString(R.string.track_order)

        // Set click listeners
        holder.btnViewDetails.setOnClickListener {
            onOrderClick(order)
        }

        holder.orderCard.setOnClickListener {
            onOrderClick(order)
        }
    }

    private fun formatDate(dateString: String?, context: android.content.Context): String {
        return if (!dateString.isNullOrEmpty()) {
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                outputFormat.format(date ?: Date())
            } catch (e: Exception) {
                // Use string resource for fallback
                context.getString(R.string.recent)
            }
        } else {
            context.getString(R.string.recent)
        }
    }

    private fun setStatusChipStyle(chip: Chip, status: String) {
        when (status) {
            "Pending" -> {
                chip.setChipBackgroundColorResource(R.color.status_pending)
                chip.setTextColor(ContextCompat.getColor(chip.context, android.R.color.white))
            }
            "Confirmed" -> {
                chip.setChipBackgroundColorResource(R.color.status_confirmed)
                chip.setTextColor(ContextCompat.getColor(chip.context, android.R.color.white))
            }
            "Processing" -> {
                chip.setChipBackgroundColorResource(R.color.status_processing)
                chip.setTextColor(ContextCompat.getColor(chip.context, android.R.color.white))
            }
            "Shipped" -> {
                chip.setChipBackgroundColorResource(R.color.status_shipped)
                chip.setTextColor(ContextCompat.getColor(chip.context, android.R.color.white))
            }
            "Delivered" -> {
                chip.setChipBackgroundColorResource(R.color.status_delivered)
                chip.setTextColor(ContextCompat.getColor(chip.context, android.R.color.white))
            }
            else -> {
                chip.setChipBackgroundColorResource(R.color.gray_light)
                chip.setTextColor(ContextCompat.getColor(chip.context, android.R.color.black))
            }
        }
    }

    override fun getItemCount(): Int = orders.size

    fun updateOrders(newOrders: List<Order>) {
        orders = newOrders
        notifyDataSetChanged()
    }
}