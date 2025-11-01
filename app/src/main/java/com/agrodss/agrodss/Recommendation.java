package com.agrodss.agrodss;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import java.io.File;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public class Recommendation extends Fragment {

    private static final String TAG = "RecommendationFragment";

    private ImageView imagePreview;
    private CardView uploadCard;
    private TextView fileNameText;
    private Button uploadButton;

    private Uri selectedImageUri;
    private String selectedFileName;

    // 1. Permission launcher
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        Log.d(TAG, "Permission result: " + granted);
        if (granted) {
            openImagePicker();
        } else {
            Toast.makeText(requireContext(), "Permission denied – cannot select images", Toast.LENGTH_LONG).show();
        }
    });

    // 2. Image picker launcher
    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        Log.d(TAG, "Image picker result code: " + result.getResultCode());
        Log.d(TAG, "Activity.RESULT_OK: " + requireActivity().RESULT_OK);

        if (result.getResultCode() == requireActivity().RESULT_OK) {
            Intent data = result.getData();
            if (data != null) {
                Uri uri = data.getData();
                Log.d(TAG, "Received URI: " + uri);
                if (uri != null) {
                    selectedImageUri = uri;
                    selectedFileName = getFileName(uri);
                    Log.d(TAG, "File name: " + selectedFileName);
                    handleImageSelection(uri);
                } else {
                    Log.e(TAG, "URI is null");
                }
            } else {
                Log.e(TAG, "Data is null");
            }
        } else {
            Log.d(TAG, "Image picker cancelled or failed");
        }
    });

    // 3. Retrofit service
    private interface UploadService {
        @Multipart
        @POST("upload")
            // Change this to your actual endpoint
        Call<UploadResponse> uploadImage(@Part MultipartBody.Part file);
    }

    // Response class for upload
    public static class UploadResponse {
        public String message;
        public String url;
    }

    private UploadService uploadService;

    public Recommendation() {
        Log.d(TAG, "Constructor called");
        // Initialize Retrofit - you can move this to Application class
        try {
            Retrofit retrofit = new Retrofit.Builder().baseUrl("https://agrodss.com/pesticidesRecommendation.php").addConverterFactory(GsonConverterFactory.create()).build();
            uploadService = retrofit.create(UploadService.class);
            Log.d(TAG, "Retrofit initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Retrofit: " + e.getMessage());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView called");
        View view = inflater.inflate(R.layout.fragment_recommendation, container, false);
        initViews(view);
        setListeners();
        return view;
    }

    private void initViews(View v) {
        Log.d(TAG, "Initializing views");
        imagePreview = v.findViewById(R.id.imagePreview);
        uploadCard = v.findViewById(R.id.uploadCard);
        fileNameText = v.findViewById(R.id.fileNameText);
        uploadButton = v.findViewById(R.id.uploadButton);

        // Log view initialization
        Log.d(TAG, "imagePreview: " + (imagePreview != null ? "found" : "null"));
        Log.d(TAG, "uploadCard: " + (uploadCard != null ? "found" : "null"));
        Log.d(TAG, "fileNameText: " + (fileNameText != null ? "found" : "null"));
        Log.d(TAG, "uploadButton: " + (uploadButton != null ? "found" : "null"));
    }

    private void setListeners() {
        Log.d(TAG, "Setting up listeners");
        if (uploadCard != null) {
            uploadCard.setOnClickListener(v -> {
                Log.d(TAG, "Upload card clicked");
                requestPermissionAndPick();
            });
        } else {
            Log.e(TAG, "uploadCard is null - cannot set click listener");
        }

        if (uploadButton != null) {
            uploadButton.setOnClickListener(v -> {
                Log.d(TAG, "Upload button clicked");
                uploadImage();
            });
        } else {
            Log.e(TAG, "uploadButton is null - cannot set click listener");
        }
    }

    private void requestPermissionAndPick() {
        Log.d(TAG, "Requesting permission");
        String permission;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
            Log.d(TAG, "Android 13+, requesting READ_MEDIA_IMAGES");
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
            Log.d(TAG, "Android <13, requesting READ_EXTERNAL_STORAGE");
        }

        // Check if we already have permission
        if (ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission already granted, opening image picker");
            openImagePicker();
        } else {
            Log.d(TAG, "Requesting permission: " + permission);
            permissionLauncher.launch(permission);
        }
    }

    private void openImagePicker() {
        Log.d(TAG, "Opening image picker");
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            Intent chooser = Intent.createChooser(intent, "Select an Image");
            Log.d(TAG, "Launching image picker with chooser");
            pickImageLauncher.launch(chooser);

        } catch (Exception e) {
            Log.e(TAG, "Error opening image picker: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleImageSelection(Uri uri) {
        Log.d(TAG, "Handling image selection: " + uri);
        try {
            // Load image with Glide
            Glide.with(this).load(uri).centerCrop().into(imagePreview);

            imagePreview.setVisibility(View.VISIBLE);
            uploadCard.setVisibility(View.GONE);
            uploadButton.setVisibility(View.VISIBLE);

            String displayText = selectedFileName != null ? selectedFileName : "Image selected";
            fileNameText.setText(displayText);

            Log.d(TAG, "Image preview updated successfully");
            Toast.makeText(requireContext(), "Image selected!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e(TAG, "Error handling image selection: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(Uri uri) {
        Log.d(TAG, "Getting file name for URI: " + uri);
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (var cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) {
                        result = cursor.getString(idx);
                        Log.d(TAG, "Got file name from cursor: " + result);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting file name from cursor: " + e.getMessage());
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
            Log.d(TAG, "Got file name from last path segment: " + result);
        }
        return result;
    }

    private void uploadImage() {
        Log.d(TAG, "Upload image called");
        if (selectedImageUri == null) {
            Log.e(TAG, "No image selected for upload");
            Toast.makeText(requireContext(), "Select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), "Uploading…", Toast.LENGTH_SHORT).show();
        uploadButton.setEnabled(false);

        try {
            // Create file from URI
            File file = FileUtils.createFileFromUri(requireContext(), selectedImageUri, selectedFileName);
            if (file == null) {
                Log.e(TAG, "Failed to create file from URI");
                Toast.makeText(requireContext(), "Failed to prepare file", Toast.LENGTH_SHORT).show();
                uploadButton.setEnabled(true);
                return;
            }

            Log.d(TAG, "File created: " + file.getAbsolutePath() + ", size: " + file.length());

            RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), file);
            MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), requestFile);

            Log.d(TAG, "Starting upload request");
            uploadService.uploadImage(body).enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<UploadResponse> call, @NonNull Response<UploadResponse> response) {
                    uploadButton.setEnabled(true);
                    Log.d(TAG, "Upload response code: " + response.code());

                    if (response.isSuccessful()) {
                        Log.d(TAG, "Upload successful");
                        Toast.makeText(requireContext(), "Upload successful!", Toast.LENGTH_LONG).show();
                        resetUI();
                    } else {
                        Log.e(TAG, "Upload failed with code: " + response.code());
                        Toast.makeText(requireContext(), "Server error: " + response.code(), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<UploadResponse> call, @NonNull Throwable t) {
                    uploadButton.setEnabled(true);
                    Log.e(TAG, "Upload failed: " + t.getMessage(), t);
                    Toast.makeText(requireContext(), "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error during upload preparation: " + e.getMessage(), e);
            uploadButton.setEnabled(true);
            Toast.makeText(requireContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void resetUI() {
        Log.d(TAG, "Resetting UI");
        imagePreview.setVisibility(View.GONE);
        uploadCard.setVisibility(View.VISIBLE);
        uploadButton.setVisibility(View.GONE);
        fileNameText.setText("No file chosen");
        selectedImageUri = null;
        selectedFileName = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "Fragment resumed");
    }
}