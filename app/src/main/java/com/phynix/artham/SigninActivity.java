package com.phynix.artham;


import com.phynix.artham.activities.HomeActivity;
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
import com.phynix.artham.auth.AuthManager;
import com.phynix.artham.utils.ThemeManager;
import com.phynix.artham.viewmodels.SignInViewModel;

public class SignInActivity extends BaseActivity {

    private static final String TAG = "SignInActivity";

    private LinearLayout btnGoogleSignIn;
    private LinearLayout btnLocalMode;
    private ImageView backButton, helpButton;
    private ProgressBar loadingIndicator;

    private SignInViewModel viewModel;
    private GoogleSignInClient mGoogleSignInClient;

    // Modern Activity Result API to handle Google Sign-In response
    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    handleSignInResult(task);
                } else {
                    Log.w(TAG, "Sign-in result: " + result.getResultCode());
                    viewModel.setLoading(false);
                    // Code 0 usually means the user cancelled the dialog or network/config issue (SHA-1)
                    if (result.getResultCode() == 0) {
                        Toast.makeText(this, "Sign-in cancelled.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before super.onCreate to ensure consistent styling
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        // Hide Action Bar for clean UI
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        viewModel = new ViewModelProvider(this).get(SignInViewModel.class);

        configureGoogleSignIn();
        initializeUI();
        setupClickListeners();
        observeViewModel();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already signed in and update UI accordingly.
        if (AuthManager.isSignedIn(this)) {
            navigateToHome();
        }
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
        btnLocalMode = findViewById(R.id.btnLocalMode);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        backButton = findViewById(R.id.backButton);
        helpButton = findViewById(R.id.helpButton);
    }

    private void setupClickListeners() {
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
        btnLocalMode.setOnClickListener(v -> enterGuestMode());
        backButton.setOnClickListener(v -> finish());
        helpButton.setOnClickListener(v -> Toast.makeText(this,
                "Sign in to sync your expenses across devices.",
                Toast.LENGTH_SHORT).show());
    }

    private void observeViewModel() {
        viewModel.getUserId().observe(this, userId -> {
            if (userId != null) {
                navigateToHome();
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
            btnGoogleSignIn.setAlpha(isLoading ? 0.5f : 1.0f);
            if (btnLocalMode != null) {
                btnLocalMode.setEnabled(!isLoading);
                btnLocalMode.setAlpha(isLoading ? 0.5f : 1.0f);
            }
        });
    }

    private void navigateToHome() {
        Toast.makeText(this, "Welcome to Artham!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Enter Guest Mode — All data stored locally in Room DB.
     * User can sign in later to sync data to cloud.
     */
    private void enterGuestMode() {
        getSharedPreferences("AppPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("is_local_mode", true)
                .apply();
        Toast.makeText(this, "Welcome! Using offline mode.", Toast.LENGTH_SHORT).show();
        navigateToHome();
    }

    private void signInWithGoogle() {
        getSharedPreferences("AppPrefs", MODE_PRIVATE)
                .edit()
                .putBoolean("is_local_mode", false)
                .apply();
        viewModel.setLoading(true);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                String idToken = account.getIdToken();
                if (idToken != null) {
                    String email = account.getEmail();
                    String displayName = account.getDisplayName();
                    String photoUrl = account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null;

                    // Save user profile locally to avoid skeleton loader issues
                    AuthManager.saveUserProfile(this, displayName, email, photoUrl);

                    // Authenticate directly with Supabase (no Firebase)
                    viewModel.signInWithGoogle(idToken, email, displayName, photoUrl);
                } else {
                    viewModel.setLoading(false);
                    Toast.makeText(this, "Sign-in failed: No ID token received.", Toast.LENGTH_SHORT).show();
                }
            } else {
                viewModel.setLoading(false);
            }
        } catch (ApiException e) {
            Log.w(TAG, "Google sign in failed code=" + e.getStatusCode());
            viewModel.setLoading(false);

            if (e.getStatusCode() == 10) {
                Toast.makeText(this, "Configuration Error: SHA-1 Mismatch. Check Google Cloud Console.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Sign-in failed. Please try again.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}