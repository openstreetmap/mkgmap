/*
 * Copyright (C) 2017.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.util;

import java.util.ArrayList;
//import java.util.Arrays;
import java.util.List;
import java.util.Collections;

import uk.me.parabola.util.Java2DConverter;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.filters.ShapeMergeFilter;
import uk.me.parabola.mkgmap.general.MapShape;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Test polygon splitting
 * @author Ticker Berkin
 *
 */
public class ShapeSplitterTest {

    @Test
    public void test1_SimpleSplit() {
	// simple diamond shape
	int[][] os = { {1,1}, {5,3}, {7,7}, {3,5} };
	splitTester st = new splitTester(os);
	
	st.cutPosn(3, false);
	int[][] f1 = { {1,1}, {3,2}, {3,5} };
	st.addExpected(f1);
	int[][] f2 = { {3,2}, {5,3}, {7,7}, {3,5} };
	st.addExpected(f2);
	st.runSplitShape();
	st.runClipToBounds();
	st.runJava2D();	
//	st.runSuthHodg();

	st.cutPosn(5, true);
	int[][] t1 = { {1,1}, {5,3}, {6,5}, {3,5} };
	st.addExpected(t1);
	int[][] t2 = { {6,5}, {7,7}, {3,5} };
	st.addExpected(t2);
	st.runSplitShape();
	st.runClipToBounds();
	st.runJava2D();
//	st.runSuthHodg();
    }

    @Test
    public void test2_cutToHole() {
	// shape has hole with cut to get to it
	int[][] os = { {1,1}, {3,1}, {3,3}, {2,3}, {2,4}, {4,4}, {4,3}, {3,3}, {3,1}, {5,1}, {5,5}, {1,5} };
	splitTester st = new splitTester(os);

	// cut across existing cut
	st.cutPosn(2, true);
	int[][] t1 = { {1,1}, {3,1}, {3,2}, {1,2} };
	st.addExpected(t1);
	int[][] t2 = { {3,1}, {5,1}, {5,2}, {3,2} };
	st.addExpected(t2);
	int[][] t3 = { {1,2}, {3,2}, {3,3}, {2,3}, {2,4}, {4,4}, {4,3}, {3,3}, {3,2}, {5,2}, {5,5}, {1,5} };
	st.addExpected(t3);
	st.runSplitShape();
	st.runClipToBounds();
//!!!	st.runJava2D();  !!! java2D can't handle this
//	st.runSuthHodg();
	
	// cut along cut and through other side of hole
	st.cutPosn(3, false);
	int[][] f1 = { {1,1}, {3,1}, {3,3}, {2,3}, {2,4}, {3,4}, {3,5}, {1,5} };
	st.addExpected(f1);
	int[][] f2 = { {3,1}, {5,1}, {5,5}, {3,5}, {3,4}, {4,4}, {4,3}, {3,3} };
	st.addExpected(f2);
	st.runSplitShape();
	st.runClipToBounds();
	st.runJava2D();
//	st.runSuthHodg();
    }

