package com.nived.whealth;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImagePreprocessor {
    
    public static Bitmap preprocess(Context context, String inputPath, String outputPath, 
                                    double cropLeft, double cropTop, double cropWidth, double cropHeight) throws IOException {
        // 1. Get EXIF rotation
        ExifInterface exif = new ExifInterface(inputPath);
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        int rotationDegrees = 0;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotationDegrees = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotationDegrees = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotationDegrees = 270;
                break;
        }

        // 2. Decode with sample size to limit memory consumption
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(inputPath, options);
        
        int longestEdge = Math.max(options.outWidth, options.outHeight);
        int sampleSize = 1;
        while (longestEdge / sampleSize > 1400) {
            sampleSize *= 2;
        }
        
        options.inJustDecodeBounds = false;
        options.inSampleSize = sampleSize;
        Bitmap decoded = BitmapFactory.decodeFile(inputPath, options);
        if (decoded == null) {
            throw new IOException("Failed to decode bitmap");
        }

        // 3. Rotate bitmap if EXIF specifies rotation
        Bitmap processed = decoded;
        if (rotationDegrees != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            processed = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);
            if (processed != decoded) {
                decoded.recycle();
            }
        }

        // 4. Force portrait orientation: if image is landscape, rotate 90 degrees clockwise
        if (processed.getWidth() > processed.getHeight()) {
            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            Bitmap rotated = Bitmap.createBitmap(processed, 0, 0, processed.getWidth(), processed.getHeight(), matrix, true);
            if (rotated != processed) {
                processed.recycle();
            }
            processed = rotated;
        }

        // 5. Crop relative to visual overlay frame coordinates
        int w = processed.getWidth();
        int h = processed.getHeight();
        
        int cropX = (int) (cropLeft * w);
        int cropY = (int) (cropTop * h);
        int cropW = (int) (cropWidth * w);
        int cropH = (int) (cropHeight * h);
        
        // Ensure boundaries are safe
        cropX = Math.max(0, cropX);
        cropY = Math.max(0, cropY);
        if (cropX + cropW > w) cropW = w - cropX;
        if (cropY + cropH > h) cropH = h - cropY;
        
        Bitmap cropped = Bitmap.createBitmap(processed, cropX, cropY, cropW, cropH);
        if (cropped != processed) {
            processed.recycle();
        }

        // 6. Save processed image as JPEG with 80% quality
        if (outputPath != null) {
            File outFile = new File(outputPath);
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                cropped.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            }
        }

        return cropped;
    }
}
