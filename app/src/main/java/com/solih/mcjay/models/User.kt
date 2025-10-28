package com.solih.mcjay.models

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String? = null,
    val username: String,
    val email: String? = null,
    val mobile: String? = null,
    val profile_image: String? = null,
    val address: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
) {
    fun isProfileComplete(): Boolean {
        return !name.isNullOrEmpty() &&
                !email.isNullOrEmpty() &&
                !mobile.isNullOrEmpty() &&
                !address.isNullOrEmpty()
    }

    fun getCompletionPercentage(): Int {
        var completed = 0
        val totalFields = 4 // name, email, mobile, address

        if (!name.isNullOrEmpty()) completed++
        if (!email.isNullOrEmpty()) completed++
        if (!mobile.isNullOrEmpty()) completed++
        if (!address.isNullOrEmpty()) completed++

        return (completed * 100) / totalFields
    }
}