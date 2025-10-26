package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class Favorite(
    val id: String? = null,
    val user_id: String, // Changed from userid
    val product_id: String,
    val created_at: String? = null
) {
    companion object {
        fun create(userId: String, productId: String): Favorite {
            return Favorite(
                user_id = userId,
                product_id = productId
            )
        }
    }
}