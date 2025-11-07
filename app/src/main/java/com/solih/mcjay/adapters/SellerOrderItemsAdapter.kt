package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.solih.mcjay.R
import com.solih.mcjay.models.OrderItem

class SellerOrderItemsAdapter(
    private var orderItems: List<OrderItem>
) : RecyclerView.Adapter<SellerOrderItemsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProductImage: ImageView = itemView.findViewById(R.id.ivProductImage)
        val tvProductName: TextView = itemView.findViewById(R.id.tvProductName)
        val tvProductPrice: TextView = itemView.findViewById(R.id.tvProductPrice)
        val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        val tvSubtotal: TextView = itemView.findViewById(R.id.tvSubtotal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_seller_order_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val orderItem = orderItems[position]

        // Load product image using Glide
        if (!orderItem.product_image_url.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(orderItem.product_image_url)
                .apply(RequestOptions()
                    .transform(RoundedCorners(8))
                    .placeholder(R.drawable.ic_image_placeholder) // Use your placeholder
                    .error(R.drawable.error_image)) // Use your error image
                .into(holder.ivProductImage)
        } else {
            // Set placeholder if no image URL
            holder.ivProductImage.setImageResource(R.drawable.ic_image_placeholder)
        }

        // Set other product information
        holder.tvProductName.text = orderItem.product_name ?: "Product ${orderItem.product_id}"
        holder.tvProductPrice.text = "$${String.format("%.2f", orderItem.price)}"
        holder.tvQuantity.text = "Qty: ${orderItem.quantity}"
        holder.tvSubtotal.text = "$${String.format("%.2f", orderItem.subtotal)}"


    }

    override fun getItemCount(): Int = orderItems.size

    fun updateOrderItems(newOrderItems: List<OrderItem>) {
        orderItems = newOrderItems
        notifyDataSetChanged()
    }
}