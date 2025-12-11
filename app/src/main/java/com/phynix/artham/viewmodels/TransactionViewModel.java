package com.phynix.artham.viewmodels;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.phynix.artham.db.DataRepository;
import com.phynix.artham.models.TransactionModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class TransactionViewModel extends AndroidViewModel {

    private static final String TAG = "TransactionViewModel";

    private final DataRepository repository;
    private final String cashbookId;

    // LiveData for reactive UI updates
    private final MutableLiveData<List<TransactionModel>> allTransactions = new MutableLiveData<>();
    private final MutableLiveData<List<TransactionModel>> filteredTransactions = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>();

    public TransactionViewModel(@NonNull Application application, String cashbookId) {
        super(application);
        this.repository = DataRepository.getInstance(application);
        this.cashbookId = cashbookId;

        Log.d(TAG, "TransactionViewModel initialized, cashbook: " + cashbookId);

        allTransactions.setValue(new ArrayList<>());
        filteredTransactions.setValue(new ArrayList<>());

        loadTransactions();
    }

    // --- Public Getters for LiveData ---
    public LiveData<List<TransactionModel>> getFilteredTransactions() {
        return filteredTransactions;
    }

    public LiveData<List<TransactionModel>> getAllTransactions() {
        return allTransactions;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void clearError() {
        errorMessage.setValue(null);
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * Loads transactions from repository
     */
    private void loadTransactions() {
        Log.d(TAG, "Loading transactions...");
        isLoading.postValue(true);

        if (cashbookId == null) {
            errorMessage.postValue("Error: No cashbook selected.");
            isLoading.postValue(false);
            return;
        }

        repository.getAllTransactions(cashbookId,
                transactions -> {
                    Log.d(TAG, "Transactions loaded successfully: " + transactions.size() + " items");
                    allTransactions.postValue(transactions);
                    filteredTransactions.postValue(transactions); // Initially, show all
                    isLoading.postValue(false);
                },
                error -> {
                    Log.e(TAG, "Error loading transactions: " + error);
                    errorMessage.postValue(error);
                    isLoading.postValue(false);
                    allTransactions.postValue(new ArrayList<>());
                    filteredTransactions.postValue(new ArrayList<>());
                }
        );
    }

    /**
     * Refreshes transactions by reloading from repository
     */
    public void refreshTransactions() {
        Log.d(TAG, "Refreshing transactions...");
        loadTransactions();
    }

    /**
     * Adds a new transaction.
     */
    public void addTransaction(TransactionModel transaction) {
        if (cashbookId == null) {
            errorMessage.postValue("Error: No cashbook selected.");
            return;
        }
        repository.addTransaction(cashbookId, transaction, success -> {
            if (!success) {
                errorMessage.postValue("Failed to add transaction.");
            }
        });
    }

    /**
     * [FIXED] Added this method to solve the error in EditTransactionActivity
     * Updates an existing transaction.
     */
    public void updateTransaction(TransactionModel transaction) {
        if (cashbookId == null) {
            errorMessage.postValue("Error: No cashbook selected.");
            return;
        }
        // Assuming your DataRepository has an updateTransaction method.
        // If not, 'addTransaction' usually works for updates too if the ID is set.
        repository.updateTransaction(cashbookId, transaction, success -> {
            if (!success) {
                errorMessage.postValue("Failed to update transaction.");
            }
        });
    }

    /**
     * Deletes a transaction.
     */
    public void deleteTransaction(String transactionId) {
        if (cashbookId == null) {
            errorMessage.postValue("Error: No cashbook selected.");
            return;
        }
        repository.deleteTransaction(cashbookId, transactionId, success -> {
            if (!success) {
                errorMessage.postValue("Failed to delete transaction.");
            }
        });
    }

    /**
     * Filters transactions based on multiple criteria
     */
    public void filter(String query, long startDate, long endDate, String entryType,
                       List<String> categories, List<String> paymentModes) {

        List<TransactionModel> originalList = allTransactions.getValue();
        if (originalList == null) {
            Log.w(TAG, "No transactions to filter");
            filteredTransactions.postValue(new ArrayList<>());
            return;
        }

        Log.d(TAG, "Applying filters - Query: " + query + ", Type: " + entryType);

        try {
            String searchQuery = (query != null) ? query.toLowerCase(Locale.getDefault()).trim() : "";
            List<String> safeCategories = (categories != null) ? categories : new ArrayList<>();
            List<String> safePaymentModes = (paymentModes != null) ? paymentModes : new ArrayList<>();

            List<TransactionModel> filteredList = originalList.stream()
                    .filter(transaction -> {
                        if (transaction == null) return false;

                        // Search Query Filter
                        boolean matchesSearch = searchQuery.isEmpty() ||
                                (transaction.getTransactionCategory() != null &&
                                        transaction.getTransactionCategory().toLowerCase(Locale.getDefault()).contains(searchQuery)) ||
                                (transaction.getPartyName() != null &&
                                        transaction.getPartyName().toLowerCase(Locale.getDefault()).contains(searchQuery)) ||
                                (transaction.getRemark() != null &&
                                        transaction.getRemark().toLowerCase(Locale.getDefault()).contains(searchQuery));

                        // Date Filter (0 means no filter)
                        boolean matchesDate = (startDate == 0 && endDate == 0) ||
                                (transaction.getTimestamp() >= startDate && transaction.getTimestamp() <= endDate);

                        // Entry Type Filter
                        boolean matchesEntryType = "All".equalsIgnoreCase(entryType) || (entryType == null) ||
                                (entryType.equalsIgnoreCase(transaction.getType()));

                        // Category Filter
                        boolean matchesCategory = safeCategories.isEmpty() ||
                                safeCategories.contains(transaction.getTransactionCategory());

                        // Payment Mode Filter
                        boolean matchesPaymentMode = safePaymentModes.isEmpty() ||
                                safePaymentModes.contains(transaction.getPaymentMode());

                        return matchesSearch && matchesDate && matchesEntryType && matchesCategory && matchesPaymentMode;
                    })
                    .collect(Collectors.toList());

            Log.d(TAG, "Filter applied: " + filteredList.size() + " transactions match criteria");
            filteredTransactions.postValue(filteredList);

        } catch (Exception e) {
            Log.e(TAG, "Error applying filters", e);
            filteredTransactions.postValue(originalList); // On error, show all
        }
    }

    public void clearFilters() {
        Log.d(TAG, "Clearing all filters");
        List<TransactionModel> originalList = allTransactions.getValue();
        if (originalList != null) {
            filteredTransactions.postValue(originalList);
        }
    }

    public String getCashbookId() {
        return cashbookId;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        Log.d(TAG, "TransactionViewModel cleared");
    }
}