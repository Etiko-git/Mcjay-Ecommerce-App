package com.solih.mcjay.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.solih.mcjay.R
import com.solih.mcjay.models.OrderItem
import com.solih.mcjay.models.Product

class SellerOrderItemsAdapter(
    private var orderItems: List<OrderItem>,
    private val productMap: Map<String, Product>
) : RecyclerView.Adapter<SellerOrderItemsAdapter.OrderItemViewHolder>() {

    inner class OrderItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProductImage: ImageView = itemView.findViewById(R.id.ivProductImage)
        private val tvProductName: TextView = itemView.findViewById(R.id.tvProductName)
        private val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvSubtotal: TextView = itemView.findViewById(R.id.tvSubtotal)
        private val tvItemStatus: TextView = itemView.findViewById(R.id.tvItemStatus)

        fun bind(orderItem: OrderItem) {
            // Get product details from productMap
            val product = productMap[orderItem.product_id]

            // Load product image using the getFirstImageUrl() method from Product class
            val imageUrl = product?.getFirstImageUrl() ?: orderItem.product_image_url
            if (!imageUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_product_placeholder)
                    .error(R.drawable.ic_error)
                    .into(ivProductImage)
            } else {
                ivProductImage.setImageResource(R.drawable.ic_product_placeholder)
            }

            // Set product name - prioritize product name from productMap, fallback to orderItem
            val productName = product?.name ?: orderItem.product_name ?: "Unknown Product"
            tvProductName.text = productName

            // Set quantity and price
            tvQuantity.text = "Qty: ${orderItem.quantity}"
            tvPrice.text = "$${String.format("%.2f", orderItem.price)}"

            // Set subtotal
            tvSubtotal.text = "$${String.format("%.2f", orderItem.subtotal)}"

            // Set item status with color
            tvItemStatus.text = orderItem.item_status
            setStatusColor(orderItem.item_status)
        }

        private fun setStatusColor(status: String) {
            val colorRes = when (status.lowercase()) {
                "pending" -> R.color.orange
                "confirmed" -> R.color.blue
                "processing" -> R.color.purple
                "shipped" -> R.color.teal
                "delivered" -> R.color.green
                "cancelled" -> R.color.red
                else -> R.color.gray
            }
            tvItemStatus.setTextColor(itemView.context.getColor(colorRes))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order_product, parent, false)
        return OrderItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderItemViewHolder, position: Int) {
        holder.bind(orderItems[position])
    }

    override fun getItemCount(): Int = orderItems.size

    fun updateOrderItems(newOrderItems: List<OrderItem>) {
        orderItems = newOrderItems
        notifyDataSetChanged()
    }
}