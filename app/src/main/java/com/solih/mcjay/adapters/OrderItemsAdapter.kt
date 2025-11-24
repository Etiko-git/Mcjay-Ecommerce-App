package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.solih.mcjay.R
import com.solih.mcjay.models.OrderItem

class OrderItemsAdapter(
    private val orderItems: List<OrderItem>
) : RecyclerView.Adapter<OrderItemsAdapter.OrderItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_order_product_admin, parent, false)
        return OrderItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderItemViewHolder, position: Int) {
        holder.bind(orderItems[position])
    }

    override fun getItemCount(): Int = orderItems.size

    inner class OrderItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProduct: ImageView = itemView.findViewById(R.id.ivProduct)
        private val tvProductName: TextView = itemView.findViewById(R.id.tvProductName)
        private val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvSubtotal: TextView = itemView.findViewById(R.id.tvSubtotal)
        private val tvItemStatus: TextView = itemView.findViewById(R.id.tvItemStatus)

        fun bind(orderItem: OrderItem) {
            // Load product image if available
            if (!orderItem.product_image_url.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(orderItem.product_image_url)
                    .placeholder(R.drawable.ic_placeholder)
                    .error(R.drawable.ic_placeholder)
                    .into(ivProduct)
            } else {
                ivProduct.setImageResource(R.drawable.ic_placeholder)
            }

            // Set product name (use the enriched name or fallback)
            tvProductName.text = orderItem.product_name ?: "Product ID: ${orderItem.product_id}"
            tvQuantity.text = "Qty: ${orderItem.quantity}"
            tvPrice.text = "Price: $${orderItem.price}"
            tvSubtotal.text = "Subtotal: $${orderItem.subtotal}"
            tvItemStatus.text = orderItem.item_status

            // Update status background
            updateStatusBackground(tvItemStatus, orderItem.item_status)
        }

        private fun updateStatusBackground(textView: TextView, status: String) {
            val backgroundRes = when (status.lowercase()) {
                "completed", "delivered" -> R.drawable.bg_status_completed
                "pending", "processing" -> R.drawable.bg_status_pending
                "cancelled", "failed" -> R.drawable.bg_status_cancelled
                "shipped" -> R.drawable.bg_status_shipped
                else -> R.drawable.bg_status_pending
            }

            textView.setBackgroundResource(backgroundRes)
        }
    }
}