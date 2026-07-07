package com.nived.whealth;

public class ConfidenceCalculator {
    
    public static double calculate(MeasurementParser.BPResult result) {
        if (result == null) return 0.0;
        
        int populatedCount = 0;
        if (result.systolic > 0) populatedCount++;
        if (result.diastolic > 0) populatedCount++;
        if (result.pulse > 0) populatedCount++;
        
        if (populatedCount == 0) return 0.0;
        
        double base = 0.5;
        if ("Seven Segment Recognition".equals(result.source)) {
            base = 1.0;
        } else if ("Google ML Kit OCR".equals(result.source)) {
            base = (result.systolicConfidence + result.diastolicConfidence + result.pulseConfidence) / 3.0;
        } else if ("Tesseract OCR".equals(result.source)) {
            base = 0.70;
        }
        
        double multiplier = 1.0;
        if (populatedCount == 1) {
            multiplier = 0.4;
        } else if (populatedCount == 2) {
            multiplier = 0.8;
        }
        
        boolean rangeOk = true;
        if (result.systolic > 0 && (result.systolic < 70 || result.systolic > 240)) rangeOk = false;
        if (result.diastolic > 0 && (result.diastolic < 40 || result.diastolic > 150)) rangeOk = false;
        if (result.pulse > 0 && (result.pulse < 35 || result.pulse > 200)) rangeOk = false;
        
        double score = base * multiplier;
        if (!rangeOk) {
            score *= 0.5;
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }
}
