package com.solih.mcjay.models

data class UserProfile(
    val id: String,
    val name: String?,
    val email: String?,
    val mobile: String?,
    val address: String?,
    val profile_image_url: String?,
    val profile_complete: Boolean?
)
