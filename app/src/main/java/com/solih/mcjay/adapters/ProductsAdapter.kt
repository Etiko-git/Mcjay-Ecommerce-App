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

class ProductsAdapter(
    private val products: List<Product>,
    private val onDeleteClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductsAdapter.ProductViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product_admin, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount(): Int = products.size

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivProduct: ImageView = itemView.findViewById(R.id.ivProduct)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvSeller: TextView = itemView.findViewById(R.id.tvSeller)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvPrice)
        private val tvStock: TextView = itemView.findViewById(R.id.tvStock)
        private val btnDelete: View = itemView.findViewById(R.id.btnDelete)

        fun bind(product: Product) {
            // Load image
            Glide.with(itemView.context)
                .load(product.getFirstImageUrl())
                .placeholder(R.drawable.ic_product_placeholder)
                .into(ivProduct)

            tvName.text = product.name
            tvSeller.text = "Seller: ${product.seller_name ?: "Unknown"}"
            tvPrice.text = "$${product.price}"
            tvStock.text = "Stock: ${product.stock_quantity}"

            btnDelete.setOnClickListener {
                onDeleteClick(product)
            }
        }
    }
}