package com.solih.mcjay.models

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
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
    val images: JsonElement? = null,
    val ratings: Float = 0f,
    val reviews_count: Int = 0,
    val is_active: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null,
    // New seller fields
    val seller_id: String? = null,
    val seller_name: String? = null
){
    // Improved image URL extraction for storage URLs
    fun getImageUrls(): List<String> {
        return try {
            when (images) {
                null -> emptyList()
                else -> {
                    val jsonString = images.toString()
                    // Handle both JSON array format and string format
                    if (jsonString.startsWith('[') && jsonString.endsWith(']')) {
                        jsonString.removeSurrounding("[", "]")
                            .split(",")
                            .map { it.trim().removeSurrounding("\"") }
                            .filter { it.isNotBlank() }
                    } else {
                        listOf(jsonString.removeSurrounding("\""))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Product", "Error parsing image URLs: ${e.message}")
            emptyList()
        }
    }

    // Get first image URL or placeholder - IMPROVED for storage URLs
    fun getFirstImageUrl(): String {
        return getImageUrls().firstOrNull()?.takeIf { it.isNotBlank() }
            ?: "https://via.placeholder.com/300x300/FFFFFF/CCCCCC?text=No+Image"
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

    // Check if product is in stock
    fun isInStock(): Boolean {
        return stock_quantity > 0 && is_active
    }
}