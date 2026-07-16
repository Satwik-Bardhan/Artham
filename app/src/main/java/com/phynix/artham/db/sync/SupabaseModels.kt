package com.phynix.artham.db.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase-compatible data models for cloud sync.
 * These map directly to the PostgreSQL tables in Supabase.
 *
 * Note: Supabase table IDs are TEXT (not UUID) to support
 * app-generated IDs from both Firebase push keys and local UUIDs.
 */

// ═══════════════════════════════════════
//  CASHBOOK — maps to public.cashbooks
// ═══════════════════════════════════════

@Serializable
data class SupabaseCashbook(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    @SerialName("theme_color") val themeColor: String? = null,
    @SerialName("theme_icon") val themeIcon: String? = null,
    val currency: String = "INR",
    @SerialName("total_balance") val totalBalance: Double = 0.0,
    @SerialName("transaction_count") val transactionCount: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_current") val isCurrent: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("deleted_at") val deletedAt: String? = null
)

// ═══════════════════════════════════════
//  TRANSACTION — maps to public.transactions
// ═══════════════════════════════════════

@Serializable
data class SupabaseTransaction(
    val id: String,
    @SerialName("cashbook_id") val cashbookId: String,
    val amount: Double = 0.0,
    val type: String,  // "IN" or "OUT"
    @SerialName("transaction_category") val transactionCategory: String? = null,
    @SerialName("party_name") val partyName: String? = null,
    @SerialName("payment_mode") val paymentMode: String? = null,
    val remark: String? = null,
    val timestamp: Long,
    val tags: String? = null,
    val location: String? = null,
    @SerialName("attachment_uri") val attachmentUri: String? = null,
    @SerialName("auto_frequency") val autoFrequency: String? = null,
    @SerialName("tax_rate") val taxRate: Double = 0.0,
    @SerialName("tax_amount") val taxAmount: Double = 0.0,
    @SerialName("tax_inclusive") val taxInclusive: Boolean = false,
    @SerialName("deleted_at") val deletedAt: String? = null
)

// ═══════════════════════════════════════
//  CATEGORY — maps to public.categories
// ═══════════════════════════════════════

@Serializable
data class SupabaseCategory(
    val id: String,
    @SerialName("cashbook_id") val cashbookId: String,
    val name: String,
    val type: String? = null,  // "Income" or "Expense"
    @SerialName("color_hex") val colorHex: String? = null,
    @SerialName("icon_res_id") val iconResId: Int? = null,
    @SerialName("is_custom") val isCustom: Boolean = false,
    @SerialName("deleted_at") val deletedAt: String? = null
)
