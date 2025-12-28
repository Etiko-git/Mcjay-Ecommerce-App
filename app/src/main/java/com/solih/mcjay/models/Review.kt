package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class Review(
    val id: Int? = null,
    val user_id: String,
    val product_id: String,
    val rating: Int,
    val review_text: String? = null,
    val review_image_url: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val user_name: String? = null,
)

@Serializable
data class ReviewInput(
    val user_id: String,
    val product_id: String,
    val rating: Int,
    val review_text: String? = null,
    val review_image_url: String? = null
)