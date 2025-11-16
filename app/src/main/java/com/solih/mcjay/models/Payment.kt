package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class Payment(
    val payment_id: Int? = null,
    val seller_id: String,
    val user_id: String,
    val order_id: Int? = null,
    val order_number: String? = null,
    val payment_trackingid: String? = null,
    val amount: Double,
    val seller_amount: Double? = null,
    val company_amount: Double? = null,
    val payment_method: String,
    val payment_status: String = "pending",
    val paid_at: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)