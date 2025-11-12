package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class TopProduct(
    val productId: String,
    val productName: String,
    val totalQuantity: Int,
    val totalSales: Double
)