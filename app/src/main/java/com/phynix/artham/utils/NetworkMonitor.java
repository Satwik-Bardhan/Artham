package com.phynix.artham.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * NetworkMonitor — Singleton that monitors network connectivity changes.
 * When the network becomes available, it triggers a sync of any
 * offline-queued transactions.
 */
public class NetworkMonitor {

    private static final String TAG = "NetworkMonitor";
    private static volatile NetworkMonitor INSTANCE;

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean isMonitoring = false;

    private NetworkMonitor() {}

    public static synchronized NetworkMonitor getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new NetworkMonitor();
        }
        return INSTANCE;
    }

    /**
     * Check if the device currently has an active internet connection.
     */
    public static boolean isOnline(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) return false;

            NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
            if (caps == null) return false;

            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Exception e) {
            Log.e(TAG, "Error checking network status", e);
            return false;
        }
    }

    /**
     * Start monitoring network changes. When connectivity is restored,
     * automatically syncs any pending offline transactions.
     */
    public void startMonitoring(Context context) {
        if (isMonitoring) return;

        connectivityManager = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                Log.d(TAG, "Network available — triggering offline sync");
                OfflineSyncWorker.syncNow(context.getApplicationContext());
                // Trigger Supabase sync engine on reconnect
                com.phynix.artham.db.sync.SyncWorker.triggerImmediateSync(context.getApplicationContext());
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                Log.d(TAG, "Network lost");
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
            isMonitoring = true;
            Log.d(TAG, "Network monitoring started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register network callback", e);
        }
    }

    /**
     * Stop monitoring network changes.
     */
    public void stopMonitoring() {
        if (!isMonitoring || connectivityManager == null || networkCallback == null) return;

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            isMonitoring = false;
            Log.d(TAG, "Network monitoring stopped");
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister network callback", e);
        }
    }
}
