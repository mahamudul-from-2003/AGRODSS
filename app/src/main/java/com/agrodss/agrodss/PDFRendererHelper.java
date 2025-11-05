package com.agrodss.agrodss;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PDFRendererHelper {
    private static final String TAG = "PDFRendererHelper";

    private Context context;
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor parcelFileDescriptor;

    public PDFRendererHelper(Context context) {
        this.context = context;
        Log.d(TAG, "Constructor: PDFRendererHelper created");
    }

    public boolean openPdfFromAssets(String pdfFileName) {
        Log.d(TAG, "========== OPEN PDF START ==========");
        Log.d(TAG, "openPdfFromAssets: Attempting to open: " + pdfFileName);

        closeRenderer();

        try {
            // List all files in assets
            try {
                String[] assetFiles = context.getAssets().list("");
                Log.d(TAG, "openPdfFromAssets: Files in assets root: " + java.util.Arrays.toString(assetFiles));
            } catch (IOException e) {
                Log.e(TAG, "openPdfFromAssets: Could not list assets", e);
            }

            // Copy PDF from assets to temporary file
            Log.d(TAG, "openPdfFromAssets: Copying PDF from assets to temp file");
            File tempFile = copyPdfFromAssetsToTemp(pdfFileName);

            if (tempFile == null) {
                Log.e(TAG, "openPdfFromAssets: Failed to copy PDF from assets");
                return false;
            }

            Log.d(TAG, "openPdfFromAssets: Temp file created: " + tempFile.getAbsolutePath());
            Log.d(TAG, "openPdfFromAssets: Temp file size: " + tempFile.length() + " bytes");
            Log.d(TAG, "openPdfFromAssets: Temp file exists: " + tempFile.exists());
            Log.d(TAG, "openPdfFromAssets: Temp file canRead: " + tempFile.canRead());

            Log.d(TAG, "openPdfFromAssets: Opening ParcelFileDescriptor");
            parcelFileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY);
            Log.d(TAG, "openPdfFromAssets: ParcelFileDescriptor opened successfully");

            Log.d(TAG, "openPdfFromAssets: Creating PdfRenderer");
            pdfRenderer = new PdfRenderer(parcelFileDescriptor);
            Log.d(TAG, "openPdfFromAssets: PdfRenderer created successfully");
            Log.d(TAG, "openPdfFromAssets: Page count: " + pdfRenderer.getPageCount());

            Log.d(TAG, "========== OPEN PDF SUCCESS ==========");
            return true;

        } catch (IOException e) {
            Log.e(TAG, "openPdfFromAssets: IOException occurred", e);
            Log.e(TAG, "openPdfFromAssets: Error message: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "openPdfFromAssets: Unexpected exception", e);
            Log.e(TAG, "openPdfFromAssets: Error message: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private File copyPdfFromAssetsToTemp(String pdfFileName) {
        Log.d(TAG, "copyPdfFromAssetsToTemp: Starting for file: " + pdfFileName);

        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        File tempFile = null;

        try {
            Log.d(TAG, "copyPdfFromAssetsToTemp: Opening asset input stream");
            inputStream = context.getAssets().open(pdfFileName);
            Log.d(TAG, "copyPdfFromAssetsToTemp: Input stream opened, available bytes: " + inputStream.available());

            Log.d(TAG, "copyPdfFromAssetsToTemp: Creating temp file");
            tempFile = File.createTempFile("temp_pdf", ".pdf", context.getCacheDir());
            Log.d(TAG, "copyPdfFromAssetsToTemp: Temp file created at: " + tempFile.getAbsolutePath());

            Log.d(TAG, "copyPdfFromAssetsToTemp: Opening output stream");
            outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[1024];
            int length;
            int totalBytesRead = 0;

            Log.d(TAG, "copyPdfFromAssetsToTemp: Starting file copy");
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
                totalBytesRead += length;
            }

            Log.d(TAG, "copyPdfFromAssetsToTemp: File copy completed. Total bytes: " + totalBytesRead);

            inputStream.close();
            outputStream.close();

            Log.d(TAG, "copyPdfFromAssetsToTemp: Streams closed");
            Log.d(TAG, "copyPdfFromAssetsToTemp: Final file size: " + tempFile.length() + " bytes");

            return tempFile;

        } catch (IOException e) {
            Log.e(TAG, "copyPdfFromAssetsToTemp: IOException occurred", e);
            Log.e(TAG, "copyPdfFromAssetsToTemp: Error message: " + e.getMessage());

            // Try to close streams
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException ex) {
                Log.e(TAG, "copyPdfFromAssetsToTemp: Error closing streams", ex);
            }

            // Delete temp file if it was created
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                Log.d(TAG, "copyPdfFromAssetsToTemp: Temp file deleted: " + deleted);
            }

            return null;
        }
    }

    public Bitmap renderPage(int pageIndex, int scaleFactor) {
        Log.d(TAG, "========== RENDER PAGE START ==========");
        Log.d(TAG, "renderPage: pageIndex=" + pageIndex + ", scaleFactor=" + scaleFactor);

        if (pdfRenderer == null) {
            Log.e(TAG, "renderPage: PDF renderer is not initialized");
            return null;
        }

        if (currentPage != null) {
            Log.d(TAG, "renderPage: Closing previous page");
            currentPage.close();
        }

        try {
            Log.d(TAG, "renderPage: Opening page " + pageIndex);
            currentPage = pdfRenderer.openPage(pageIndex);
            Log.d(TAG, "renderPage: Page opened successfully");
            Log.d(TAG, "renderPage: Page width=" + currentPage.getWidth() + ", height=" + currentPage.getHeight());

            // Calculate bitmap size with scale factor
            int width = currentPage.getWidth() * scaleFactor / 10;
            int height = currentPage.getHeight() * scaleFactor / 10;

            Log.d(TAG, "renderPage: Calculated bitmap size: " + width + "x" + height);

            Log.d(TAG, "renderPage: Creating bitmap");
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Log.d(TAG, "renderPage: Bitmap created successfully");

            Log.d(TAG, "renderPage: Rendering page to bitmap");
            currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            Log.d(TAG, "renderPage: Page rendered successfully");

            Log.d(TAG, "========== RENDER PAGE SUCCESS ==========");
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "renderPage: Exception occurred", e);
            Log.e(TAG, "renderPage: Error message: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public int getPageCount() {
        int count = pdfRenderer != null ? pdfRenderer.getPageCount() : 0;
        Log.d(TAG, "getPageCount: Returning " + count);
        return count;
    }

    public void closeRenderer() {
        Log.d(TAG, "closeRenderer: Starting cleanup");

        if (currentPage != null) {
            Log.d(TAG, "closeRenderer: Closing current page");
            currentPage.close();
            currentPage = null;
        }

        if (pdfRenderer != null) {
            Log.d(TAG, "closeRenderer: Closing PDF renderer");
            pdfRenderer.close();
            pdfRenderer = null;
        }

        if (parcelFileDescriptor != null) {
            try {
                Log.d(TAG, "closeRenderer: Closing file descriptor");
                parcelFileDescriptor.close();
            } catch (IOException e) {
                Log.e(TAG, "closeRenderer: Error closing file descriptor", e);
            }
            parcelFileDescriptor = null;
        }

        Log.d(TAG, "closeRenderer: Cleanup completed");
    }
}