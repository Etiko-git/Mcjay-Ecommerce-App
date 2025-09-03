package com.solih.mcjay

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.cio.CIO

object SupabaseClientInstance {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = "https://ltqlcmtofvayqdjselaj.supabase.co",  // Replace with your Supabase URL
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imx0cWxjbXRvZnZheXFkanNlbGFqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTQ2ODI5MDEsImV4cCI6MjA3MDI1ODkwMX0.R89nzP2RJlu83myT3RnlD5vhYH6ToxRzFYELFyemPyA"                // Replace with your anon/public key
        ) {

            // Use Ktor CIO engine for networking
            httpEngine = CIO.create()


            install(Auth)       // Enables authentication
            install(Postgrest)  // Enables database queries (.from, .eq, etc.)
            install(Storage)    // Enables storage (.storage for file uploads)
            install(Realtime)
            install(Functions)
        }
    }

}
