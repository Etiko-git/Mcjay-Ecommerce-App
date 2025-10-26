package com.solih.mcjay.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Product(
    val id: Int? = null,  // Changed from String to Int (SERIAL PRIMARY KEY)
    val product_id: String,  // Added this field (VARCHAR(20))
    val name: String,
    val description: String? = null,
    val category: String,  // Changed from product_category to String
    val type: String? = null,
    val brand: String? = null,
    val price: Double,
    val discount_price: Double? = null,
    val stock_quantity: Int = 0,
    val sku: String? = null,
    val images: JsonElement? = null,
    val ratings: Float = 0f,
    val reviews_count: Int = 0,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null
) {
    // Helper function to get image URLs
    fun getImageUrls(): List<String> {
        return try {
            images?.toString()?.removeSurrounding("[", "]")?.split(",")?.map {
                it.trim().removeSurrounding("\"")
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Get first image URL or placeholder
    fun getFirstImageUrl(): String {
        return getImageUrls().firstOrNull()?.takeIf { it.isNotBlank() }
            ?: "https://via.placeholder.com/300"
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

    // Use product_id as the main ID for the app
    fun getIdString(): String {
        return product_id
    }
}