    @Test
    public void test3_cutSpiral() {
	// Imagine shape is rope, folded and the two loose ends on inside of a spiral
	int[][] os = {
	    // start: lower clockwise out
	    {7,10}, {6,10}, {6,6}, {10,6}, {10,14}, {2,14}, {2,2}, {14,2},
	    // fold
	    {14,14}, {13,14}, {13,3},
	    // lower anti-clockwise in
	    {3,3}, {3,13}, {9,13}, {9,7}, {7,7},
	    // lower clockwise out
	    {7,8}, {8,8}, {8,12}, {4,12}, {4,4}, {12,4}, {12,15},
	    // fold
	    {15,15}, {15,1},
	    // lower anti-clockwise in :end
	    {1,1}, {1,15}, {11,15}, {11,5}, {5,5}, {5,11}, {7,11}
	};
	splitTester st = new splitTester(os);
	
	st.cutPosn(9, true);
	// left hand going up
	int[][] l1 = { {1,9}, {1,1}, {15,1}, {15,9}, {14,9}, {14,2}, {2,2}, {2,9} };
	st.addExpected(l1);
	int[][] l2 = { {3,9}, {3,3}, {13,3}, {13,9}, {12,9}, {12,4}, {4,4}, {4,9} };
	st.addExpected(l2);
	int[][] l3 = { {5,9}, {5,5}, {11,5}, {11,9}, {10,9}, {10,6}, {6,6}, {6,9} };
	st.addExpected(l3);
	int[][] l4 = { {8,9}, {8,8}, {7,8}, {7,7}, {9,7}, {9,9} };
	st.addExpected(l4);
	// right hand going up
	int[][] r1 = { {1,9}, {1,15}, {11,15}, {11,9}, {10,9}, {10,14}, {2,14}, {2,9} };
	st.addExpected(r1);
	int[][] r2 = { {3,9}, {3,13}, {9,13}, {9,9}, {8,9}, {8,12}, {4,12}, {4,9} };
	st.addExpected(r2);
	int[][] r3 = { {5,9}, {5,11}, {7,11}, {7,10}, {6,10}, {6,9} };
	st.addExpected(r3);
	int[][] r4 = { {12,9}, {12,15}, {15,15}, {15,9}, {14,9}, {14,14}, {13,14}, {13,9} };
	st.addExpected(r4);
	st.runSplitShape();
	st.runClipToBounds();
	st.runJava2D();
//	st.runSuthHodg();
    }

    @Test
    public void test4_cutFlash() {
	// a complicated shape that needs to be drawn on graph paper to understand
	int[][] os = {
	    {20,18}, {15,18}, {6,9}, {6,10}, {4,8}, {4,18},
	    {1,18}, {1,1}, {20,1}, {20,10},
	    {12,2}, {12,10}, {11,9}, {11,10}, {9,8}, {9,10}, {2,3},
	    {2,10}, {3,11}, {3,5}, {13,15}, {13,7}, {16,10}, {16,8}, {18,10}, {18,9}, {20,11}
	};
	splitTester st = new splitTester(os);
	
	st.cutPosn(9, true);
	// left hand going up
	int[][] l1 = { {1,9}, {1,1}, {20,1}, {20,9}, {19,9}, {12,2}, {12,9}, {10,9}, {9,8}, {9,9}, {8,9}, {2,3}, {2,9} };
	st.addExpected(l1);
	int[][] l2 = { {3,9}, {3,5}, {7,9}, {5,9}, {4,8}, {4,9} };
	st.addExpected(l2);
	int[][] l3 = { {13,9}, {13,7}, {15,9} };
	st.addExpected(l3);
	int[][] l4 = { {16,9}, {16,8}, {17,9} };
	st.addExpected(l4);
	// right hand going up
	int[][] r1 = { {1,9}, {1,18}, {4,18}, {4,9}, {3,9}, {3,11}, {2,10}, {2,9} };
	st.addExpected(r1);
	int[][] r2 = { {5,9}, {6,10}, {6,9} };
	st.addExpected(r2);
	int[][] r3 = { {6,9}, {15,18}, {20,18}, {20,11}, {18,9}, {18,10}, {17,9}, {16,9}, {16,10}, {15,9}, {13,9}, {13,15}, {7,9} };
	st.addExpected(r3);
	int[][] r4 = { {8,9}, {9,10}, {9,9} };
	st.addExpected(r4);
	int[][] r5 = { {10,9}, {11,10}, {11,9} };
	st.addExpected(r5);
	int[][] r6 = { {11,9}, {12,10}, {12,9} };
	st.addExpected(r6);
	int[][] r7 = { {19,9}, {20,10}, {20,9} };
	st.addExpected(r7);
	st.runSplitShape();
	st.runClipToBounds();
	st.runJava2D();
//	st.runSuthHodg();

	st.clipBounds(1, 1, 20, 18); // this full area
	st.addExpected(os); // original shape
	st.runClipToBounds();
	st.runJava2D();
//	st.runSuthHodg();

	st.clipBounds(13, 10, 19, 16); // a solid bit
	int[][] s1 = { {13,10}, {13,16}, {19,16}, {19,10} };
	st.addExpected(s1);
	st.runClipToBounds();
	st.runJava2D();
//	st.runSuthHodg();
	
	st.clipBounds(9, 10, 13, 11); // a hole
	st.runClipToBounds();
	st.runJava2D();
//	st.runSuthHodg();
    }

