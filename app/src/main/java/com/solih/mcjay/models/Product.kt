package com.solih.mcjay.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable // Add this annotation
data class Product(
    val id: Int? = null,
    val product_id: String,
    val name: String,
    val description: String? = null,
    val category: String,
    val type: String? = null,
    val brand: String? = null,
    val price: Double,
    val discount_price: Double? = null,
    val stock_quantity: Int = 0,
    val sku: String? = null,
    val images: JsonElement? = null, // Keep as JsonElement for flexibility
    val ratings: Float = 0f,
    val reviews_count: Int = 0,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
) {
    // Helper function to get image URLs
    fun getImageUrls(): List<String> {
        return try {
            // Handle JSON array parsing safely
            images?.toString()?.removeSurrounding("[", "]")?.split(",")?.map {
                it.trim().removeSurrounding("\"")
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Get first image URL or placeholder
    fun getFirstImageUrl(): String {
        return getImageUrls().firstOrNull() ?: "https://via.placeholder.com/300"
    }

    // Check if product has discount
    fun hasDiscount(): Boolean {
        return discount_price != null && discount_price > 0 && discount_price < price
    }

    // Calculate discount percentage
    fun getDiscountPercentage(): Int {
        return if (hasDiscount()) {
            (((price - discount_price!!) / price) * 100).toInt()
        } else {
            0
        }
    }
}