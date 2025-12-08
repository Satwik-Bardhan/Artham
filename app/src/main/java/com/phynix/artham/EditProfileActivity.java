package com.phynix.artham;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.phynix.artham.models.Users;
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
    private LinearLayout dateOfBirthLayout, changePasswordLayout;
    private Switch twoFactorSwitch;
    private Button cancelButton, saveProfileButton;

    private FirebaseAuth mAuth;
    private DatabaseReference userDatabaseRef;
    private StorageReference storageReference;
    private FirebaseUser currentUser;

    private Calendar dobCalendar;
    private Uri imageUri;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    imageUri = result.getData().getData();
                    Glide.with(this).load(imageUri).into(profileImageView);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        storageReference = FirebaseStorage.getInstance().getReference("profile_pictures");

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
        changePasswordLayout = findViewById(R.id.changePasswordLayout);
        twoFactorSwitch = findViewById(R.id.twoFactorSwitch);
        cancelButton = findViewById(R.id.cancelButton);
        saveProfileButton = findViewById(R.id.saveProfileButton);

        dobCalendar = Calendar.getInstance();
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        cancelButton.setOnClickListener(v -> finish());

        editProfilePictureButton.setOnClickListener(v -> openImagePicker());
        dateOfBirthLayout.setOnClickListener(v -> showDatePicker());
        saveProfileButton.setOnClickListener(v -> saveProfileChanges());

        changePasswordLayout.setOnClickListener(v -> showChangePasswordDialog());

        twoFactorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> Toast.makeText(this, "2FA Toggled: " + isChecked, Toast.LENGTH_SHORT).show());
    }

    private void showChangePasswordDialog() {
        if (currentUser.getEmail() == null) {
            Toast.makeText(this, "Cannot change password: No email associated.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setMessage("We will send a password reset link to your email address:\n\n" + currentUser.getEmail())
                .setPositiveButton("Send Email", (dialog, which) -> {
                    mAuth.sendPasswordResetEmail(currentUser.getEmail())
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Reset email sent! Check your inbox.", Toast.LENGTH_LONG).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Failed to send email: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
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
                    } else {
                        dateOfBirthText.setText("Select Date");
                    }

                    if (user.getProfile() != null && !user.getProfile().isEmpty()) {
                        Glide.with(EditProfileActivity.this)
                                .load(user.getProfile())
                                .placeholder(R.drawable.ic_person_placeholder)
                                .error(R.drawable.ic_person_placeholder)
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

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void saveProfileChanges() {
        String fullName = editFullName.getText().toString().trim();
        String phoneNumber = editPhoneNumber.getText().toString().trim();

        if (TextUtils.isEmpty(fullName)) {
            editFullName.setError("Full name is required.");
            editFullName.requestFocus();
            return;
        }

        // [FIX] Validate Phone Number Length
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

        saveProfileButton.setEnabled(false);
        saveProfileButton.setText("Saving...");

        if (imageUri != null) {
            uploadImageAndSaveData(fullName, phoneNumber, dobCalendar.getTimeInMillis());
        } else {
            saveDataToDatabase(fullName, phoneNumber, dobCalendar.getTimeInMillis(), null);
        }
    }

    private void uploadImageAndSaveData(String fullName, String phoneNumber, long dobTimestamp) {
        final StorageReference fileReference = storageReference.child(currentUser.getUid() + ".jpg");

        fileReference.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> fileReference.getDownloadUrl().addOnSuccessListener(uri -> {
                    String imageUrl = uri.toString();
                    saveDataToDatabase(fullName, phoneNumber, dobTimestamp, imageUrl);
                }))
                .addOnFailureListener(e -> {
                    Toast.makeText(EditProfileActivity.this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    saveProfileButton.setEnabled(true);
                    saveProfileButton.setText("Save Changes");
                });
    }

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
                    saveProfileButton.setEnabled(true);
                    saveProfileButton.setText("Save Changes");

                    if (task.isSuccessful()) {
                        Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(EditProfileActivity.this, "Failed to update database.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}