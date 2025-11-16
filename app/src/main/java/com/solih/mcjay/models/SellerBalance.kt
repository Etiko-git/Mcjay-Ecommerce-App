// SellerBalance.kt
package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class SellerBalance(
    val id: Int? = null,
    val seller_id: String,
    val balance: Double = 0.0,
    val total_earnings: Double = 0.0,
    val created_at: String? = null,
    val updated_at: String? = null
)