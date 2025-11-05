package com.agrodss.agrodss;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

public class Publication extends Fragment {

    private static final String TAG = "Publication";

    private Spinner pdfSpinner;
    private ImageView pdfImageView;
    private Button btnPrev, btnNext, btnZoomIn, btnZoomOut, btnResetZoom;
    private TextView tvPageInfo;

    private PDFRendererHelper pdfRendererHelper;
    private int currentPageIndex = 0;
    private int scaleFactor = 10; // Default scale (1x)
    private String currentPdfFile = "";

    // PDF files in assets folder
    private final String[] pdfFiles = {"", "publication1.pdf", "publication2.pdf"};

    public Publication() {
        // Required empty public constructor
        Log.d(TAG, "Constructor called");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView: Starting");
        View view = inflater.inflate(R.layout.fragment_publication, container, false);

        initializeViews(view);
        setupClickListeners();
        setupSpinner();

        // Initialize PDF renderer
        pdfRendererHelper = new PDFRendererHelper(requireContext());
        Log.d(TAG, "onCreateView: Completed successfully");

        return view;
    }

    private void initializeViews(View view) {
        Log.d(TAG, "initializeViews: Starting");

        pdfSpinner = view.findViewById(R.id.pdfSpinner);
        pdfImageView = view.findViewById(R.id.pdfImageView);
        btnPrev = view.findViewById(R.id.btnPrev);
        btnNext = view.findViewById(R.id.btnNext);
        btnZoomIn = view.findViewById(R.id.btnZoomIn);
        btnZoomOut = view.findViewById(R.id.btnZoomOut);
        btnResetZoom = view.findViewById(R.id.btnResetZoom);
        tvPageInfo = view.findViewById(R.id.tvPageInfo);

        // Log null checks
        Log.d(TAG, "pdfSpinner: " + (pdfSpinner != null ? "OK" : "NULL"));
        Log.d(TAG, "pdfImageView: " + (pdfImageView != null ? "OK" : "NULL"));
        Log.d(TAG, "btnPrev: " + (btnPrev != null ? "OK" : "NULL"));
        Log.d(TAG, "btnNext: " + (btnNext != null ? "OK" : "NULL"));
        Log.d(TAG, "tvPageInfo: " + (tvPageInfo != null ? "OK" : "NULL"));

        // Initially disable navigation buttons
        updateButtonStates();
        Log.d(TAG, "initializeViews: Completed");
    }

