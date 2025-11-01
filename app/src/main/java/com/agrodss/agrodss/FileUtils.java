package com.agrodss.agrodss;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class FileUtils {
    public static File createFileFromUri(Context ctx, Uri uri, String displayName) {
        try {
            File cacheDir = new File(ctx.getCacheDir(), "uploads");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            File outFile = new File(cacheDir, displayName != null ? displayName : "temp_image.jpg");

            try (InputStream in = ctx.getContentResolver().openInputStream(uri);
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
            }
            return outFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}