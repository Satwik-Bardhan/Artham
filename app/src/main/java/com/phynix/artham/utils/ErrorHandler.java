package com.phynix.artham.utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.phynix.artham.auth.AuthManager;

import java.io.IOException;

public class ErrorHandler {
    private static final String TAG = "ErrorHandler";

    public static void handleDatabaseError(@NonNull Context context, @NonNull String errorMessage) {
        Log.e(TAG, "Database error: " + errorMessage);
        showErrorToUser(context, errorMessage != null ? errorMessage : "An unexpected error occurred. Please try again.");
    }

    public static void handleAuthError(@NonNull Context context, @Nullable Exception e) {
        Log.e(TAG, "Authentication error", e);

        String message = "Authentication failed. Please try again.";
        if (e != null && e.getMessage() != null) {
            String errorMsg = e.getMessage().toLowerCase();
            if (errorMsg.contains("network")) {
                message = "Network error. Please check your connection.";
            } else if (errorMsg.contains("password") || errorMsg.contains("invalid-credential")) {
                message = "Invalid email or password.";
            } else if (errorMsg.contains("user-not-found")) {
                message = "No account found with this email.";
            } else if (errorMsg.contains("email-already-in-use")) {
                message = "This email address is already in use.";
            }
        }

        showErrorToUser(context, message);
    }

    public static void handleExportError(@NonNull Context context, @NonNull Exception e) {
        Log.e(TAG, "Export error", e);

        String message;
        if (e instanceof SecurityException) {
            message = "Storage permission required to save files.";
        } else if (e instanceof IOException) {
            message = "Failed to write file. Please check storage space.";
        } else {
            message = "Export failed. Please try again.";
        }

        showErrorToUser(context, message);
    }

    private static void showErrorToUser(@NonNull Context context, String message) {
        // Always show Toast on the main thread to prevent CalledFromWrongThreadException
        android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        mainHandler.post(() -> {
            // Check if context is valid before showing toast
            if (context instanceof Activity && ((Activity) context).isFinishing()) {
                return;
            }
            try {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to show error toast", e);
            }
        });
    }

    public static void showLoadingError(@NonNull Context context, String operation) {
        String message = "Failed to " + operation + ". Please check your connection and try again.";
        showErrorToUser(context, message);
    }
}