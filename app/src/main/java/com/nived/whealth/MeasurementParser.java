package com.nived.whealth;

import com.google.mlkit.vision.text.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MeasurementParser {
    
    public static class BPResult {
        public int systolic = 0;
        public int diastolic = 0;
        public int pulse = 0;
        
        public double systolicConfidence = 1.0;
        public double diastolicConfidence = 1.0;
        public double pulseConfidence = 1.0;
        
        public String imagePath;
        public String source = "Manual";
    }
    
    private static class OCRItem {
        final String text;
        final android.graphics.Rect rect;
        final float confidence;
        
        OCRItem(String text, android.graphics.Rect rect, float confidence) {
            this.text = text;
            this.rect = rect;
            this.confidence = confidence;
        }
    }
    
    public static BPResult parseMLKit(Text text, int imageHeight) {
        BPResult result = new BPResult();
        result.source = "Google ML Kit OCR";
        
        List<OCRItem> items = new ArrayList<>();
        float totalConf = 0;
        int count = 0;
        
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    String raw = element.getText().trim();
                    String clean = normalize(raw);
                    if (clean.matches("^\\d{2,3}$")) {
                        float conf = 1.0f;
                        try {
                            conf = element.getConfidence();
                        } catch (NoSuchMethodError | Exception ignored) {}
                        
                        items.add(new OCRItem(clean, element.getBoundingBox(), conf));
                        totalConf += conf;
                        count++;
                    }
                }
            }
        }
        
        if (items.isEmpty()) return null;
        
        double avgConf = count > 0 ? (totalConf / count) : 1.0;
        
        // Sort by vertical position (y coordinate)
        Collections.sort(items, (a, b) -> Integer.compare(a.rect.top, b.rect.top));
        
        String sysVal = "";
        String diaVal = "";
        String pulseVal = "";
        
        for (OCRItem item : items) {
            double relY = (double) (item.rect.top + item.rect.bottom) / (2.0 * imageHeight);
            if (relY < 0.45) {
                if (sysVal.isEmpty()) sysVal = item.text;
            } else if (relY < 0.80) {
                if (diaVal.isEmpty()) diaVal = item.text;
            } else {
                if (pulseVal.isEmpty()) pulseVal = item.text;
            }
        }
        
        if ((sysVal.isEmpty() || diaVal.isEmpty() || pulseVal.isEmpty()) && items.size() == 3) {
            sysVal = items.get(0).text;
            diaVal = items.get(1).text;
            pulseVal = items.get(2).text;
        }
        
        try {
            if (!sysVal.isEmpty()) result.systolic = Integer.parseInt(sysVal);
            if (!diaVal.isEmpty()) result.diastolic = Integer.parseInt(diaVal);
            if (!pulseVal.isEmpty()) result.pulse = Integer.parseInt(pulseVal);
        } catch (NumberFormatException ignored) {}
        
        result.systolicConfidence = avgConf;
        result.diastolicConfidence = avgConf;
        result.pulseConfidence = avgConf;
        
        return result;
    }
    
    public static BPResult parseTesseract(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        
        BPResult result = new BPResult();
        result.source = "Tesseract OCR";
        
        String[] lines = text.split("\n");
        List<String> numbers = new ArrayList<>();
        for (String line : lines) {
            String clean = normalize(line.trim());
            if (clean.matches("^\\d{2,3}$")) {
                numbers.add(clean);
            }
        }
        
        if (numbers.size() < 2) return null;
        
        try {
            if (numbers.size() >= 3) {
                result.systolic = Integer.parseInt(numbers.get(0));
                result.diastolic = Integer.parseInt(numbers.get(1));
                result.pulse = Integer.parseInt(numbers.get(2));
            } else {
                result.systolic = Integer.parseInt(numbers.get(0));
                result.diastolic = Integer.parseInt(numbers.get(1));
            }
        } catch (NumberFormatException ignored) {}
        
        result.systolicConfidence = 0.70;
        result.diastolicConfidence = 0.70;
        result.pulseConfidence = 0.70;
        
        return result;
    }
    
    private static String normalize(String input) {
        if (input == null) return "";
        return input.replace("I", "1")
                    .replace("l", "1")
                    .replace("O", "0")
                    .replace("o", "0")
                    .replace("Q", "0")
                    .replace("S", "5")
                    .replace("s", "5")
                    .replace("Z", "2")
                    .replace("z", "2")
                    .replace("B", "8")
                    .replaceAll("[^0-9]", "");
    }
}
