package just.edu.bd.agrodss.pro;

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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_publication, container, false);

        initializeViews(view);
        setupClickListeners();
        setupSpinner();

        // Initialize PDF renderer
        pdfRendererHelper = new PDFRendererHelper(requireContext());

        return view;
    }

    private void initializeViews(View view) {
        pdfSpinner = view.findViewById(R.id.pdfSpinner);
        pdfImageView = view.findViewById(R.id.pdfImageView);
        btnPrev = view.findViewById(R.id.btnPrev);
        btnNext = view.findViewById(R.id.btnNext);
        btnZoomIn = view.findViewById(R.id.btnZoomIn);
        btnZoomOut = view.findViewById(R.id.btnZoomOut);
        btnResetZoom = view.findViewById(R.id.btnResetZoom);
        tvPageInfo = view.findViewById(R.id.tvPageInfo);


        // Initially disable navigation buttons
        updateButtonStates();
    }

    private void setupSpinner() {
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

        pdfSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    String selectedFile = pdfFiles[position];
                    loadPdf(selectedFile);
                } else {
                    clearPdfDisplay();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupClickListeners() {
        btnPrev.setOnClickListener(v -> {
            showPreviousPage();
        });
        btnNext.setOnClickListener(v -> {
            showNextPage();
        });
        btnZoomIn.setOnClickListener(v -> {
            zoomIn();
        });
        btnZoomOut.setOnClickListener(v -> {
            zoomOut();
        });
        btnResetZoom.setOnClickListener(v -> {
            resetZoom();
        });
    }

    private void loadPdf(String pdfFileName) {
        try {
            currentPdfFile = pdfFileName;
            currentPageIndex = 0;
            scaleFactor = 10; // Reset zoom

            boolean openResult = pdfRendererHelper.openPdfFromAssets(pdfFileName);
            if (openResult) {
                int pageCount = pdfRendererHelper.getPageCount();

                displayCurrentPage();
                Toast.makeText(getContext(), "PDF loaded: " + pageCount + " pages", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Failed to load PDF", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error loading PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

    }

    private void displayCurrentPage() {

        if (pdfRendererHelper == null || currentPdfFile.isEmpty()) {
            return;
        }

        Bitmap pageBitmap = pdfRendererHelper.renderPage(currentPageIndex, scaleFactor);

        if (pageBitmap != null) {
            pdfImageView.setImageBitmap(pageBitmap);
            updatePageInfo();
            updateButtonStates();
        } else {
            Toast.makeText(getContext(), "Failed to render page", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPreviousPage() {
        if (currentPageIndex > 0) {
            currentPageIndex--;
            displayCurrentPage();
        } else {
            Log.d(TAG, "showPreviousPage: Already at first page");
        }
    }

    private void showNextPage() {
        if (pdfRendererHelper != null && currentPageIndex < pdfRendererHelper.getPageCount() - 1) {
            currentPageIndex++;
            displayCurrentPage();
        } else {
            Log.d(TAG, "showNextPage: Already at last page or no PDF loaded");
        }
    }

    private void zoomIn() {
        if (scaleFactor < 20) { // Maximum 2x zoom
            scaleFactor += 2;
            displayCurrentPage();
        } else {
            Log.d(TAG, "zoomIn: Already at maximum zoom");
        }
    }

    private void zoomOut() {
        if (scaleFactor > 5) { // Minimum 0.5x zoom
            scaleFactor -= 2;
            displayCurrentPage();
        } else {
            Log.d(TAG, "zoomOut: Already at minimum zoom");
        }
    }

    private void resetZoom() {
        scaleFactor = 10;
        displayCurrentPage();
    }

    private void updatePageInfo() {
        if (pdfRendererHelper != null) {
            int totalPages = pdfRendererHelper.getPageCount();
            String pageInfo = getString(R.string.page_info, currentPageIndex + 1, totalPages);
            tvPageInfo.setText(pageInfo);
        }
    }

    private void updateButtonStates() {
        if (pdfRendererHelper != null && pdfRendererHelper.getPageCount() > 0) {
            boolean prevEnabled = currentPageIndex > 0;
            boolean nextEnabled = currentPageIndex < pdfRendererHelper.getPageCount() - 1;
            boolean zoomInEnabled = scaleFactor < 20;
            boolean zoomOutEnabled = scaleFactor > 5;

            btnPrev.setEnabled(prevEnabled);
            btnNext.setEnabled(nextEnabled);
            btnZoomIn.setEnabled(zoomInEnabled);
            btnZoomOut.setEnabled(zoomOutEnabled);
        } else {
            btnPrev.setEnabled(false);
            btnNext.setEnabled(false);
            btnZoomIn.setEnabled(false);
            btnZoomOut.setEnabled(false);
        }
    }

    private void clearPdfDisplay() {
        pdfImageView.setImageBitmap(null);
        tvPageInfo.setText("Page 0 of 0");
        currentPdfFile = "";
        currentPageIndex = 0;
        updateButtonStates();

        if (pdfRendererHelper != null) {
            pdfRendererHelper.closeRenderer();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (pdfRendererHelper != null) {
            pdfRendererHelper.closeRenderer();
        }
    }
}