    private void setupSpinner() {
        Log.d(TAG, "setupSpinner: Starting");

        // Create array for spinner display
        String[] displayNames = {"Select a PDF", "Publication 1", "Publication 2"};

        // Create adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                displayNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        pdfSpinner.setAdapter(adapter);

        Log.d(TAG, "setupSpinner: Adapter set with " + displayNames.length + " items");

        pdfSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "Spinner onItemSelected: position=" + position);
                if (position > 0) {
                    String selectedFile = pdfFiles[position];
                    Log.d(TAG, "Loading PDF: " + selectedFile);
                    loadPdf(selectedFile);
                } else {
                    Log.d(TAG, "Clearing PDF display");
                    clearPdfDisplay();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                Log.d(TAG, "Spinner onNothingSelected");
            }
        });

        Log.d(TAG, "setupSpinner: Completed");
    }

    private void setupClickListeners() {
        Log.d(TAG, "setupClickListeners: Starting");
        btnPrev.setOnClickListener(v -> {
            Log.d(TAG, "btnPrev clicked");
            showPreviousPage();
        });
        btnNext.setOnClickListener(v -> {
            Log.d(TAG, "btnNext clicked");
            showNextPage();
        });
        btnZoomIn.setOnClickListener(v -> {
            Log.d(TAG, "btnZoomIn clicked");
            zoomIn();
        });
        btnZoomOut.setOnClickListener(v -> {
            Log.d(TAG, "btnZoomOut clicked");
            zoomOut();
        });
        btnResetZoom.setOnClickListener(v -> {
            Log.d(TAG, "btnResetZoom clicked");
            resetZoom();
        });
        Log.d(TAG, "setupClickListeners: Completed");
    }

    private void loadPdf(String pdfFileName) {
        Log.d(TAG, "========== LOAD PDF START ==========");
        Log.d(TAG, "loadPdf: Attempting to load: " + pdfFileName);

        try {
            currentPdfFile = pdfFileName;
            currentPageIndex = 0;
            scaleFactor = 10; // Reset zoom

            Log.d(TAG, "loadPdf: Current settings - pageIndex=" + currentPageIndex + ", scaleFactor=" + scaleFactor);

            boolean openResult = pdfRendererHelper.openPdfFromAssets(pdfFileName);
            Log.d(TAG, "loadPdf: openPdfFromAssets returned: " + openResult);

            if (openResult) {
                int pageCount = pdfRendererHelper.getPageCount();
                Log.d(TAG, "loadPdf: PDF opened successfully. Page count: " + pageCount);

                displayCurrentPage();
                Toast.makeText(getContext(), "PDF loaded: " + pageCount + " pages", Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "loadPdf: Failed to open PDF");
                Toast.makeText(getContext(), "Failed to load PDF", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "loadPdf: Exception occurred", e);
            Toast.makeText(getContext(), "Error loading PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        Log.d(TAG, "========== LOAD PDF END ==========");
    }

    private void displayCurrentPage() {
        Log.d(TAG, "displayCurrentPage: Starting");
        Log.d(TAG, "displayCurrentPage: pdfRendererHelper=" + (pdfRendererHelper != null ? "OK" : "NULL"));
        Log.d(TAG, "displayCurrentPage: currentPdfFile=" + currentPdfFile);
        Log.d(TAG, "displayCurrentPage: currentPageIndex=" + currentPageIndex);
        Log.d(TAG, "displayCurrentPage: scaleFactor=" + scaleFactor);

        if (pdfRendererHelper == null || currentPdfFile.isEmpty()) {
            Log.w(TAG, "displayCurrentPage: Cannot display - helper or file is null/empty");
            return;
        }

        Log.d(TAG, "displayCurrentPage: Calling renderPage");
        Bitmap pageBitmap = pdfRendererHelper.renderPage(currentPageIndex, scaleFactor);

        if (pageBitmap != null) {
            Log.d(TAG, "displayCurrentPage: Bitmap created successfully - " +
                    pageBitmap.getWidth() + "x" + pageBitmap.getHeight());
            pdfImageView.setImageBitmap(pageBitmap);
            Log.d(TAG, "displayCurrentPage: Bitmap set to ImageView");

            updatePageInfo();
            updateButtonStates();

            Log.d(TAG, "displayCurrentPage: Completed successfully");
        } else {
            Log.e(TAG, "displayCurrentPage: Failed to render page - bitmap is null");
            Toast.makeText(getContext(), "Failed to render page", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPreviousPage() {
        Log.d(TAG, "showPreviousPage: currentPageIndex=" + currentPageIndex);
        if (currentPageIndex > 0) {
            currentPageIndex--;
            Log.d(TAG, "showPreviousPage: Moving to page " + currentPageIndex);
            displayCurrentPage();
        } else {
            Log.d(TAG, "showPreviousPage: Already at first page");
        }
    }

    private void showNextPage() {
        Log.d(TAG, "showNextPage: currentPageIndex=" + currentPageIndex);
        if (pdfRendererHelper != null && currentPageIndex < pdfRendererHelper.getPageCount() - 1) {
            currentPageIndex++;
            Log.d(TAG, "showNextPage: Moving to page " + currentPageIndex);
            displayCurrentPage();
        } else {
            Log.d(TAG, "showNextPage: Already at last page or no PDF loaded");
        }
    }

    private void zoomIn() {
        Log.d(TAG, "zoomIn: Current scaleFactor=" + scaleFactor);
        if (scaleFactor < 20) { // Maximum 2x zoom
            scaleFactor += 2;
            Log.d(TAG, "zoomIn: New scaleFactor=" + scaleFactor);
            displayCurrentPage();
        } else {
            Log.d(TAG, "zoomIn: Already at maximum zoom");
        }
    }

    private void zoomOut() {
        Log.d(TAG, "zoomOut: Current scaleFactor=" + scaleFactor);
        if (scaleFactor > 5) { // Minimum 0.5x zoom
            scaleFactor -= 2;
            Log.d(TAG, "zoomOut: New scaleFactor=" + scaleFactor);
            displayCurrentPage();
        } else {
            Log.d(TAG, "zoomOut: Already at minimum zoom");
        }
    }

    private void resetZoom() {
        Log.d(TAG, "resetZoom: Resetting scaleFactor from " + scaleFactor + " to 10");
        scaleFactor = 10;
        displayCurrentPage();
    }

    private void updatePageInfo() {
        if (pdfRendererHelper != null) {
            int totalPages = pdfRendererHelper.getPageCount();
            String pageInfo = getString(R.string.page_info, currentPageIndex + 1, totalPages);
            tvPageInfo.setText(pageInfo);
            Log.d(TAG, "updatePageInfo: " + pageInfo);
        }
    }

    private void updateButtonStates() {
        Log.d(TAG, "updateButtonStates: Starting");
        if (pdfRendererHelper != null && pdfRendererHelper.getPageCount() > 0) {
            boolean prevEnabled = currentPageIndex > 0;
            boolean nextEnabled = currentPageIndex < pdfRendererHelper.getPageCount() - 1;
            boolean zoomInEnabled = scaleFactor < 20;
            boolean zoomOutEnabled = scaleFactor > 5;

            btnPrev.setEnabled(prevEnabled);
            btnNext.setEnabled(nextEnabled);
            btnZoomIn.setEnabled(zoomInEnabled);
            btnZoomOut.setEnabled(zoomOutEnabled);

            Log.d(TAG, "updateButtonStates: prev=" + prevEnabled + ", next=" + nextEnabled +
                    ", zoomIn=" + zoomInEnabled + ", zoomOut=" + zoomOutEnabled);
        } else {
            btnPrev.setEnabled(false);
            btnNext.setEnabled(false);
            btnZoomIn.setEnabled(false);
            btnZoomOut.setEnabled(false);
            Log.d(TAG, "updateButtonStates: All buttons disabled (no PDF)");
        }
    }

    private void clearPdfDisplay() {
        Log.d(TAG, "clearPdfDisplay: Starting");
        pdfImageView.setImageBitmap(null);
        tvPageInfo.setText("Page 0 of 0");
        currentPdfFile = "";
        currentPageIndex = 0;
        updateButtonStates();

        if (pdfRendererHelper != null) {
            pdfRendererHelper.closeRenderer();
            Log.d(TAG, "clearPdfDisplay: Renderer closed");
        }
        Log.d(TAG, "clearPdfDisplay: Completed");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView: Cleaning up");
        if (pdfRendererHelper != null) {
            pdfRendererHelper.closeRenderer();
        }
    }
}