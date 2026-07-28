package just.edu.bd.agrodss.pro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import androidx.activity.result.PickVisualMediaRequest;  // IMPORTANT: Add this
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private final OkHttpClient client;

    public static class DiseaseResult {
        public String diseaseName;
        public List<String> pesticideNames;
        public String rule;
    }

    // CORRECTED: Photo Picker Launcher
    private final ActivityResultLauncher<PickVisualMediaRequest> photoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    selectedFileName = getFileName(uri);
                    selectedMimeType = requireContext().getContentResolver().getType(uri);
                    handleImageSelection(uri);
                } else {
                    Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show();
                }
            });

    // Camera permission
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    openCamera();
                } else {
                    Toast.makeText(requireContext(), "Camera permission is required to take photos", Toast.LENGTH_LONG).show();
                }
            });

    // Camera launcher
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == requireActivity().RESULT_OK) {
                    // Handle camera result
                    if (selectedImageUri != null) {
                        selectedFileName = "photo_" + System.currentTimeMillis() + ".jpg";
                        selectedMimeType = "image/jpeg";
                        handleImageSelection(selectedImageUri);
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

        if (resultText != null) resultText.setVisibility(View.GONE);
        if (resultTitle != null) resultTitle.setVisibility(View.GONE);
        if (scrollView != null) scrollView.setVisibility(View.GONE);
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
                case 0:
                    requestCameraPermission();
                    break;
                case 1:
                    openPhotoPicker();
                    break;
            }
        });
        builder.show();
    }

    // CORRECTED: Open Photo Picker
    private void openPhotoPicker() {
        photoPickerLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()
        );
    }

    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                File photoFile = createImageFile();
                if (photoFile != null) {
                    selectedImageUri = FileProvider.getUriForFile(requireContext(),
                            requireContext().getPackageName() + ".provider", photoFile);
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, selectedImageUri);
                    cameraLauncher.launch(takePictureIntent);
                }
            } else {
                Toast.makeText(requireContext(), "No camera app found", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error opening camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void handleImageSelection(Uri uri) {
        try {
            Glide.with(this).load(uri).centerCrop().into(imagePreview);
            imagePreview.setVisibility(View.VISIBLE);

            String displayText = selectedFileName != null ? selectedFileName : "Image selected";
            fileNameText.setText(displayText);

            uploadButton.setVisibility(View.VISIBLE);
            cancelButton.setVisibility(View.GONE);
            chooseText.setVisibility(View.GONE);
            uploadCard.setVisibility(View.GONE);
            hideResults();

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideResults() {
        if (resultTitle != null) resultTitle.setVisibility(View.GONE);
        if (scrollView != null) scrollView.setVisibility(View.GONE);
        if (resultText != null) resultText.setVisibility(View.GONE);
    }

    private void showResults() {
        if (resultTitle != null) resultTitle.setVisibility(View.VISIBLE);
        if (scrollView != null) scrollView.setVisibility(View.VISIBLE);
        if (resultText != null) resultText.setVisibility(View.VISIBLE);
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
                // Log error
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
        uploadButton.setEnabled(false);

        new Thread(() -> {
            try {
                File file = createFileFromUri(selectedImageUri);
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
                requireActivity().runOnUiThread(() -> {
                    uploadButton.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private File createFileFromUri(Uri uri) throws IOException {
        File cacheDir = new File(requireContext().getCacheDir(), "uploads");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        String fileName = selectedFileName != null ? selectedFileName : "temp_image.jpg";
        File outFile = new File(cacheDir, fileName);

        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                fos.write(buf, 0, len);
            }
        }
        return outFile;
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
                throw new IOException(json.optString("error", "Classification failed"));
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
        } catch (Exception e) {
            throw new IOException("Failed to get response from server");
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
        uploadButton.setEnabled(true);

        if (imagePreview != null) {
            Glide.with(this).clear(imagePreview);
        }
    }
}