package com.nived.whealth;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DigitContourDetector {
    
    public static class DigitRect {
        public final Rect rect;
        public final MatOfPoint contour;
        
        public DigitRect(Rect rect, MatOfPoint contour) {
            this.rect = rect;
            this.contour = contour;
        }
    }
    
    public static List<List<DigitRect>> detectAndGroup(Mat binaryLcd) {
        int w = binaryLcd.cols();
        int h = binaryLcd.rows();
        
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binaryLcd, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        
        List<DigitRect> validDigits = new ArrayList<>();
        
        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            
            double relHeight = (double) rect.height / h;
            double relWidth = (double) rect.width / w;
            double aspectRatio = (double) rect.width / rect.height;
            
            // Single digit filter criteria (taller than wide, proportional size)
            if (relHeight >= 0.08 && relHeight <= 0.40 &&
                relWidth >= 0.015 && relWidth <= 0.25 &&
                aspectRatio >= 0.12 && aspectRatio <= 1.1) {
                validDigits.add(new DigitRect(rect, contour));
            }
        }
        
        hierarchy.release();
        
        // Group into three rows: SYS, DIA, Pulse
        List<DigitRect> sysRow = new ArrayList<>();
        List<DigitRect> diaRow = new ArrayList<>();
        List<DigitRect> pulseRow = new ArrayList<>();
        
        for (DigitRect digit : validDigits) {
            double centerY = digit.rect.y + (double) digit.rect.height / 2;
            double relY = centerY / h;
            
            if (relY < 0.45) {
                sysRow.add(digit);
            } else if (relY < 0.80) {
                diaRow.add(digit);
            } else {
                pulseRow.add(digit);
            }
        }
        
        // Sort each row left-to-right (x-coordinate)
        Collections.sort(sysRow, (a, b) -> Integer.compare(a.rect.x, b.rect.x));
        Collections.sort(diaRow, (a, b) -> Integer.compare(a.rect.x, b.rect.x));
        Collections.sort(pulseRow, (a, b) -> Integer.compare(a.rect.x, b.rect.x));
        
        List<List<DigitRect>> rows = new ArrayList<>();
        rows.add(sysRow);
        rows.add(diaRow);
        rows.add(pulseRow);
        
        return rows;
    }
}
