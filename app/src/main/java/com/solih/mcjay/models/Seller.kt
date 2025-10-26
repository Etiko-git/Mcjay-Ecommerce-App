package com.solih.mcjay.models

import java.io.Serializable
import kotlinx.serialization.Serializable as KSerializable

@KSerializable
data class Seller(
    val id: String,
    val full_name: String,
    val email: String,
    val mobile_number: String,
    val user_type: String = "seller",
    val created_at: Long,
    val is_verified: Boolean = false,
    val store_name: String? = null,
    val tax_id: String? = null,
    val store_description: String? = null,
    val business_address: String? = null,
    val profile_image: String? = null,
    val updated_at: Long? = null
) : Serializable