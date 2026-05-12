package com.phynix.artham;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
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
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.phynix.artham.models.Users;
import com.phynix.artham.utils.ThemeManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EditProfileActivity extends BaseActivity {

    private static final String TAG = "EditProfileActivity";

    private ImageView profileImageView, backButton, editProfilePictureButton;
    private EditText editFullName;
    private TextView displayEmail, displayUid, dateOfBirthText;
    private LinearLayout dateOfBirthLayout;
    private Button saveProfileButton, cancelButton, deleteAccountButton;

    private FirebaseAuth mAuth;
    private DatabaseReference userDatabaseRef;
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
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userDatabaseRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());

        initViews();
        setupImagePicker();
        loadUserInfo();

        backButton.setOnClickListener(v -> finish());
        cancelButton.setOnClickListener(v -> finish());

        // EXPLICIT BUTTON LISTENER
        saveProfileButton.setOnClickListener(v -> {
            Log.d(TAG, "Save Button Clicked!");
            saveProfileChanges();
        });

        View.OnClickListener imgClick = v -> openImageChooser();
        editProfilePictureButton.setOnClickListener(imgClick);
        profileImageView.setOnClickListener(imgClick);

        dateOfBirthLayout.setOnClickListener(v -> showDatePicker());
        deleteAccountButton.setOnClickListener(v -> showDeleteAccountDialog());
    }

    private void initViews() {
        // Even if using ShapeableImageView in XML, referencing it as ImageView here is perfectly fine
        profileImageView = findViewById(R.id.profileImageView);
        backButton = findViewById(R.id.backButton);
        editProfilePictureButton = findViewById(R.id.editProfilePictureButton);

        editFullName = findViewById(R.id.editFullName);
        displayEmail = findViewById(R.id.displayEmail);
        displayUid = findViewById(R.id.displayUid);

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

                        // Temporarily show the selected image locally before uploading
                        Glide.with(this)
                                .load(imageUri)
                                .centerCrop()
                                .circleCrop()
                                .into(profileImageView);

                        Log.d(TAG, "Image selected: " + imageUri.toString());
                    }
                }
        );
    }

    private void openImageChooser() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void loadUserInfo() {
        if (currentUser.getEmail() != null) displayEmail.setText(currentUser.getEmail());
        if (currentUser.getUid() != null) displayUid.setText(currentUser.getUid());

        userDatabaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Users user = snapshot.getValue(Users.class);
                if (user != null) {
                    if (user.getUserName() != null) editFullName.setText(user.getUserName());
                    else if (user.getName() != null) editFullName.setText(user.getName());

                    if (user.getEmail() != null && !user.getEmail().isEmpty()) displayEmail.setText(user.getEmail());

                    if (user.getProfile() != null && !user.getProfile().isEmpty() && !isDestroyed()) {
                        currentPhotoUrl = user.getProfile();

                        // PERFECT CIRCLE GLIDE LOGIC
                        Glide.with(EditProfileActivity.this)
                                .load(currentPhotoUrl)
                                .placeholder(R.drawable.ic_person_placeholder)
                                .error(R.drawable.ic_person_placeholder)
                                .centerCrop() // Scales image to fill bounds without stretching
                                .circleCrop() // Crops into a perfect circle
                                .into(profileImageView);
                    }

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
                Toast.makeText(EditProfileActivity.this, "Failed to load user data.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveProfileChanges() {
        String name = editFullName.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            editFullName.setError("Name is required");
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Updating Profile");
        progressDialog.setMessage("Please wait...");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        if (imageUri != null) {
            progressDialog.setMessage("Uploading image to Firebase...");
            Log.d(TAG, "Starting image upload process");

            /* * IMPORTANT: Bulletproof storage initialization.
             * Tries the specific bucket first, falls back to default if it fails.
             */
            FirebaseStorage storage;
            try {
                storage = FirebaseStorage.getInstance("gs://artham-67.firebasestorage.app");
            } catch (Exception e) {
                Log.e(TAG, "Storage init failed. Falling back to default.", e);
                storage = FirebaseStorage.getInstance();
            }

            StorageReference fileRef = storage.getReference().child("profile_pictures/" + currentUser.getUid() + ".jpg");
            byte[] imageData = getCompressedImageData(imageUri);

            if (imageData == null || imageData.length == 0) {
                progressDialog.dismiss();
                Toast.makeText(this, "Image processing failed. Try a different photo.", Toast.LENGTH_LONG).show();
                return;
            }

            StorageMetadata metadata = new StorageMetadata.Builder().setContentType("image/jpeg").build();
            UploadTask uploadTask = fileRef.putBytes(imageData, metadata);

            uploadTask.addOnSuccessListener(taskSnapshot -> {
                Log.d(TAG, "Upload successful, fetching URL...");
                progressDialog.setMessage("Image uploaded! Securing link...");

                fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    updateDatabase(name, uri.toString(), progressDialog);
                }).addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Log.e(TAG, "Failed to get download URL", e);
                    Toast.makeText(EditProfileActivity.this, "Error getting link: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });

            }).addOnFailureListener(e -> {
                progressDialog.dismiss();
                Log.e(TAG, "Image Upload Failed", e);
                Toast.makeText(EditProfileActivity.this, "Image Upload Failed. Check your network or Firebase rules.", Toast.LENGTH_LONG).show();
            });

        } else {
            Log.d(TAG, "No image selected. Updating database only.");
            progressDialog.setMessage("Saving profile data...");
            updateDatabase(name, null, progressDialog);
        }
    }

    private byte[] getCompressedImageData(Uri uri) {
        try {
            InputStream inputBounds = getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputBounds, null, options);
            if (inputBounds != null) inputBounds.close();

            int reqWidth = 600;
            int reqHeight = 600;
            int inSampleSize = 1;

            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                final int halfHeight = options.outHeight / 2;
                final int halfWidth = options.outWidth / 2;
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2;
                }
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;

            InputStream inputImage = getContentResolver().openInputStream(uri);
            Bitmap scaledBitmap = BitmapFactory.decodeStream(inputImage, null, options);
            if (inputImage != null) inputImage.close();

            if (scaledBitmap != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error compressing image: " + e.getMessage());
        }
        return null;
    }

    private void updateDatabase(String name, String imageUrl, ProgressDialog loadingBar) {
        loadingBar.setMessage("Finalizing update...");
        Map<String, Object> profileUpdates = new HashMap<>();
        profileUpdates.put("name", name);
        profileUpdates.put("userName", name);

        if (dobTimestamp > 0) profileUpdates.put("dateOfBirthTimestamp", dobTimestamp);
        if (imageUrl != null) profileUpdates.put("profile", imageUrl);

        userDatabaseRef.updateChildren(profileUpdates)
                .addOnSuccessListener(aVoid -> {
                    loadingBar.dismiss();
                    Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    loadingBar.dismiss();
                    Log.e(TAG, "Database Update Failed", e);
                    Toast.makeText(EditProfileActivity.this, "Database Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
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