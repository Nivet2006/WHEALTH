package com.nived.whealth;

import org.opencv.core.*;
import java.util.List;

public class SevenSegmentRecognizer {
    
    private static final double SEGMENT_THRESHOLD = 0.22;
    
    public static String recognizeRow(Mat binaryLcd, List<DigitContourDetector.DigitRect> digits) {
        if (digits == null || digits.isEmpty()) return null;
        
        StringBuilder sb = new StringBuilder();
        for (DigitContourDetector.DigitRect digit : digits) {
            Mat digitMat = new Mat(binaryLcd, digit.rect);
            int digitVal = recognizeDigit(digitMat);
            digitMat.release();
            
            if (digitVal == -1) {
                return null; // If any digit fails, SevenSegment recognition for row fails
            }
            sb.append(digitVal);
        }
        
        return sb.toString();
    }
    
    private static int recognizeDigit(Mat digitMat) {
        // Evaluate ON/OFF for segments A, B, C, D, E, F, G
        boolean a = getSegmentDensity(digitMat, 0.20, 0.80, 0.00, 0.20) > SEGMENT_THRESHOLD;
        boolean b = getSegmentDensity(digitMat, 0.72, 1.00, 0.10, 0.50) > SEGMENT_THRESHOLD;
        boolean c = getSegmentDensity(digitMat, 0.72, 1.00, 0.50, 0.90) > SEGMENT_THRESHOLD;
        boolean d = getSegmentDensity(digitMat, 0.20, 0.80, 0.80, 1.00) > SEGMENT_THRESHOLD;
        boolean e = getSegmentDensity(digitMat, 0.00, 0.28, 0.50, 0.90) > SEGMENT_THRESHOLD;
        boolean f = getSegmentDensity(digitMat, 0.00, 0.28, 0.10, 0.50) > SEGMENT_THRESHOLD;
        boolean g = getSegmentDensity(digitMat, 0.20, 0.80, 0.40, 0.60) > SEGMENT_THRESHOLD;
        
        StringBuilder pattern = new StringBuilder();
        pattern.append(a ? "1" : "0");
        pattern.append(b ? "1" : "0");
        pattern.append(c ? "1" : "0");
        pattern.append(d ? "1" : "0");
        pattern.append(e ? "1" : "0");
        pattern.append(f ? "1" : "0");
        pattern.append(g ? "1" : "0");
        
        return decodePattern(pattern.toString());
    }
    
    private static double getSegmentDensity(Mat digitMat, double relXStart, double relXEnd, double relYStart, double relYEnd) {
        int w = digitMat.cols();
        int h = digitMat.rows();
        
        int x1 = (int) (relXStart * w);
        int x2 = (int) (relXEnd * w);
        int y1 = (int) (relYStart * h);
        int y2 = (int) (relYEnd * h);
        
        x1 = Math.max(0, Math.min(x1, w - 1));
        x2 = Math.max(0, Math.min(x2, w - 1));
        y1 = Math.max(0, Math.min(y1, h - 1));
        y2 = Math.max(0, Math.min(y2, h - 1));
        
        if (x2 <= x1 || y2 <= y1) return 0.0;
        
        int whiteCount = 0;
        int totalCount = (x2 - x1) * (y2 - y1);
        
        for (int y = y1; y < y2; y++) {
            for (int x = x1; x < x2; x++) {
                double[] pixel = digitMat.get(y, x);
                if (pixel != null && pixel[0] > 128) {
                    whiteCount++;
                }
            }
        }
        
        return (double) whiteCount / totalCount;
    }
    
    private static int decodePattern(String pattern) {
        switch (pattern) {
            case "1111110": return 0;
            case "0110000": 
            case "0110001": 
            case "0010000": 
                return 1;
            case "1101101": return 2;
            case "1111001": return 3;
            case "0110011": return 4;
            case "1011011": return 5;
            case "1011111": 
            case "1011101": 
            case "0011111": 
                return 6;
            case "1110000": 
            case "1110010": 
                return 7;
            case "1111111": return 8;
            case "1111011": 
            case "1110011": 
                return 9;
        }
        return -1;
    }
}
