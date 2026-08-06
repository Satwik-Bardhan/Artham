package com.phynix.artham.activities;


import com.phynix.artham.R;
import com.phynix.artham.BaseActivity;
import com.phynix.artham.SignInActivity;
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
import com.phynix.artham.auth.AuthManager;

import com.phynix.artham.models.Users;
import com.phynix.artham.utils.ThemeManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EditProfileActivity extends BaseActivity {

    private static final String TAG = "EditProfileActivity";

    private ImageView profileImageView, backButton, editProfilePictureButton;
    private EditText editFullName, editUid;
    private TextView displayEmail, dateOfBirthText;
    private LinearLayout dateOfBirthLayout;
    private Button saveProfileButton, cancelButton, deleteAccountButton;



    private Uri imageUri;
    private String originalTheme;
    private final Calendar dobCalendar = Calendar.getInstance();
    private long dobTimestamp = 0;
    private String currentPhotoUrl;

    private ActivityResultLauncher<Intent> imagePickerLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        originalTheme = ThemeManager.getTheme(this);

        if (!AuthManager.isSignedIn(this)) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupImagePicker();
        setupListeners();
        loadUserInfo();
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> finish());
        cancelButton.setOnClickListener(v -> finish());

        View.OnClickListener imgClick = v -> openImageChooser();
        if (editProfilePictureButton != null) editProfilePictureButton.setOnClickListener(imgClick);
        if (profileImageView != null) profileImageView.setOnClickListener(imgClick);

        if (dateOfBirthLayout != null) dateOfBirthLayout.setOnClickListener(v -> showDatePicker());
        if (deleteAccountButton != null) deleteAccountButton.setOnClickListener(v -> showDeleteAccountDialog());

        if (saveProfileButton != null) {
            saveProfileButton.setOnClickListener(v -> {
                Log.d(TAG, "Save Button Clicked!");
                saveProfileChanges();
            });
        }
    }

    private void initViews() {
        // Even if using ShapeableImageView in XML, referencing it as ImageView here is perfectly fine
        profileImageView = findViewById(R.id.profileImageView);
        backButton = findViewById(R.id.backButton);
        editProfilePictureButton = findViewById(R.id.editProfilePictureButton);

        editFullName = findViewById(R.id.editFullName);
        displayEmail = findViewById(R.id.displayEmail);
        editUid = findViewById(R.id.editUid);

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
        if (intent.resolveActivity(getPackageManager()) == null) {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
        }
        imagePickerLauncher.launch(intent);
    }

    private void loadUserInfo() {
        // Load UID into editable input
        if (editUid != null) {
            editUid.setText(AuthManager.getUserId(this));
        }

        // Load saved profile data from SharedPreferences
        android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);

        // Load name (try user_display_name first, then user_name from Google login)
        String savedName = prefs.getString("user_display_name", "");
        if (savedName.isEmpty()) {
            savedName = prefs.getString("user_name", "");
        }
        if (!savedName.isEmpty()) {
            editFullName.setText(savedName);
        }

        // Load email
        String savedEmail = AuthManager.getUserEmail(this);
        if (savedEmail == null || savedEmail.isEmpty()) {
            savedEmail = com.phynix.artham.auth.SupabaseAuthManager.getCurrentUserEmail();
        }
        if (savedEmail != null && !savedEmail.isEmpty()) {
            displayEmail.setText(savedEmail);
            getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                    .edit().putString("user_email", savedEmail).apply();
        }

        // Load DOB
        long savedDob = prefs.getLong("user_dob", 0);
        if (savedDob > 0) {
            dobTimestamp = savedDob;
            dobCalendar.setTimeInMillis(savedDob);
            updateDobText(savedDob);
        }

        // Load profile photo (try local path first, then Google photo URL)
        String savedPhotoPath = prefs.getString("user_photo_path", "");
        if (!savedPhotoPath.isEmpty()) {
            // Check if it's a local file or URL
            if (savedPhotoPath.startsWith("/")) {
                File photoFile = new File(savedPhotoPath);
                if (photoFile.exists()) {
                    currentPhotoUrl = savedPhotoPath;
                    Glide.with(this)
                            .load(photoFile)
                            .skipMemoryCache(true)
                            .centerCrop()
                            .circleCrop()
                            .into(profileImageView);
                }
            } else {
                // It's a URL (e.g. Google photo URL)
                currentPhotoUrl = savedPhotoPath;
                Glide.with(this)
                        .load(savedPhotoPath)
                        .centerCrop()
                        .circleCrop()
                        .into(profileImageView);
            }
        } else {
            // Fallback: try Google photo URL
            String photoUrl = prefs.getString("user_photo_url", "");
            if (!photoUrl.isEmpty()) {
                currentPhotoUrl = photoUrl;
                Glide.with(this)
                        .load(photoUrl)
                        .centerCrop()
                        .circleCrop()
                        .into(profileImageView);
            }
        }
    }

    private void saveProfileChanges() {
        String name = editFullName.getText().toString().trim();
        String uid = editUid != null ? editUid.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            editFullName.setError("Name is required");
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(uid)) {
            if (editUid != null) editUid.setError("User ID is required");
            Toast.makeText(this, "Please enter a User ID", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uid.length() < 3) {
            if (editUid != null) editUid.setError("UID must be at least 3 characters");
            Toast.makeText(this, "UID must be at least 3 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!uid.matches("^[a-zA-Z0-9._-]+$")) {
            if (editUid != null) {
                editUid.setError("Only '.', '-', and '_' special characters are allowed");
            }
            Toast.makeText(this, "Only '.', '-', and '_' special characters are allowed in UID", Toast.LENGTH_LONG).show();
            return;
        }

        // Read image data on main thread FIRST (URI permissions may not work on background threads)
        final byte[] imageData;
        if (imageUri != null) {
            imageData = getCompressedImageData(imageUri);
            Log.d(TAG, "Image data read: " + (imageData != null ? imageData.length + " bytes" : "null"));
        } else {
            imageData = null;
            Log.d(TAG, "No new image selected");
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Updating Profile");
        progressDialog.setMessage("Checking User ID...");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        AuthManager.checkUidAvailability(this, uid, isAvailable -> {
            if (!isAvailable) {
                progressDialog.dismiss();
                if (editUid != null) {
                    editUid.setError("This UID is already used by someone");
                }
                Toast.makeText(EditProfileActivity.this, "This UID is already used by someone", Toast.LENGTH_LONG).show();
                return;
            }

            progressDialog.setMessage("Please wait...");
            saveProfileWithUid(progressDialog, name, uid, imageData);
        });
    }

    private void saveProfileWithUid(ProgressDialog progressDialog, String name, String uid, byte[] imageData) {
        // Save on background thread
        new Thread(() -> {
            try {
                // Persist custom UID
                AuthManager.saveCustomUid(this, uid);

                android.content.SharedPreferences.Editor editor =
                        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit();

                // Save name
                editor.putString("user_display_name", name);
                editor.putString("user_name", name);
                Log.d(TAG, "Saving name: " + name + ", custom UID: " + uid);

                // Save DOB if set
                if (dobTimestamp > 0) {
                    editor.putLong("user_dob", dobTimestamp);
                }

                // Save profile photo to internal storage
                if (imageData != null) {
                    File photoDir = new File(getFilesDir(), "profile");
                    if (!photoDir.exists()) photoDir.mkdirs();

                    // Delete old profile photo files to prevent cache collision & free disk space
                    File[] oldFiles = photoDir.listFiles();
                    if (oldFiles != null) {
                        for (File f : oldFiles) {
                            try { f.delete(); } catch (Exception ignored) {}
                        }
                    }

                    File photoFile = new File(photoDir, "profile_photo_" + System.currentTimeMillis() + ".jpg");

                    try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                        fos.write(imageData);
                        fos.flush();
                    }

                    String photoPath = photoFile.getAbsolutePath();
                    editor.putString("user_photo_path", photoPath);
                    editor.putString("user_photo_url", photoPath);
                    Log.d(TAG, "Profile photo saved to: " + photoPath + " (size: " + photoFile.length() + " bytes)");
                }

                // Use commit() instead of apply() to guarantee save before reading
                boolean saved = editor.commit();
                Log.d(TAG, "SharedPreferences commit result: " + saved);

                // Update SessionCache so Settings/Home show updated data immediately
                com.phynix.artham.utils.SessionCache cache = com.phynix.artham.utils.SessionCache.getInstance();
                com.phynix.artham.models.Users cachedUser = cache.getCachedUserProfile();
                if (cachedUser != null) {
                    cachedUser.setName(name);
                    cachedUser.setUid(uid);
                    if (imageData != null) {
                        String savedPhoto = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                                .getString("user_photo_path", "");
                        if (!savedPhoto.isEmpty()) {
                            cachedUser.setProfile(savedPhoto);
                        }
                    }
                    cache.cacheUserProfile(cachedUser);
                } else {
                    String savedPhoto = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                            .getString("user_photo_path", "");
                    com.phynix.artham.models.Users newUser = new com.phynix.artham.models.Users(
                            uid, name, "", null, savedPhoto);
                    cache.cacheUserProfile(newUser);
                }
                Log.d(TAG, "SessionCache updated with name: " + name + ", UID: " + uid);

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Profile saved: " + name, Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error saving profile", e);
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private byte[] getCompressedImageData(Uri uri) {
        if (uri == null) return null;
        try {
            Bitmap bitmap = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                bitmap = android.graphics.ImageDecoder.decodeBitmap(
                        android.graphics.ImageDecoder.createSource(getContentResolver(), uri)
                );
            } else {
                bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            }

            if (bitmap != null) {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                float max = 600f;
                if (width > max || height > max) {
                    float ratio = Math.min(max / width, max / height);
                    int newWidth = Math.round(width * ratio);
                    int newHeight = Math.round(height * ratio);
                    bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error decoding image with ImageDecoder/MediaStore, trying stream fallback", e);
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                Bitmap b = BitmapFactory.decodeStream(is);
                if (b != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    b.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    return baos.toByteArray();
                }
            } catch (Exception ex) {
                Log.e(TAG, "Fallback image decode failed", ex);
            }
        }
        return null;
    }

    private void updateDatabase(String name, String imageUrl, ProgressDialog loadingBar) {
        loadingBar.dismiss();
        finish();
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
        pd.setMessage("Deleting account and erasing cloud data...");
        pd.setCancelable(false);
        pd.setCanceledOnTouchOutside(false);
        pd.show();

        com.phynix.artham.auth.SupabaseAuthManager.deleteUserAccount(this, new com.phynix.artham.auth.SupabaseAuthManager.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                if (!isFinishing() && !isDestroyed()) {
                    pd.dismiss();
                    Toast.makeText(EditProfileActivity.this, "Account deleted successfully", Toast.LENGTH_SHORT).show();
                }
                logoutAndRedirect();
            }

            @Override
            public void onError(String error) {
                if (!isFinishing() && !isDestroyed()) {
                    pd.dismiss();
                    Toast.makeText(EditProfileActivity.this, "Local data cleared. " + error, Toast.LENGTH_LONG).show();
                }
                logoutAndRedirect();
            }
        });
    }

    private void logoutAndRedirect() {
        // AuthManager.signOut is already called inside deleteUserAccount, avoid double-call
        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}