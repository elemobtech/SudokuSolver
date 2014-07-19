package com.example.sudokusolver.util;

import java.util.ArrayList;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

public class ImgManipUtil {

	public static final int THRESHOLD = 50;
	
	public static final String TAG_MAT_DIMENS = "Mat dimensions";
	public static final String TAG_BMP_DIMENS = "Bitmap dimensions";
	public static final String TAG_SUBMAT_DIMENS = "Submat dimensions";
	public static final String TAG_WHITE_POINT = "White point coorinates";
	public static final String TAG_TILE_STATUS = "tile status";
	public static final String TAG_HOUGHLINES = "HoughLines info";
	public static final String TAG_ERROR_FIND_GRID = "findGridArea error";
	public static final String TAG_ERROR_FLOODFILL = "Floodfill setPixel error";

	/**
	 * converts bitmap to single channel 8 bit Mat
	 * @param bmp-- bitmap to convert
	 * @return
	 */
	public static Mat bitmapToMat(Bitmap bmp) {
		Mat mat = new Mat(bmp.getHeight(), bmp.getWidth(), CvType.CV_8UC1);
		Utils.bitmapToMat(bmp, mat);

		String matInfo = String.format("cols: %d, rows: %d", mat.cols(),
				mat.rows());
		Log.d(TAG_MAT_DIMENS, matInfo);

		return mat;
	}

	/**
	 * converts mat to RGB bitmap
	 * @param mat-- mat to convert
	 * @return
	 */
	public static Bitmap matToBitmap(Mat mat) {
		Bitmap bmp = Bitmap.createBitmap(mat.cols(), mat.rows(),
				Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(mat, bmp);

		String bmpInfo = String.format("width: %d, height %d", bmp.getWidth(),
				bmp.getHeight());
		Log.d(TAG_BMP_DIMENS, bmpInfo);

		return bmp;
	}

	/**
	 * performs OpenCV image manipulations to extract and undistort sudoku puzzle from image
	 * @return-- Mat image of fixed puzzle
	 */
	public static Mat extractSudokuGrid(Bitmap bitmap){
		//convert source bitmap to mat; use canny operation
		Mat m = bitmapToMat(bitmap);
		Imgproc.Canny(m, m, 50, 200);

		//trim external noise to localize the sudoku puzzle and stores in bmp then m2
		Bitmap bmp = findGridArea(matToBitmap(m));
		Mat m2 = bitmapToMat(bmp);
		bmp.recycle();
		Imgproc.cvtColor(m2, m2, Imgproc.COLOR_RGB2GRAY);

		//uses houghlines to find lines in the image; sort them by horizontal/vertical
		Mat lines = new Mat();
		List<double[]> horizontalLines = new ArrayList<double[]>();
		List<double[]> verticalLines = new ArrayList<double[]>();

		Imgproc.HoughLinesP(m2, lines, 1, Math.PI/180, 150);
		for(int i = 0; i < lines.cols(); i++){
			double[] line = lines.get(0, i);
			double x1 = line[0];
			double y1 = line[1];
			double x2 = line[2];
			double y2 = line[3];
			if(Math.abs(y2 - y1) < Math.abs(x2 - x1)){
				horizontalLines.add(line);
			}
			else if(Math.abs(x2 - x1) < Math.abs(y2 - y1)){
				verticalLines.add(line);
			}
			//Point start = new Point(x1, y1);
			//Point end = new Point(x2, y2);
			//Log.d("line points", x1 + "," + y1 + " " + x2 + "," + y2);
			//Core.line(m2, start, end, new Scalar(255, 255, 255), 3);
		}
		String lineInfo = String.format("horizontal: %d, vertical: %d, total: %d", horizontalLines.size(), verticalLines.size(), lines.cols());
		Log.d(TAG_HOUGHLINES, lineInfo);

		//lines for four boundaries of sudoku grid; find edges of the sudoku grid 
		double[] topLine = horizontalLines.get(0);
		double[] bottomLine = horizontalLines.get(0);
		double[] leftLine = verticalLines.get(0);
		double[] rightLine = verticalLines.get(0);

		double xMin = 1000;
		double xMax = 0;
		double yMin = 1000;
		double yMax = 0;

		for(int i = 0; i < horizontalLines.size(); i++){
			if(horizontalLines.get(i)[1] < yMin || horizontalLines.get(i)[3] < yMin){
				topLine = horizontalLines.get(i);
				yMin = horizontalLines.get(i)[1];
			}
			else if(horizontalLines.get(i)[1] > yMax || horizontalLines.get(i)[3] > yMax){
				bottomLine = horizontalLines.get(i);
				yMax = horizontalLines.get(i)[1];
			}
		}

		for(int i = 0; i < verticalLines.size(); i++){
			if(verticalLines.get(i)[0] < xMin || verticalLines.get(i)[2] < xMin){
				leftLine = verticalLines.get(i);
				xMin = verticalLines.get(i)[0];
			}
			else if(verticalLines.get(i)[0] > xMax || verticalLines.get(i)[2] > xMax){
				rightLine = verticalLines.get(i);
				xMax = verticalLines.get(i)[0];
			}
		}

		//obtain four corners of sudoku grid and apply perspective transform to undistort image
		Point topLeft = findCorner(topLine, leftLine);
		Point bottomLeft = findCorner(bottomLine, leftLine);
		Point topRight = findCorner(topLine, rightLine);
		Point bottomRight = findCorner(bottomLine, rightLine);

		Mat result = fixPerspective(topLeft, topRight, bottomLeft, bottomRight, m2);
		return result;
	}
	
	/**
	 * erodes mat
	 **/
	public static void erodeMat(Mat mat, int factor) {
		// Mat manip = bitmapToMat(fixedBmp);
		Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_CROSS,
				new Size(factor, factor));
		Imgproc.erode(mat, mat, kernel);
		// fixedBmp = matToBitmap(manip);
	}

