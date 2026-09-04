package com.phynix.artham.viewmodels;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.phynix.artham.auth.AuthManager;
import com.phynix.artham.db.DataRepository;
import com.phynix.artham.models.CashbookModel;
import com.phynix.artham.models.TransactionModel;
import com.phynix.artham.models.Users;
import com.phynix.artham.utils.Constants;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeViewModel extends AndroidViewModel {

    private static final String TAG = "HomeViewModel";

    private final DataRepository repository;
    private final ExecutorService executorService;

    private String currentUserId;
    private String currentCashbookId;

    // --- Data Sources ---
    private final MutableLiveData<List<TransactionModel>> transactions = new MutableLiveData<>();
    private final MutableLiveData<List<CashbookModel>> cashbooks = new MutableLiveData<>();
    private final MutableLiveData<CashbookModel> activeCashbook = new MutableLiveData<>();
    private final MutableLiveData<Users> userProfile = new MutableLiveData<>();

    // --- UI Summaries ---
    private final MutableLiveData<Double> totalIncome = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> totalExpense = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> currentBalance = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> todayIncome = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> todayExpense = new MutableLiveData<>(0.0);
    private final MutableLiveData<Double> todayBalance = new MutableLiveData<>(0.0);
    private final MutableLiveData<List<TransactionModel>> todaysTransactions = new MutableLiveData<>();
    private final MutableLiveData<List<TransactionModel>> recentTransactions = new MutableLiveData<>();

    // --- State ---
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public HomeViewModel(@NonNull Application application) {
        super(application);
        repository = DataRepository.getInstance(application);
        executorService = Executors.newSingleThreadExecutor();
        currentUserId = AuthManager.getUserId(application);
        loadCashbooks();
    }

    // ============================================
    // Getters
    // ============================================

    public LiveData<List<TransactionModel>> getTransactions() { return transactions; }
    public LiveData<List<CashbookModel>> getCashbooks() { return cashbooks; }
    public LiveData<CashbookModel> getActiveCashbook() { return activeCashbook; }
    public LiveData<Users> getUserProfile() { return userProfile; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    public LiveData<Double> getTotalIncome() { return totalIncome; }
    public LiveData<Double> getTotalExpense() { return totalExpense; }
    public LiveData<Double> getCurrentBalance() { return currentBalance; }
    public LiveData<Double> getTodayBalance() { return todayBalance; }
    public LiveData<List<TransactionModel>> getTodaysTransactions() { return todaysTransactions; }
    public LiveData<List<TransactionModel>> getRecentTransactions() { return recentTransactions; }

    public String getCurrentCashbookId() { return currentCashbookId; }

    // ============================================
    // Actions
    // ============================================

    public void loadCashbooks() {
        if (currentUserId == null) {
            currentUserId = AuthManager.getUserId(getApplication());
        }
        if (currentUserId == null) return;

        isLoading.setValue(true);
        repository.getCashbooks(data -> {
            cashbooks.setValue(data);

            // If switchCashbook was already called (from HomeActivity.onCreate),
            // don't override the user's selection
            if (currentCashbookId != null) {
                // Verify the selected cashbook still exists in the loaded list
                boolean stillExists = data.stream()
                        .anyMatch(c -> c != null && currentCashbookId.equals(c.getCashbookId()));
                if (stillExists) {
                    switchCashbook(currentCashbookId);
                    return;
                }
            }

            String lastId = getActiveCashbookIdFromPrefs();
            String targetId = null;

            if (lastId != null && data.stream()
                    .anyMatch(c -> c != null && lastId.equals(c.getCashbookId()))) {
                targetId = lastId;
            } else if (!data.isEmpty()) {
                targetId = data.get(0).getCashbookId();
            }

            if (targetId != null) {
                switchCashbook(targetId);
            } else {
                activeCashbook.setValue(null);
                isLoading.setValue(false);
            }
        }, error -> {
            errorMessage.setValue(error);
            isLoading.setValue(false);
        });
    }

    public void switchCashbook(String cashbookId) {
        if (cashbookId == null || currentUserId == null) return;

        currentCashbookId = cashbookId;
        saveActiveCashbookIdToPrefs(cashbookId);

        List<CashbookModel> currentList = cashbooks.getValue();
        if (currentList != null) {
            for (CashbookModel c : currentList) {
                if (c != null && cashbookId.equals(c.getCashbookId())) {
                    activeCashbook.setValue(c);
                    break;
                }
            }
        }

        isLoading.setValue(true);

        repository.subscribeToTransactions(cashbookId,
                this::processTransactions,
                error -> {
                    errorMessage.setValue(error);
                    isLoading.setValue(false);
                }
        );
    }

    private void processTransactions(List<TransactionModel> rawData) {
        executorService.execute(() -> {
            double in = 0, out = 0;
            double tIn = 0, tOut = 0;
            List<TransactionModel> todayList = new ArrayList<>();
            List<TransactionModel> previousList = new ArrayList<>();

            if (rawData != null) {
                for (TransactionModel t : rawData) {
                    if (t == null) continue;
                    double amount = t.getAmount();
                    boolean isIncome = Constants.TRANSACTION_TYPE_IN.equalsIgnoreCase(t.getType());

                    if (isIncome) in += amount;
                    else out += amount;

                    if (isToday(t.getTimestamp())) {
                        todayList.add(t);
                        if (isIncome) tIn += amount;
                        else tOut += amount;
                    } else {
                        previousList.add(t);
                    }
                }
            }

            if (!previousList.isEmpty()) {
                previousList.sort((a, b) -> {
                    if (a == null && b == null) return 0;
                    if (a == null) return 1;
                    if (b == null) return -1;
                    return Long.compare(b.getTimestamp(), a.getTimestamp());
                });
            }
            List<TransactionModel> latestPrevious = previousList.size() > 10
                    ? previousList.subList(0, 10) : previousList;

            double finalIn = in;
            double finalOut = out;
            double finalTIn = tIn;
            double finalTOut = tOut;

            transactions.postValue(rawData != null ? rawData : new ArrayList<>());
            totalIncome.postValue(finalIn);
            totalExpense.postValue(finalOut);
            currentBalance.postValue(finalIn - finalOut);

            todayIncome.postValue(finalTIn);
            todayExpense.postValue(finalTOut);
            todayBalance.postValue(finalTIn - finalTOut);
            todaysTransactions.postValue(todayList);
            recentTransactions.postValue(new ArrayList<>(latestPrevious));

            isLoading.postValue(false);
        });
    }

    private boolean isToday(long timestamp) {
        Calendar tCal = Calendar.getInstance();
        tCal.setTimeInMillis(timestamp);
        Calendar now = Calendar.getInstance();
        return tCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                tCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
    }

    private void saveActiveCashbookIdToPrefs(String cashbookId) {
        if (currentUserId == null) return;
        SharedPreferences prefs = getApplication().getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.PREF_ACTIVE_CASHBOOK_PREFIX + currentUserId, cashbookId).apply();
    }

    private String getActiveCashbookIdFromPrefs() {
        if (currentUserId == null) return null;
        SharedPreferences prefs = getApplication().getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(Constants.PREF_ACTIVE_CASHBOOK_PREFIX + currentUserId, null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}