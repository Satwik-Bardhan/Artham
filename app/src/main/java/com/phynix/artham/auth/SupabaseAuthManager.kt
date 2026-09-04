package com.phynix.artham.auth

import android.content.Context
import android.util.Log
import com.phynix.artham.utils.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

    /** Holds the restored custom_uid from Supabase after cross-device login */
    @JvmStatic
    var restoredCustomUid: String? = null

    /** Set synchronously when sign-out starts so isAuthenticated() returns false immediately */
    @Volatile
    private var isSigningOut = false

    // ═══════════════════════════════════════
    //  JAVA-FRIENDLY CALLBACK INTERFACE
    // ═══════════════════════════════════════

    interface AuthCallback {
        fun onSuccess(userId: String)
        fun onError(error: String)
    }

    interface UidCheckCallback {
        fun onResult(isAvailable: Boolean)
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
        val photo_url: String? = null,
        val custom_uid: String? = null
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
                isSigningOut = false // Reset on successful sign-in

                // 2. Create/update user profile in public.users table
                ensureUserProfile(supabaseUserId, firebaseUid, email, displayName, photoUrl)

                // 3. Restore existing custom_uid from Supabase (for cross-device login)
                try {
                    val users = SupabaseClientProvider.client.from("users")
                        .select {
                            filter { eq("auth_id", supabaseUserId) }
                        }
                        .decodeList<ExistingUser>()
                    val existingUid = users.firstOrNull()?.customUid
                    if (!existingUid.isNullOrEmpty()) {
                        Log.d(TAG, "Restoring existing custom_uid from Supabase: $existingUid")
                        restoredCustomUid = existingUid
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore custom_uid from Supabase (non-fatal)", e)
                }

                // 4. Notify caller on main thread
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
        // Set flag IMMEDIATELY (synchronously) so isAuthenticated() returns false right away
        isSigningOut = true
        scope.launch {
            try {
                restoredCustomUid = null
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
     * Get the current Supabase user email, or null if not authenticated.
     */
    @JvmStatic
    fun getCurrentUserEmail(): String? {
        return SupabaseClientProvider.client.auth.currentUserOrNull()?.email
    }

    /**
     * Check if a Supabase session is active.
     */
    @JvmStatic
    fun isAuthenticated(): Boolean {
        if (isSigningOut) return false
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
        val email: String? = null,
        @kotlinx.serialization.SerialName("custom_uid") val customUid: String? = null
    )

    /**
     * Check whether a custom UID is available (not claimed by any other user).
     * Uses Supabase RPC function to bypass RLS restrictions.
     * Falls back to client-side query if RPC is not available.
     */
    @JvmStatic
    fun checkUidAvailability(customUid: String, currentAuthId: String?, callback: UidCheckCallback) {
        scope.launch {
            try {
                val targetUid = customUid.trim()
                if (targetUid.isEmpty()) {
                    withContext(Dispatchers.Main) { callback.onResult(false) }
                    return@launch
                }

                // If target matches current restored UID, it's available for the user
                if (!restoredCustomUid.isNullOrEmpty() && targetUid.equals(restoredCustomUid, ignoreCase = true)) {
                    withContext(Dispatchers.Main) { callback.onResult(true) }
                    return@launch
                }

                // Try RPC function first (bypasses RLS)
                var rpcResult: Boolean? = null
                try {
                    val params = buildJsonObject {
                        put("target_uid", targetUid)
                        put("current_auth_id", currentAuthId ?: "")
                    }
                    val response = SupabaseClientProvider.client.postgrest
                        .rpc("check_uid_available", params)
                        .decodeAs<Boolean>()
                    rpcResult = response
                } catch (e: Exception) {
                    Log.w(TAG, "RPC check_uid_available not available, falling back to client-side check", e)
                }

                if (rpcResult != null) {
                    withContext(Dispatchers.Main) { callback.onResult(rpcResult) }
                    return@launch
                }

                // Fallback: client-side query
                val usersByCustomUid = try {
                    SupabaseClientProvider.client.from("users")
                        .select {
                            filter { eq("custom_uid", targetUid) }
                        }
                        .decodeList<ExistingUser>()
                } catch (e: Exception) {
                    emptyList<ExistingUser>()
                }

                val usersByUsername = try {
                    SupabaseClientProvider.client.from("users")
                        .select {
                            filter { eq("username", targetUid) }
                        }
                        .decodeList<ExistingUser>()
                } catch (e: Exception) {
                    emptyList<ExistingUser>()
                }

                val allMatching = (usersByCustomUid + usersByUsername).distinctBy { it.id }

                // Check if any matches belong to a DIFFERENT user
                val takenByOther = allMatching.any { user ->
                    val isCurrentUser = (user.authId != null && user.authId.equals(currentAuthId, ignoreCase = true)) ||
                            user.id.equals(currentAuthId, ignoreCase = true)
                    !isCurrentUser
                }

                withContext(Dispatchers.Main) {
                    callback.onResult(!takenByOther)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking UID availability", e)
                withContext(Dispatchers.Main) {
                    callback.onResult(true)
                }
            }
        }
    }

    /**
     * Update custom_uid / username for user in Supabase.
     */
    @JvmStatic
    fun updateCustomUid(authId: String, customUid: String, callback: AuthCallback?) {
        scope.launch {
            try {
                val trimmed = customUid.trim()
                restoredCustomUid = trimmed
                val updateData = mapOf(
                    "custom_uid" to trimmed,
                    "username" to trimmed
                )

                var updatedCount = 0

                // 1. Update by auth_id
                try {
                    SupabaseClientProvider.client.from("users").update(updateData) {
                        filter { eq("auth_id", authId) }
                    }
                    updatedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Update by auth_id failed", e)
                }

                // 2. Update by id (primary key)
                try {
                    SupabaseClientProvider.client.from("users").update(updateData) {
                        filter { eq("id", authId) }
                    }
                    updatedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Update by id failed", e)
                }

                // 3. Update by user email (for linked user rows)
                val userEmail = getCurrentUserEmail()?.trim()?.lowercase()
                if (!userEmail.isNullOrEmpty()) {
                    try {
                        SupabaseClientProvider.client.from("users").update(updateData) {
                            filter { eq("email", userEmail) }
                        }
                        updatedCount++
                    } catch (e: Exception) {
                        Log.w(TAG, "Update by email failed", e)
                    }
                }

                Log.d(TAG, "Successfully updated custom_uid/username in Supabase to $trimmed")
                withContext(Dispatchers.Main) {
                    callback?.onSuccess(authId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update custom_uid in Supabase", e)
                withContext(Dispatchers.Main) {
                    callback?.onError(e.message ?: "This UID is already taken by another user.")
                }
            }
        }
    }

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
                    if (!targetUser.customUid.isNullOrEmpty()) {
                        restoredCustomUid = targetUser.customUid
                        Log.d(TAG, "Restored custom UID: ${targetUser.customUid}")
                    }
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

    @Serializable
    private data class ExistingCashbook(val id: String)

    /**
     * Delete user's account and all associated cloud & local data.
     */
    @JvmStatic
    fun deleteUserAccount(context: Context, callback: AuthCallback?) {
        scope.launch {
            var success = false
            var criticalFailure = false
            try {
                val authId = getCurrentUserId()
                val userEmail = getCurrentUserEmail()?.trim()?.lowercase()

                Log.d(TAG, "Starting account deletion. authId=$authId, email=$userEmail")

                if (authId != null) {
                    // 1. Resolve the public.users table ID (may differ from authId)
                    val possibleUserIds = mutableSetOf(authId)

                    // Query users table by auth_id to get the table PK
                    try {
                        val usersByAuthId = SupabaseClientProvider.client.from("users")
                            .select {
                                filter { eq("auth_id", authId) }
                            }
                            .decodeList<ExistingUser>()
                        for (user in usersByAuthId) {
                            possibleUserIds.add(user.id)
                            if (!user.customUid.isNullOrEmpty()) {
                                possibleUserIds.add(user.customUid)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to query users by auth_id", e)
                    }

                    // Also query users by email for migrated users
                    if (!userEmail.isNullOrEmpty()) {
                        try {
                            val usersByEmail = SupabaseClientProvider.client.from("users")
                                .select {
                                    filter { eq("email", userEmail) }
                                }
                                .decodeList<ExistingUser>()
                            for (user in usersByEmail) {
                                possibleUserIds.add(user.id)
                                if (!user.customUid.isNullOrEmpty()) {
                                    possibleUserIds.add(user.customUid)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to query users by email", e)
                        }
                    }

                    Log.d(TAG, "Resolved possible user_ids for cashbook lookup: $possibleUserIds")

                    // 2. Collect ALL cashbooks belonging to this user (across all user_id variants)
                    val allCashbookIds = mutableSetOf<String>()
                    for (userId in possibleUserIds) {
                        try {
                            val cashbooks = SupabaseClientProvider.client.from("cashbooks")
                                .select {
                                    filter { eq("user_id", userId) }
                                }
                                .decodeList<ExistingCashbook>()
                            for (cb in cashbooks) {
                                allCashbookIds.add(cb.id)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to query cashbooks for user_id=$userId", e)
                        }
                    }
                    Log.d(TAG, "Found ${allCashbookIds.size} cashbooks to delete")

                    // 3. Delete transactions and categories for ALL user's cashbooks
                    for (cbId in allCashbookIds) {
                        try {
                            SupabaseClientProvider.client.from("transactions").delete {
                                filter { eq("cashbook_id", cbId) }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to delete transactions for cashbook $cbId", e)
                        }
                        try {
                            SupabaseClientProvider.client.from("categories").delete {
                                filter { eq("cashbook_id", cbId) }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to delete categories for cashbook $cbId", e)
                        }
                    }

                    // 4. Delete ALL cashbooks (by all user_id variants)
                    for (userId in possibleUserIds) {
                        try {
                            SupabaseClientProvider.client.from("cashbooks").delete {
                                filter { eq("user_id", userId) }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to delete cashbooks for user_id=$userId", e)
                            criticalFailure = true
                        }
                    }

                    // 5. Delete user profile rows (by auth_id and email)
                    try {
                        SupabaseClientProvider.client.from("users").delete {
                            filter { eq("auth_id", authId) }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete user profile for auth_id $authId", e)
                        criticalFailure = true
                    }

                    if (!userEmail.isNullOrEmpty()) {
                        try {
                            SupabaseClientProvider.client.from("users").delete {
                                filter { eq("email", userEmail) }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to delete user profile for email $userEmail", e)
                        }
                    }
                }

                success = !criticalFailure
                Log.d(TAG, "Account deletion completed. success=$success")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting user account", e)
            } finally {
                // Clear local data WITHOUT re-pushing to cloud
                withContext(Dispatchers.Main) {
                    try {
                        // Use signOutForDeletion which does NOT call forcePushAll
                        AuthManager.signOutForDeletion(context)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error during signOutForDeletion in deleteUserAccount", e)
                    }
                    if (success) {
                        callback?.onSuccess("")
                    } else {
                        callback?.onError("Some cloud data could not be deleted, but local data has been cleared.")
                    }
                }
            }
        }
    }
}