	/**
	 * dilates mat
	 * @param mat-- Mat containing image of sudoku grid
	 */
	public static void dilateMat(Mat mat, int factor) {
		Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_CROSS,
				new Size(factor, factor));
		Imgproc.dilate(mat, mat, kernel);
	}
	
	/**
	 * returns undistorted version of Mat using transformation from OpenCV library
	 * @param upLeft-- top left corner coordinates
	 * @param upRight-- top right corner coordinates
	 * @param downLeft-- bottom left corner coordinates
	 * @param downRight-- bottom right corner coordinates
	 * @param source-- source Mat
	 * @return
	 */
	private static Mat fixPerspective(Point upLeft, Point upRight, Point downLeft, Point downRight, Mat source){
		List<Point> src = new ArrayList<Point>();
		List<Point> dest = new ArrayList<Point>();
		Mat result = new Mat(source.size(), source.type());

		//add the four corners to List
		src.add(upLeft);
		src.add(upRight);
		src.add(downLeft);
		src.add(downRight);

		Point topLeft = new Point(0,0);
		Point topRight = new Point(source.cols(), 0);
		Point bottomLeft = new Point(0, source.rows());
		Point bottomRight = new Point(source.cols(), source.rows());

		//add destination corners to List (adjusted for rotation)
		dest.add(topRight);
		dest.add(bottomRight);
		dest.add(topLeft);
		dest.add(bottomLeft);

		//convert List to Mat
		Mat srcM = Converters.vector_Point2f_to_Mat(src);
		Mat destM = Converters.vector_Point2f_to_Mat(dest);

		//apply perspective transform using 3x3 matrix
		Mat perspectiveTrans = new Mat(3, 3, CvType.CV_32FC1);
		perspectiveTrans = Imgproc.getPerspectiveTransform(srcM, destM);
		Imgproc.warpPerspective(source, result, perspectiveTrans, result.size());

		return result;
	}

	/**
	 * returns point of intersection between two lines
	 * @param l1
	 * @param l2
	 * @return
	 */
	private static Point findCorner(double[]l1, double[]l2){
		double x1 = l1[0];
		double y1 = l1[1];
		double x2 = l1[2];
		double y2 = l1[3];
		double x3 = l2[0];
		double y3 = l2[1];
		double x4 = l2[2];
		double y4 = l2[3];

		double d = (x1-x2) * (y3-y4) - (y1-y2) * (x3-x4);
		double x = ((x1*y2 - y1*x2) * (x3-x4) - (x1-x2) * (x3*y4 - y3*x4)) / d;
		double y = ((x1*y2 - y1*x2) * (y3-y4) - (y1-y2) * (x3*y4 - y3*x4)) / d;

		Point p = new Point(x,y);
		return p;
	}

	/**
	 * trims the bitmap to contain only the sudoku grid
	 * @param bmp
	 * @return
	 */
	private static Bitmap findGridArea(Bitmap bmp){
		//find the four general edges of the sudoku grid; 5 pixel buffer region 
		//in case any part of the grid gets cut off
		int left = findBorders(1, bmp) - 5;
		int right = findBorders(2, bmp) + 5;
		int top = findBorders(3, bmp) - 5;
		int bot = findBorders(4, bmp) + 5;

		//if sides differ by more than threshold amount of pixels, then 
		//throw error since area is not square
		if(Math.abs(right - left - (bot - top)) > THRESHOLD){
			Log.d(TAG_ERROR_FIND_GRID, "not square");
		}

		Bitmap subBmp = Bitmap.createBitmap(bmp, left, top, right-left, bot-top);

		String subMatInfo = String.format("left: %d, right: %d, top: %d, bot: %d", left, right, top, bot);
		Log.d(TAG_SUBMAT_DIMENS, subMatInfo);

		return subBmp;
	}

	/**
	 * find the borders of the sudoku grid; the check for white line begins 1/3 
	 * away from the centre of the image
	 * @param side
	 * @param bmp
	 * @return
	 */
	private static int findBorders(int side, Bitmap bmp) {
		switch (side) {
		// left
		case 1:
			for(int i = bmp.getWidth()/3; i > 0; i--){
				if(isBorderHeight(i, bmp)) return i;
			}
			break;
		// right
		case 2:
			for(int i = 2*bmp.getWidth()/3; i < bmp.getWidth(); i++){
				if(isBorderHeight(i, bmp)) return i;
			}
			break;
		// top
		case 3:
			for(int i = bmp.getHeight()/3; i > 0; i--){
				if(isBorderWidth(i,bmp)) return i;
			}
			break;
		// bottom
		case 4:
			for(int i = 2*bmp.getHeight()/3; i < bmp.getHeight(); i++){
				if(isBorderWidth(i,bmp)) return i;
			}
			break;
		}

		//returns negative border if not found
		Log.d(TAG_ERROR_FIND_GRID, "boundary not found: side " + side);
		return -6;
	}

	/**
	 * checks if horizontal line(width) is outside the sudoku grid
	 * @param height -- y coordinate
	 * @param bmp -- bitmap containing image
	 * @return
	 */
	private static boolean isBorderWidth(int height, Bitmap bmp) {
		for (int i = 2*bmp.getWidth()/5; i < 3*bmp.getWidth()/5; i++) {
			// if pixel is black
			if(bmp.getPixel(i, height) == Color.WHITE){
				return false;
			}
		}
		return true;
	}

	/**
	 * checks if vertical line(height) is outside the sudoku grid
	 * @param width -- x coordinate
	 * @param bmp -- bitmap containing image
	 * @return
	 */
	private static boolean isBorderHeight(int width, Bitmap bmp) {
		for (int i = 2*bmp.getHeight()/5; i < 3*bmp.getHeight()/5; i++) {
			//if pixel is black
			if(bmp.getPixel(width, i) == Color.WHITE){
				return false;
			}
		}
		return true;
	}

	/**
	 * debugging method that draws white line to mat
	 * @param line
	 * @param m
	 */
	public static void drawLine(double[] line, Mat m){
		double x1 = line[0];
		double y1 = line[1];
		double x2 = line[2];
		double y2 = line[3];

		Point start = new Point(x1, y1);
		Point end = new Point(x2, y2);
		Log.d("boundaries", x1 + "," + y1 + " " + x2 + "," + y2);
		Core.line(m, start, end, new Scalar(255, 255, 255), 3);
	}
}
