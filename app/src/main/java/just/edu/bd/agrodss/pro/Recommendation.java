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

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Recommendation extends Fragment {

    private static final String TAG = "RecommendationFragment";
    private static final String BASE_API_URL = "http://103.159.2.97:8000";

    private ImageView imagePreview;
    private CardView uploadCard;
    private TextView fileNameText, resultText, chooseText, resultTitle;
    private MaterialButton uploadButton, cancelButton;
    private View scrollView;
    private ProgressBar progressBar;
    private Uri selectedImageUri;
    private String selectedFileName;
    private String currentPhotoPath;
    private final OkHttpClient client;

    public static class DiseaseResult {
        public String disease;
        public double confidence;
        public String recommendation;
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

                // Step 1: Upload image to get prediction
                String filename = uploadImageToAPI(file);

                // Step 2: Get prediction results
                DiseaseResult result = getPredictionResults(filename);

                requireActivity().runOnUiThread(() -> {
                    uploadButton.setEnabled(true);
                    displayResult(result);
                    progressBar.setVisibility(View.GONE);
                    //Toast.makeText(requireContext(), "Analysis complete!", Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                //Log.e(TAG, "Upload error: " + e.getMessage(), e);
                requireActivity().runOnUiThread(() -> {
                    uploadButton.setEnabled(true);
                    //Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private String uploadImageToAPI(File imageFile) throws IOException {
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", imageFile.getName(),
                        RequestBody.create(MediaType.parse("image/*"), imageFile))
                .build();

        Request request = new Request.Builder()
                .url(BASE_API_URL + "/predict")
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body().string();
            //Log.d(TAG, "Upload response: " + responseBody);

            JSONObject jsonResponse = new JSONObject(responseBody);
            return jsonResponse.getString("filename");

        } catch (Exception e) {
            throw new IOException("Failed to upload image: " + e.getMessage());
        }
    }

    private DiseaseResult getPredictionResults(String filename) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_API_URL + "/data/filename=" + filename)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body().string();
            //Log.d(TAG, "Prediction response: " + responseBody);

            JSONObject jsonResponse = new JSONObject(responseBody);

            if (jsonResponse.has("data")) {
                String dataString = jsonResponse.getString("data");
                JSONObject dataObject = new JSONObject(dataString);

                DiseaseResult result = new DiseaseResult();
                result.disease = dataObject.getString("prediction");
                result.confidence = dataObject.getDouble("confidence");
                result.recommendation = generateRecommendation(result.disease, result.confidence);

                return result;
            } else {
                throw new IOException("No 'data' field in response");
            }

        } catch (Exception e) {
            throw new IOException("Failed to get results: " + e.getMessage());
        }
    }

    private String generateRecommendation(String disease, double confidence) {

        if (confidence < 50.0) {
            return "<b>দুঃখিত</b>, আপনার প্রদত্ত ছবিটি সিস্টেম সঠিক ভাবে এনালাইসিস করতে পারেনি অনুগ্রহকরে আরো স্পষ্ট ছবি দিয়ে পুনরায় চেষ্টা করুন।";
        }

        switch (disease.trim()) {
            case "Bean Angular Leaf Spot":
                return "<b>রোগ:</b> শিমের কৌণিক পাতার দাগ<br><br>" +
                        "<b>কীটনাশক:</b> কার্বেন্ডাজিম, কপার হাইড্রক্সাইড, মানকোজেব<br><br>" +
                        "<b>ব্যবহারবিধি:</b><br>কার্বেন্ডাজিমগ্রুপের (ডেরোসাল, ব্যাভিস্টিন) প্রতি শতক জমিতে ২.৫ মিলি. / লি. হারে পানিতে মিশিয়ে ১০ দিন পরপর ৩ বার শেষ বিকেলে স্প্রে করতে হবে।<br><br>কপার হাইড্রক্সাইডগ্রুপের (কোসিড, চ্যাম্প) প্রতি শতক জমিতে ৫ মিলি. / লি. হারে পানিতে মিশিয়ে ১০ দিন পরপর ৩ বার শেষ বিকেলে স্প্রে করতে হবে।<br><br>মানকোজেবগ্রুপের (মানজাতে, ডাইথেন এম-৪৫) প্রতি শতক জমিতে ১৬ মিলি. / লি. হারে পানিতে মিশিয়ে ১০ দিন পরপর ৩ বার শেষ বিকেলে স্প্রে করতে হবে।";

            case "Bean Bean Rust":
                return "<b>রোগ:</b> শিমের মরিচা<br><br>" +
                        "<b>কীটনাশক:</b> প্রোপিকোনাজল, টেবুকোনাজল, ট্রাইফ্লোক্সিস্ট্রবিন<br><br>" +
                        "<b>ব্যবহারবিধি:</b><br>প্রপিকোনাজলগ্রুপের ছত্রাকনাশক (টিল্ট, ব্যানার ম্যাক্স) প্রতি শতক জমিতে ১.২৫ মিলি. / লি. হারে পানিতে মিশিয়ে ১০ দিন পরপর ৩ বার শেষ বিকেলে স্প্রে করতে হবে।<br><br>টেবুকোনাজলগ্রুপের ছত্রাকনাশক (ফলিকুর, এলিট) প্রতি শতক জমিতে  ২ মিলি. / লি. হারে পানিতে মিশিয়ে ১০ দিন পরপর ৩ বার শেষ বিকেলে স্প্রে করতে হবে।<br><br>ট্রাইফ্লোক্সিস্ট্রবিনগ্রুপের ছত্রাকনাশক (টিল্ট, ব্যানার ম্যাক্স) প্রতি শতক জমিতে ৫ মিলি. / লি. হারে পানিতে মিশিয়ে ১০ দিন পরপর ৩ বার শেষ বিকেলে স্প্রে করতে হবে।";

            case "Rice Bacterial Leaf Blight":
                return "<b>রোগ:</b> পেডি ব্যাকটেরিয়াল লিফ ব্লাইট<br><br>" +
                        "<b>কীটনাশক:</b> স্ট্রীপটোমাইসিন<br><br>" +
                        "<b>ব্যবহারবিধি:</b><br>পেডি ব্যাকটেরিয়াল লিফ ব্লাইট রোগের জন্য স্ট্রীপটোমাইসিন কীটনাশক(ব্লাসটিসিডিন অথবা ব্লাসটিসিডিন ১ গ্রাম অথবা স্ট্রীপটোমাইসিন সালফেট ২গ্রাম) ১ লিটার পানিতে মিশিয়ে শতক প্রতি স্প্রে করতে হবে৷";

            case "Rice Brown Spot":
                return "<b>রোগ:</b> পেডি ব্রাউন স্পট<br><br>" +
                        "<b>কীটনাশক:</b> ট্রীসাইক্লাজল<br><br>" +
                        "<b>ব্যবহারবিধি:</b><br>পেডি ব্রাউন স্পট রোগের জন্য ট্রীসাইক্লাজল কীটনাশক (বেলিটন অথবা ট্রিফরিন ০.৫গ্রাম ২ লিটার পানিতে মিশিয়ে অথবা ট্রিজল ১গ্রাম  ৩ লিটার পানিতে মিশিয়ে)শতক প্রতি স্প্রে করতে হবে৷";

            case "Rice Hispa":
                return "<b>রোগ:</b> ধানের পাতার ঝাপটা<br><br>" +
                        "<b>কীটনাশক:</b> কারবেনডাজিম<br><br>" +
                        "<b>ব্যবহারবিধি:</b><br>ধানের পাতার ঝাপটা রোগের জন্য কারবেনডাজিম কীটনাশক (বেভিসটিন অথবা ভিটাভেক্স ০.৫গ্রাম  অথবা ফানডাজল ১গ্রাম  ) ১ লিটার পানিতে মিশিয়েশতক প্রতি স্প্রে করতে হবে৷";

            case "Rice Healthy":
                return "ধানের সুস্থ্য এবং স্বাস্থ্যকর পাতা।<br><br>কোনো কীটনাশকের প্রয়োজন নেই।";

            default:
                return "<b>রোগ:</b> " + disease + "<br><br>দুঃখিত, এই রোগের জন্য আমাদের ডাটাবেসে এখনো তথ্য নেই।";
        }
    }

    private void displayResult(DiseaseResult result) {
        if (resultText == null || result == null) return;

        String fullText = result.recommendation;

        // Convert newlines to HTML breaks and render HTML
        String htmlText = fullText.replace("\n", "<br>");
        resultText.setText(android.text.Html.fromHtml(htmlText, android.text.Html.FROM_HTML_MODE_LEGACY));

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
        currentPhotoPath = null;
        uploadButton.setEnabled(true);

        // Clear the image from Glide
        if (imagePreview != null) {
            Glide.with(this).clear(imagePreview);
        }
    }
}