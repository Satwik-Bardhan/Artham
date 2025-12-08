package com.phynix.artham.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class TransactionViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;
    private final String cashbookId;

    public TransactionViewModelFactory(Application application, String cashbookId) {
        this.application = application;
        this.cashbookId = cashbookId;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(TransactionViewModel.class)) {
            // [FIX] Passes the cashbookId to the ViewModel
            // noinspection unchecked
            return (T) new TransactionViewModel(application, cashbookId);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}