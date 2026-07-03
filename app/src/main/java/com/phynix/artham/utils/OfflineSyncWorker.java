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

                // Use CountDownLatch to properly wait for all Firebase callbacks
                final java.util.concurrent.CountDownLatch latch =
                        new java.util.concurrent.CountDownLatch(pendingList.size());
                final java.util.concurrent.atomic.AtomicInteger successCount =
                        new java.util.concurrent.atomic.AtomicInteger(0);
                final java.util.concurrent.atomic.AtomicInteger failCount =
                        new java.util.concurrent.atomic.AtomicInteger(0);

                for (OfflineTransactionManager.PendingTransaction pending : pendingList) {
                    String cashbookId = pending.getCashbookId();
                    TransactionModel transaction = pending.getTransaction();
                    String pendingId = pending.getPendingId();

                    if (cashbookId == null || transaction == null) {
                        Log.w(TAG, "Skipping invalid pending entry: " + pendingId);
                        OfflineTransactionManager.removePendingTransaction(appContext, pendingId);
                        latch.countDown();
                        continue;
                    }

                    // Use the existing Firebase save method
                    repository.addTransaction(cashbookId, transaction, success -> {
                        if (success) {
                            Log.d(TAG, "✓ Synced transaction: " + pendingId);
                            OfflineTransactionManager.removePendingTransaction(appContext, pendingId);
                            successCount.incrementAndGet();
                        } else {
                            Log.w(TAG, "✗ Failed to sync transaction: " + pendingId + " — will retry later");
                            failCount.incrementAndGet();
                        }
                        latch.countDown();
                    });
                }

                // Wait for all callbacks with a reasonable timeout (30 seconds)
                boolean completed = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);

                if (!completed) {
                    Log.w(TAG, "Sync timed out after 30 seconds — some transactions may still be pending");
                }

                int remaining = OfflineTransactionManager.getPendingCount(appContext);
                Log.d(TAG, "Sync finished: " + successCount.get() + " succeeded, "
                        + failCount.get() + " failed, " + remaining + " still pending");

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
