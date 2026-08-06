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
        triggerSync(context, null)
    }

    /**
     * Trigger a full sync cycle with an optional completion callback.
     * The callback runs on the MAIN thread when sync finishes (success or failure).
     */
    @JvmStatic
    fun triggerSync(context: Context, onComplete: Runnable?) {
        if (isSyncing) {
            Log.d(TAG, "Sync already in progress, skipping.")
            return
        }
        if (!SupabaseAuthManager.isAuthenticated()) {
            Log.d(TAG, "Not authenticated with Supabase, skipping sync.")
            onComplete?.let {
                kotlinx.coroutines.MainScope().launch { it.run() }
            }
            return
        }

        scope.launch {
            try {
                syncAll(context)
            } catch (e: Exception) {
                Log.e(TAG, "triggerSync failed", e)
            } finally {
                onComplete?.let {
                    withContext(Dispatchers.Main) { it.run() }
                }
            }
        }
    }

    /**
     * Force push ALL local data to Supabase — used for data recovery.
     * Marks ALL cashbooks, transactions, and categories as PENDING
     * so pushCashbooks/pushTransactions will re-push everything.
     */
    @JvmStatic
    fun forcePushAll(context: Context, onComplete: Runnable?) {
        if (!SupabaseAuthManager.isAuthenticated()) {
            Log.d(TAG, "Not authenticated, cannot force push.")
            onComplete?.let { kotlinx.coroutines.MainScope().launch { it.run() } }
            return
        }

        scope.launch {
            try {
                val db = ArthamDatabase.getInstance(context)

                // Mark ALL non-deleted cashbooks as PENDING
                val allCashbooks = withContext(Dispatchers.IO) { db.cashbookDao().getAll() }
                var cashbookCount = 0
                for (cb in allCashbooks) {
                    cb.syncStatus = "PENDING"
                    cb.lastModified = System.currentTimeMillis()
                    db.cashbookDao().update(cb)
                    cashbookCount++
                }

                // Mark ALL non-deleted transactions as PENDING
                val allTransactions = withContext(Dispatchers.IO) {
                    val txList = mutableListOf<com.phynix.artham.db.room.entity.TransactionEntity>()
                    for (cb in allCashbooks) {
                        txList.addAll(db.transactionDao().getByCashbook(cb.id))
                    }
                    txList
                }
                var txCount = 0
                for (tx in allTransactions) {
                    tx.syncStatus = "PENDING"
                    tx.lastModified = System.currentTimeMillis()
                    db.transactionDao().update(tx)
                    txCount++
                }

                Log.d(TAG, "Force push: marked $cashbookCount cashbooks and $txCount transactions as PENDING")

                // Now run normal sync which will push everything
                syncAll(context)

                Log.d(TAG, "Force push completed!")
            } catch (e: Exception) {
                Log.e(TAG, "Force push failed", e)
            }

            onComplete?.let {
                withContext(Dispatchers.Main) { it.run() }
            }
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
            // Ensure profile is linked/created first (self-healing for existing logged-in sessions)
            val authId = SupabaseAuthManager.getCurrentUserId()
            if (authId != null) {
                val email = com.phynix.artham.auth.AuthManager.getUserEmail(context)
                val name = com.phynix.artham.auth.AuthManager.getUserName(context)
                val photoUrl = com.phynix.artham.auth.AuthManager.getUserPhotoUrl(context)
                SupabaseAuthManager.ensureUserProfile(authId, null, email, name, photoUrl)
            }
            val supabaseUserId = getSupabaseUserIdFromDb(context) ?: run {
                Log.w(TAG, "No Supabase user ID found, cannot sync.")
                return
            }

            Log.d(TAG, "Starting sync cycle...")

            // Check if this is a fresh install (no local cashbooks = pull first)
            val localCashbookCount = withContext(Dispatchers.IO) { db.cashbookDao().getAll().size }
            
            if (localCashbookCount == 0) {
                // Fresh install: pull from cloud first, then push any remaining local changes
                Log.d(TAG, "Fresh install detected — pulling from cloud first")
                pullCashbooks(context, db, supabaseUserId)
                pullTransactions(db)
                pullCategories(db)
                pushCashbooks(db, supabaseUserId)
                pushCategories(db)
                pushTransactions(db)
            } else {
                // Normal sync: push first, then pull
                pushCashbooks(db, supabaseUserId)
                pushCategories(db)
                pushTransactions(db)
                pullCashbooks(context, db, supabaseUserId)
                pullTransactions(db)
                pullCategories(db)
            }

            // Recalculate cashbook stats ONLY if there are local transactions
            recalculateAllCashbookStats(db)

            // Update last sync timestamp
            context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()

            Log.d(TAG, "Sync cycle completed successfully.")
            // Broadcast that sync completed so active activities/ViewModels can reload their data
            val intent = android.content.Intent("com.phynix.artham.ACTION_SYNC_COMPLETED")
            context.sendBroadcast(intent)
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
            val unsynced = withContext(Dispatchers.IO) { db.cashbookDao().getUnsynced() }
            val allLocal = withContext(Dispatchers.IO) { db.cashbookDao().getAll() }
            val toPush = (unsynced + allLocal).distinctBy { it.id }
            for (entity in toPush) {
                try {
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
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to push cashbook ${entity.name}, will retry next sync", e)
                }
            }

            // Push deleted
            val deleted = withContext(Dispatchers.IO) { db.cashbookDao().getDeleted() }
            for (entity in deleted) {
                try {
                    supabase.from("cashbooks").delete {
                        filter { eq("id", entity.id) }
                    }
                    db.cashbookDao().hardDelete(entity.id)
                    Log.d(TAG, "Deleted cashbook from cloud: ${entity.id}")
                } catch (e: Exception) {
                    Log.w(TAG, "Remote delete failed for cashbook ${entity.id} (may not exist)", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push cashbooks", e)
        }
    }

    private suspend fun pushTransactions(db: ArthamDatabase) {
        try {
            val unsynced = withContext(Dispatchers.IO) { db.transactionDao().getUnsynced() }
            val allLocal = withContext(Dispatchers.IO) { db.transactionDao().getAll() }
            val toPush = (unsynced + allLocal).distinctBy { it.id }
            var pushCount = 0
            var failCount = 0
            for (entity in toPush) {
                try {
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
                    pushCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to push transaction ${entity.id}, will retry next sync", e)
                    failCount++
                }
            }

            val deleted = withContext(Dispatchers.IO) { db.transactionDao().getDeleted() }
            for (entity in deleted) {
                try {
                    supabase.from("transactions").delete {
                        filter { eq("id", entity.id) }
                    }
                    db.transactionDao().hardDelete(entity.id)
                } catch (e: Exception) {
                    Log.w(TAG, "Remote delete failed for transaction ${entity.id}", e)
                }
            }
            if (pushCount > 0 || deleted.isNotEmpty() || failCount > 0) {
                Log.d(TAG, "Transactions: pushed=$pushCount, deleted=${deleted.size}, failed=$failCount")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push transactions", e)
        }
    }

    private suspend fun pushCategories(db: ArthamDatabase) {
        try {
            val unsynced = withContext(Dispatchers.IO) { db.categoryDao().getUnsynced() }
            for (entity in unsynced) {
                if (!entity.isCustom) {
                    // Default system categories are global/predefined; mark as SYNCED
                    entity.syncStatus = "SYNCED"
                    db.categoryDao().update(entity)
                    continue
                }
                try {
                    val model = SupabaseCategory(
                        id = entity.id,
                        cashbookId = entity.cashbookId ?: "",
                        name = entity.name,
                        type = entity.type,
                        colorHex = entity.colorHex,
                        iconResId = entity.iconResId,
                        isCustom = entity.isCustom
                    )
                    supabase.from("categories").upsert(model)
                    entity.syncStatus = "SYNCED"
                    db.categoryDao().update(entity)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to push category '${entity.name}'", e)
                }
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

    private suspend fun pullCashbooks(context: Context, db: ArthamDatabase, userId: String) {
        try {
            val remoteCashbooksAll = try {
                supabase.from("cashbooks").select().decodeList<SupabaseCashbook>()
            } catch (e: Exception) {
                emptyList<SupabaseCashbook>()
            }

            val remoteCashbooksByUserId = try {
                supabase.from("cashbooks")
                    .select {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<SupabaseCashbook>()
            } catch (e: Exception) {
                emptyList<SupabaseCashbook>()
            }

            val remoteCashbooksByAuthId = try {
                val authId = SupabaseAuthManager.getCurrentUserId()
                if (authId != null && authId != userId) {
                    supabase.from("cashbooks")
                        .select {
                            filter { eq("user_id", authId) }
                        }
                        .decodeList<SupabaseCashbook>()
                } else emptyList()
            } catch (e: Exception) {
                emptyList<SupabaseCashbook>()
            }

            val remoteCashbooksByCustomUid = try {
                val customUid = com.phynix.artham.auth.AuthManager.getUserId(context)
                val authId = SupabaseAuthManager.getCurrentUserId()
                if (!customUid.isNullOrEmpty() && customUid != userId && customUid != authId) {
                    supabase.from("cashbooks")
                        .select {
                            filter { eq("user_id", customUid) }
                        }
                        .decodeList<SupabaseCashbook>()
                } else emptyList()
            } catch (e: Exception) {
                emptyList<SupabaseCashbook>()
            }

            val remoteCashbooks = (remoteCashbooksAll + remoteCashbooksByUserId + remoteCashbooksByAuthId + remoteCashbooksByCustomUid)
                .filter { it.deletedAt == null }
                .distinctBy { it.id }

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
                        createdDate = parseCreatedAt(remote.createdAt)
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
                        Log.d(TAG, "Pulled new transaction from cloud: ${remote.id}")
                    } else if (local.syncStatus == "SYNCED") {
                        // Update if not locally modified
                        local.amount = remote.amount
                        local.type = remote.type
                        local.transactionCategory = remote.transactionCategory
                        local.partyName = remote.partyName
                        local.paymentMode = remote.paymentMode
                        local.remark = remote.remark
                        local.timestamp = remote.timestamp
                        local.tags = remote.tags
                        local.location = remote.location
                        local.attachmentUri = remote.attachmentUri
                        local.autoFrequency = remote.autoFrequency
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
                        Log.d(TAG, "Pulled new category from cloud: ${remote.name}")
                    } else if (local.syncStatus == "SYNCED") {
                        local.name = remote.name
                        local.type = remote.type
                        local.colorHex = remote.colorHex
                        local.iconResId = remote.iconResId ?: 0
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
    //  STATS RECALCULATION
    // ═══════════════════════════════════════

    /**
     * Recalculate balance and transaction count for all cashbooks
     * from actual Room transaction data. This ensures stats are correct
     * even if the Supabase cashbook metadata was stale (e.g. from migration).
     */
    private suspend fun recalculateAllCashbookStats(db: ArthamDatabase) {
        try {
            val allCashbooks = withContext(Dispatchers.IO) { db.cashbookDao().getAll() }
            for (cb in allCashbooks) {
                val count = db.transactionDao().countByCashbook(cb.id)
                
                // IMPORTANT: Only recalculate if there are actual local transactions.
                // If count is 0 but the cashbook has a non-zero balance from the cloud,
                // it means transactions haven't been pulled yet — preserve the cloud balance.
                if (count == 0 && (cb.totalBalance != 0.0 || cb.transactionCount != 0)) {
                    // Transactions may not have been pulled yet, preserve cloud values
                    Log.d(TAG, "Skipping recalculation for '${cb.name}' — no local transactions, preserving cloud balance=${cb.totalBalance}")
                    continue
                }
                
                val balance = db.transactionDao().calculateBalance(cb.id)
                if (cb.totalBalance != balance || cb.transactionCount != count) {
                    cb.totalBalance = balance
                    cb.transactionCount = count
                    cb.lastModified = System.currentTimeMillis()
                    // Don't change syncStatus here — let the push handle it
                    db.cashbookDao().update(cb)
                    Log.d(TAG, "Recalculated stats for '${cb.name}': balance=$balance, count=$count")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recalculate cashbook stats", e)
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

            // 1. Try querying users by auth_id
            var users = supabase.from("users")
                .select {
                    filter { eq("auth_id", authId) }
                }
                .decodeList<SupabaseUserRow>()

            // 2. Fallback: try querying by id
            if (users.isEmpty()) {
                users = supabase.from("users")
                    .select {
                        filter { eq("id", authId) }
                    }
                    .decodeList<SupabaseUserRow>()
            }

            var userId = users.firstOrNull()?.id

            // 3. Self-healing: if user row is missing in public.users, create/link it now
            if (userId == null) {
                Log.d(TAG, "User profile missing in public.users, running ensureUserProfile...")
                val email = com.phynix.artham.auth.AuthManager.getUserEmail(context)
                val name = com.phynix.artham.auth.AuthManager.getUserName(context)
                val photoUrl = com.phynix.artham.auth.AuthManager.getUserPhotoUrl(context)
                SupabaseAuthManager.ensureUserProfile(authId, null, email, name, photoUrl)

                val retryUsers = supabase.from("users")
                    .select {
                        filter { eq("auth_id", authId) }
                    }
                    .decodeList<SupabaseUserRow>()
                userId = retryUsers.firstOrNull()?.id ?: authId
            }

            if (userId != null) {
                val prefs = context.getSharedPreferences(PREF_SYNC, Context.MODE_PRIVATE)
                prefs.edit().putString("supabase_user_table_id", userId).apply()
            }
            userId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Supabase user ID, falling back to authId", e)
            SupabaseAuthManager.getCurrentUserId()
        }
    }

    @kotlinx.serialization.Serializable
    private data class SupabaseUserRow(
        val id: String,
        @kotlinx.serialization.SerialName("auth_id") val authId: String
    )
    /**
     * Parse a Supabase ISO 8601 timestamp (e.g. "2026-07-18T08:57:42.628715+00:00")
     * into epoch milliseconds for Room's createdDate field.
     */
    private fun parseCreatedAt(createdAt: String?): Long {
        if (createdAt.isNullOrEmpty()) return 0L
        return try {
            val formatter = java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
            val instant = java.time.OffsetDateTime.parse(createdAt, formatter).toInstant()
            instant.toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse created_at: $createdAt", e)
            0L
        }
    }
}
