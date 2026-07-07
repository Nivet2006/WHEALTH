package com.nived.whealth;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class CameraActivity extends Activity implements SurfaceHolder.Callback {
    public static final String EXTRA_IMAGE_URI = "image_uri";
    private static final int CAMERA_PERMISSION = 77;

    private Camera camera;
    private SurfaceView preview;
    private boolean surfaceReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        } else {
            buildUi();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            buildUi();
        } else {
            toast("Camera permission is needed to take a reading photo.");
            finish();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 18, 25));

        FrameLayout frame = new FrameLayout(this);
        frame.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));

        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        TextView guide = new TextView(this);
        guide.setText("Align the device display inside the frame");
        guide.setTextColor(Color.WHITE);
        guide.setTextSize(16);
        guide.setGravity(Gravity.CENTER);
        guide.setBackground(round(Color.argb(155, 15, 23, 42), dp(18)));
        FrameLayout.LayoutParams guideLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        guideLp.setMargins(dp(18), dp(24), dp(18), 0);
        frame.addView(guide, guideLp);

        TextView target = new TextView(this);
        target.setBackground(round(Color.argb(25, 20, 184, 166), dp(22)));
        FrameLayout.LayoutParams targetLp = new FrameLayout.LayoutParams(-1, dp(220), Gravity.CENTER);
        targetLp.setMargins(dp(24), 0, dp(24), 0);
        frame.addView(target, targetLp);

        root.addView(frame);

        Button capture = new Button(this);
        capture.setText("Capture Reading");
        capture.setAllCaps(false);
        capture.setTextColor(Color.WHITE);
        capture.setTextSize(17);
        capture.setBackground(round(Color.rgb(13, 148, 136), dp(18)));
        LinearLayout.LayoutParams captureLp = new LinearLayout.LayoutParams(-1, -2);
        captureLp.setMargins(dp(20), dp(14), dp(20), dp(20));
        capture.setLayoutParams(captureLp);
        capture.setPadding(dp(12), dp(14), dp(12), dp(14));
        capture.setOnClickListener(v -> takePhoto());
        root.addView(capture);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preview != null && surfaceReady) openCamera();
    }

    @Override
    protected void onPause() {
        releaseCamera();
        super.onPause();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        openCamera();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        restartPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        releaseCamera();
    }

    private void openCamera() {
        if (camera != null || !surfaceReady) return;
        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(preview.getHolder());
            camera.startPreview();
        } catch (Exception e) {
            toast("Unable to open camera.");
            finish();
        }
    }

    private void restartPreview() {
        if (camera == null || !surfaceReady) return;
        try {
            camera.stopPreview();
        } catch (Exception ignored) {
        }
        try {
            camera.setPreviewDisplay(preview.getHolder());
            camera.startPreview();
        } catch (IOException e) {
            toast("Unable to start camera preview.");
        }
    }

    private void takePhoto() {
        if (camera == null) {
            toast("Camera is not ready yet.");
            return;
        }
        camera.takePicture(null, null, (data, cam) -> {
            try {
                File dir = new File(getFilesDir(), "reading_photos");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create photo folder.");
                File image = new File(dir, "reading_" + System.currentTimeMillis() + ".jpg");
                try (FileOutputStream out = new FileOutputStream(image)) {
                    out.write(data);
                }

                // Compute crop coordinates matching the visual guide centered vertically
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                float density = getResources().getDisplayMetrics().density;
                
                double relLeft = (24.0 * density) / screenWidth;
                double relWidth = (screenWidth - 48.0 * density) / screenWidth;
                double relHeight = (220.0 * density) / screenHeight;
                double relTop = ((screenHeight - 220.0 * density) / 2.0) / screenHeight;

                Intent result = new Intent();
                result.putExtra(EXTRA_IMAGE_URI, Uri.fromFile(image).toString());
                result.putExtra("crop_left", relLeft);
                result.putExtra("crop_top", relTop);
                result.putExtra("crop_width", relWidth);
                result.putExtra("crop_height", relHeight);
                setResult(RESULT_OK, result);
                finish();
            } catch (Exception e) {
                toast("Could not save photo: " + e.getMessage());
                try {
                    cam.startPreview();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void releaseCamera() {
        if (camera == null) return;
        try {
            camera.stopPreview();
        } catch (Exception ignored) {
        }
        camera.release();
        camera = null;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), Color.argb(80, 255, 255, 255));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
