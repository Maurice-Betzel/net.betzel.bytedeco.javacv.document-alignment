package net.betzel.bytedeco.javacv.document;

/*
 * Copyright (C) 2016 Maurice Betzel
 *
 * Licensed either under the Apache License, Version 2.0, or (at your option)
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation (subject to the "Classpath" exception),
 * either version 2, or any later version (collectively, the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     http://www.gnu.org/licenses/
 *     http://www.gnu.org/software/classpath/license.html
 *
 * or as provided in the LICENSE.txt file that accompanied this code.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.bytedeco.javacpp.indexer.FloatArrayIndexer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.Indexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.imwrite;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_imgproc.minAreaRect;

/**
 * Document Alignment with JavaCV
 * https://github.com/bytedeco/javacv
 *
 * @author Maurice Betzel
 *         <p>
 *         /**
 *         Created by Maurice on 22.03.2016.
 */
public class Alignment {

    public static void main(String[] args) {
        try {
            new Alignment().execute(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void execute(String[] args) throws Exception {
        // If no params provided, compute the defaut image
        BufferedImage bufferedImage = args.length >= 1 ? ImageIO.read(new File(args[0])) : ImageIO.read(this.getClass().getResourceAsStream("/images/A4.jpg"));
        System.out.println("Image type: " + bufferedImage.getType());
        // Convert BufferedImage to Mat
        Mat matrix = new OpenCVFrameConverter.ToMat().convert(new Java2DFrameConverter().convert(bufferedImage));

        Size original = matrix.size();
        resize(matrix, matrix, new Size(original.width() / 4, original.height() / 4));
        showMatrix("Original", matrix);

        Mat bgr = new Mat();
        Mat hsv = new Mat();
        Mat col = new Mat();
        cvtColor(matrix, bgr, COLOR_RGB2BGR);
        cvtColor(bgr, hsv, COLOR_BGR2HSV);
        showMatrix("HSV color space", hsv);

        inRange(hsv, new Mat(new double[]{0, 0, 255, 0}), new Mat(new double[]{10, 255, 255, 0}), col);
        showMatrix("Selected color", col);

        // Find contours
        Mat hierarchy = new Mat();
        MatVector contours = new MatVector();
        findContours(col, contours, hierarchy, RETR_TREE, CHAIN_APPROX_SIMPLE);
        int contourCount = (int) contours.size();
        System.out.println("Countour count " + contourCount);

        for (int i = 0; i < contourCount; ++i) {
            // Calculate the area of each contour
            Mat contour = contours.get(i);
            double area = contourArea(contour);
            // Ignore contours that are too small or too large
            // TODO make it relative to total image, requirement: look for A5 in A4
            if (area > 65536 && area < 131072) {
                //drawContours(bgr, contours, i, new Scalar(255, 0, 255, 0));
                double epsilon = 0.02 * arcLength(contours.get(i), true);
                Mat approx = new Mat();
                approxPolyDP(contours.get(i), approx, epsilon, true);
//                List<Point> point2fList = toPointList(approx);
//                for(Point point : point2fList) {
//                    System.out.println(point.x() + " " + point.y());
//                }
                Rect boundingRect = boundingRect(approx);
                rectangle(bgr, boundingRect.tl(), boundingRect.br(), new Scalar(255, 0, 255, 0));
                RotatedRect rotatedRect = minAreaRect(approx);
                float angle = rotatedRect.angle();
                Size2f rect_size = rotatedRect.size();
                // rotate to landscape
                Size newSize = new Size((int) rect_size.width(), (int) rect_size.height());
                if(rect_size.width() < rect_size.height()) {
                    angle += 90.0;
                    newSize = new Size(newSize.height(), newSize.width());
                }
                Mat rotationMatrix = getRotationMatrix2D(rotatedRect.center(), angle, 1.0);
                Mat rotated = new Mat(matrix.size(), matrix.type());
                warpAffine(matrix, rotated, rotationMatrix, matrix.size());
                showMatrix("Rotated", rotated);
                Mat patch = new Mat();
                //newSize.width(newSize.width() + ((newSize.width() / 100) * 24));
                getRectSubPix(rotated, newSize, rotatedRect.center(), patch);
                showMatrix("Cropped", patch);
                //cvtColor(patch, patch, COLOR_BGR2GRAY);
                Rect roi = new Rect(10, 10, patch.cols(), patch.rows());
                Mat mask = matrix.apply(roi);
                patch.copyTo(mask);
                imwrite("result.png", matrix);
                showMatrix("Result", matrix);
            }
        }
        showMatrix("Contours", bgr);

        Mat gray = new Mat();
        cvtColor(matrix, gray, COLOR_BGR2GRAY);
        //showMatrix(gray);
    }

    private List<Point> toPointList(Mat matrix) {
        if(matrix.checkVector(2) < 0 ) {
            throw new IllegalArgumentException("Expecting a vector Mat");
        }
        IntIndexer indexer = matrix.createIndexer();
        int size = (int) matrix.total();
        List<Point> list = new ArrayList(size);
        for (int i = 0; i < size; i++) {
            list.add(new Point(indexer.get(0, i, 0), indexer.get(0, i, 1)));
        }
        return list;
    }


    private void showMatrix(String title, Mat matrix) {
        CanvasFrame canvas = new CanvasFrame(title, 1);
        canvas.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
        canvas.setCanvasSize(matrix.size().width(), matrix.size().height());
        OpenCVFrameConverter converter = new OpenCVFrameConverter.ToIplImage();
        canvas.showImage(converter.convert(matrix));
    }

    public static void printMat(Mat mat) {
        System.out.println("Channels: " + mat.channels());
        System.out.println("Rows: " + mat.rows());
        System.out.println("Cols: " + mat.cols());
        System.out.println("Type: " + mat.type());
        System.out.println("Dims: " + mat.dims());
        System.out.println("Depth: " + mat.depth());
    }

}
