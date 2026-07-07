package com.nived.whealth;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

public class LCDDetector {
    
    public static MatOfPoint2f detectLCD(Mat src) {
        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(gray, blurred, new Size(5, 5), 0);
        
        Mat thresh = new Mat();
        Imgproc.adaptiveThreshold(blurred, thresh, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 2);
        
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        
        MatOfPoint bestContour = null;
        double maxArea = 0;
        double imgArea = src.cols() * src.rows();
        Point imgCenter = new Point((double) src.cols() / 2, (double) src.rows() / 2);
        
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            // Reject rectangles that are too small or too large
            if (area < imgArea * 0.04 || area > imgArea * 0.92) {
                continue;
            }
            
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double peri = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approx = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true);
            
            // Check if quadrilateral
            if (approx.total() == 4) {
                Rect rect = Imgproc.boundingRect(contour);
                double aspectRatio = (double) rect.width / rect.height;
                // LCD screen proportions check
                if (aspectRatio >= 0.35 && aspectRatio <= 2.8) {
                    if (area > maxArea) {
                        maxArea = area;
                        bestContour = contour;
                    }
                }
            }
        }
        
        gray.release();
        blurred.release();
        thresh.release();
        hierarchy.release();
        
        if (bestContour != null) {
            MatOfPoint2f res = new MatOfPoint2f(bestContour.toArray());
            MatOfPoint2f approx = new MatOfPoint2f();
            double peri = Imgproc.arcLength(res, true);
            Imgproc.approxPolyDP(res, approx, 0.02 * peri, true);
            return approx;
        }
        
        return null;
    }
}
