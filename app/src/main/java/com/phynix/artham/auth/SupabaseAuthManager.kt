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
     * Serializable model for querying existing user rows.
     */
    @Serializable
    private data class ExistingUser(
        val id: String,
        @kotlinx.serialization.SerialName("auth_id") val authId: String? = null,
        @kotlinx.serialization.SerialName("firebase_uid") val firebaseUid: String? = null,
        val email: String? = null
    )

    /**
     * Serializable model for updating an existing user's auth_id.
     */
    @Serializable
    private data class AuthIdUpdate(
        @kotlinx.serialization.SerialName("auth_id") val authId: String,
        @kotlinx.serialization.SerialName("display_name") val displayName: String? = null,
        @kotlinx.serialization.SerialName("photo_url") val photoUrl: String? = null
    )

    /**
     * Creates a user profile row in public.users if it doesn't exist.
     * If a migrated user with the same email already exists (from Firebase migration),
     * links the new Supabase auth_id to that existing row so their cashbooks are found.
     * Also handles cleanup of any duplicate rows created during the sign-in process.
     */
    @JvmStatic
    suspend fun ensureUserProfile(
        authId: String,
        firebaseUid: String?,
        email: String?,
        displayName: String?,
        photoUrl: String?
    ) {
        try {
            val normalizedEmail = email?.trim()?.lowercase()
            if (!normalizedEmail.isNullOrEmpty()) {
                // Query user rows matching email (both exact and normalized)
                val emailUsers = SupabaseClientProvider.client.from("users")
                    .select {
                        filter { eq("email", normalizedEmail) }
                    }
                    .decodeList<ExistingUser>()

                // Query any row that already has this auth_id
                val authIdUsers = SupabaseClientProvider.client.from("users")
                    .select {
                        filter { eq("auth_id", authId) }
                    }
                    .decodeList<ExistingUser>()

                // Combine and deduplicate
                val allRelatedUsers = (emailUsers + authIdUsers).distinctBy { it.id }

                // Find primary user row: prefer row with firebase_uid, otherwise any existing row for this user
                val targetUser = allRelatedUsers.firstOrNull { !it.firebaseUid.isNullOrEmpty() }
                    ?: allRelatedUsers.firstOrNull()

                if (targetUser != null) {
                    // STEP 1: Delete ALL duplicate rows FIRST to free up auth_id
                    val duplicates = allRelatedUsers.filter { it.id != targetUser.id }
                    for (dup in duplicates) {
                        Log.d(TAG, "Removing duplicate user row: ${dup.id}")
                        try {
                            // Move any cashbooks from duplicate to target row first
                            SupabaseClientProvider.client.from("cashbooks").update(
                                mapOf("user_id" to targetUser.id)
                            ) {
                                filter { eq("user_id", dup.id) }
                            }
                            // Then delete the duplicate
                            SupabaseClientProvider.client.from("users").delete {
                                filter { eq("id", dup.id) }
                            }
                            Log.d(TAG, "Deleted duplicate row: ${dup.id}")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to remove duplicate row ${dup.id}", e)
                        }
                    }

                    // STEP 2: Link auth_id and update email/profile on target row
                    if (targetUser.authId != authId) {
                        Log.d(TAG, "Linking user (${targetUser.id}) with auth_id: $authId")
                        try {
                            SupabaseClientProvider.client.from("users").update(
                                AuthIdUpdate(authId = authId, displayName = displayName, photoUrl = photoUrl)
                            ) {
                                filter { eq("id", targetUser.id) }
                            }
                            Log.d(TAG, "Successfully linked user to Supabase auth")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to link user auth_id", e)
                        }
                    } else {
                        Log.d(TAG, "User already linked with correct auth_id")
                    }
                    return
                }
            }

            // No migrated user found - do a normal upsert for a brand new user
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
            // Non-fatal - auth succeeded but profile creation failed
            Log.w(TAG, "Failed to upsert user profile (non-fatal)", e)
        }
    }
}
