package com.phynix.artham.utils

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import com.phynix.artham.BuildConfig

/**
 * SupabaseClient — Singleton provider for the Supabase client.
 *
 * Initializes with BuildConfig credentials (injected via build.gradle.kts).
 * Used by SupabaseAuthManager and future sync engine.
 *
 * Java interop: Access via SupabaseClientProvider.INSTANCE.getClient()
 */
object SupabaseClientProvider {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
