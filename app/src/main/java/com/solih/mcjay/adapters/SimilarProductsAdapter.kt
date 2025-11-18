package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.solih.mcjay.R
import com.solih.mcjay.models.Product

class SimilarProductsAdapter(
    private val products: List<Product>,
    private val onProductClick: (Product) -> Unit
) : RecyclerView.Adapter<SimilarProductsAdapter.SimilarProductViewHolder>() {

    inner class SimilarProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val productImage: ImageView = itemView.findViewById(R.id.similar_product_image)
        val productName: TextView = itemView.findViewById(R.id.similar_product_name)
        val productPrice: TextView = itemView.findViewById(R.id.similar_product_price)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimilarProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_similar_product, parent, false)
        return SimilarProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: SimilarProductViewHolder, position: Int) {
        val product = products[position]

        Glide.with(holder.itemView.context)
            .load(product.getFirstImageUrl())
            .placeholder(R.drawable.placeholder_image)
            .into(holder.productImage)

        holder.productName.text = product.name

        val price = if (product.hasDiscount() && product.discount_price != null) {
            "₹${product.discount_price}"
        } else {
            "₹${product.price}"
        }
        holder.productPrice.text = price

        holder.itemView.setOnClickListener {
            onProductClick(product)
        }
    }

    override fun getItemCount(): Int = products.size
}