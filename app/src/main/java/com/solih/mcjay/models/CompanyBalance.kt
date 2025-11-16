// CompanyBalance.kt
package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class CompanyBalance(
    val id: Int? = null,
    val company_balance: Double = 0.0,
    val total_commission_earned: Double = 0.0,
    val created_at: String? = null,
    val updated_at: String? = null
)