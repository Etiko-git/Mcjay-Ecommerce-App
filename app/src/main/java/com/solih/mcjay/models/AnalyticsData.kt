package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsData(
    val total_commission_earned: Double = 0.0,
    val company_balance: Double = 0.0,
    val total_orders: Int = 0,
    val total_revenue: Double = 0.0,
    val active_sellers: Int = 0,
    val commission_data: List<CommissionData> = emptyList()
)

@Serializable
data class CommissionData(
    val date: String,
    val commission_earned: Double,
    val orders_count: Int
)