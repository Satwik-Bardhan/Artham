package com.phynix.artham;

import android.app.Application;
import com.phynix.artham.utils.ThemeManager;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Apply the saved theme configuration (Night Mode YES/NO)
        // as soon as the application starts to prevent flashing.
        ThemeManager.applyGlobalNightMode(this);
    }
}