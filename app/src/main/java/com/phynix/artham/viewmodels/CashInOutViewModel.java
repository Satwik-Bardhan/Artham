package com.phynix.artham.viewmodels;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.phynix.artham.db.DataRepository;
import com.phynix.artham.models.TransactionModel;

/**
 * CashInOutViewModel - ViewModel for handling transaction creation and editing
 */
public class CashInOutViewModel extends AndroidViewModel {

    private static final String TAG = "CashInOutViewModel";

    private final DataRepository repository;

    public CashInOutViewModel(@NonNull Application application) {
        super(application);
        repository = DataRepository.getInstance(application);
        Log.d(TAG, "CashInOutViewModel initialized for authenticated user.");
    }

    public void saveTransaction(String cashbookId, TransactionModel transaction) {
        if (!isTransactionValid(transaction, false)) {
            Log.e(TAG, "Invalid transaction. Save aborted.");
            return;
        }

        if (cashbookId == null || cashbookId.isEmpty()) {
            Log.e(TAG, "Cashbook ID is null or empty, cannot save Firebase transaction");
            return;
        }

        repository.addTransaction(cashbookId, transaction, new DataRepository.DataCallback<Boolean>() {
            @Override
            public void onCallback(Boolean success) {
                if (success) {
                    Log.d(TAG, "Firebase transaction saved successfully");
                } else {
                    Log.e(TAG, "Failed to save Firebase transaction");
                }
            }
        });
    }

    // [NEW] Method to duplicate a transaction
    public void duplicateTransaction(String cashbookId, TransactionModel originalTransaction, boolean isTemplate) {
        if (originalTransaction == null || cashbookId == null) return;

        TransactionModel newTransaction = new TransactionModel();
        newTransaction.setAmount(originalTransaction.getAmount());
        newTransaction.setType(originalTransaction.getType());
        newTransaction.setTransactionCategory(originalTransaction.getTransactionCategory());
        newTransaction.setPaymentMode(originalTransaction.getPaymentMode());
        newTransaction.setPartyName(originalTransaction.getPartyName());
        newTransaction.setTimestamp(System.currentTimeMillis()); // Current time

        if (isTemplate) {
            newTransaction.setRemark("[TEMPLATE] " + originalTransaction.getRemark());
        } else {
            newTransaction.setRemark(originalTransaction.getRemark() + " (Copy)");
        }

        // Save as a new transaction
        saveTransaction(cashbookId, newTransaction);
    }

    public void updateTransaction(String cashbookId, TransactionModel transaction) {
        if (!isTransactionValid(transaction, true)) {
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

    public boolean isTransactionValid(TransactionModel transaction, boolean isUpdate) {
        if (transaction == null) {
            Log.w(TAG, "Transaction validation failed: transaction is null");
            return false;
        }

        if (isUpdate && (transaction.getTransactionId() == null || transaction.getTransactionId().isEmpty())) {
            Log.w(TAG, "Transaction validation failed: transaction ID is empty for update");
            return false;
        }

        if (transaction.getAmount() <= 0) {
            Log.w(TAG, "Transaction validation failed: invalid amount");
            return false;
        }

        if (transaction.getTransactionCategory() == null ||
                transaction.getTransactionCategory().trim().isEmpty() ||
                transaction.getTransactionCategory().equals("Select Category")) {
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
        Log.d(TAG, "CashInOutViewModel cleared");
    }
}