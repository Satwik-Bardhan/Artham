package com.phynix.artham.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class SigninViewModel extends ViewModel {

    private final FirebaseAuth mAuth;
    private final MutableLiveData<FirebaseUser> _user = new MutableLiveData<>();
    private final MutableLiveData<String> _error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _loading = new MutableLiveData<>();

    public LiveData<FirebaseUser> getUser() { return _user; }
    public LiveData<String> getError() { return _error; }
    public LiveData<Boolean> getLoading() { return _loading; }

    public SigninViewModel() {
        mAuth = FirebaseAuth.getInstance();
    }

    // --- Magic Link Logic ---
    public void sendEmailLink(String email) {
        _loading.setValue(true);

        ActionCodeSettings actionCodeSettings =
                ActionCodeSettings.newBuilder()
                        // TODO: Verify this URL matches your Firebase Console -> Authentication -> Sign-in method -> Email Link
                        .setUrl("https://satvik-artham.firebaseapp.com/finishSignUp")
                        .setHandleCodeInApp(true)
                        .setAndroidPackageName(
                                "com.phynix.artham", // UPDATED PACKAGE NAME
                                true,
                                "1")
                        .build();

        mAuth.sendSignInLinkToEmail(email, actionCodeSettings)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        _error.setValue("Magic link sent to " + email + ". Check your inbox!");
                    } else {
                        _error.setValue("Failed to send link: " +
                                (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                    }
                    _loading.setValue(false);
                });
    }

    public void signInWithEmailLink(String email, String emailLink) {
        _loading.setValue(true);
        mAuth.signInWithEmailLink(email, emailLink)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = task.getResult().getUser();
                        _user.setValue(user);
                    } else {
                        _error.setValue("Sign in failed: " +
                                (task.getException() != null ? task.getException().getMessage() : "Invalid link"));
                    }
                    _loading.setValue(false);
                });
    }

    // --- Google Sign In Logic ---
    public void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        _loading.setValue(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        _user.setValue(mAuth.getCurrentUser());
                    } else {
                        _error.setValue("Google authentication failed.");
                    }
                    _loading.setValue(false);
                });
    }

    public void clearError() {
        _error.setValue(null);
    }
}