package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val username: String,
    val email: String,
    val mobile: String
)
