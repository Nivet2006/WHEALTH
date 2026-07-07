package com.nived.whealth;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.Arrays;

public class PerspectiveCorrector {
    
    public static Mat warp(Mat src, MatOfPoint2f corners) {
        if (corners == null || corners.total() != 4) {
            // Fallback: if no corners detected, crop central 70% width and 30% height (Omron display overlay region)
            int w = src.cols();
            int h = src.rows();
            int cropX = (int) (w * 0.15);
            int cropY = (int) (h * 0.35);
            int cropW = (int) (w * 0.70);
            int cropH = (int) (h * 0.30);
            
            cropX = Math.max(0, cropX);
            cropY = Math.max(0, cropY);
            if (cropX + cropW > w) cropW = w - cropX;
            if (cropY + cropH > h) cropH = h - cropY;
            
            return new Mat(src, new Rect(cropX, cropY, cropW, cropH));
        }
        
        Point[] pts = corners.toArray();
        Point[] sorted = sortCorners(pts);
        
        Point tl = sorted[0];
        Point tr = sorted[1];
        Point br = sorted[2];
        Point bl = sorted[3];
        
        double widthA = Math.sqrt(Math.pow(br.x - bl.x, 2) + Math.pow(br.y - bl.y, 2));
        double widthB = Math.sqrt(Math.pow(tr.x - tl.x, 2) + Math.pow(tr.y - tl.y, 2));
        int maxWidth = (int) Math.max(widthA, widthB);
        
        double heightA = Math.sqrt(Math.pow(br.x - tr.x, 2) + Math.pow(br.y - tr.y, 2));
        double heightB = Math.sqrt(Math.pow(bl.x - tl.x, 2) + Math.pow(bl.y - tl.y, 2));
        int maxHeight = (int) Math.max(heightA, heightB);
        
        // Guard against zero size
        if (maxWidth <= 0) maxWidth = 100;
        if (maxHeight <= 0) maxHeight = 100;
        
        MatOfPoint2f srcCorners = new MatOfPoint2f(tl, tr, br, bl);
        MatOfPoint2f dstCorners = new MatOfPoint2f(
            new Point(0, 0),
            new Point(maxWidth - 1, 0),
            new Point(maxWidth - 1, maxHeight - 1),
            new Point(0, maxHeight - 1)
        );
        
        Mat dest = new Mat();
        Mat transform = Imgproc.getPerspectiveTransform(srcCorners, dstCorners);
        Imgproc.warpPerspective(src, dest, transform, new Size(maxWidth, maxHeight));
        
        srcCorners.release();
        dstCorners.release();
        transform.release();
        
        return dest;
    }
    
    private static Point[] sortCorners(Point[] pts) {
        Point[] sorted = new Point[4];
        
        // Sum sum of coordinates to find TL and BR
        double minSum = Double.MAX_VALUE;
        double maxSum = -Double.MAX_VALUE;
        int tlIdx = 0;
        int brIdx = 0;
        
        for (int i = 0; i < 4; i++) {
            double sum = pts[i].x + pts[i].y;
            if (sum < minSum) {
                minSum = sum;
                tlIdx = i;
            }
            if (sum > maxSum) {
                maxSum = sum;
                brIdx = i;
            }
        }
        
        sorted[0] = pts[tlIdx]; // Top-Left
        sorted[2] = pts[brIdx]; // Bottom-Right
        
        // Find top-right and bottom-left from the remaining two points
        int trIdx = -1;
        int blIdx = -1;
        for (int i = 0; i < 4; i++) {
            if (i != tlIdx && i != brIdx) {
                if (trIdx == -1) {
                    trIdx = i;
                } else {
                    blIdx = i;
                }
            }
        }
        
        // Compare difference (y - x)
        // Bottom-Left has a larger difference (larger y, smaller x) than Top-Right
        double diffA = pts[trIdx].y - pts[trIdx].x;
        double diffB = pts[blIdx].y - pts[blIdx].x;
        
        if (diffA < diffB) {
            sorted[1] = pts[trIdx]; // Top-Right
            sorted[3] = pts[blIdx]; // Bottom-Left
        } else {
            sorted[1] = pts[blIdx];
            sorted[3] = pts[trIdx];
        }
        
        return sorted;
    }
}
