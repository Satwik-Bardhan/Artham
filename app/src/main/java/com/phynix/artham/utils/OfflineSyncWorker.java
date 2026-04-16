package com.phynix.artham.utils;

import android.content.Context;
import android.util.Log;

import com.phynix.artham.db.DataRepository;
import com.phynix.artham.models.TransactionModel;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OfflineSyncWorker — Handles background synchronization of offline-queued
 * transactions to Firebase when network connectivity is restored.
 */
public class OfflineSyncWorker {

    private static final String TAG = "OfflineSyncWorker";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean isSyncing = new AtomicBoolean(false);

    /**
     * Attempt to sync all pending offline transactions to Firebase.
     * This is called automatically when network is restored, and also
     * on app startup as a safety net.
     *
     * Runs on a background thread to avoid blocking the UI.
     */
    public static void syncNow(Context context) {
        if (context == null) return;

        // Prevent concurrent sync attempts
        if (!isSyncing.compareAndSet(false, true)) {
            Log.d(TAG, "Sync already in progress, skipping...");
            return;
        }

        executor.execute(() -> {
            try {
                Context appContext = context.getApplicationContext();

                if (!NetworkMonitor.isOnline(appContext)) {
                    Log.d(TAG, "Still offline, skipping sync");
                    isSyncing.set(false);
                    return;
                }

                List<OfflineTransactionManager.PendingTransaction> pendingList =
                        OfflineTransactionManager.getPendingTransactions(appContext);

                if (pendingList.isEmpty()) {
                    Log.d(TAG, "No pending transactions to sync");
                    isSyncing.set(false);
                    return;
                }

                Log.d(TAG, "Starting sync of " + pendingList.size() + " offline transactions");

                DataRepository repository = DataRepository.getInstance(
                        (android.app.Application) appContext);

                for (OfflineTransactionManager.PendingTransaction pending : pendingList) {
                    String cashbookId = pending.getCashbookId();
                    TransactionModel transaction = pending.getTransaction();
                    String pendingId = pending.getPendingId();

                    if (cashbookId == null || transaction == null) {
                        Log.w(TAG, "Skipping invalid pending entry: " + pendingId);
                        OfflineTransactionManager.removePendingTransaction(appContext, pendingId);
                        continue;
                    }

                    // Use the existing Firebase save method
                    repository.addTransaction(cashbookId, transaction, success -> {
                        if (success) {
                            Log.d(TAG, "✓ Synced transaction: " + pendingId);
                            OfflineTransactionManager.removePendingTransaction(appContext, pendingId);
                        } else {
                            Log.w(TAG, "✗ Failed to sync transaction: " + pendingId + " — will retry later");
                        }
                    });
                }

                // Give Firebase callbacks a moment to complete
                Thread.sleep(2000);

                int remaining = OfflineTransactionManager.getPendingCount(appContext);
                if (remaining > 0) {
                    Log.d(TAG, "Sync completed with " + remaining + " entries still pending");
                } else {
                    Log.d(TAG, "All offline transactions synced successfully!");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error during offline sync", e);
            } finally {
                isSyncing.set(false);
            }
        });
    }

    /**
     * Check if a sync operation is currently in progress.
     */
    public static boolean isSyncInProgress() {
        return isSyncing.get();
    }
}
