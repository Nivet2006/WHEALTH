package com.nived.whealth;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OCRFallbackService {

    public static void checkAndDownloadTessData(Context context) {
        File tessDir = new File(context.getFilesDir(), "tessdata");
        if (!tessDir.exists()) {
            tessDir.mkdirs();
        }
        File tessFile = new File(tessDir, "eng.traineddata");
        if (tessFile.exists() && tessFile.length() > 10000) {
            return;
        }

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL("https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(20000);
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    try (java.io.InputStream is = conn.getInputStream();
                         FileOutputStream fos = new FileOutputStream(tessFile)) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    public static Text runMLKitOcr(Context context, Uri imageUri) throws Exception {
        InputImage image = InputImage.fromFilePath(context, imageUri);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        return Tasks.await(recognizer.process(image));
    }

    public static String runTesseract(Context context, String imagePath) {
        File tessDir = new File(context.getFilesDir(), "tessdata");
        File tessFile = new File(tessDir, "eng.traineddata");
        if (!tessFile.exists()) {
            return null;
        }

        try {
            TessBaseAPI api = new TessBaseAPI();
            api.init(context.getFilesDir().getAbsolutePath(), "eng");
            api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789\n");
            
            File imgFile = new File(imagePath);
            api.setImage(imgFile);
            
            String text = api.getUTF8Text();
            api.recycle();
            return text;
        } catch (Exception e) {
            return null;
        }
    }
}
