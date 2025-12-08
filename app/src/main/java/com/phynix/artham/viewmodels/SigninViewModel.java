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
    public LiveData<FirebaseUser> getUser() { return _user; } // [FIX] Public getter

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> getError() { return _error; } // [FIX] Public getter

    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>();
    public LiveData<Boolean> getLoading() { return _loading; } // [FIX] Public getter

    public SigninViewModel() {
        mAuth = FirebaseAuth.getInstance();
    }

    public void signInWithEmail(String email, String password) {
        _loading.setValue(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        _user.setValue(mAuth.getCurrentUser());
                    } else {
                        _error.setValue(task.getException() != null ? task.getException().getMessage() : "Authentication failed.");
                    }
                    _loading.setValue(false);
                });
    }

    public void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        _loading.setValue(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        _user.setValue(mAuth.getCurrentUser());
                    } else {
                        _error.setValue(task.getException() != null ? task.getException().getMessage() : "Google sign-in failed.");
                    }
                    _loading.setValue(false);
                });
    }

    // [FIX] Removed signInAnonymously() as guest mode is no longer supported
    // public void signInAnonymously() { ... }

    // [FIX] Added method to clear error after it's shown
    public void clearError() {
        _error.setValue(null);
    }
}