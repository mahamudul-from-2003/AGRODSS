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
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

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
    private MaterialButton uploadButton;
    private View scrollView;

    private Uri selectedImageUri;
    private String selectedFileName;
    private OkHttpClient client;

    public static class DiseaseResult {
        public String disease;
        public double confidence;
        public String recommendation;
    }

    // Launchers
    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (granted) {
            openImagePicker();
        } else {
            Toast.makeText(requireContext(), "Permission denied – cannot select images", Toast.LENGTH_LONG).show();
        }
    });

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
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
        resultText = v.findViewById(R.id.resultText);
        chooseText = v.findViewById(R.id.choose);
        resultTitle = v.findViewById(R.id.result);
        scrollView = v.findViewById(R.id.scrollView); // This should be the ScrollView

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
    }

    private void setListeners() {
        uploadCard.setOnClickListener(v -> requestPermissionAndPick());
        uploadButton.setOnClickListener(v -> uploadImage());
    }

    private void requestPermissionAndPick() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
                Manifest.permission.READ_MEDIA_IMAGES :
                Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else {
            permissionLauncher.launch(permission);
        }
    }

    private void openImagePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            Intent chooser = Intent.createChooser(intent, "Select an Image");
            pickImageLauncher.launch(chooser);
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

            // Show upload button
            uploadButton.setVisibility(View.VISIBLE);

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
                Log.e(TAG, "Error getting file name: " + e.getMessage());
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

        Toast.makeText(requireContext(), "Analyzing image...", Toast.LENGTH_SHORT).show();
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
                if (filename == null) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Failed to analyze image", Toast.LENGTH_LONG).show();
                        uploadButton.setEnabled(true);
                    });
                    return;
                }

                // Step 2: Get prediction results
                DiseaseResult result = getPredictionResults(filename);

                requireActivity().runOnUiThread(() -> {
                    uploadButton.setEnabled(true);
                    if (result != null) {
                        displayResult(result);
                        Toast.makeText(requireContext(), "Analysis complete!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), "No results found", Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Upload error: " + e.getMessage(), e);
                requireActivity().runOnUiThread(() -> {
                    uploadButton.setEnabled(true);
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
            Log.d(TAG, "Upload response: " + responseBody);

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
            Log.d(TAG, "Prediction response: " + responseBody);

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
        // Convert to percentage (47.57 means 47.57%)
        double confidencePercent = confidence;

        if (confidencePercent < 95.0) {
            return "দুঃখিত, আপনার প্রদত্ত ছবিটি সিস্টেম সঠিক ভাবে এনালাইসিস করতে পারেনি (" +
                    String.format("%.1f", confidence) + "% আত্মবিশ্বাস)।\n\nঅনুগ্রহকরে আরো স্পষ্ট ছবি দিয়ে পুনরায় চেষ্টা করুন।";
        }

        switch (disease.trim()) {
            case "Bean Angular Leaf Spot":
                return "রোগ: শিমের কৌণিক পাতার দাগ\n\n" +
                        "কীটনাশক: কার্বেন্ডাজিম, কপার হাইড্রক্সাইড, মানকোজেব\n\n" +
                        "ব্যবহারবিধি:\nকার্বেন্ডাজিমগ্রুপের (ডেরোসাল, ব্যাভিস্টিন) প্রতি শতক জমিতে ২.৫ মিলি. / লি. হারে পানিতে মিশিয়ে ১০ দিন পরপর ৩ বার শেষ বিকেলে স্প্রে করতে হবে।\n\nকপার হাইড্রক্সাইডগ্রুপের (কোসিড, চ্যাম্প) প্রতি শতক জমিতে ৫ মিলি. / লি. হারে পানিতে মিশিয়ে ১০ দিন পরপর ৩ বার শেষ বিকেলে স্প্রে করতে হবে।\n\nমানকোজেবগ্রুপের (মানজাতে, ডাইথেন এম-৪৫) প্রতি শতক জমিতে ১৬ মিলি. / লি. হারে পানিতে মিশিয়ে ১০ দিন পরপর ৩ বার শেষ বিকেলে স্প্রে করতে হবে।";

            case "Bean Bean Rust":
                return "রোগ: শিমের মরিচা\n\n" +
                        "কীটনাশক: প্রোপিকোনাজল, টেবুকোনাজল, ট্রাইফ্লোক্সিস্ট্রবিন\n\n" +
                        "ব্যবহারবিধি:\nপ্রপিকোনাজলগ্রুপের ছত্রাকনাশক (টিল্ট, ব্যানার ম্যাক্স) প্রতি শতক জমিতে ১.২৫ মিলি. / লি. হারে পানিতে মিশিয়ে ১০ দিন পরপর ৩ বার শেষ বিকেলে স্প্রে করতে হবে।\n\nটেবুকোনাজলগ্রুপের ছত্রাকনাশক (ফলিকুর, এলিট) প্রতি শতক জমিতে  ২ মিলি. / লি. হারে পানিতে মিশিয়ে ১০ দিন পরপর ৩ বার শেষ বিকেলে স্প্রে করতে হবে।\n\nট্রাইফ্লোক্সিস্ট্রবিনগ্রুপের ছত্রাকনাশক (টিল্ট, ব্যানার ম্যাক্স) প্রতি শতক জমিতে ৫ মিলি. / লি. হারে পানিতে মিশিয়ে ১০ দিন পরপর ৩ বার শেষ বিকেলে স্প্রে করতে হবে।";

            case "Rice Bacterial Leaf Blight":
                return "রোগ: পেডি ব্যাকটেরিয়াল লিফ ব্লাইট\n\n" +
                        "কীটনাশক: স্ট্রীপটোমাইসিন\n\n" +
                        "ব্যবহারবিধি:\nপেডি ব্যাকটেরিয়াল লিফ ব্লাইট রোগের জন্য স্ট্রীপটোমাইসিন কীটনাশক(ব্লাসটিসিডিন অথবা ব্লাসটিসিডিন ১ গ্রাম অথবা স্ট্রীপটোমাইসিন সালফেট ২গ্রাম) ১ লিটার পানিতে মিশিয়ে শতক প্রতি স্প্রে করতে হবে৷";

            case "Rice Brown Spot":
                return "রোগ: পেডি ব্রাউন স্পট\n\n" +
                        "কীটনাশক: ট্রীসাইক্লাজল\n\n" +
                        "ব্যবহারবিধি:\nপেডি ব্রাউন স্পট রোগের জন্য ট্রীসাইক্লাজল কীটনাশক (বেলিটন অথবা ট্রিফরিন ০.৫গ্রাম ২ লিটার পানিতে মিশিয়ে অথবা ট্রিজল ১গ্রাম  ৩ লিটার পানিতে মিশিয়ে)শতক প্রতি স্প্রে করতে হবে৷";

            case "Rice Hispa":
                return "রোগ: ধানের পাতার ঝাপটা\n\n" +
                        "কীটনাশক: কারবেনডাজিম\n\n" +
                        "ব্যবহারবিধি:\nধানের পাতার ঝাপটা রোগের জন্য কারবেনডাজিম কীটনাশক (বেভিসটিন অথবা ভিটাভেক্স ০.৫গ্রাম  অথবা ফানডাজল ১গ্রাম  ) ১ লিটার পানিতে মিশিয়েশতক প্রতি স্প্রে করতে হবে৷";

            case "Rice Healthy":
                return "ধানের সুস্থ্য এবং স্বাস্থ্যকর পাতা।\n\nকোনো কীটনাশকের প্রয়োজন নেই।";

            default:
                return "রোগ: " + disease + "\n\nদুঃখিত, এই রোগের জন্য আমাদের ডাটাবেসে এখনো তথ্য নেই।";
        }
    }

    private void displayResult(DiseaseResult result) {
        if (resultText == null || result == null) return;

        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("🔍 রোগ নির্ণয় ফলাফল:\n\n");
        //resultBuilder.append("রোগ: ").append(result.disease).append("\n");
        //resultBuilder.append("আত্মবিশ্বাস: ").append(String.format("%.1f%%", result.confidence)).append("\n\n");
        resultBuilder.append(result.recommendation);

        resultText.setText(resultBuilder.toString());
        showResults(); // Make results visible
    }

    private void resetUI() {
        requireActivity().runOnUiThread(() -> {
            // Reset to initial state
            imagePreview.setVisibility(View.GONE);
            uploadCard.setVisibility(View.VISIBLE);
            chooseText.setVisibility(View.VISIBLE);
            uploadButton.setVisibility(View.GONE);
            fileNameText.setText("No file chosen");
            hideResults();
            selectedImageUri = null;
            selectedFileName = null;
            uploadButton.setEnabled(true);
        });
    }

    // Optional: Add a method to handle back button or reset
    public void resetSelection() {
        resetUI();
    }
}