package com.nived.whealth;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 42;
    private static final String[] PROFILE_KEYS = {
            "name", "dob", "gender", "height", "phone", "emergency_name",
            "emergency_phone", "blood_group", "conditions", "allergies", "medications", "doctor"
    };
    private static final String[] PROFILE_TITLES = {
            "What should we call you?", "When were you born?", "Gender", "Height",
            "Your phone number", "Emergency contact name", "Emergency contact number",
            "Blood group", "Known conditions", "Allergies", "Current medications", "Doctor details"
    };
    private static final String[] PROFILE_HINTS = {
            "Name", "Example: 1984-01-10 or 10 Jan 1984", "Example: Male, Female, Other", "Example: 172 cm",
            "Phone number", "Name", "Phone number", "Example: O+", "Diabetes, hypertension, asthma...",
            "Food or medicine allergies...", "Medicine names or doses...", "Name and phone"
    };
    private static final String[] PROFILE_HELP = {
            "This appears on your dashboard and reports.",
            "Enter your date of birth. We'll compute your age automatically.",
            "Optional, but useful for doctor-facing reports.",
            "Helps with BMI and weight context later.",
            "Kept locally on this phone.",
            "Shown prominently in the emergency screen.",
            "Used for one-tap emergency dialing.",
            "Helpful in emergency and report views.",
            "Short notes are enough. You can skip this.",
            "You can skip this if none.",
            "You can update this anytime.",
            "Optional, useful for exported reports."
    };

    private final int ink = Color.rgb(17, 24, 39);
    private final int muted = Color.rgb(92, 101, 116);
    private final int bg = Color.rgb(246, 248, 251);
    private final int surface = Color.WHITE;
    private final int soft = Color.rgb(236, 253, 245);
    private final int teal = Color.rgb(13, 148, 136);
    private final int tealDark = Color.rgb(15, 118, 110);
    private final int line = Color.rgb(226, 232, 240);
    private final int danger = Color.rgb(220, 38, 38);

    private HealthDb db;
    private Uri pendingImageUri;
    private Map<String, EditText> activeFields;
    private Map<String, String> pendingProfileValues;
    private String activeModule;
    private boolean choosingModulesDuringSetup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize OpenCV
        org.opencv.android.OpenCVLoader.initDebug();
        
        // Start background download of Tesseract eng.traineddata
        OCRFallbackService.checkAndDownloadTessData(this);

        db = new HealthDb(this);
        if (db.hasProfile()) {
            showDashboard();
        } else {
            showProfileSetup(null);
        }
    }

    private void showProfileSetup(List<String> modules) {
        choosingModulesDuringSetup = false;
        pendingProfileValues = new LinkedHashMap<>(db.profile());
        for (String key : PROFILE_KEYS) {
            if (!pendingProfileValues.containsKey(key)) pendingProfileValues.put(key, "");
        }
        showProfileStep(modules, 0);
    }

    private void showProfileStep(List<String> modules, int step) {
        choosingModulesDuringSetup = false;
        if (step >= PROFILE_KEYS.length) {
            if (db.hasProfile()) {
                db.saveProfile(pendingProfileValues, modules == null ? db.enabledModules() : modules);
                toast("Profile updated.");
                showDashboard();
            } else {
                showModuleSetup(pendingProfileValues);
            }
            return;
        }

        String key = PROFILE_KEYS[step];
        LinearLayout root = page();
        root.addView(progress(step + 1, PROFILE_KEYS.length));
        root.addView(eyebrow("Profile setup"));
        root.addView(title(PROFILE_TITLES[step]));
        root.addView(body(PROFILE_HELP[step]));

        LinearLayout inputCard = card();
        final View inputView;
        if ("gender".equals(key)) {
            android.widget.Spinner spinner = new android.widget.Spinner(this);
            String[] genderOptions = {"Male", "Female"};
            android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                    this, android.R.layout.simple_spinner_item, genderOptions);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setPadding(dp(14), dp(12), dp(14), dp(12));
            spinner.setBackground(round(Color.rgb(248, 250, 252), dp(16), dp(1), line));

            String existing = safe(pendingProfileValues.get(key), "");
            if ("Female".equalsIgnoreCase(existing)) {
                spinner.setSelection(1);
            } else {
                spinner.setSelection(0);
            }

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(8), 0, dp(8));
            spinner.setLayoutParams(lp);
            inputCard.addView(spinner);
            inputView = spinner;
        } else {
            EditText input = new EditText(this);
            input.setHint(PROFILE_HINTS[step]);
            input.setText(safe(pendingProfileValues.get(key), ""));
            input.setInputType(inputTypeFor(key));
            if (isLongProfileField(key)) input.setMinLines(4);
            styleInput(input);
            inputCard.addView(input);
            inputView = input;
        }
        root.addView(inputCard);

        LinearLayout controls = row();
        if (step > 0) {
            Button back = secondary("Back");
            back.setOnClickListener(v -> {
                pendingProfileValues.put(key, getInputValue(inputView));
                showProfileStep(modules, step - 1);
            });
            controls.addView(back);
        }

        Button next = primary(step == PROFILE_KEYS.length - 1 ? (db.hasProfile() ? "Save" : "Choose Modules") : "Next");
        next.setOnClickListener(v -> {
            String value = getInputValue(inputView);
            if (isRequired(key) && value.isEmpty()) {
                toast("This one is required.");
                pulse(inputCard);
                return;
            }
            pendingProfileValues.put(key, value);
            showProfileStep(modules, step + 1);
        });
        controls.addView(next);
        root.addView(controls);

        if (!isRequired(key)) {
            TextView skip = link("Skip for now");
            skip.setOnClickListener(v -> {
                pendingProfileValues.put(key, "");
                showProfileStep(modules, step + 1);
            });
            root.addView(skip);
        }

        show(root);
    }

    private void showModuleSetup(Map<String, String> profileValues) {
        choosingModulesDuringSetup = profileValues != null && !db.hasProfile();
        LinearLayout root = page();
        root.addView(eyebrow("Personalize"));
        root.addView(title("Pick your dashboard"));
        root.addView(body("Choose only what matters to you. You can keep it focused and change it later."));

        List<String> current = profileValues == null ? db.enabledModules() : new ArrayList<>();
        List<CheckBox> checks = new ArrayList<>();
        for (String module : HealthDb.DEFAULT_MODULES) {
            LinearLayout choice = choiceCard();
            CheckBox box = new CheckBox(this);
            box.setText(module);
            box.setTextSize(17);
            box.setTextColor(ink);
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(teal));
            box.setChecked(current.contains(module) || (current.isEmpty() &&
                    ("Blood Pressure".equals(module) || "Blood Sugar".equals(module) || "Weight".equals(module))));
            choice.setOnClickListener(v -> {
                box.setChecked(!box.isChecked());
                pulse(choice);
            });
            checks.add(box);
            choice.addView(box);
            root.addView(choice);
        }

        Button finish = primary(db.hasProfile() ? "Save Modules" : "Finish Setup");
        finish.setOnClickListener(v -> {
            ArrayList<String> selected = new ArrayList<>();
            for (CheckBox box : checks) {
                if (box.isChecked()) selected.add(box.getText().toString());
            }
            if (selected.isEmpty()) {
                toast("Select at least one module.");
                return;
            }
            db.saveProfile(profileValues == null ? db.profile() : profileValues, selected);
            pendingProfileValues = null;
            showDashboard();
        });
        root.addView(finish);
        show(root);
    }

    private void showDashboard() {
        choosingModulesDuringSetup = false;
        Map<String, String> profile = db.profile();
        LinearLayout root = page();
        root.addView(eyebrow("WHealth"));
        root.addView(title("Hello, " + safe(profile.get("name"), "there")));

        LinearLayout hero = accentCard();
        hero.addView(label("Today's Health"));
        List<HealthDb.RecordSummary> timeline = db.records(null, 8);
        hero.addView(big(timeline.isEmpty() ? "Ready when you are" : timeline.get(0).displayValue()));
        hero.addView(body(timeline.isEmpty() ? "Add your first reading from a quick action below." : "Latest: " + timeline.get(0).type));
        root.addView(hero);

        Button emergency = danger("Emergency");
        emergency.setOnClickListener(v -> showEmergency(profile));
        root.addView(emergency);

        root.addView(section("Your Modules"));
        for (String module : db.enabledModules()) {
            HealthDb.RecordSummary latest = db.latest(module);
            LinearLayout card = card();
            card.addView(label(module));
            card.addView(big(latest == null ? "Not recorded yet" : latest.displayValue()));
            if (latest != null) card.addView(statusLine(latest));
            card.setOnClickListener(v -> showHistory(module));
            root.addView(card);
        }

        root.addView(section("Quick Add"));
        for (String module : db.enabledModules()) {
            Button add = secondary("+ " + module);
            add.setOnClickListener(v -> {
                pendingImageUri = null;
                showEntry(module);
            });
            root.addView(add);
        }

        root.addView(section("Timeline"));
        if (timeline.isEmpty()) {
            root.addView(emptyState("No records yet", "Your saved readings will appear here as a simple health timeline."));
        } else {
            for (HealthDb.RecordSummary record : timeline) root.addView(recordRow(record));
        }

        LinearLayout controls = row();
        Button profileButton = secondary("Edit Profile");
        profileButton.setOnClickListener(v -> showProfileSetup(db.enabledModules()));
        Button moduleButton = secondary("Edit Modules");
        moduleButton.setOnClickListener(v -> showModuleSetup(null));
        controls.addView(profileButton);
        controls.addView(moduleButton);
        root.addView(controls);
        show(root);
    }

    private void showEntry(String module) {
        activeModule = module;
        activeFields = new LinkedHashMap<>();
        LinearLayout root = page();
        root.addView(navTitle("Add " + module));
        root.addView(body("A calm review flow: enter values, optionally attach a photo, then confirm before saving."));

        LinearLayout valueCard = card();
        valueCard.addView(label("Reading"));
        for (String field : fieldsFor(module)) {
            addInput(valueCard, activeFields, field, field, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }
        root.addView(valueCard);

        LinearLayout photoCard = card();
        photoCard.addView(label("Photo assist"));
        photoCard.addView(body(pendingImageUri == null ? "Capture a reading photo for automated OCR prefill." : "Photo captured. Review every value before saving."));
        if (pendingImageUri != null) {
            android.widget.ImageView previewImg = new android.widget.ImageView(this);
            if ("file".equalsIgnoreCase(pendingImageUri.getScheme())) {
                Bitmap bmp = android.graphics.BitmapFactory.decodeFile(pendingImageUri.getPath());
                if (bmp != null) {
                    previewImg.setImageBitmap(bmp);
                } else {
                    previewImg.setImageURI(pendingImageUri);
                }
            } else {
                previewImg.setImageURI(pendingImageUri);
            }
            previewImg.setAdjustViewBounds(true);
            previewImg.setMaxHeight(dp(150));
            previewImg.setPadding(0, dp(8), 0, dp(8));
            LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(-1, -2);
            imgLp.setMargins(0, dp(4), 0, dp(4));
            previewImg.setLayoutParams(imgLp);
            photoCard.addView(previewImg);
        }
        Button image = secondary(pendingImageUri == null ? "Capture Photo" : "Recapture Photo");
        image.setOnClickListener(v -> {
            Intent intent = new Intent(this, CameraActivity.class);
            startActivityForResult(intent, PICK_IMAGE);
        });
        Button ocr = secondary("Prefill Review Form");
        ocr.setOnClickListener(v -> {
            prefillDemoValues(module);
            pulse(valueCard);
            toast("Values prefilled for review. Please verify before saving.");
        });
        photoCard.addView(image);
        photoCard.addView(ocr);
        root.addView(photoCard);

        EditText notes = new EditText(this);
        notes.setHint("Notes, symptoms, context...");
        notes.setMinLines(3);
        notes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        styleInput(notes);
        root.addView(notes);

        Button save = primary("Confirm & Save");
        save.setOnClickListener(v -> {
            Map<String, Double> values = new LinkedHashMap<>();
            for (Map.Entry<String, EditText> entry : activeFields.entrySet()) {
                String raw = text(entry.getValue());
                if (raw.isEmpty()) {
                    toast("Please fill " + entry.getKey() + ".");
                    pulse(entry.getValue());
                    return;
                }
                try {
                    values.put(entry.getKey(), Double.parseDouble(raw));
                } catch (NumberFormatException e) {
                    toast("Check " + entry.getKey() + ".");
                    pulse(entry.getValue());
                    return;
                }
            }
            String source = pendingImageUri == null ? "Manual" : "Image-assisted review";
            db.saveRecord(module, values, defaultUnit(module), text(notes),
                    pendingImageUri == null ? "" : pendingImageUri.toString(), source, pendingImageUri == null ? 100 : 90);
            toast("Record saved.");
            showDashboard();
        });
        root.addView(save);
        show(root);
    }

    private void showHistory(String module) {
        LinearLayout root = page();
        root.addView(navTitle(module));
        root.addView(body("Tap add to save a new reading. Recent records stay local on this phone."));
        Button add = primary("+ Add Record");
        add.setOnClickListener(v -> {
            pendingImageUri = null;
            showEntry(module);
        });
        root.addView(add);

        List<HealthDb.RecordSummary> records = db.records(module, 100);
        if (records.isEmpty()) root.addView(emptyState("No records yet", "Your " + module + " history will appear here."));
        else for (HealthDb.RecordSummary record : records) root.addView(recordRow(record));
        show(root);
    }

    private void showEmergency(Map<String, String> profile) {
        LinearLayout root = page();
        root.addView(navTitle("Emergency"));
        LinearLayout contact = accentCard();
        contact.addView(label("Emergency Contact"));
        contact.addView(big(safe(profile.get("emergency_name"), "Emergency contact")));
        contact.addView(body(safe(profile.get("emergency_phone"), "No number saved")));
        root.addView(contact);

        Button call = danger("Call Emergency Contact");
        call.setOnClickListener(v -> {
            String phone = safe(profile.get("emergency_phone"), "");
            if (phone.isEmpty()) toast("No emergency phone saved.");
            else startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
        });
        root.addView(call);

        root.addView(section("Medical Info"));
        String dobVal = profile.get("dob");
        if (dobVal != null && !dobVal.trim().isEmpty()) {
            String ageStr = calculateAgeStr(dobVal);
            if (!ageStr.isEmpty()) {
                root.addView(infoLine("Age", ageStr));
            }
            root.addView(infoLine("Date of birth", dobVal));
        }
        root.addView(infoLine("Blood group", safe(profile.get("blood_group"), "Not provided")));
        root.addView(infoLine("Conditions", safe(profile.get("conditions"), "Not provided")));
        root.addView(infoLine("Allergies", safe(profile.get("allergies"), "Not provided")));
        root.addView(infoLine("Medications", safe(profile.get("medications"), "Not provided")));
        show(root);
    }

    private String calculateAgeStr(String dobString) {
        if (dobString == null || dobString.trim().isEmpty()) return "";
        String[] formats = {"yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "MMM dd, yyyy", "dd MMM yyyy", "yyyy"};
        java.util.Calendar birth = java.util.Calendar.getInstance();
        boolean parsed = false;

        int year = -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(dobString);
        if (m.find()) {
            try {
                year = Integer.parseInt(m.group());
            } catch (Exception ignored) {}
        }

        for (String format : formats) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(format, java.util.Locale.US);
                sdf.setLenient(false);
                java.util.Date date = sdf.parse(dobString);
                if (date != null) {
                    birth.setTime(date);
                    parsed = true;
                    break;
                }
            } catch (Exception ignored) {}
        }

        java.util.Calendar today = java.util.Calendar.getInstance();
        if (parsed) {
            int age = today.get(java.util.Calendar.YEAR) - birth.get(java.util.Calendar.YEAR);
            if (today.get(java.util.Calendar.DAY_OF_YEAR) < birth.get(java.util.Calendar.DAY_OF_YEAR)) {
                age--;
            }
            if (age < 0) age = 0;
            return age + " years";
        } else if (year != -1) {
            int age = today.get(java.util.Calendar.YEAR) - year;
            if (age < 0) age = 0;
            return age + " years";
        }

        try {
            int age = Integer.parseInt(dobString.replaceAll("[^0-9]", ""));
            if (age > 0 && age < 130) {
                return age + " years";
            }
        } catch (Exception ignored) {}

        return "";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            String uriStr = data.getStringExtra("image_uri");
            double cropLeft = data.getDoubleExtra("crop_left", 0.08);
            double cropTop = data.getDoubleExtra("crop_top", 0.35);
            double cropWidth = data.getDoubleExtra("crop_width", 0.84);
            double cropHeight = data.getDoubleExtra("crop_height", 0.30);

            if (uriStr != null) {
                pendingImageUri = Uri.parse(uriStr);
                if (activeModule != null) {
                    showEntry(activeModule);
                    runOcr(pendingImageUri, activeModule, cropLeft, cropTop, cropWidth, cropHeight);
                }
            } else {
                pendingImageUri = data.getData();
                if (pendingImageUri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(
                                pendingImageUri,
                                data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (Exception ignored) {}
                    if (activeModule != null) {
                        showEntry(activeModule);
                        runOcr(pendingImageUri, activeModule, 0.08, 0.35, 0.84, 0.30);
                    }
                }
            }
        }
    }

    private void runOcr(Uri imageUri, String module, double cropLeft, double cropTop, double cropWidth, double cropHeight) {
        if (imageUri == null) return;

        toast("Processing reading photo...");

        new Thread(() -> {
            try {
                String inputPath = "";
                if ("file".equalsIgnoreCase(imageUri.getScheme())) {
                    inputPath = imageUri.getPath();
                    if (inputPath.startsWith("/") && inputPath.contains(":")) {
                        inputPath = inputPath.substring(1);
                    }
                } else {
                    java.io.File tempFile = new java.io.File(getCacheDir(), "ocr_input.jpg");
                    try (java.io.InputStream is = getContentResolver().openInputStream(imageUri);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }
                    inputPath = tempFile.getAbsolutePath();
                }

                File debugDir = new File(getFilesDir(), "ocr_debug");
                if (!debugDir.exists()) debugDir.mkdirs();

                String rotatedPath = new File(debugDir, "rotated.jpg").getAbsolutePath();
                String croppedPath = new File(debugDir, "cropped_lcd.jpg").getAbsolutePath();
                String thresholdPath = new File(debugDir, "thresholded.jpg").getAbsolutePath();

                // Step 1 & 2: EXIF Auto Rotate, Landscape Normalize, and Visual crop
                Bitmap croppedBmp = ImagePreprocessor.preprocess(this, inputPath, croppedPath, 
                        cropLeft, cropTop, cropWidth, cropHeight);

                final Uri croppedUri = Uri.fromFile(new File(croppedPath));

                // Load Mat for OpenCV enhancements
                org.opencv.core.Mat srcMat = new org.opencv.core.Mat();
                org.opencv.android.Utils.bitmapToMat(croppedBmp, srcMat);

                // Step 3 & 4: Warp LCD (already cropped, but let's warp skewness if detected)
                org.opencv.core.MatOfPoint2f lcdCorners = LCDDetector.detectLCD(srcMat);
                org.opencv.core.Mat warpedMat = PerspectiveCorrector.warp(srcMat, lcdCorners);

                // Step 5: Binary enhancements
                org.opencv.core.Mat thresholdMat = ThresholdProcessor.process(warpedMat);

                Bitmap thresholdBmp = Bitmap.createBitmap(thresholdMat.cols(), thresholdMat.rows(), Bitmap.Config.ARGB_8888);
                org.opencv.android.Utils.matToBitmap(thresholdMat, thresholdBmp);
                try (FileOutputStream fos = new FileOutputStream(thresholdPath)) {
                    thresholdBmp.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                }

                srcMat.release();
                warpedMat.release();

                MeasurementParser.BPResult finalResult = new MeasurementParser.BPResult();
                finalResult.imagePath = croppedPath;

                if ("Blood Pressure".equals(module)) {
                    // Step 6 & 7: Digit segments row grouping
                    List<List<DigitContourDetector.DigitRect>> digitRows = 
                            DigitContourDetector.detectAndGroup(thresholdMat);

                    // Step 8: Seven Segment Recognition
                    String sys = SevenSegmentRecognizer.recognizeRow(thresholdMat, digitRows.get(0));
                    String dia = SevenSegmentRecognizer.recognizeRow(thresholdMat, digitRows.get(1));
                    String pulse = SevenSegmentRecognizer.recognizeRow(thresholdMat, digitRows.get(2));

                    thresholdMat.release();

                    if (sys != null && dia != null && pulse != null) {
                        finalResult.systolic = Integer.parseInt(sys);
                        finalResult.diastolic = Integer.parseInt(dia);
                        finalResult.pulse = Integer.parseInt(pulse);
                        finalResult.source = "Seven Segment Recognition";
                        finalResult.systolicConfidence = 1.0;
                        finalResult.diastolicConfidence = 1.0;
                        finalResult.pulseConfidence = 1.0;
                    } else {
                        // Step 8.5: Fallback to PaddleOCR
                        MeasurementParser.BPResult paddleRes = PaddleOCRService.runPaddleOCR(this, croppedPath);
                        if (paddleRes != null) {
                            finalResult = paddleRes;
                            finalResult.imagePath = croppedPath;
                        } else {
                            // Step 9: Fallback to Google ML Kit
                            try {
                                com.google.mlkit.vision.text.Text visionText = 
                                        OCRFallbackService.runMLKitOcr(this, croppedUri);
                                MeasurementParser.BPResult mlKitRes = 
                                        MeasurementParser.parseMLKit(visionText, croppedBmp.getHeight());
                                if (mlKitRes != null) {
                                    finalResult = mlKitRes;
                                    finalResult.imagePath = croppedPath;
                                } else {
                                    // Step 10: Fallback to Tesseract
                                    String tessText = OCRFallbackService.runTesseract(this, croppedPath);
                                    MeasurementParser.BPResult tessRes = MeasurementParser.parseTesseract(tessText);
                                    if (tessRes != null) {
                                        finalResult = tessRes;
                                        finalResult.imagePath = croppedPath;
                                    }
                                }
                            } catch (Exception e) {
                                String tessText = OCRFallbackService.runTesseract(this, croppedPath);
                                MeasurementParser.BPResult tessRes = MeasurementParser.parseTesseract(tessText);
                                if (tessRes != null) {
                                    finalResult = tessRes;
                                    finalResult.imagePath = croppedPath;
                                }
                            }
                        }
                    }
                } else {
                    thresholdMat.release();
                    try {
                        com.google.mlkit.vision.text.Text visionText = 
                                OCRFallbackService.runMLKitOcr(this, croppedUri);
                        MeasurementParser.BPResult mlKitRes = 
                                MeasurementParser.parseMLKit(visionText, croppedBmp.getHeight());
                        if (mlKitRes != null) {
                            finalResult = mlKitRes;
                            finalResult.imagePath = croppedPath;
                        }
                    } catch (Exception ignored) {}
                }

                final double score = ConfidenceCalculator.calculate(finalResult);
                final MeasurementParser.BPResult uiResult = finalResult;

                runOnUiThread(() -> {
                    pendingImageUri = croppedUri;
                    showEntry(module);

                    if (uiResult.systolic > 0) setField("SYS", String.valueOf(uiResult.systolic));
                    if (uiResult.diastolic > 0) setField("DIA", String.valueOf(uiResult.diastolic));
                    if (uiResult.pulse > 0) setField("Pulse", String.valueOf(uiResult.pulse));

                    if (uiResult.systolic == 0 && uiResult.diastolic == 0 && uiResult.pulse == 0) {
                        toast("OCR could not confidently read this image. Please enter manually.");
                    } else if (score < 0.85) {
                        toast("⚠ Please verify detected values (confidence: " + (int)(score * 100) + "%)");
                    } else {
                        toast("Values detected via " + uiResult.source + "!");
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    toast("Failed to run CV pipeline: " + e.getMessage());
                    prefillDemoValues(module);
                });
            }
        });
    }

    private String getInputValue(View view) {
        if (view instanceof android.widget.Spinner) {
            return ((android.widget.Spinner) view).getSelectedItem().toString();
        } else if (view instanceof EditText) {
            return text((EditText) view);
        }
        return "";
    }

    @Override
    public void onBackPressed() {
        if (choosingModulesDuringSetup) {
            showProfileSetup(null);
            return;
        }
        if (db.hasProfile()) showDashboard();
        else super.onBackPressed();
    }

    private void addInput(LinearLayout root, Map<String, EditText> inputs, String key, String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(inputType);
        styleInput(input);
        inputs.put(key, input);
        root.addView(input);
    }

    private void prefillDemoValues(String module) {
        if ("Blood Pressure".equals(module)) {
            setField("SYS", "170");
            setField("DIA", "101");
            setField("Pulse", "68");
        } else if ("Blood Sugar".equals(module)) setField("Reading", "96");
        else if ("Weight".equals(module)) setField("Weight", "72.4");
        else if ("Oxygen".equals(module)) setField("SpO2", "98");
        else if ("Temperature".equals(module)) setField("Temperature", "36.8");
        else if ("Water".equals(module)) setField("Glasses", "1");
        else if ("Sleep".equals(module)) setField("Hours", "7.5");
        else for (String key : activeFields.keySet()) setField(key, "1");
    }

    private void setField(String key, String value) {
        EditText field = activeFields.get(key);
        if (field != null) field.setText(value);
    }

    private String[] fieldsFor(String module) {
        if ("Blood Pressure".equals(module)) return new String[]{"SYS", "DIA", "Pulse"};
        if ("Blood Sugar".equals(module)) return new String[]{"Reading"};
        if ("Weight".equals(module)) return new String[]{"Weight"};
        if ("Heart Rate".equals(module)) return new String[]{"Pulse"};
        if ("Oxygen".equals(module)) return new String[]{"SpO2"};
        if ("Temperature".equals(module)) return new String[]{"Temperature"};
        if ("Medicines".equals(module)) return new String[]{"Taken"};
        if ("Water".equals(module)) return new String[]{"Glasses"};
        if ("Sleep".equals(module)) return new String[]{"Hours"};
        return new String[]{"Value"};
    }

    private String defaultUnit(String module) {
        if ("Blood Sugar".equals(module)) return "mg/dL";
        if ("Weight".equals(module)) return "kg";
        if ("Heart Rate".equals(module)) return "bpm";
        if ("Oxygen".equals(module)) return "%";
        if ("Temperature".equals(module)) return "C";
        if ("Water".equals(module)) return "glasses";
        if ("Sleep".equals(module)) return "hours";
        return "count";
    }

    private int inputTypeFor(String key) {
        if ("phone".equals(key) || "emergency_phone".equals(key)) return InputType.TYPE_CLASS_PHONE;
        if (isLongProfileField(key)) return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        return InputType.TYPE_CLASS_TEXT;
    }

    private boolean isLongProfileField(String key) {
        return "conditions".equals(key) || "allergies".equals(key) || "medications".equals(key);
    }

    private boolean isRequired(String key) {
        return "name".equals(key) || "phone".equals(key) || "emergency_name".equals(key) || "emergency_phone".equals(key);
    }

    private TextView statusLine(HealthDb.RecordSummary record) {
        TextView view = body(record.source + " | " + HealthDb.dateTime(record.recordedAt) + " | " + classify(record));
        view.setTextColor(statusColor(record));
        return view;
    }

    private String classify(HealthDb.RecordSummary record) {
        if (!"Blood Pressure".equals(record.type)) return record.confidence + "% confidence";
        double sys = number(record.measurements.get("SYS"));
        double dia = number(record.measurements.get("DIA"));
        if (sys >= 180 || dia >= 120) return "Hypertensive crisis";
        if (sys >= 140 || dia >= 90) return "High";
        if (sys >= 120 || dia >= 80) return "Elevated";
        return "Normal";
    }

    private int statusColor(HealthDb.RecordSummary record) {
        String status = classify(record);
        if (status.contains("crisis")) return Color.rgb(185, 28, 28);
        if (status.contains("High")) return Color.rgb(194, 65, 12);
        if (status.contains("Elevated")) return Color.rgb(161, 98, 7);
        return Color.rgb(21, 128, 61);
    }

    private double number(String value) {
        if (value == null) return 0;
        try {
            return Double.parseDouble(value.split(" ")[0]);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private LinearLayout recordRow(HealthDb.RecordSummary record) {
        LinearLayout row = card();
        row.addView(label(record.type + "  |  " + HealthDb.dateTime(record.recordedAt)));
        row.addView(big(record.displayValue()));
        row.addView(statusLine(record));
        if (record.notes != null && !record.notes.isEmpty()) row.addView(body(record.notes));
        return row;
    }

    private LinearLayout infoLine(String label, String value) {
        LinearLayout row = card();
        row.addView(label(label));
        row.addView(body(value));
        return row;
    }

    private LinearLayout emptyState(String title, String detail) {
        LinearLayout row = card();
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView icon = big("...");
        icon.setTextColor(teal);
        row.addView(icon);
        row.addView(big(title));
        row.addView(body(detail));
        return row;
    }

    private void show(LinearLayout root) {
        ScrollView view = scroll(root);
        view.setAlpha(0f);
        view.setTranslationY(dp(14));
        setContentView(view);
        view.animate().alpha(1f).translationY(0).setDuration(260).start();
    }

    private ScrollView scroll(LinearLayout root) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(bg);
        scroll.addView(root);
        return scroll;
    }

    private LinearLayout page() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(28));
        return root;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(round(surface, dp(18), 0, line));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        card.setLayoutParams(lp);
        return card;
    }

    private LinearLayout accentCard() {
        LinearLayout card = card();
        card.setBackground(round(soft, dp(22), 0, soft));
        card.setElevation(dp(1));
        return card;
    }

    private LinearLayout choiceCard() {
        LinearLayout card = card();
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        return card;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(6));
        row.setLayoutParams(lp);
        return row;
    }

    private TextView navTitle(String text) {
        TextView title = title(text);
        title.setOnClickListener(v -> showDashboard());
        return title;
    }

    private TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(ink);
        view.setTextSize(30);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.START);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private TextView eyebrow(String text) {
        TextView view = label(text);
        view.setTextColor(tealDark);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, 0, 0, dp(6));
        return view;
    }

    private TextView progress(int current, int total) {
        TextView view = label("Step " + current + " of " + total);
        view.setGravity(Gravity.END);
        view.setTextColor(tealDark);
        return view;
    }

    private TextView section(String text) {
        TextView view = label(text);
        view.setTextColor(ink);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextSize(18);
        view.setPadding(0, dp(18), 0, dp(4));
        return view;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(muted);
        view.setTextSize(14);
        return view;
    }

    private TextView big(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(ink);
        view.setTextSize(23);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(4), 0, dp(2));
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(muted);
        view.setTextSize(15);
        view.setLineSpacing(2f, 1.05f);
        view.setPadding(0, dp(4), 0, dp(8));
        return view;
    }

    private TextView link(String text) {
        TextView view = body(text);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(tealDark);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(10), 0, dp(10));
        return view;
    }

    private Button primary(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackground(round(teal, dp(16), 0, teal));
        space(button);
        return button;
    }

    private Button secondary(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(tealDark);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackground(round(Color.TRANSPARENT, dp(16), dp(1), line));
        space(button);
        return button;
    }

    private Button danger(String text) {
        Button button = primary(text);
        button.setBackground(round(danger, dp(16), 0, danger));
        return button;
    }

    private void styleInput(EditText input) {
        input.setTextColor(ink);
        input.setHintTextColor(Color.rgb(148, 163, 184));
        input.setTextSize(18);
        input.setSingleLine(false);
        input.setBackground(round(Color.rgb(248, 250, 252), dp(16), dp(1), line));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(8));
        input.setLayoutParams(lp);
    }

    private void space(View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(4), dp(8), dp(4), dp(8));
        view.setLayoutParams(lp);
        view.setPadding(dp(10), dp(10), dp(10), dp(10));
    }

    private GradientDrawable round(int color, int radius, int strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private void pulse(View view) {
        view.animate().scaleX(1.03f).scaleY(1.03f).setDuration(110)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(130).start())
                .start();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String text(EditText input) {
        return input.getText().toString().trim();
    }

    private String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
