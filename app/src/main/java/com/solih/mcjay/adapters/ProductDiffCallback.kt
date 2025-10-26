package com.solih.mcjay.adapters // Or a common 'utils' package

import androidx.recyclerview.widget.DiffUtil
import com.solih.mcjay.models.Product

class ProductDiffCallback : DiffUtil.ItemCallback<Product>() {
    override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean {
        // Your Product.id is nullable, ensure comparison handles this.
        // If both are null, they are not the "same item" unless that's intended.
        // Usually, a non-null unique ID is preferred for areItemsTheSame.
        return oldItem.id != null && oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean {
        return oldItem == newItem // Relies on Product being a data class
    }
}
