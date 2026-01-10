package com.phynix.artham.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class SigninViewModel extends ViewModel {

    private final FirebaseAuth mAuth;
    private final MutableLiveData<FirebaseUser> _user = new MutableLiveData<>();
    private final MutableLiveData<String> _error = new MutableLiveData<>();

    // Initialize with false so the UI knows not to show the loader immediately
    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);

    public LiveData<FirebaseUser> getUser() { return _user; }
    public LiveData<String> getError() { return _error; }
    public LiveData<Boolean> getLoading() { return _loading; }

    public SigninViewModel() {
        mAuth = FirebaseAuth.getInstance();
    }

    /**
     * Updates the loading state.
     * This fixes the "cannot find symbol: method setLoading(boolean)" error.
     */
    public void setLoading(boolean isLoading) {
        _loading.setValue(isLoading);
    }

    // --- Google Sign In Logic ---
    public void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        setLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        _user.setValue(mAuth.getCurrentUser());
                    } else {
                        _error.setValue(task.getException() != null ?
                                task.getException().getMessage() : "Google authentication failed.");
                    }
                    setLoading(false);
                });
    }

    public void clearError() {
        _error.setValue(null);
    }
}