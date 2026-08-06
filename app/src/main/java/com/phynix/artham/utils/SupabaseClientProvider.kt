package com.phynix.artham.utils

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import com.phynix.artham.BuildConfig

/**
 * SupabaseClient — Singleton provider for the Supabase client.
 *
 * Initializes with BuildConfig credentials (injected via build.gradle.kts).
 * Session persistence is handled via SharedPreferences so users stay
 * logged in across app restarts indefinitely (until explicit sign-out).
 *
 * Java interop: Access via SupabaseClientProvider.INSTANCE.getClient()
 */
object SupabaseClientProvider {

    private var appContext: Context? = null

    /**
     * Must be called from Application.onCreate() before any Supabase usage.
     */
    @JvmStatic
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Postgrest)
        }
    }
}
