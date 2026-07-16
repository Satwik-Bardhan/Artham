package com.phynix.artham.db.sync

import android.content.Context
import android.util.Log
import com.phynix.artham.auth.SupabaseAuthManager
import com.phynix.artham.db.room.ArthamDatabase
import com.phynix.artham.db.room.entity.CashbookEntity
import com.phynix.artham.db.room.entity.CategoryEntity
import com.phynix.artham.db.room.entity.TransactionEntity
import com.phynix.artham.utils.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.*

/**
 * SyncEngine — Bidirectional sync between Room (local) and Supabase (cloud).
 *
 * Push: Room entities with syncStatus != SYNCED → Supabase (upsert/delete)
 * Pull: Supabase rows → Room entities (merge with last-write-wins)
 *
 * Thread-safe: all operations run on Dispatchers.IO via coroutines.
 * Java-friendly: @JvmStatic methods with callbacks.
 */
object SyncEngine {

    private const val TAG = "SyncEngine"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val supabase get() = SupabaseClientProvider.client

    // Tracks whether a sync is already in progress
    @Volatile
    private var isSyncing = false

    // Last sync timestamp (stored in SharedPreferences)
    private const val PREF_SYNC = "sync_prefs"
    private const val KEY_LAST_SYNC = "last_sync_timestamp"

    // ═══════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════

    /**
     * Trigger a full sync cycle (push + pull). Non-blocking.
     * Safe to call from Java — fires and forgets.
     */
    @JvmStatic
    fun triggerSync(context: Context) {
        if (isSyncing) {
            Log.d(TAG, "Sync already in progress, skipping.")
            return
        }
        if (!SupabaseAuthManager.isAuthenticated()) {
            Log.d(TAG, "Not authenticated with Supabase, skipping sync.")
            return
        }

        scope.launch {
            syncAll(context)
        }
    }

    /**
     * Full sync cycle — call from a coroutine (e.g. SyncWorker).
     */
    suspend fun syncAll(context: Context) {
        if (isSyncing) return
        isSyncing = true

        try {
            val db = ArthamDatabase.getInstance(context)
            val supabaseUserId = getSupabaseUserIdFromDb(context) ?: run {
                Log.w(TAG, "No Supabase user ID found, cannot sync.")
                return
            }

            Log.d(TAG, "Starting sync cycle...")

            // Push local changes to Supabase
            pushCashbooks(db, supabaseUserId)
            pushCategories(db)
            pushTransactions(db)

            // Pull remote changes from Supabase
            pullCashbooks(db, supabaseUserId)
            pullTransactions(db)
            pullCategories(db)

            // Update last sync timestamp
            context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()

            Log.d(TAG, "Sync cycle completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Sync cycle failed", e)
        } finally {
            isSyncing = false
        }
    }

    // ═══════════════════════════════════════
    //  PUSH: Room → Supabase
    // ═══════════════════════════════════════

