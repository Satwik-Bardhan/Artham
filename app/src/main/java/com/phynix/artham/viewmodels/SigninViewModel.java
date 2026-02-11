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
    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>(false);

    public SigninViewModel() {
        mAuth = FirebaseAuth.getInstance();
    }

    public LiveData<FirebaseUser> getUser() { return _user; }
    public LiveData<String> getError() { return _error; }
    public LiveData<Boolean> getLoading() { return _loading; }

    public void setLoading(boolean isLoading) {
        _loading.setValue(isLoading);
    }

    public void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        setLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Success: The Activity observer will handle navigation
                        _user.setValue(mAuth.getCurrentUser());
                    } else {
                        String msg = "Authentication failed.";
                        if (task.getException() != null) {
                            msg = task.getException().getMessage();
                        }
                        _error.setValue(msg);
                    }
                    setLoading(false);
                });
    }

    public void clearError() {
        _error.setValue(null);
    }
}