package com.solih.mcjay

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize Supabase at app startup
        val supabase = SupabaseClientInstance.client
    }
}
