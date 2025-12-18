package com.phynix.artham;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.phynix.artham.viewmodels.SigninViewModel;

public class SigninActivity extends AppCompatActivity {

    private static final String TAG = "SigninActivity";

    private EditText emailInput;
    private Button btnSignInEmail;
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

        // Handle incoming Email Links
        checkIntentForEmailLink(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkIntentForEmailLink(intent);
    }

    private void checkIntentForEmailLink(Intent intent) {
        if (intent != null && intent.getData() != null) {
            String emailLink = intent.getData().toString();
            FirebaseAuth auth = FirebaseAuth.getInstance();

            if (auth.isSignInWithEmailLink(emailLink)) {
                SharedPreferences prefs = getSharedPreferences("ArthamPrefs", MODE_PRIVATE);
                String email = prefs.getString("email_for_signin", "");

                if (TextUtils.isEmpty(email)) {
                    // Email not saved locally (user might be on a different device)
                    emailInput.requestFocus();
                    btnSignInEmail.setText("Verify Link & Sign In");
                    Toast.makeText(this, "Please enter your email to complete the sign-in", Toast.LENGTH_LONG).show();

                    // Override the button listener to verify instead of send
                    btnSignInEmail.setOnClickListener(v -> {
                        String userEmail = emailInput.getText().toString().trim();
                        if (TextUtils.isEmpty(userEmail)) {
                            emailInput.setError("Email required");
                            return;
                        }
                        viewModel.signInWithEmailLink(userEmail, emailLink);
                    });
                } else {
                    // We have the email, complete sign in automatically
                    emailInput.setText(email);
                    viewModel.signInWithEmailLink(email, emailLink);
                }
            }
        }
    }

    private void initializeUI() {
        emailInput = findViewById(R.id.emailInput);
        btnSignInEmail = findViewById(R.id.btnSignInEmail);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        backButton = findViewById(R.id.backButton);
        helpButton = findViewById(R.id.helpButton);
    }

    private void setupClickListeners() {
        // Send Magic Link
        btnSignInEmail.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.setError("Enter a valid email");
                emailInput.requestFocus();
                return;
            }

            // Save email to SharedPrefs to use when the link is clicked
            SharedPreferences prefs = getSharedPreferences("ArthamPrefs", MODE_PRIVATE);
            prefs.edit().putString("email_for_signin", email).apply();

            viewModel.sendEmailLink(email);
        });

        // Google Sign In
        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());

        backButton.setOnClickListener(v -> onBackPressed());
        helpButton.setOnClickListener(v -> Toast.makeText(this, "Enter your email to receive a passwordless login link", Toast.LENGTH_SHORT).show());
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
            btnSignInEmail.setEnabled(!isLoading);
            btnGoogleSignIn.setEnabled(!isLoading);
            emailInput.setEnabled(!isLoading);
        });
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }
}