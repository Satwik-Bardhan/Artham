package com.phynix.artham.utils;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.database.DatabaseError;

import java.io.IOException;

public class ErrorHandler {
    private static final String TAG = "ErrorHandler";

    public static void handleFirebaseError(@NonNull Context context, @NonNull DatabaseError error) {
        Log.e(TAG, "Firebase error: " + error.getMessage());

        String userMessage;
        switch (error.getCode()) {
            case DatabaseError.NETWORK_ERROR:
                userMessage = "Network connection failed. Please check your internet connection.";
                break;
            case DatabaseError.PERMISSION_DENIED:
                userMessage = "Permission denied. Please log in again.";
                break;
            case DatabaseError.UNAVAILABLE:
                userMessage = "Service temporarily unavailable. Please try again later.";
                break;
            case DatabaseError.USER_CODE_EXCEPTION:
                userMessage = "Invalid data format. Please try again.";
                break;
            default:
                userMessage = "An unexpected error occurred. Please try again.";
        }

        showErrorToUser(context, userMessage);
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
        // Check if context is valid before showing toast
        if (context instanceof Activity && !((Activity) context).isFinishing()) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        } else if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }

    public static void showLoadingError(@NonNull Context context, String operation) {
        String message = "Failed to " + operation + ". Please check your connection and try again.";
        showErrorToUser(context, message);
    }
}