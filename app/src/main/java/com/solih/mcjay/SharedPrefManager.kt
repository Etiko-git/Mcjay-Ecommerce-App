// SharedPrefManager.kt
package com.solih.mcjay

import android.content.Context
import android.content.SharedPreferences

class SharedPrefManager private constructor(context: Context) {

    companion object {
        private const val PREF_NAME = "mcjay_prefs"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_AUTH_TYPE = "auth_type" // "myid" or "supabase"
        private const val KEY_USER_NAME = "user_name"

        @Volatile
        private var INSTANCE: SharedPrefManager? = null

        fun getInstance(context: Context): SharedPrefManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharedPrefManager(context).also { INSTANCE = it }
            }
        }
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveMyIDAuth(token: String) {
        with(sharedPreferences.edit()) {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_AUTH_TOKEN, token)
            putString(KEY_AUTH_TYPE, "myid")
            apply()
        }
    }

    fun saveSupabaseAuth() {
        with(sharedPreferences.edit()) {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_AUTH_TYPE, "supabase")
            apply()
        }
    }

    fun saveUserName(name: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_USER_NAME, name)
            apply()
        }
    }

    fun isLoggedIn(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getAuthToken(): String? {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null)
    }

    fun getAuthType(): String {
        return sharedPreferences.getString(KEY_AUTH_TYPE, "") ?: ""
    }

    fun getUserName(): String {
        return sharedPreferences.getString(KEY_USER_NAME, "Guest") ?: "Guest"
    }

    fun clearAuth() {
        with(sharedPreferences.edit()) {
            remove(KEY_IS_LOGGED_IN)
            remove(KEY_AUTH_TOKEN)
            remove(KEY_AUTH_TYPE)
            remove(KEY_USER_NAME)
            apply()
        }
    }
}