    @Test
    public void test4_clipTest() {
	// shape to defeat naive sutherland-hodge implementation
	int[][] os = {
	    {2,1}, {10,1}, {10,10}, {1,10}, {1,2},
	    {2,3},
	    {2,5}, {4,5}, {4,6}, {2,6}, {2,9},
	    {5,9}, {5,7}, {6,7}, {6,9}, {9,9},
	    {9,6}, {7,6}, {7,5}, {9,5}, {9,2},
	    {6,2}, {6,4}, {5,4}, {5,2}, {3,2}
	};
	splitTester st = new splitTester(os);
	st.clipBounds(3, 3, 8, 8);

	int[][] s1 = { {6,3}, {6,4}, {5,4}, {5,3} };
	st.addExpected(s1);
	int[][] s2 = { {7,5}, {8,5}, {8,6}, {7,6} };
	st.addExpected(s2);
	int[][] s3 = { {5,7}, {6,7}, {6,8}, {5,8} };
	st.addExpected(s3);
	int[][] s4 = { {3,5}, {4,5}, {4,6}, {3,6} };
	st.addExpected(s4);
	st.runClipToBounds();
	st.runJava2D();
//	st.runSuthHodg();
    }


    private class splitTester {

	List<Coord> origShape;
	long origArea;
	
	List<List<Coord>> expectedShapes, resultShapes;
	long totalArea;

        boolean dividingInTwo;
	int dividingLine;
	boolean isLongitude;
	Area fstBounds, lstBounds;

	String algorithm;

	List<Coord> makeShape(int[][] lowPrecPoints) {
	    List<Coord> points = new ArrayList<>();
	    for (int [] intPair : lowPrecPoints)
		points.add(new Coord(intPair[0], intPair[1]));
	    points.add(points.get(0));
	    return points;
	}

	splitTester(int[][] lowPrecPoints) {
	    origShape = makeShape(lowPrecPoints);
	    origArea = ShapeMergeFilter.calcAreaSizeTestVal(origShape);
	}

	void cutPosn(int dividingLine, boolean isLongitude) {
	    dividingInTwo = true;
	    this.dividingLine = dividingLine;
	    this.isLongitude = isLongitude;
	    expectedShapes = new ArrayList<>();
	    totalArea = 0;
	    // make Area bounding boxes for some of the algorithms
	    MapShape tempShape = new MapShape();
	    tempShape.setPoints(origShape);
	    Area bounds = tempShape.getBounds();
	    if (isLongitude) {
		fstBounds = new Area(bounds.getMinLat(),
				     bounds.getMinLong(),
				     bounds.getMaxLat(),
				     dividingLine);
		lstBounds = new Area(bounds.getMinLat(),
				     dividingLine,
				     bounds.getMaxLat(),
				     bounds.getMaxLong());
	    } else {
		fstBounds = new Area(bounds.getMinLat(),
				     bounds.getMinLong(),
				     dividingLine,
				     bounds.getMaxLong());
		lstBounds = new Area(dividingLine,
				     bounds.getMinLong(),
				     bounds.getMaxLat(),
				     bounds.getMaxLong());
	    }
	}

	void clipBounds(int minLat, int minLong, int maxLat, int maxLong) {
	    dividingInTwo = false;
	    expectedShapes = new ArrayList<>();
	    totalArea = 0;
	    fstBounds = new Area(minLat, minLong, maxLat, maxLong);
	}
    
	void addExpected(int[][] lowPrecPoints) {
	    List<Coord> another = makeShape(lowPrecPoints);
	    totalArea += Math.abs(ShapeMergeFilter.calcAreaSizeTestVal(another));
	    expectedShapes.add(another);
	}

