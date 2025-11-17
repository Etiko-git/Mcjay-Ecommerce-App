package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class OrderItemActual(
    val order_item_id: Int? = null,
    val order_id: Int? = null,
    val product_id: String? = null,
    val quantity: Int? = null,
    val price: Double? = null,
    val item_status_type: String? = null,
    val created_at: String? = null
)

@Serializable
data class DailySalesResponseActual(
    val created_at: String? = null,
    val quantity: Int? = null,
    val price: Double? = null,
    val item_status_type: String? = null,
    val product_id: String? = null  // Add this field
)

@Serializable
data class TopProductResponseActual(
    val product_id: String? = null,
    val quantity: Int? = null,
    val price: Double? = null,
    val item_status_type: String? = null
)

@Serializable
data class ProductResponse(
    val product_id: String? = null,
    val name: String? = null
)

@Serializable
data class SalesData(
    val totalSales: Double,
    val orderCount: Int,
    val totalItems: Int
)

@Serializable
data class OrderItemDetail(
    val date: String,
    val quantity: Int,
    val productName: String,
    val amount: Double
)

@Serializable
data class DailySales(
    val date: String,
    val sales: Double
)

@Serializable
data class TopProduct(
    val productId: String,
    val productName: String,
    val totalQuantity: Int,
    val totalSales: Double
)

