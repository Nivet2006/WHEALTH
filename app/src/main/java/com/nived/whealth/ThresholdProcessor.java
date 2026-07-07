package com.nived.whealth;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public class ThresholdProcessor {
    
    public static Mat process(Mat src) {
        Mat gray = new Mat();
        if (src.channels() == 3) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        } else {
            src.copyTo(gray);
        }
        
        // CLAHE Contrast Enhancement
        Mat claheMat = new Mat();
        org.opencv.imgproc.CLAHE clahe = Imgproc.createCLAHE(3.0, new Size(8, 8));
        clahe.apply(gray, claheMat);
        
        // Median Blur
        Mat blurred = new Mat();
        Imgproc.medianBlur(claheMat, blurred, 3);
        
        // Adaptive Thresholding to binary (producing white digits on black background for contouring)
        Mat thresh = new Mat();
        Imgproc.adaptiveThreshold(blurred, thresh, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 15, 6);
        
        // Morphological Closing
        Mat closed = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
        Imgproc.morphologyEx(thresh, closed, Imgproc.MORPH_CLOSE, kernel);
        
        gray.release();
        claheMat.release();
        blurred.release();
        thresh.release();
        kernel.release();
        
        return closed;
    }
}
