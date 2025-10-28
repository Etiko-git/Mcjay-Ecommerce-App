package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class CartItem(
    val id: Int? = null,
    val user_id: String,
    val product_id: Int,
    val quantity: Int,
    val price: Double,
    val created_at: String? = null,
    val updated_at: String? = null
)