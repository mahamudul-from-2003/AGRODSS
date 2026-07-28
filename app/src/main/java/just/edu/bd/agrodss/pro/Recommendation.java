package just.edu.bd.agrodss.pro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Recommendation extends Fragment {

    private static final String TAG = "RecommendationFragment";
    private static final String BASE_API_URL = "http://103.159.2.97:8081";

    private ImageView imagePreview;
    private CardView uploadCard;
    private TextView fileNameText, resultText, chooseText, resultTitle;
    private MaterialButton uploadButton, cancelButton;
    private View scrollView;
    private ProgressBar progressBar;
    private Uri selectedImageUri;
    private String selectedFileName;
    private String selectedMimeType;
    private String currentPhotoPath;
    private final OkHttpClient client;

    public static class DiseaseResult {
        public String diseaseName;
        public List<String> pesticideNames;
        public String rule;
    }

    // Launchers for permissions
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) {
            openCamera();
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to take photos", Toast.LENGTH_LONG).show();
        }
    });

    private final ActivityResultLauncher<String> storagePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) {
            openGallery();
        } else {
            Toast.makeText(requireContext(), "Storage permission is required to choose images", Toast.LENGTH_LONG).show();
        }
    });

    // Launcher for camera
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == requireActivity().RESULT_OK) {
            // Camera photo was taken successfully
            if (currentPhotoPath != null) {
                File photoFile = new File(currentPhotoPath);
                if (photoFile.exists()) {
                    selectedImageUri = FileProvider.getUriForFile(requireContext(),
                            requireContext().getPackageName() + ".provider", photoFile);
                    selectedFileName = "photo_" + System.currentTimeMillis() + ".jpg";
                    selectedMimeType = "image/jpeg";
                    handleImageSelection(selectedImageUri);
                }
            }
        }
    });

    // Launcher for gallery
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == requireActivity().RESULT_OK) {
            Intent data = result.getData();
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    selectedImageUri = uri;
                    selectedFileName = getFileName(uri);
                    String mimeType = requireContext().getContentResolver().getType(uri);
                    selectedMimeType = mimeType != null ? mimeType : "image/jpeg";
                    handleImageSelection(uri);
                }
            }
        }
    });

    public Recommendation() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recommendation, container, false);
        initViews(view);
        setListeners();
        return view;
    }

    private void initViews(View v) {
        imagePreview = v.findViewById(R.id.imagePreview);
        uploadCard = v.findViewById(R.id.uploadCard);
        fileNameText = v.findViewById(R.id.fileNameText);
        uploadButton = v.findViewById(R.id.uploadButton);
        cancelButton = v.findViewById(R.id.cancelButton);
        resultText = v.findViewById(R.id.resultText);
        chooseText = v.findViewById(R.id.choose);
        resultTitle = v.findViewById(R.id.result);
        scrollView = v.findViewById(R.id.scrollView);
        progressBar = v.findViewById(R.id.progressBar);

        // Initialize visibility
        if (resultText != null) {
            resultText.setVisibility(View.GONE);
        }
        if (resultTitle != null) {
            resultTitle.setVisibility(View.GONE);
        }
        if (scrollView != null) {
            scrollView.setVisibility(View.GONE);
        }

        // Initially hide cancel button
        cancelButton.setVisibility(View.GONE);
    }

    private void setListeners() {
        uploadCard.setOnClickListener(v -> showImageSourceDialog());
        uploadButton.setOnClickListener(v -> uploadImage());
        cancelButton.setOnClickListener(v -> resetUI());
    }

    private void showImageSourceDialog() {
        String[] options = {"Take Photo", "Choose from Gallery"};

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Choose Image Source");
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0: // Take Photo
                    requestCameraPermission();
                    break;
                case 1: // Choose from Gallery
                    requestStoragePermission();
                    break;
            }
        });
        builder.show();
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void requestStoragePermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                Manifest.permission.READ_MEDIA_IMAGES :
                Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            openGallery();
        } else {
            storagePermissionLauncher.launch(permission);
        }
    }

    private void openCamera() {
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

            // Ensure there's a camera activity to handle the intent
            if (takePictureIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                // Create the File where the photo should go
                File photoFile = createImageFile();
                selectedImageUri = FileProvider.getUriForFile(requireContext(),
                        requireContext().getPackageName() + ".provider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, selectedImageUri);
                cameraLauncher.launch(takePictureIntent);
            } else {
                Toast.makeText(requireContext(), "No camera app found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error opening camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
            //Log.e(TAG, "Camera error: " + e.getMessage());
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void openGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            Intent chooser = Intent.createChooser(intent, "Select an Image");
            galleryLauncher.launch(chooser);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleImageSelection(Uri uri) {
        try {
            // Show image preview
            Glide.with(this).load(uri).centerCrop().into(imagePreview);
            imagePreview.setVisibility(View.VISIBLE);

            // Update file name text
            String displayText = selectedFileName != null ? selectedFileName : "Image selected";
            fileNameText.setText(displayText);

            // Show upload button, hide cancel button
            uploadButton.setVisibility(View.VISIBLE);
            cancelButton.setVisibility(View.GONE);

            // Hide choose text and upload card
            chooseText.setVisibility(View.GONE);
            uploadCard.setVisibility(View.GONE);

            // Hide previous results
            hideResults();

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideResults() {
        if (resultTitle != null) {
            resultTitle.setVisibility(View.GONE);
        }
        if (scrollView != null) {
            scrollView.setVisibility(View.GONE);
        }
        if (resultText != null) {
            resultText.setVisibility(View.GONE);
        }
    }

    private void showResults() {
        if (resultTitle != null) {
            resultTitle.setVisibility(View.VISIBLE);
        }
        if (scrollView != null) {
            scrollView.setVisibility(View.VISIBLE);
        }
        if (resultText != null) {
            resultText.setVisibility(View.VISIBLE);
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (var cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx != -1) {
                        result = cursor.getString(idx);
                    }
                }
            } catch (Exception e) {
                //Log.e(TAG, "Error getting file name: " + e.getMessage());
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    private void uploadImage() {
        if (selectedImageUri == null) {
            Toast.makeText(requireContext(), "Select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        //Toast.makeText(requireContext(), "Analyzing image...", Toast.LENGTH_SHORT).show();
        uploadButton.setEnabled(false);

        new Thread(() -> {
            try {
                File file = FileUtils.createFileFromUri(requireContext(), selectedImageUri, selectedFileName);
                if (file == null) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Failed to prepare file", Toast.LENGTH_SHORT).show();
                        uploadButton.setEnabled(true);
                    });
                    return;
                }

                String mimeType = selectedMimeType != null ? selectedMimeType : "image/jpeg";
                DiseaseResult result = classifyImage(file, mimeType);

                requireActivity().runOnUiThread(() -> {
                    uploadButton.setEnabled(true);
                    displayResult(result);
                    progressBar.setVisibility(View.GONE);
                });

            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : "একটি ত্রুটি ঘটেছে। আবার চেষ্টা করুন।";
                requireActivity().runOnUiThread(() -> {
                    uploadButton.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private DiseaseResult classifyImage(File imageFile, String mimeType) throws IOException {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", imageFile.getName(),
                        RequestBody.create(MediaType.parse(mimeType), imageFile))
                .build();

        Request request = new Request.Builder()
                .url(BASE_API_URL + "/api/v1/disease/classify")
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            JSONObject json = new JSONObject(responseBody);

            if (!response.isSuccessful() || !json.optBoolean("success", false)) {
                throw new IOException(json.optString("error", "শ্রেণীবদ্ধকরণ ব্যর্থ হয়েছে।"));
            }

            JSONObject data = json.getJSONObject("data");
            DiseaseResult result = new DiseaseResult();
            result.diseaseName = data.optString("diseaseName", null);
            result.rule = data.optString("rule", null);

            result.pesticideNames = new ArrayList<>();
            JSONArray pesticideArray = data.optJSONArray("pesticideName");
            if (pesticideArray != null) {
                for (int i = 0; i < pesticideArray.length(); i++) {
                    result.pesticideNames.add(pesticideArray.getString(i));
                }
            }
            return result;

        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("সার্ভার থেকে সঠিক উত্তর পাওয়া যায়নি।");
        }
    }

    private void displayResult(DiseaseResult result) {
        if (resultText == null || result == null) return;

        StringBuilder html = new StringBuilder();
        if (result.diseaseName != null) {
            html.append("<b>রোগ:</b> ").append(result.diseaseName).append("<br><br>");
        }
        if (result.pesticideNames != null && !result.pesticideNames.isEmpty()) {
            html.append("<b>কীটনাশক:</b> ")
                    .append(android.text.TextUtils.join(", ", result.pesticideNames))
                    .append("<br><br>");
        }
        if (result.rule != null) {
            html.append("<b>ব্যবহারবিধি:</b><br>").append(result.rule.replace("\n", "<br>"));
        }

        resultText.setText(android.text.Html.fromHtml(html.toString(), android.text.Html.FROM_HTML_MODE_LEGACY));

        showResults();
        uploadButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.VISIBLE);
    }

    private void resetUI() {
        // Reset to initial state
        imagePreview.setVisibility(View.GONE);
        uploadCard.setVisibility(View.VISIBLE);
        chooseText.setVisibility(View.VISIBLE);
        uploadButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);
        fileNameText.setText("No file chosen");
        hideResults();
        selectedImageUri = null;
        selectedFileName = null;
        selectedMimeType = null;
        currentPhotoPath = null;
        uploadButton.setEnabled(true);

        // Clear the image from Glide
        if (imagePreview != null) {
            Glide.with(this).clear(imagePreview);
        }
    }
}