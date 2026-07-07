package com.nived.whealth;

import android.content.Context;

public class PaddleOCRService {
    
    public static MeasurementParser.BPResult runPaddleOCR(Context context, String imagePath) {
        try {
            // Verify PaddleOCR library presence at runtime using reflection.
            // This prevents any compile-time conflicts with Kotlin callbacks
            // or varying interface signatures between FastDeploy, Paddle-Lite, and Ncnn backends.
            Class<?> ocrClass = Class.forName("com.equationl.paddleocr4android.OCR");
            android.util.Log.d("PaddleOCRService", "PaddleOCR library linked successfully: " + ocrClass.getName());
            
            // Return null to trigger the next fallback (Google ML Kit),
            // since the 15MB+ model files (.nb/.onnx) are not pre-packaged in assets.
            return null;
        } catch (Throwable t) {
            android.util.Log.e("PaddleOCRService", "PaddleOCR not available or failed to load: " + t.getMessage());
            return null;
        }
    }
}
