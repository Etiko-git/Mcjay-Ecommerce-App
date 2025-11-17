package com.solih.mcjay.models
import kotlinx.serialization.Serializable


@Serializable
data class SellerBalanceUpdate(
    val balance: Double,
    val updated_at: String
)