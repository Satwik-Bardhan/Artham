package com.phynix.artham;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.phynix.artham.viewmodels.SigninViewModel;

public class SigninActivity extends AppCompatActivity {

    private static final String TAG = "SigninActivity";

    private LinearLayout btnGoogleSignIn;
    private ImageView backButton, helpButton;
    private ProgressBar loadingIndicator;

    private SigninViewModel viewModel;
    private GoogleSignInClient mGoogleSignInClient;

    // Modern Activity Result API to handle Google Sign-In response
    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    handleSignInResult(task);
                } else {
                    Log.d(TAG, "Sign-in cancelled or failed with code: " + result.getResultCode());
                    viewModel.setLoading(false); // Ensure loader stops if user cancels
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);

        // Hide ActionBar for a clean, professional look
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        viewModel = new ViewModelProvider(this).get(SigninViewModel.class);

        configureGoogleSignIn();
        initializeUI();
        setupClickListeners();
        observeViewModel();
    }

    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void initializeUI() {
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        backButton = findViewById(R.id.backButton);
        helpButton = findViewById(R.id.helpButton);
    }

    private void setupClickListeners() {
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        backButton.setOnClickListener(v -> finish());
        helpButton.setOnClickListener(v -> Toast.makeText(this,
                "Please sign in with your Google account to secure your data.",
                Toast.LENGTH_SHORT).show());
    }

    private void observeViewModel() {
        // Observe successful login
        viewModel.getUser().observe(this, firebaseUser -> {
            if (firebaseUser != null) {
                Toast.makeText(this, "Welcome to Artham!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, HomePage.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });

        // Observe errors
        viewModel.getError().observe(this, errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        // Observe loading state
        viewModel.getLoading().observe(this, isLoading -> {
            loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnGoogleSignIn.setEnabled(!isLoading);
            btnGoogleSignIn.setAlpha(isLoading ? 0.5f : 1.0f); // Visual feedback
        });
    }

    private void signInWithGoogle() {
        viewModel.setLoading(true); // Start loading immediately on click
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                // Pass the account to the ViewModel for Firebase authentication
                viewModel.firebaseAuthWithGoogle(account);
            }
        } catch (ApiException e) {
            Log.w(TAG, "Google sign in failed code=" + e.getStatusCode());
            viewModel.setLoading(false);
            Toast.makeText(this, "Sign-in failed. Please try again.", Toast.LENGTH_SHORT).show();
        }
    }
}