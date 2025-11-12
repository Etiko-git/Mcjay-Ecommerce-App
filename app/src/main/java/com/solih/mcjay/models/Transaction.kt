package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class Transaction(
    val transaction_id: Int? = null,
    val seller_id: String,
    val amount: Double,
    val type: String, // "sale" or "withdrawal"
    val description: String,
    val status: String, // "completed", "pending", "failed"
    val created_at: String? = null
)