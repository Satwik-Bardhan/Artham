package com.phynix.artham.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.phynix.artham.db.DataRepository;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.utils.Constants;

public class CashInOutViewModel extends AndroidViewModel {

    private final DataRepository repository;

    // LiveData for UI State
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> operationSuccess = new MutableLiveData<>();

    public CashInOutViewModel(@NonNull Application application) {
        super(application);
        repository = DataRepository.getInstance(application);
    }

    // --- Getters for LiveData ---
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getOperationSuccess() { return operationSuccess; }

    // --- Actions ---

    public void saveTransaction(String cashbookId, TransactionModel transaction) {
        if (!validateTransaction(transaction)) return;

        isLoading.setValue(true);
        repository.addTransaction(cashbookId, transaction, success -> {
            isLoading.postValue(false);
            if (success) {
                operationSuccess.postValue(true);
            } else {
                errorMessage.postValue("Failed to save transaction.");
            }
        });
    }

    public void updateTransaction(String cashbookId, TransactionModel transaction) {
        if (!validateTransaction(transaction)) return;

        isLoading.setValue(true);
        repository.updateTransaction(cashbookId, transaction, success -> {
            isLoading.postValue(false);
            if (success) {
                operationSuccess.postValue(true);
            } else {
                errorMessage.postValue("Failed to update transaction.");
            }
        });
    }

    public void deleteTransaction(String cashbookId, String transactionId) {
        if (cashbookId == null || transactionId == null) {
            errorMessage.setValue("Invalid ID provided.");
            return;
        }

        isLoading.setValue(true);
        repository.deleteTransaction(cashbookId, transactionId, success -> {
            isLoading.postValue(false);
            if (success) {
                operationSuccess.postValue(true);
            } else {
                errorMessage.postValue("Failed to delete transaction.");
            }
        });
    }

    public void duplicateTransaction(String cashbookId, TransactionModel originalTransaction, boolean isTemplate) {
        if (originalTransaction == null || cashbookId == null) return;

        TransactionModel newTransaction = new TransactionModel();
        newTransaction.setAmount(originalTransaction.getAmount());
        newTransaction.setType(originalTransaction.getType());
        newTransaction.setTransactionCategory(originalTransaction.getTransactionCategory());
        newTransaction.setPaymentMode(originalTransaction.getPaymentMode());
        newTransaction.setPartyName(originalTransaction.getPartyName());
        newTransaction.setTimestamp(System.currentTimeMillis());

        if (isTemplate) {
            newTransaction.setRemark("[TEMPLATE] " + originalTransaction.getRemark());
        } else {
            newTransaction.setRemark(originalTransaction.getRemark() + " (Copy)");
        }

        // Reuse save logic
        saveTransaction(cashbookId, newTransaction);
    }

    // --- Validation ---

    private boolean validateTransaction(TransactionModel transaction) {
        if (transaction == null) {
            errorMessage.setValue("Invalid transaction data.");
            return false;
        }

        if (transaction.getAmount() <= 0) {
            errorMessage.setValue("Amount must be greater than 0.");
            return false;
        }

        if (transaction.getTransactionCategory() == null ||
                transaction.getTransactionCategory().trim().isEmpty() ||
                "Select Category".equals(transaction.getTransactionCategory())) {
            errorMessage.setValue("Please select a category.");
            return false;
        }

        // Using Constants class for safer type checking
        if (transaction.getType() == null ||
                (!transaction.getType().equals(Constants.TRANSACTION_TYPE_IN) &&
                        !transaction.getType().equals(Constants.TRANSACTION_TYPE_OUT))) {
            errorMessage.setValue("Invalid transaction type.");
            return false;
        }

        return true;
    }
}