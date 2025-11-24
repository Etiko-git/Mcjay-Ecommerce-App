package com.solih.mcjay.data.models

import kotlinx.serialization.Serializable

@Serializable
data class AdminProfile(
    val id: String? = null,
    val email: String,
    val full_name: String,
    val password: String? = null,
    val user_type: String = "admin",
    val created_at: String? = null,
    val updated_at: String? = null
)