    private suspend fun pushCashbooks(db: ArthamDatabase, userId: String) {
        try {
            // Push unsynced (new or modified)
            val unsynced = withContext(Dispatchers.IO) { db.cashbookDao().getUnsynced() }
            for (entity in unsynced) {
                val model = SupabaseCashbook(
                    id = entity.id,
                    userId = userId,
                    name = entity.name,
                    description = entity.description,
                    category = entity.category,
                    themeColor = entity.themeColor,
                    themeIcon = entity.themeIcon,
                    currency = entity.currency,
                    totalBalance = entity.totalBalance,
                    transactionCount = entity.transactionCount,
                    isActive = entity.isActive,
                    isCurrent = entity.isCurrent,
                    isFavorite = entity.isFavorite
                )
                supabase.from("cashbooks").upsert(model)
                entity.syncStatus = "SYNCED"
                db.cashbookDao().update(entity)
                Log.d(TAG, "Pushed cashbook: ${entity.name}")
            }

            // Push deleted
            val deleted = withContext(Dispatchers.IO) { db.cashbookDao().getDeleted() }
            for (entity in deleted) {
                try {
                    supabase.from("cashbooks").delete {
                        filter { eq("id", entity.id) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Remote delete failed for cashbook ${entity.id} (may not exist)", e)
                }
                db.cashbookDao().hardDelete(entity.id)
                Log.d(TAG, "Deleted cashbook from cloud: ${entity.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push cashbooks", e)
        }
    }

    private suspend fun pushTransactions(db: ArthamDatabase) {
        try {
            val unsynced = withContext(Dispatchers.IO) { db.transactionDao().getUnsynced() }
            for (entity in unsynced) {
                val model = SupabaseTransaction(
                    id = entity.id,
                    cashbookId = entity.cashbookId,
                    amount = entity.amount,
                    type = entity.type ?: "OUT",
                    transactionCategory = entity.transactionCategory,
                    partyName = entity.partyName,
                    paymentMode = entity.paymentMode,
                    remark = entity.remark,
                    timestamp = entity.timestamp,
                    tags = entity.tags,
                    location = entity.location,
                    attachmentUri = entity.attachmentUri,
                    autoFrequency = entity.autoFrequency,
                    taxRate = entity.taxRate,
                    taxAmount = entity.taxAmount,
                    taxInclusive = entity.taxInclusive
                )
                supabase.from("transactions").upsert(model)
                entity.syncStatus = "SYNCED"
                db.transactionDao().update(entity)
            }

            val deleted = withContext(Dispatchers.IO) { db.transactionDao().getDeleted() }
            for (entity in deleted) {
                try {
                    supabase.from("transactions").delete {
                        filter { eq("id", entity.id) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Remote delete failed for transaction ${entity.id}", e)
                }
                db.transactionDao().hardDelete(entity.id)
            }
            if (unsynced.isNotEmpty() || deleted.isNotEmpty()) {
                Log.d(TAG, "Pushed ${unsynced.size} transactions, deleted ${deleted.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push transactions", e)
        }
    }

    private suspend fun pushCategories(db: ArthamDatabase) {
        try {
            val unsynced = withContext(Dispatchers.IO) { db.categoryDao().getUnsynced() }
            for (entity in unsynced) {
                val model = SupabaseCategory(
                    id = entity.id,
                    cashbookId = entity.cashbookId,
                    name = entity.name,
                    type = entity.type,
                    colorHex = entity.colorHex,
                    iconResId = entity.iconResId,
                    isCustom = entity.isCustom
                )
                supabase.from("categories").upsert(model)
                entity.syncStatus = "SYNCED"
                db.categoryDao().update(entity)
            }

            val deleted = withContext(Dispatchers.IO) { db.categoryDao().getDeleted() }
            for (entity in deleted) {
                try {
                    supabase.from("categories").delete {
                        filter { eq("id", entity.id) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Remote delete failed for category ${entity.id}", e)
                }
                db.categoryDao().hardDelete(entity.id)
            }
            if (unsynced.isNotEmpty() || deleted.isNotEmpty()) {
                Log.d(TAG, "Pushed ${unsynced.size} categories, deleted ${deleted.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push categories", e)
        }
    }

    // ═══════════════════════════════════════
    //  PULL: Supabase → Room
    // ═══════════════════════════════════════

    private suspend fun pullCashbooks(db: ArthamDatabase, userId: String) {
        try {
            val remoteCashbooks = supabase.from("cashbooks")
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeList<SupabaseCashbook>()

            for (remote in remoteCashbooks) {
                val local = withContext(Dispatchers.IO) { db.cashbookDao().getById(remote.id) }
                if (local == null) {
                    // New from cloud — insert locally
                    val entity = CashbookEntity().apply {
                        id = remote.id
                        name = remote.name
                        description = remote.description
                        category = remote.category
                        themeColor = remote.themeColor
                        themeIcon = remote.themeIcon
                        currency = remote.currency
                        totalBalance = remote.totalBalance
                        transactionCount = remote.transactionCount
                        isActive = remote.isActive
                        isCurrent = remote.isCurrent
                        isFavorite = remote.isFavorite
                        this.userId = userId
                        lastModified = System.currentTimeMillis()
                        syncStatus = "SYNCED"
                    }
                    db.cashbookDao().insert(entity)
                    Log.d(TAG, "Pulled new cashbook from cloud: ${remote.name}")
                } else if (local.syncStatus == "SYNCED") {
                    // Update local if it hasn't been modified locally
                    local.name = remote.name
                    local.description = remote.description
                    local.category = remote.category
                    local.themeColor = remote.themeColor
                    local.themeIcon = remote.themeIcon
                    local.totalBalance = remote.totalBalance
                    local.transactionCount = remote.transactionCount
                    local.isActive = remote.isActive
                    local.isCurrent = remote.isCurrent
                    local.isFavorite = remote.isFavorite
                    local.syncStatus = "SYNCED"
                    db.cashbookDao().update(local)
                }
                // If local.syncStatus is PENDING/MODIFIED, don't overwrite — push will handle it
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull cashbooks", e)
        }
    }

    private suspend fun pullTransactions(db: ArthamDatabase) {
        try {
            // Get all local cashbook IDs to scope the pull
            val localCashbooks = withContext(Dispatchers.IO) { db.cashbookDao().getAll() }

            for (cashbook in localCashbooks) {
                val remoteTransactions = supabase.from("transactions")
                    .select {
                        filter { eq("cashbook_id", cashbook.id) }
                    }
                    .decodeList<SupabaseTransaction>()

                for (remote in remoteTransactions) {
                    val local = withContext(Dispatchers.IO) { db.transactionDao().getById(remote.id) }
                    if (local == null) {
                        // New from cloud
                        val entity = TransactionEntity().apply {
                            id = remote.id
                            cashbookId = remote.cashbookId
                            amount = remote.amount
                            type = remote.type
                            transactionCategory = remote.transactionCategory
                            partyName = remote.partyName
                            paymentMode = remote.paymentMode
                            remark = remote.remark
                            timestamp = remote.timestamp
                            tags = remote.tags
                            location = remote.location
                            attachmentUri = remote.attachmentUri
                            autoFrequency = remote.autoFrequency
                            taxRate = remote.taxRate
                            taxAmount = remote.taxAmount
                            taxInclusive = remote.taxInclusive
                            lastModified = System.currentTimeMillis()
                            syncStatus = "SYNCED"
                        }
                        db.transactionDao().insert(entity)
                    } else if (local.syncStatus == "SYNCED") {
                        // Update if not locally modified
                        local.amount = remote.amount
                        local.type = remote.type
                        local.transactionCategory = remote.transactionCategory
                        local.partyName = remote.partyName
                        local.paymentMode = remote.paymentMode
                        local.remark = remote.remark
                        local.tags = remote.tags
                        local.taxRate = remote.taxRate
                        local.taxAmount = remote.taxAmount
                        local.taxInclusive = remote.taxInclusive
                        local.syncStatus = "SYNCED"
                        db.transactionDao().update(local)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull transactions", e)
        }
    }

    private suspend fun pullCategories(db: ArthamDatabase) {
        try {
            val localCashbooks = withContext(Dispatchers.IO) { db.cashbookDao().getAll() }

            for (cashbook in localCashbooks) {
                val remoteCategories = supabase.from("categories")
                    .select {
                        filter { eq("cashbook_id", cashbook.id) }
                    }
                    .decodeList<SupabaseCategory>()

                for (remote in remoteCategories) {
                    val local = withContext(Dispatchers.IO) { db.categoryDao().getById(remote.id) }
                    if (local == null) {
                        val entity = CategoryEntity().apply {
                            id = remote.id
                            cashbookId = remote.cashbookId
                            name = remote.name
                            type = remote.type
                            colorHex = remote.colorHex
                            iconResId = remote.iconResId ?: 0
                            isCustom = remote.isCustom
                            lastModified = System.currentTimeMillis()
                            syncStatus = "SYNCED"
                        }
                        db.categoryDao().insert(entity)
                    } else if (local.syncStatus == "SYNCED") {
                        local.name = remote.name
                        local.type = remote.type
                        local.colorHex = remote.colorHex
                        local.isCustom = remote.isCustom
                        local.syncStatus = "SYNCED"
                        db.categoryDao().update(local)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull categories", e)
        }
    }

    // ═══════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════

    /**
     * Get the Supabase user's UUID from the public.users table.
     * This is the UUID primary key (not auth_id), used as foreign key in cashbooks.
     */
    private suspend fun getSupabaseUserIdFromDb(context: Context): String? {
        return try {
            val authId = SupabaseAuthManager.getCurrentUserId() ?: return null

            // Check if we have it cached
            val prefs = context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
            val cached = prefs.getString("supabase_user_table_id", null)
            if (cached != null) return cached

            // Query the users table
            val users = supabase.from("users")
                .select {
                    filter { eq("auth_id", authId) }
                }
                .decodeList<SupabaseUserRow>()

            val userId = users.firstOrNull()?.id
            if (userId != null) {
                prefs.edit().putString("supabase_user_table_id", userId).apply()
            }
            userId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Supabase user ID", e)
            null
        }
    }

    @kotlinx.serialization.Serializable
    private data class SupabaseUserRow(
        val id: String,
        @kotlinx.serialization.SerialName("auth_id") val authId: String
    )
}
