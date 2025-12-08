package com.phynix.artham.viewmodels;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.phynix.artham.db.DataRepository;
import com.phynix.artham.models.TransactionModel;

/**
 * TransactionDetailViewModel - ViewModel for managing transaction details
 * Handles updating and deleting transactions for authenticated users.
 * [FIX] Guest mode logic has been removed.
 */
public class TransactionDetailViewModel extends AndroidViewModel {

    private static final String TAG = "TransactionDetailViewModel";

    private final DataRepository repository;

    public TransactionDetailViewModel(@NonNull Application application) {
        super(application);
        this.repository = DataRepository.getInstance(application);
        Log.d(TAG, "TransactionDetailViewModel initialized for authenticated user.");
    }

    /**
     * Updates a transaction in Firebase.
     * @param cashbookId The ID of the cashbook
     * @param transaction The transaction to update
     */
    public void updateTransaction(String cashbookId, TransactionModel transaction) {
        if (!isTransactionValid(transaction)) {
            Log.e(TAG, "Invalid transaction, update aborted.");
            return;
        }

        if (cashbookId == null || cashbookId.isEmpty()) {
            Log.e(TAG, "Cashbook ID is null or empty, cannot update Firebase transaction");
            return;
        }

        repository.updateTransaction(cashbookId, transaction, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    Log.d(TAG, "Firebase transaction updated successfully");
                } else {
                    Log.e(TAG, "Failed to update Firebase transaction");
                }
            }
        });
    }

    /**
     * Deletes a transaction from Firebase.
     * @param cashbookId The ID of the cashbook
     * @param transactionId The ID of the transaction to delete
     */
    public void deleteTransaction(String cashbookId, String transactionId) {
        if (transactionId == null || transactionId.isEmpty()) {
            Log.e(TAG, "Transaction ID is null or empty, cannot delete");
            return;
        }

        if (cashbookId == null || cashbookId.isEmpty()) {
            Log.e(TAG, "Cashbook ID is null or empty, cannot delete Firebase transaction");
            return;
        }

        repository.deleteTransaction(cashbookId, transactionId, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    Log.d(TAG, "Firebase transaction deleted successfully");
                } else {
                    Log.e(TAG, "Failed to delete Firebase transaction");
                }
            }
        });
    }

    /**
     * Validates transaction data before updating
     * @param transaction The transaction to validate
     * @return true if valid, false otherwise
     */
    public boolean isTransactionValid(TransactionModel transaction) {
        if (transaction == null) {
            Log.w(TAG, "Transaction validation failed: transaction is null");
            return false;
        }

        if (transaction.getTransactionId() == null || transaction.getTransactionId().isEmpty()) {
            Log.w(TAG, "Transaction validation failed: transaction ID is empty");
            return false;
        }

        if (transaction.getAmount() <= 0) {
            Log.w(TAG, "Transaction validation failed: invalid amount");
            return false;
        }

        if (transaction.getTransactionCategory() == null || transaction.getTransactionCategory().trim().isEmpty()) {
            Log.w(TAG, "Transaction validation failed: category is empty");
            return false;
        }

        if (transaction.getType() == null ||
                (!transaction.getType().equals("IN") && !transaction.getType().equals("OUT"))) {
            Log.w(TAG, "Transaction validation failed: invalid type");
            return false;
        }

        return true;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "TransactionDetailViewModel cleared");
    }
}