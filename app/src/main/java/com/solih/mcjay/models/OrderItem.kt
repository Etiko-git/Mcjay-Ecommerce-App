package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class OrderItem(
    val order_item_id: Int? = null,
    val order_id: Int,
    val order_number: String, // New field
    val product_id: String,
    val seller_id: String,
    val quantity: Int,
    val price: Double,
    val subtotal: Double,
    val item_status: String = "Pending",
    val created_at: String? = null,
    val updated_at: String? = null,
    val product_name: String? = null,
    val product_image_url: String? = null
)