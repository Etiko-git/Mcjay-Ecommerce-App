package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val order_id: Int? = null,
    val order_number: String? = null,
    val user_id: String,
    val seller_id: String? = null,
    val total_amount: Double,
    val payment_method: String,
    val payment_status: String = "Pending",
    val order_status: String = "Pending",
    val shipping_address: String,
    val tracking_number: String? = null,
    val delivery_date: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)