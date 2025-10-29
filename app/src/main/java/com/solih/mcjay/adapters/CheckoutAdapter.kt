package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.solih.mcjay.R
import com.solih.mcjay.models.CartItem
import com.solih.mcjay.models.Product

class CheckoutAdapter(
    private val cartItems: List<CartItem>,
    private val productsMap: Map<Int, Product>
) : RecyclerView.Adapter<CheckoutAdapter.CheckoutViewHolder>() {

    inner class CheckoutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val productImage: ImageView = itemView.findViewById(R.id.productImage)
        val productName: TextView = itemView.findViewById(R.id.productName)
        val productPrice: TextView = itemView.findViewById(R.id.productPrice)
        val productQuantity: TextView = itemView.findViewById(R.id.productQuantity)
        val itemTotal: TextView = itemView.findViewById(R.id.itemTotal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CheckoutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_checkout_product, parent, false)
        return CheckoutViewHolder(view)
    }

    override fun onBindViewHolder(holder: CheckoutViewHolder, position: Int) {
        val cartItem = cartItems[position]
        val product = productsMap[cartItem.product_id]

        product?.let {
            // Load product image
            if (!it.getFirstImageUrl().isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(it.getFirstImageUrl())
                    .placeholder(R.drawable.placeholder_image)
                    .into(holder.productImage)
            }

            holder.productName.text = it.name
            holder.productPrice.text = "$${String.format("%.2f", it.discount_price ?: it.price)}"
            holder.productQuantity.text = "Qty: ${cartItem.quantity}"

            val itemTotal = (it.discount_price ?: it.price) * cartItem.quantity
            holder.itemTotal.text = "$${String.format("%.2f", itemTotal)}"
        }
    }

    override fun getItemCount() = cartItems.size
}