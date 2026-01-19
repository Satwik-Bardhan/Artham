package com.phynix.artham.viewmodels;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
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

public class HomePageViewModel extends AndroidViewModel {

    private static final String TAG = "HomePageViewModel";

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

    // --- State ---
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // --- Listeners for Cleanup ---
    private ValueEventListener activeTransactionListener;
    private DatabaseReference activeTransactionRef;

    public HomePageViewModel(@NonNull Application application) {
        super(application);
        repository = DataRepository.getInstance(application);
        executorService = Executors.newSingleThreadExecutor();

        if (repository.isUserAuthenticated()) {
            currentUserId = repository.getCurrentUserId();
            loadCashbooks();
        } else {
            errorMessage.setValue("User not logged in.");
        }
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

    public String getCurrentCashbookId() { return currentCashbookId; }

    // ============================================
    // Actions
    // ============================================

    private void loadCashbooks() {
        isLoading.setValue(true);
        repository.getCashbooks(data -> {
            cashbooks.setValue(data);

            String lastId = getActiveCashbookIdFromPrefs();
            String targetId = null;

            if (lastId != null && data.stream().anyMatch(c -> c.getCashbookId().equals(lastId))) {
                targetId = lastId;
            } else if (!data.isEmpty()) {
                targetId = data.get(0).getCashbookId();
            }

            if (targetId != null) {
                switchCashbook(targetId);
            } else {
                isLoading.setValue(false);
            }
        }, error -> {
            errorMessage.setValue(error);
            isLoading.setValue(false);
        });
    }

    public void switchCashbook(String cashbookId) {
        if (cashbookId == null) return;

        if (activeTransactionListener != null && activeTransactionRef != null) {
            activeTransactionRef.removeEventListener(activeTransactionListener);
        }

        currentCashbookId = cashbookId;
        saveActiveCashbookIdToPrefs(cashbookId);

        List<CashbookModel> currentList = cashbooks.getValue();
        if (currentList != null) {
            for (CashbookModel c : currentList) {
                if (c.getCashbookId().equals(cashbookId)) {
                    activeCashbook.setValue(c);
                    break;
                }
            }
        }

        isLoading.setValue(true);

        activeTransactionRef = FirebaseDatabase.getInstance().getReference()
                .child(Constants.NODE_USERS)
                .child(currentUserId)
                .child(Constants.NODE_CASHBOOKS)
                .child(cashbookId)
                .child(Constants.NODE_TRANSACTIONS);

        activeTransactionListener = repository.subscribeToTransactions(cashbookId,
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

            for (TransactionModel t : rawData) {
                double amount = t.getAmount();
                boolean isIncome = Constants.TRANSACTION_TYPE_IN.equalsIgnoreCase(t.getType());

                if (isIncome) in += amount;
                else out += amount;

                if (isToday(t.getTimestamp())) {
                    todayList.add(t);
                    if (isIncome) tIn += amount;
                    else tOut += amount;
                }
            }

            double finalIn = in;
            double finalOut = out;
            double finalTIn = tIn;
            double finalTOut = tOut;

            transactions.postValue(rawData);
            totalIncome.postValue(finalIn);
            totalExpense.postValue(finalOut);
            currentBalance.postValue(finalIn - finalOut);

            todayIncome.postValue(finalTIn);
            todayExpense.postValue(finalTOut);
            todayBalance.postValue(finalTIn - finalTOut);
            todaysTransactions.postValue(todayList);

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
        SharedPreferences prefs = getApplication().getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(Constants.PREF_ACTIVE_CASHBOOK_PREFIX + currentUserId, cashbookId).apply();
    }

    private String getActiveCashbookIdFromPrefs() {
        SharedPreferences prefs = getApplication().getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(Constants.PREF_ACTIVE_CASHBOOK_PREFIX + currentUserId, null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (activeTransactionListener != null && activeTransactionRef != null) {
            activeTransactionRef.removeEventListener(activeTransactionListener);
        }
    }
}