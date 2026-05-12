package com.phynix.artham.viewmodels;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

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
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            saveNewUserProfileIfNeeded(firebaseUser);
                        }
                        _user.setValue(firebaseUser);
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

    /**
     * Checks if the user already has profile data in Firebase.
     * If not (new user), saves their Google account name, email, and profile photo.
     */
    private void saveNewUserProfileIfNeeded(FirebaseUser firebaseUser) {
        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(firebaseUser.getUid());

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists() || !snapshot.hasChild("name")) {
                    // New user — populate profile from Google account data
                    Map<String, Object> userData = new HashMap<>();

                    String displayName = firebaseUser.getDisplayName();
                    if (displayName != null && !displayName.isEmpty()) {
                        userData.put("name", displayName);
                        userData.put("userName", displayName);
                    }

                    String email = firebaseUser.getEmail();
                    if (email != null && !email.isEmpty()) {
                        userData.put("email", email);
                    }

                    Uri photoUrl = firebaseUser.getPhotoUrl();
                    if (photoUrl != null) {
                        userData.put("profile", photoUrl.toString());
                    }

                    userData.put("uid", firebaseUser.getUid());

                    if (!userData.isEmpty()) {
                        userRef.updateChildren(userData);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // Silently fail — profile can be set up later
            }
        });
    }

    public void clearError() {
        _error.setValue(null);
    }
}