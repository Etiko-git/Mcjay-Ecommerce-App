// Withdrawal.kt
package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class Withdrawal(
    val withdraw_id: Int? = null,
    val seller_id: String,
    val amount: Double,
    val payment_method: String,
    val status: String = "completed",
    val created_at: String? = null,
    val updated_at: String? = null
)