package com.phynix.artham.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class CashInOutViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;

    public CashInOutViewModelFactory(Application application) {
        this.application = application;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(CashInOutViewModel.class)) {
            // [FIX] Removed the isGuest parameter from the constructor
            // noinspection unchecked
            return (T) new CashInOutViewModel(application);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}