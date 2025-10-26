package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.solih.mcjay.R
import com.solih.mcjay.models.CartItem
import com.solih.mcjay.models.Product

class CartAdapter(
    private val cartItems: List<CartItem>,
    private val products: Map<Int, Product>, // Map of product_id to Product
    private val onItemAction: (CartItem, String) -> Unit
) : RecyclerView.Adapter<CartAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.cartItemCard)
        val imageView: ImageView = itemView.findViewById(R.id.productImage)
        val nameTextView: TextView = itemView.findViewById(R.id.productName)
        val priceTextView: TextView = itemView.findViewById(R.id.productPrice)
        val quantityTextView: TextView = itemView.findViewById(R.id.quantityText)
        val increaseButton: ImageButton = itemView.findViewById(R.id.increaseButton)
        val decreaseButton: ImageButton = itemView.findViewById(R.id.decreaseButton)
        val removeButton: ImageButton = itemView.findViewById(R.id.removeButton)
        val totalPriceTextView: TextView = itemView.findViewById(R.id.totalPrice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cart, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cartItem = cartItems[position]
        val product = products[cartItem.product_id]

        if (product == null) {
            // Hide or show placeholder if product not found
            holder.nameTextView.text = "Product not available"
            holder.priceTextView.text = "$0.00"
            holder.totalPriceTextView.text = "$0.00"
            holder.quantityTextView.text = cartItem.quantity.toString()

            // Disable buttons if product not found
            holder.increaseButton.isEnabled = false
            holder.decreaseButton.isEnabled = false
            holder.removeButton.isEnabled = true // Still allow removal

            // Set placeholder image
            Glide.with(holder.itemView.context)
                .load(R.drawable.placeholder_image)
                .into(holder.imageView)

            return
        }

        // Load product image
        Glide.with(holder.itemView.context)
            .load(product.getFirstImageUrl())
            .placeholder(R.drawable.placeholder_image)
            .into(holder.imageView)

        holder.nameTextView.text = product.name

        // Calculate price - use discount price if available
        val unitPrice = product.discount_price ?: product.price
        holder.priceTextView.text = "$${String.format("%.2f", unitPrice)}"

        holder.quantityTextView.text = cartItem.quantity.toString()

        // Calculate total price for this item
        val totalPrice = unitPrice * cartItem.quantity
        holder.totalPriceTextView.text = "$${String.format("%.2f", totalPrice)}"

        // Set click listeners
        holder.increaseButton.setOnClickListener {
            onItemAction(cartItem, "increase")
        }

        holder.decreaseButton.setOnClickListener {
            onItemAction(cartItem, "decrease")
        }

        holder.removeButton.setOnClickListener {
            onItemAction(cartItem, "remove")
        }

        // Whole item click
        holder.cardView.setOnClickListener {
            // You can add navigation to product detail here if needed
        }
    }

    override fun getItemCount() = cartItems.size
}