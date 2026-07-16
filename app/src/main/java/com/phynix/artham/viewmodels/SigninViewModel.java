package com.phynix.artham.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.phynix.artham.auth.SupabaseAuthManager;

/**
 * SignInViewModel — Handles Google Sign-In via Supabase Auth.
 *
 * Flow: Google Sign-In SDK → ID Token → Supabase Auth
 * No Firebase Auth dependency.
 */
public class SignInViewModel extends ViewModel {

    private final MutableLiveData<String> _userId = new MutableLiveData<>();
    private final MutableLiveData<String> _error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);

    public LiveData<String> getUserId() { return _userId; }
    public LiveData<String> getError() { return _error; }
    public LiveData<Boolean> getLoading() { return _loading; }

    public void setLoading(boolean isLoading) {
        _loading.setValue(isLoading);
    }

    /**
     * Authenticate with Supabase using a Google ID token.
     * The user profile is created/updated in Supabase's public.users table
     * by SupabaseAuthManager.signInWithGoogle().
     */
    public void signInWithGoogle(String idToken, String email, String displayName, String photoUrl) {
        setLoading(true);

        SupabaseAuthManager.signInWithGoogle(idToken, null, email, displayName, photoUrl,
                new SupabaseAuthManager.AuthCallback() {
                    @Override
                    public void onSuccess(String userId) {
                        _userId.setValue(userId);
                        setLoading(false);
                    }

                    @Override
                    public void onError(String error) {
                        _error.setValue(error);
                        setLoading(false);
                    }
                });
    }

    public void clearError() {
        _error.setValue(null);
    }
}