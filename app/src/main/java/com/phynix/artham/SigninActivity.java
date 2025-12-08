package com.phynix.artham;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;
import com.phynix.artham.viewmodels.SigninViewModel;

public class SigninActivity extends AppCompatActivity {

    private static final String TAG = "SigninActivity";

    private EditText emailEditText, passwordEditText;
    private Button signinButton;
    private TextView forgotPasswordText, signUpText;
    private ImageView togglePasswordVisibility, backButton, helpButton;
    private CardView googleSignInCard;
    private ProgressBar loadingIndicator;

    private SigninViewModel viewModel;
    private GoogleSignInClient mGoogleSignInClient;

    private boolean isPasswordVisible = false;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            viewModel.firebaseAuthWithGoogle(account);
                        } else {
                            Toast.makeText(this, "Google sign in failed.", Toast.LENGTH_SHORT).show();
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
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        viewModel = new ViewModelProvider(this).get(SigninViewModel.class);

        // [FIX] Use auto-generated web client ID
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
        emailEditText = findViewById(R.id.email);
        passwordEditText = findViewById(R.id.password);
        signinButton = findViewById(R.id.signinButton);
        forgotPasswordText = findViewById(R.id.forgotPassword);
        signUpText = findViewById(R.id.signUpText);
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility);
        googleSignInCard = findViewById(R.id.googleSigninCard);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        backButton = findViewById(R.id.backButton);
        helpButton = findViewById(R.id.helpButton);
    }

    private void observeViewModel() {
        viewModel.getUser().observe(this, firebaseUser -> {
            if (firebaseUser != null) {
                updateUI(firebaseUser);
            }
        });

        viewModel.getError().observe(this, errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
            }
        });

        viewModel.getLoading().observe(this, isLoading -> {
            setLoading(isLoading);
        });
    }

    private void setupClickListeners() {
        signinButton.setOnClickListener(v -> attemptEmailPasswordSignIn());
        googleSignInCard.setOnClickListener(v -> signInWithGoogle());
        signUpText.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));

        forgotPasswordText.setOnClickListener(v -> {
            // [FIX] Safety check before getting text
            if (emailEditText != null && emailEditText.getText() != null) {
                Intent intent = new Intent(this, ForgotPasswordActivity.class);
                intent.putExtra("email", emailEditText.getText().toString().trim());
                startActivity(intent);
            }
        });

        togglePasswordVisibility.setOnClickListener(v -> togglePasswordVisibility());
        backButton.setOnClickListener(v -> onBackPressed());
        helpButton.setOnClickListener(v -> Toast.makeText(this, "Help button clicked", Toast.LENGTH_SHORT).show());
    }

    private void attemptEmailPasswordSignIn() {
        // [FIX] Add explicit null checks to prevent crashes
        if (emailEditText == null || passwordEditText == null) return;
        if (emailEditText.getText() == null || passwordEditText.getText() == null) return;

        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("A valid email is required.");
            emailEditText.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required.");
            passwordEditText.requestFocus();
            return;
        }

        viewModel.signInWithEmail(email, password);
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            passwordEditText.setTransformationMethod(new PasswordTransformationMethod());
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_off);
        } else {
            passwordEditText.setTransformationMethod(null);
            togglePasswordVisibility.setImageResource(R.drawable.ic_visibility_on);
        }
        isPasswordVisible = !isPasswordVisible;
        passwordEditText.setSelection(passwordEditText.length());
    }

    private void setLoading(boolean isLoading) {
        loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        signinButton.setEnabled(!isLoading);
        googleSignInCard.setEnabled(!isLoading);
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            Toast.makeText(this, "Sign In Successful", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, HomePage.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }
}