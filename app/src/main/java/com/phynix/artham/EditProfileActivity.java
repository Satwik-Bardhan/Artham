package com.phynix.artham;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.phynix.artham.models.Users;
import com.phynix.artham.utils.ThemeManager; // [NEW IMPORT]
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private ImageView profileImageView, editProfilePictureButton, backButton;
    private EditText editFullName, editPhoneNumber;
    private TextView displayEmail, dateOfBirthText;
    private LinearLayout dateOfBirthLayout;
    private Button cancelButton, saveProfileButton;

    // Progress Dialog for better UX
    private ProgressDialog loadingBar;

    private FirebaseAuth mAuth;
    private DatabaseReference userDatabaseRef;
    private StorageReference storageReference;
    private FirebaseUser currentUser;

    private Calendar dobCalendar;
    private Uri imageUri;

    // Launcher to pick image from Gallery
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    imageUri = result.getData().getData();
                    // Show the selected image immediately (Circular crop)
                    Glide.with(this)
                            .load(imageUri)
                            .circleCrop()
                            .into(profileImageView);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // [FIX] Apply Theme BEFORE super.onCreate()
        ThemeManager.applyActivityTheme(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "No user logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userDatabaseRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        // Point to "profile_pictures" folder in Storage
        storageReference = FirebaseStorage.getInstance("gs://artham-67").getReference("profile_pictures");
        initializeUI();
        setupClickListeners();
        loadUserProfile();
    }

    private void initializeUI() {
        profileImageView = findViewById(R.id.profileImageView);
        editProfilePictureButton = findViewById(R.id.editProfilePictureButton);
        backButton = findViewById(R.id.backButton);
        editFullName = findViewById(R.id.editFullName);
        editPhoneNumber = findViewById(R.id.editPhoneNumber);
        displayEmail = findViewById(R.id.displayEmail);
        dateOfBirthText = findViewById(R.id.dateOfBirthText);
        dateOfBirthLayout = findViewById(R.id.dateOfBirthLayout);
        cancelButton = findViewById(R.id.cancelButton);
        saveProfileButton = findViewById(R.id.saveProfileButton);

        dobCalendar = Calendar.getInstance();

        // Initialize Progress Dialog
        loadingBar = new ProgressDialog(this);
        loadingBar.setTitle("Saving Profile");
        loadingBar.setMessage("Please wait while we update your account...");
        loadingBar.setCanceledOnTouchOutside(false);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        cancelButton.setOnClickListener(v -> finish());

        // Allow clicking BOTH the small edit icon AND the large image to pick a photo
        View.OnClickListener pickImageListener = v -> openImagePicker();
        editProfilePictureButton.setOnClickListener(pickImageListener);
        profileImageView.setOnClickListener(pickImageListener);

        dateOfBirthLayout.setOnClickListener(v -> showDatePicker());
        saveProfileButton.setOnClickListener(v -> saveProfileChanges());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void loadUserProfile() {
        if (currentUser.getEmail() != null) {
            displayEmail.setText(currentUser.getEmail());
        }

        userDatabaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Users user = snapshot.getValue(Users.class);
                if (user != null) {
                    if (user.getUserName() != null) editFullName.setText(user.getUserName());
                    if (user.getPhoneNumber() != null) editPhoneNumber.setText(user.getPhoneNumber());

                    if (user.getDateOfBirthTimestamp() > 0) {
                        updateDobText(user.getDateOfBirthTimestamp());
                    }

                    // Load existing profile picture using Glide
                    if (user.getProfile() != null && !user.getProfile().isEmpty()) {
                        Glide.with(EditProfileActivity.this)
                                .load(user.getProfile())
                                .placeholder(R.drawable.ic_person_placeholder)
                                .circleCrop() // Make it round
                                .into(profileImageView);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EditProfileActivity.this, "Failed to load profile.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveProfileChanges() {
        String fullName = editFullName.getText().toString().trim();
        String phoneNumber = editPhoneNumber.getText().toString().trim();

        if (TextUtils.isEmpty(fullName)) {
            editFullName.setError("Full name is required.");
            editFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(phoneNumber)) {
            editPhoneNumber.setError("Phone number is required.");
            editPhoneNumber.requestFocus();
            return;
        }

        if (phoneNumber.length() != 10) {
            editPhoneNumber.setError("Phone number must be exactly 10 digits.");
            editPhoneNumber.requestFocus();
            return;
        }

        // Show loading dialog
        loadingBar.show();

        // Check if user picked a new image
        if (imageUri != null) {
            uploadImageAndSaveData(fullName, phoneNumber, dobCalendar.getTimeInMillis());
        } else {
            // Just save text data
            saveDataToDatabase(fullName, phoneNumber, dobCalendar.getTimeInMillis(), null);
        }
    }

    // Step 1: Upload Image to Firebase Storage
    private void uploadImageAndSaveData(String fullName, String phoneNumber, long dobTimestamp) {
        final StorageReference fileReference = storageReference.child(currentUser.getUid() + ".jpg");

        fileReference.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot ->
                        // Step 2: Get Download URL
                        fileReference.getDownloadUrl().addOnSuccessListener(uri -> {
                            String imageUrl = uri.toString();
                            // Step 3: Save Data + Image URL to Database
                            saveDataToDatabase(fullName, phoneNumber, dobTimestamp, imageUrl);
                        })
                )
                .addOnFailureListener(e -> {
                    loadingBar.dismiss();
                    Toast.makeText(EditProfileActivity.this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // Step 3: Save Data to Realtime Database
    private void saveDataToDatabase(String fullName, String phoneNumber, long dobTimestamp, @Nullable String imageUrl) {
        Map<String, Object> profileUpdates = new HashMap<>();
        profileUpdates.put("userName", fullName);
        profileUpdates.put("phoneNumber", phoneNumber);

        if (!dateOfBirthText.getText().toString().equals("Select Date")) {
            profileUpdates.put("dateOfBirthTimestamp", dobTimestamp);
        }

        if (imageUrl != null) {
            profileUpdates.put("profile", imageUrl);
        }

        userDatabaseRef.updateChildren(profileUpdates)
                .addOnCompleteListener(task -> {
                    loadingBar.dismiss();
                    if (task.isSuccessful()) {
                        Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                        finish(); // Close activity and go back to Settings
                    } else {
                        Toast.makeText(EditProfileActivity.this, "Failed to update database.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // --- Helper Methods ---

    private void showDatePicker() {
        new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    dobCalendar.set(year, month, dayOfMonth);
                    updateDobText(dobCalendar.getTimeInMillis());
                },
                dobCalendar.get(Calendar.YEAR),
                dobCalendar.get(Calendar.MONTH),
                dobCalendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDobText(long timestamp) {
        dobCalendar.setTimeInMillis(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM dd, yyyy", Locale.US);
        dateOfBirthText.setText(sdf.format(dobCalendar.getTime()));
    }
}