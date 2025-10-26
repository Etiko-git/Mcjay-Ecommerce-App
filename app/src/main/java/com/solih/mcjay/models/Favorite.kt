package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class Favorite(
    val id: Int? = null,
    val user_id: String,
    val product_id: Int, // Should match your database type
    val created_at: String? = null
)