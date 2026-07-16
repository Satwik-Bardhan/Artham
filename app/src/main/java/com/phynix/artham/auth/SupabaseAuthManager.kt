package com.phynix.artham.auth

import android.util.Log
import com.phynix.artham.utils.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable

/**
 * SupabaseAuthManager — Handles Supabase authentication alongside Firebase.
 *
 * During the dual-auth period (Phase 2-3), both Firebase and Supabase auth
 * run in parallel. Firebase remains the primary auth for data access.
 * Supabase auth is set up now so Phase 3 (Sync Engine) can use it.
 *
 * Java interop: All public methods are @JvmStatic and use callbacks.
 */
object SupabaseAuthManager {

    private const val TAG = "SupabaseAuth"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ═══════════════════════════════════════
    //  JAVA-FRIENDLY CALLBACK INTERFACE
    // ═══════════════════════════════════════

    interface AuthCallback {
        fun onSuccess(userId: String)
        fun onError(error: String)
    }

    // ═══════════════════════════════════════
    //  SERIALIZABLE USER PROFILE (for Postgrest)
    // ═══════════════════════════════════════

    @Serializable
    data class UserProfile(
        val auth_id: String,
        val firebase_uid: String? = null,
        val email: String? = null,
        val display_name: String? = null,
        val photo_url: String? = null
    )

    // ═══════════════════════════════════════
    //  SIGN IN WITH GOOGLE ID TOKEN
    // ═══════════════════════════════════════

    /**
     * Authenticate with Supabase using a Google ID token.
     * Call this AFTER Firebase auth succeeds, passing the same ID token.
     *
     * @param idToken    The Google ID token from Google Sign-In
     * @param firebaseUid The Firebase UID (for mapping in users table)
     * @param email      User's email
     * @param displayName User's display name
     * @param photoUrl   User's profile photo URL
     * @param callback   Result callback (runs on main thread)
     */
    @JvmStatic
    fun signInWithGoogle(
        idToken: String,
        firebaseUid: String?,
        email: String?,
        displayName: String?,
        photoUrl: String?,
        callback: AuthCallback?
    ) {
        scope.launch {
            try {
                // 1. Sign in to Supabase Auth with the Google ID token
                SupabaseClientProvider.client.auth.signInWith(IDToken) {
                    provider = Google
                    this.idToken = idToken
                }

                val supabaseUser = SupabaseClientProvider.client.auth.currentUserOrNull()
                val supabaseUserId = supabaseUser?.id ?: ""

                Log.d(TAG, "Supabase auth successful. User ID: $supabaseUserId")

                // 2. Create/update user profile in public.users table
                ensureUserProfile(supabaseUserId, firebaseUid, email, displayName, photoUrl)

                // 3. Notify caller on main thread
                withContext(Dispatchers.Main) {
                    callback?.onSuccess(supabaseUserId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Supabase auth failed", e)
                withContext(Dispatchers.Main) {
                    callback?.onError(e.message ?: "Supabase auth failed")
                }
            }
        }
    }

    // ═══════════════════════════════════════
    //  SIGN OUT
    // ═══════════════════════════════════════

    /**
     * Sign out of Supabase. Call alongside Firebase sign-out.
     */
    @JvmStatic
    fun signOut(callback: AuthCallback?) {
        scope.launch {
            try {
                SupabaseClientProvider.client.auth.signOut()
                Log.d(TAG, "Supabase sign-out successful")
                withContext(Dispatchers.Main) {
                    callback?.onSuccess("")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Supabase sign-out failed", e)
                withContext(Dispatchers.Main) {
                    callback?.onError(e.message ?: "Sign-out failed")
                }
            }
        }
    }

    // ═══════════════════════════════════════
    //  CURRENT USER
    // ═══════════════════════════════════════

    /**
     * Get the current Supabase user ID, or null if not authenticated.
     */
    @JvmStatic
    fun getCurrentUserId(): String? {
        return SupabaseClientProvider.client.auth.currentUserOrNull()?.id
    }

    /**
     * Check if a Supabase session is active.
     */
    @JvmStatic
    fun isAuthenticated(): Boolean {
        return SupabaseClientProvider.client.auth.currentUserOrNull() != null
    }

    // ═══════════════════════════════════════
    //  PRIVATE: USER PROFILE MANAGEMENT
    // ═══════════════════════════════════════

    /**
     * Creates a user profile row in public.users if it doesn't exist.
     * Updates it if it does exist (upsert by auth_id).
     */
    private suspend fun ensureUserProfile(
        authId: String,
        firebaseUid: String?,
        email: String?,
        displayName: String?,
        photoUrl: String?
    ) {
        try {
            val profile = UserProfile(
                auth_id = authId,
                firebase_uid = firebaseUid,
                email = email,
                display_name = displayName,
                photo_url = photoUrl
            )

            // Upsert: insert if not exists, update if exists
            SupabaseClientProvider.client.from("users").upsert(profile) {
                onConflict = "auth_id"
            }

            Log.d(TAG, "User profile upserted for auth_id: $authId")
        } catch (e: Exception) {
            // Non-fatal — auth succeeded but profile creation failed
            // This can happen if RLS policies aren't set up yet
            Log.w(TAG, "Failed to upsert user profile (non-fatal)", e)
        }
    }
}
