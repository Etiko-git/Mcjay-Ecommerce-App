package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class Favorite(
    val user_id: String,
    val product_id: String
)