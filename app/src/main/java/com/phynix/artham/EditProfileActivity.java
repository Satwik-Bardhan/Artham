package com.phynix.artham;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.phynix.artham.models.Users;
import com.phynix.artham.utils.ThemeManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private ImageView profileImageView, backButton, editProfilePictureButton;
    private EditText editFullName, editPhoneNumber;
    private TextView displayEmail, dateOfBirthText;
    private LinearLayout dateOfBirthLayout;
    private Button saveProfileButton, cancelButton, deleteAccountButton;

    private FirebaseAuth mAuth;
    private DatabaseReference userDatabaseRef;
    private StorageReference userProfileStorageRef;
    private FirebaseUser currentUser;

    private Uri imageUri;
    private final Calendar dobCalendar = Calendar.getInstance();
    private long dobTimestamp = 0;
    private String currentPhotoUrl;

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            finish();
            return;
        }

        userDatabaseRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        userProfileStorageRef = FirebaseStorage.getInstance().getReference().child("Profile Images");

        initViews();
        setupImagePicker();
        loadUserInfo();

        // Listeners
        backButton.setOnClickListener(v -> finish());
        cancelButton.setOnClickListener(v -> finish());
        saveProfileButton.setOnClickListener(v -> saveProfileChanges());

        View.OnClickListener imgClick = v -> openImageChooser();
        editProfilePictureButton.setOnClickListener(imgClick);
        profileImageView.setOnClickListener(imgClick);

        dateOfBirthLayout.setOnClickListener(v -> showDatePicker());
        deleteAccountButton.setOnClickListener(v -> showDeleteAccountDialog());
    }

    private void initViews() {
        profileImageView = findViewById(R.id.profileImageView);
        backButton = findViewById(R.id.backButton);
        editProfilePictureButton = findViewById(R.id.editProfilePictureButton);

        editFullName = findViewById(R.id.editFullName);
        editPhoneNumber = findViewById(R.id.editPhoneNumber);

        displayEmail = findViewById(R.id.displayEmail);
        dateOfBirthText = findViewById(R.id.dateOfBirthText);
        dateOfBirthLayout = findViewById(R.id.dateOfBirthLayout);

        saveProfileButton = findViewById(R.id.saveProfileButton);
        cancelButton = findViewById(R.id.cancelButton);

        deleteAccountButton = findViewById(R.id.delete_account_button);
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        imageUri = result.getData().getData();
                        profileImageView.setImageURI(imageUri);
                    }
                }
        );
    }

    private void openImageChooser() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void loadUserInfo() {
        // 1. Try setting email from Auth first (Fastest)
        if (currentUser.getEmail() != null) {
            displayEmail.setText(currentUser.getEmail());
        }

        userDatabaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Users user = snapshot.getValue(Users.class);
                if (user != null) {
                    // Update Name
                    if (user.getUserName() != null) {
                        editFullName.setText(user.getUserName());
                    } else if (user.getName() != null) {
                        editFullName.setText(user.getName());
                    }

                    // Update Phone
                    if (user.getPhoneNumber() != null) {
                        editPhoneNumber.setText(user.getPhoneNumber());
                    } else if (user.getPhone() != null) {
                        editPhoneNumber.setText(user.getPhone());
                    }

                    // [FIX] Update Email from DB if available (Overrides Auth if DB has it)
                    if (user.getEmail() != null && !user.getEmail().isEmpty()) {
                        displayEmail.setText(user.getEmail());
                    }

                    // Update Profile Image
                    if (user.getProfile() != null && !user.getProfile().isEmpty() && !isDestroyed()) {
                        currentPhotoUrl = user.getProfile();
                        Glide.with(EditProfileActivity.this)
                                .load(currentPhotoUrl)
                                .placeholder(R.drawable.ic_person_placeholder)
                                .circleCrop()
                                .into(profileImageView);
                    }

                    // Update DOB
                    if (snapshot.hasChild("dateOfBirthTimestamp")) {
                        dobTimestamp = snapshot.child("dateOfBirthTimestamp").getValue(Long.class);
                        updateDobText(dobTimestamp);
                    } else {
                        dateOfBirthText.setText("Select Date");
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(EditProfileActivity.this, "Failed to load data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveProfileChanges() {
        String name = editFullName.getText().toString().trim();
        String phone = editPhoneNumber.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            editFullName.setError("Name is required");
            return;
        }

        // Phone Validation: 10 digits and starts with 9, 8, or 7
        if (!TextUtils.isEmpty(phone)) {
            if (phone.length() != 10) {
                editPhoneNumber.setError("Phone number must be 10 digits");
                return;
            }
            char firstDigit = phone.charAt(0);
            if (firstDigit != '9' && firstDigit != '8' && firstDigit != '7') {
                editPhoneNumber.setError("Invalid number. Must start with 9, 8, or 7");
                return;
            }
        } else {
            editPhoneNumber.setError("Phone number is required");
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Updating Profile");
        progressDialog.setMessage("Please wait...");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        if (imageUri != null) {
            StorageReference fileRef = userProfileStorageRef.child(currentUser.getUid() + ".jpg");
            fileRef.putFile(imageUri).addOnSuccessListener(taskSnapshot ->
                    fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        updateDatabase(name, phone, uri.toString(), progressDialog);
                    })
            ).addOnFailureListener(e -> {
                progressDialog.dismiss();
                Toast.makeText(EditProfileActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
            });
        } else {
            updateDatabase(name, phone, null, progressDialog);
        }
    }

    private void updateDatabase(String name, String phone, String imageUrl, ProgressDialog loadingBar) {
        Map<String, Object> profileUpdates = new HashMap<>();
        // Use keys that match your Users model fields
        profileUpdates.put("name", name);
        profileUpdates.put("userName", name); // Save both to be safe
        profileUpdates.put("phone", phone);
        profileUpdates.put("phoneNumber", phone); // Save both to be safe

        if (dobTimestamp > 0) profileUpdates.put("dateOfBirthTimestamp", dobTimestamp);
        if (imageUrl != null) profileUpdates.put("profile", imageUrl);

        userDatabaseRef.updateChildren(profileUpdates)
                .addOnCompleteListener(task -> {
                    loadingBar.dismiss();
                    if (task.isSuccessful()) {
                        Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(EditProfileActivity.this, "Failed to update database.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showDatePicker() {
        new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    dobCalendar.set(year, month, dayOfMonth);
                    dobTimestamp = dobCalendar.getTimeInMillis();
                    updateDobText(dobTimestamp);
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

    private void showDeleteAccountDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This will erase all data.")
                .setPositiveButton("Delete", (dialog, which) -> deleteUserData())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteUserData() {
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Deleting account...");
        pd.show();

        userDatabaseRef.removeValue().addOnSuccessListener(aVoid -> {
            if (currentUser != null) {
                currentUser.delete().addOnCompleteListener(task -> {
                    pd.dismiss();
                    if (task.isSuccessful()) {
                        logoutAndRedirect();
                    } else {
                        Toast.makeText(this, "Re-login required to delete account.", Toast.LENGTH_LONG).show();
                        logoutAndRedirect();
                    }
                });
            }
        });
    }

    private void logoutAndRedirect() {
        mAuth.signOut();
        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, SigninActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}