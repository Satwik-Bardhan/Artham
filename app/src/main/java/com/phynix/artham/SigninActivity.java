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

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            viewModel.firebaseAuthWithGoogle(account);
                        }
                    } catch (ApiException e) {
                        Log.w(TAG, "Google sign in failed", e);
                        Toast.makeText(this, "Google sign in failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signin);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        viewModel = new ViewModelProvider(this).get(SigninViewModel.class);

        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        initializeUI();
        setupClickListeners();
        observeViewModel();
    }

    private void initializeUI() {
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        backButton = findViewById(R.id.backButton);
        helpButton = findViewById(R.id.helpButton);
    }

    private void setupClickListeners() {
        // Google Sign In
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());

        backButton.setOnClickListener(v -> onBackPressed());
        helpButton.setOnClickListener(v -> Toast.makeText(this, "Sign in with your Google account to continue.", Toast.LENGTH_SHORT).show());
    }

    private void observeViewModel() {
        viewModel.getUser().observe(this, firebaseUser -> {
            if (firebaseUser != null) {
                Toast.makeText(this, "Welcome to Artham!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, HomePage.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        });

        viewModel.getError().observe(this, errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });

        viewModel.getLoading().observe(this, isLoading -> {
            loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            btnGoogleSignIn.setEnabled(!isLoading);
        });
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }
}