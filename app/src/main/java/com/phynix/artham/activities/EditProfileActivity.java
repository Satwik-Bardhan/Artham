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
    private EditText editFullName;
    private TextView displayEmail, displayUid, dateOfBirthText;
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
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void loadUserInfo() {
        // Load UID
        displayUid.setText(AuthManager.getUserId(this));

        // Load saved profile data from SharedPreferences
        android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);

        // Load name
        String savedName = prefs.getString("user_display_name", "");
        if (!savedName.isEmpty()) {
            editFullName.setText(savedName);
        }

        // Load email
        String savedEmail = prefs.getString("user_email", "");
        if (!savedEmail.isEmpty()) {
            displayEmail.setText(savedEmail);
        }

        // Load DOB
        long savedDob = prefs.getLong("user_dob", 0);
        if (savedDob > 0) {
            dobTimestamp = savedDob;
            updateDobText(savedDob);
        }

        // Load profile photo from internal storage
        String savedPhotoPath = prefs.getString("user_photo_path", "");
        if (!savedPhotoPath.isEmpty()) {
            File photoFile = new File(savedPhotoPath);
            if (photoFile.exists()) {
                currentPhotoUrl = savedPhotoPath;
                Glide.with(this)
                        .load(photoFile)
                        .centerCrop()
                        .circleCrop()
                        .into(profileImageView);
            }
        }
    }

    private void saveProfileChanges() {
        String name = editFullName.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            editFullName.setError("Name is required");
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
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
        progressDialog.setMessage("Please wait...");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        // Save on background thread
        new Thread(() -> {
            try {
                android.content.SharedPreferences.Editor editor =
                        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit();

                // Save name
                editor.putString("user_display_name", name);
                Log.d(TAG, "Saving name: " + name);

                // Save DOB if set
                if (dobTimestamp > 0) {
                    editor.putLong("user_dob", dobTimestamp);
                }

                // Save profile photo to internal storage
                if (imageData != null) {
                    File photoDir = new File(getFilesDir(), "profile");
                    if (!photoDir.exists()) photoDir.mkdirs();
                    File photoFile = new File(photoDir, "profile_photo.jpg");

                    try (FileOutputStream fos = new FileOutputStream(photoFile)) {
                        fos.write(imageData);
                        fos.flush();
                    }

                    String photoPath = photoFile.getAbsolutePath();
                    editor.putString("user_photo_path", photoPath);
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
                    if (imageData != null) {
                        // Set profile to local file path so Glide can load it
                        String savedPhoto = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                                .getString("user_photo_path", "");
                        if (!savedPhoto.isEmpty()) {
                            cachedUser.setProfile(savedPhoto);
                        }
                    }
                    cache.cacheUserProfile(cachedUser);
                } else {
                    // Create a new cache entry
                    String userId = AuthManager.getUserId(this);
                    String savedPhoto = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                            .getString("user_photo_path", "");
                    com.phynix.artham.models.Users newUser = new com.phynix.artham.models.Users(
                            userId, name, "", null, savedPhoto);
                    cache.cacheUserProfile(newUser);
                }
                Log.d(TAG, "SessionCache updated with name: " + name);

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
        pd.setMessage("Deleting account...");
        pd.show();

        pd.dismiss();
        logoutAndRedirect();
    }

    private void logoutAndRedirect() {
        AuthManager.signOut(this);
        getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}