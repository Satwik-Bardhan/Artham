package com.phynix.artham;

import android.app.Application;
import com.phynix.artham.utils.NetworkMonitor;
import com.phynix.artham.utils.OfflineSyncWorker;
import com.phynix.artham.utils.ThemeManager;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Get the saved theme (Default is now Dark)
        String savedTheme = ThemeManager.getTheme(this);

        // 2. Apply it globally to the app
        ThemeManager.applyTheme(savedTheme);

        // 3. Start network monitoring for offline transaction sync
        NetworkMonitor.getInstance().startMonitoring(this);

        // 4. Attempt to sync any transactions queued from a previous session
        OfflineSyncWorker.syncNow(this);
    }
}