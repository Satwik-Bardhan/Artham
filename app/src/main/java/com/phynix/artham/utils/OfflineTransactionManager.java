package com.phynix.artham.utils;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.phynix.artham.models.TransactionModel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OfflineTransactionManager — Manages a local queue of transactions
 * that were created while the device was offline.
 * Data is stored as a JSON file in the Downloads/Artham folder
 * (same location as PDF reports).
 */
public class OfflineTransactionManager {

    private static final String TAG = "OfflineTransactionManager";
    private static final String OFFLINE_FILE_NAME = "artham_offline_queue.json";
    private static final Gson gson = new Gson();

    /**
     * Wrapper class that pairs a transaction with its target cashbook.
     */
    public static class PendingTransaction {
        private String pendingId;
        private String cashbookId;
        private TransactionModel transaction;
        private long queuedAt;

        public PendingTransaction() {}

        public PendingTransaction(String cashbookId, TransactionModel transaction) {
            this.pendingId = UUID.randomUUID().toString();
            this.cashbookId = cashbookId;
            this.transaction = transaction;
            this.queuedAt = System.currentTimeMillis();
        }

        public String getPendingId() { return pendingId; }
        public void setPendingId(String pendingId) { this.pendingId = pendingId; }
        public String getCashbookId() { return cashbookId; }
        public void setCashbookId(String cashbookId) { this.cashbookId = cashbookId; }
        public TransactionModel getTransaction() { return transaction; }
        public void setTransaction(TransactionModel transaction) { this.transaction = transaction; }
        public long getQueuedAt() { return queuedAt; }
        public void setQueuedAt(long queuedAt) { this.queuedAt = queuedAt; }
    }

    /**
     * Queue a transaction for later sync.
     */
    public static synchronized void queueTransaction(Context context, String cashbookId, TransactionModel transaction) {
        List<PendingTransaction> pending = getPendingTransactions(context);
        pending.add(new PendingTransaction(cashbookId, transaction));
        savePendingList(context, pending);
        Log.d(TAG, "Transaction queued offline. Pending count: " + pending.size());
    }

    /**
     * Get all pending (not yet synced) transactions.
     */
    public static synchronized List<PendingTransaction> getPendingTransactions(Context context) {
        String json = readOfflineFile(context);
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            Type listType = new TypeToken<List<PendingTransaction>>() {}.getType();
            List<PendingTransaction> list = gson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Error parsing offline queue", e);
            return new ArrayList<>();
        }
    }

    /**
     * Remove a specific pending transaction after successful sync.
     */
    public static synchronized void removePendingTransaction(Context context, String pendingId) {
        List<PendingTransaction> pending = getPendingTransactions(context);
        pending.removeIf(p -> p.getPendingId().equals(pendingId));
        savePendingList(context, pending);
    }

    /**
     * Get the count of pending offline transactions.
     */
    public static int getPendingCount(Context context) {
        return getPendingTransactions(context).size();
    }

    /**
     * Check if there are any pending transactions.
     */
    public static boolean hasPendingTransactions(Context context) {
        return getPendingCount(context) > 0;
    }

    // --- Internal File I/O (Downloads/Artham folder) ---

    private static void savePendingList(Context context, List<PendingTransaction> list) {
        String json = gson.toJson(list);
        writeOfflineFile(context, json);
    }

    /**
     * Write JSON data to the offline queue file in Downloads/Artham.
     * Uses app-internal files dir as the primary storage for reliable read/write,
     * since MediaStore doesn't support updating files easily.
     */
    private static void writeOfflineFile(Context context, String json) {
        try {
            // Use app's internal files directory for reliable read-write.
            // This is always accessible without permissions.
            File arthamDir = new File(context.getFilesDir(), "Artham");
            if (!arthamDir.exists()) arthamDir.mkdirs();

            File file = new File(arthamDir, OFFLINE_FILE_NAME);
            FileWriter writer = new FileWriter(file, false);
            writer.write(json);
            writer.flush();
            writer.close();
            Log.d(TAG, "Offline queue saved: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to write offline file", e);
        }
    }

    /**
     * Clear all pending offline transactions.
     * Must be called during logout and account deletion to prevent
     * stale transactions from syncing into a new account.
     */
    public static synchronized void clearQueue(Context context) {
        try {
            File arthamDir = new File(context.getFilesDir(), "Artham");
            File file = new File(arthamDir, OFFLINE_FILE_NAME);
            if (file.exists()) {
                file.delete();
                Log.d(TAG, "Offline queue cleared");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear offline queue", e);
        }
    }

    /**
     * Read JSON data from the offline queue file.
     */
    private static String readOfflineFile(Context context) {
        try {
            File arthamDir = new File(context.getFilesDir(), "Artham");
            File file = new File(arthamDir, OFFLINE_FILE_NAME);

            if (!file.exists()) return null;

            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to read offline file", e);
            return null;
        }
    }
}
