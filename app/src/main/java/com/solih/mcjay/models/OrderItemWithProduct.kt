package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class OrderItemWithProduct(
    val order_item_id: Int? = null,
    val order_id: Int,
    val order_number: String,
    val product_id: String,
    val seller_id: String,
    val quantity: Int,
    val price: Double,
    val subtotal: Double,
    val item_status: String = "Pending",
    val created_at: String? = null,
    val updated_at: String? = null,
    val product_name: String? = null,
    val product_image_url: String? = null,
    val products: Product? = null
) {
    // Helper method to get the product name with fallbacks
    fun getDisplayProductName(): String {
        return products?.name ?: product_name ?: "Unknown Product"
    }

    // Helper method to get the first image URL with fallbacks
    fun getDisplayImageUrl(): String {
        return products?.getFirstImageUrl() ?: product_image_url ?: "https://via.placeholder.com/300x300/FFFFFF/CCCCCC?text=No+Image"
    }
}