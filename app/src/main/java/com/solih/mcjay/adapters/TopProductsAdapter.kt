package com.solih.mcjay.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.solih.mcjay.R
import com.solih.mcjay.models.TopProduct

class TopProductsAdapter(
    private var products: List<TopProduct>
) : RecyclerView.Adapter<TopProductsAdapter.TopProductViewHolder>() {

    inner class TopProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvProductName: TextView = itemView.findViewById(R.id.tvProductName)
        private val tvQuantity: TextView = itemView.findViewById(R.id.tvQuantity)
        private val tvSales: TextView = itemView.findViewById(R.id.tvSales)
        private val tvRank: TextView = itemView.findViewById(R.id.tvRank)

        fun bind(product: TopProduct, position: Int) {
            // Set rank
            tvRank.text = "${position + 1}"

            // Set product name
            tvProductName.text = product.productName

            // Set quantity sold
            tvQuantity.text = "${product.totalQuantity} sold"

            // Set total sales
            tvSales.text = "$${String.format("%.2f", product.totalSales)}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_product, parent, false)
        return TopProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: TopProductViewHolder, position: Int) {
        holder.bind(products[position], position)
    }

    override fun getItemCount(): Int = products.size

    fun updateProducts(newProducts: List<TopProduct>) {
        products = newProducts
        notifyDataSetChanged()
    }
}