	void preSplit(String algorithm) {
	    this.algorithm = algorithm;
	    if (dividingInTwo)
		assertEquals(algorithm + " bits area", Math.abs(origArea), totalArea);
	    resultShapes = null;
	}
		
	void checkResults() {
	    assertEquals(algorithm + " number of areas", expectedShapes.size(), resultShapes.size());
	    long resTotalArea = 0;
	    for (List<Coord> resShape : resultShapes) {
		long resArea = ShapeMergeFilter.calcAreaSizeTestVal(resShape);
		resTotalArea += Math.abs(resArea);
		MapShape resTemp = new MapShape();
		resTemp.setPoints(resShape);
		Area resBounds = resTemp.getBounds();
		// try and find in expected shapes
		boolean foundIt = false;
		for (List<Coord> expShape : expectedShapes) {
		    long expArea = ShapeMergeFilter.calcAreaSizeTestVal(expShape);
		    MapShape expTemp = new MapShape();
		    expTemp.setPoints(expShape);
		    Area expBounds = expTemp.getBounds();
		    if (Math.abs(expArea) == Math.abs(resArea) && expBounds.equals(resBounds)) {
			foundIt = true;
			// attempt to check points
			// make unclosed copy for manipulation
			List<Coord> resPoints = new ArrayList<>(resShape);
			resPoints.remove(resPoints.size()-1);
			if (expArea != resArea) // if sign of areas different
			    Collections.reverse(resPoints);
			// rotate the list so have same first elem
			Coord expFst = expShape.get(0);
			int inx;
			for (inx = 0; inx < resPoints.size(); ++inx)
			    if (resPoints.get(inx).equals(expFst))
				break;
			assertNotEquals("find first point failed", resPoints.size(), inx);
			if (inx > 0)
			    Collections.rotate(resPoints, -inx);
			// now step through. Maybe need to allow duplicate/extra in result set
			// if java2D... might keeps some on the cut line
			inx = 0;
			for (Coord resPoint : resPoints) {
			    assertTrue(algorithm + " point " + inx, resPoint.equals(expShape.get(inx)));
                            ++inx;
			}
			break;
		    } // if shape has correct area and bounds
		} // for each expectedShape
		assertTrue(algorithm + " result shape not matched", foundIt);
	    } // for each resultShape
	    assertEquals(algorithm + " result total area", totalArea, resTotalArea);
	} // checkResults

	void runSplitShape() {
	    preSplit("splitShape");
	    resultShapes = new ArrayList<>();
	    assertTrue(algorithm + " Not applicable to clip", dividingInTwo);
	    ShapeSplitter.splitShape(origShape, dividingLine << Coord.DELTA_SHIFT, isLongitude, resultShapes, resultShapes, null);
	    checkResults();
	}

	void runClipToBounds() {
	    preSplit("clipToBounds");
	    resultShapes = ShapeSplitter.clipToBounds(origShape, fstBounds, null);
	    if (dividingInTwo) {
		List<List<Coord>> moreShapes = ShapeSplitter.clipToBounds(origShape, lstBounds, null);
		resultShapes.addAll(moreShapes);
	    }
	    checkResults();
	}

	void runJava2D() {
	    preSplit("java2D");
	    java.awt.geom.Area area = Java2DConverter.createArea(origShape);
	    java.awt.geom.Area clipper = Java2DConverter.createBoundsArea(fstBounds);
	    clipper.intersect(area);
	    resultShapes = Java2DConverter.areaToShapes(clipper);
	    if (dividingInTwo) {
		clipper = Java2DConverter.createBoundsArea(lstBounds);
		clipper.intersect(area);
		List<List<Coord>> moreShapes = Java2DConverter.areaToShapes(clipper);
		resultShapes.addAll(moreShapes);
	    }
	    checkResults();
	}

	void runSuthHodg() {
/*	    
	    preSplit("suthHodg");

started to look at this to see if could fit in to testing like above but gave up.
	    Path2D.Double ShapeSplitter.clipSinglePathWithSutherlandHodgman (double[] points, int num, Rectangle2D clippingRect, Rectangle2D.Double bbox) {

	    checkResults();
*/
	}

    }
	
}
