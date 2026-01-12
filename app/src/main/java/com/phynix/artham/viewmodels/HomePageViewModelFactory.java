package com.phynix.artham.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class HomePageViewModelFactory implements ViewModelProvider.Factory {
    private final Application application;

    public HomePageViewModelFactory(Application application) {
        this.application = application;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(HomePageViewModel.class)) {
            //noinspection unchecked
            return (T) new HomePageViewModel(application);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}