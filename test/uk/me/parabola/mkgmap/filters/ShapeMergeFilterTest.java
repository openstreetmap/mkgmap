/*
 * Copyright (C) 2014.
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

package uk.me.parabola.mkgmap.filters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.MapShape;
//import uk.me.parabola.util.GpxCreator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;


public class ShapeMergeFilterTest {
	// create one Coord instance for each point in a small test grid 
	private static final HashMap<Integer,Coord> map = new HashMap<Integer,Coord>(){
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		{
			for (int latHp = 0; latHp < 100; latHp +=5){
				for (int lonHp = 0; lonHp < 100; lonHp += 5){
					Coord co = Coord.makeHighPrecCoord(latHp, lonHp);
					put(latHp*1000 + lonHp,co);
				}
			}
		}
	};

	@Test
	public void testAreaTestVal(){
		List<Coord> points = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(10,10));
			add(getPoint(30,10));
			add(getPoint(30,30));
			add(getPoint(10,30));
			add(getPoint(10,10)); // close
			
		}};
		assertEquals(2 * (20 * 20),ShapeMergeFilter.calcAreaSizeTestVal(points));
	}	
	/**
	 * two simple shapes, sharing one point
	 */
	@Test
	public void testSimpleSharingOne(){
		List<Coord> points1 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(15,10));
			add(getPoint(30,25));
			add(getPoint(25,30));
			add(getPoint(10,30));
			add(getPoint(5,20));
			add(getPoint(15,10)); // close
		}};
		
		List<Coord> points2 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(25,30));
			add(getPoint(30,35));
			add(getPoint(20,40));
			add(getPoint(15,35));
			add(getPoint(25,30));
		}};
		testVariants("simple shapes sharing one point", points1, points2,1,10);
	}
	
	/**
	 * two simple shapes, sharing one edge 
	 */
	@Test
	public void testSimpleNonOverlapping(){
		List<Coord> points1 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(15,10));
			add(getPoint(30,25));
			add(getPoint(25,30));
			add(getPoint(15,35));
			add(getPoint(5,20));
			add(getPoint(15,10)); // close
		}};
		
		List<Coord> points2 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(25,30));
			add(getPoint(30,35));
			add(getPoint(20,40));
			add(getPoint(15,35));
			add(getPoint(25,30));
		}};
		testVariants("simple shapes", points1, points2,1,8);
	}

	/**
	 * two simple shapes, sharing three consecutive points 
	 */

	@Test
	public void test3SharedPointsNonOverlapping(){
		List<Coord> points1 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(15,10));
			add(getPoint(30,25));
			add(getPoint(25,30));
			add(getPoint(20,35)); 
			add(getPoint(15,35));
			add(getPoint(5,20));
			add(getPoint(15,10));// close
		}};
		
		List<Coord> points2 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(25,30));
			add(getPoint(30,35));
			add(getPoint(20,40));
			add(getPoint(15,35));
			add(getPoint(20,35));
			add(getPoint(25,30));// close
		}};
		testVariants("test 3 consecutive shared points", points1, points2, 1, 8);
	}
	
	/**
	 * two simple shapes, sharing three consecutive points 
	 */

	@Test
	public void test2SharedPointsNoEdge(){
		List<Coord> points1 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(15,10));
			add(getPoint(30,25));
			add(getPoint(25,30));
			add(getPoint(15,35));
			add(getPoint(5,20));
			add(getPoint(15,10));// close
		}};
		
		List<Coord> points2 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(25,30));
			add(getPoint(30,35));
			add(getPoint(20,40));
			add(getPoint(15,35));
			add(getPoint(20,35));
			add(getPoint(25,30));// close
		}};
		testVariants("test 2 non-consecutive shared points", points1, points2, 1, 11);
	}
	
	/**
	 * one u-formed shape, the other closes it to a rectangular shape with a hole
	 * They are sharing 4 points. 
	 */

	@Test
	public void testCloseUFormed(){
		List<Coord> points1 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			// u-formed shaped (open at top)
			add(getPoint(15,50));
			add(getPoint(30,50));
			add(getPoint(30,55));
			add(getPoint(20,55)); 
			add(getPoint(20,65));
			add(getPoint(30,65));
			add(getPoint(30,70));
			add(getPoint(15,70));
			add(getPoint(15,50));// close
		}};


		List<Coord> points2 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(35,50));
			add(getPoint(35,70));
			add(getPoint(30,70));
			add(getPoint(30,65));
			add(getPoint(30,55));
			add(getPoint(30,50));
			add(getPoint(35,50)); // close
		}};
		
		testVariants("test close U formed shape", points1, points2, 1, 11);
	}
	
	/**
	 * one u-formed shape, the fits into the u and shares all points
	 */

	@Test
	public void testFillUFormed(){
		List<Coord> points1 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			// u-formed shaped (open at top)
			add(getPoint(15,50));
			add(getPoint(30,50));
			add(getPoint(30,55));
			add(getPoint(20,55)); 
			add(getPoint(20,65));
			add(getPoint(30,65));
			add(getPoint(30,70));
			add(getPoint(15,70));
			add(getPoint(15,50)); // close
		}};

		
		List<Coord> points2 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(30,55));
			add(getPoint(30,65));
			add(getPoint(20,65));
			add(getPoint(20,55));
			add(getPoint(30,55)); // close
		}};
		testVariants("test fill U-formed shape", points1, points2, 1, 5);
	}
	
	/**
	 * one u-formed shape, the fits into the u and shares all points
	 */

	@Test
	public void testFillHole(){
		List<Coord> points1 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			// a rectangle with a hole 
			add(getPoint(35,50));
			add(getPoint(35,70));
			add(getPoint(15,70));
			add(getPoint(15,50));
			add(getPoint(30,50));
			add(getPoint(30,55));
			add(getPoint(20,55)); 
			add(getPoint(20,65));
			add(getPoint(30,65));
			add(getPoint(30,50));
			add(getPoint(35,50));// close
		}};

		List<Coord> points2 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(30,55));
			add(getPoint(30,65));
			add(getPoint(20,65));
			add(getPoint(20,55));
			add(getPoint(30,55)); // close
		}};
		testVariants("test-fill-hole", points1, points2, 1, 6); // expect 8 points if spike is not removed  
	}

	@Test
	public void testDuplicate(){
		List<Coord> points1 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(30,55));
			add(getPoint(30,65));
			add(getPoint(20,65));
			add(getPoint(20,55));
			add(getPoint(30,55)); // close
		}};
		List<Coord> points2 = new ArrayList<Coord>(points1);
		
		testVariants("test duplicate", points1, points2, 1, 5);
	}

	@Test
	public void testOverlap(){
		List<Coord> points1 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(30,55));
			add(getPoint(30,65));
			add(getPoint(20,65));
			add(getPoint(20,55));
			add(getPoint(30,55)); // close
		}};

		List<Coord> points2 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(30,55));
			add(getPoint(30,65));
			add(getPoint(25,65));
			add(getPoint(25,55));
			add(getPoint(30,55)); // close
		}};
		// no merge expected
		testVariants("test overlap", points1, points2, 2, 5);
	}

	/*
	 * shapes are connected at multiple edges like two 
	 * w-formed shapes.
	 */
	@Test
	public void testTwoWShaped(){
		List<Coord> points1 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(0,5));
			add(getPoint(35,5));
			add(getPoint(35,20));
			add(getPoint(30,15));
			add(getPoint(25,20));
			add(getPoint(25,10));
			add(getPoint(15,10));
			add(getPoint(15,20));
			add(getPoint(10,15));
			add(getPoint(5,20));
			add(getPoint(0,20));
			add(getPoint(0,5)); // close
		}};

		List<Coord> points2 = new ArrayList<Coord>(){/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

		{
			add(getPoint(35,35));
			add(getPoint(35,20));
			add(getPoint(30,15));
			add(getPoint(25,20));
			add(getPoint(25,25));
			add(getPoint(15,25));
			add(getPoint(15,20));
			add(getPoint(10,15));
			add(getPoint(5,20));
			add(getPoint(0,20));
			add(getPoint(5,35));
			add(getPoint(35,35)); // close
		}};
		
		// wanted: merge that removes at least the longer shared sequence
		testVariants("test two w-shaped", points1, points2, 1, 16);
	}

	/**
	 * Test all variants regarding clockwise/ccw direction and positions of the points 
	 * in the list and the order of shapes. 
	 * @param list1
	 * @param list2
	 */
	void testVariants(String msg, List<Coord> list1, List<Coord> list2, int expectedNumShapes, int expectedNumPoints){
		MapShape s1 = new MapShape(1);
		MapShape s2 = new MapShape(2);
		s1.setMinResolution(22);
		s2.setMinResolution(22);
		for (int i = 0; i < 4; i++){
			for (int j = 0; j < list1.size(); j++){
				List<Coord> points1 = new ArrayList<>(list1);
				if ((i & 1) != 0)
					Collections.reverse(points1);
				points1.remove(points1.size()-1);
				Collections.rotate(points1, j);
				points1.add(points1.get(0));
				s1.setPoints(points1);
				for (int k = 0; k < list2.size(); k++){
					List<Coord> points2 = new ArrayList<>(list2);
					if ((i & 2) != 0)
						Collections.reverse(points2);
					points2.remove(points2.size()-1);
					Collections.rotate(points2, k);
					points2.add(points2.get(0));
					s2.setPoints(points2);
					
					for (int l = 0; l < 2; l++){
						String testId = msg+" i="+i+",j="+j+",k="+k+",l="+l;
						if (l == 0)
							testOneVariant(testId, s1, s2, expectedNumShapes,expectedNumPoints);
						else 
							testOneVariant(testId, s2, s1, expectedNumShapes,expectedNumPoints);
					}
				}
			}
		}
		return;
	}
	
	void testOneVariant(String testId, MapShape s1, MapShape s2, int expectedNumShapes, int expectedNumPoints){
		ShapeMergeFilter smf = new ShapeMergeFilter(24, false);
		List<MapShape> res = smf.merge(Arrays.asList(s1,s2));
		assertTrue(testId, res != null);
		assertEquals(testId,expectedNumShapes, res.size() );
//		if (res.get(0).getPoints().size() != expectedNumPoints){
//			GpxCreator.createGpx("e:/ld/s1", s1.getPoints());
//			GpxCreator.createGpx("e:/ld/s2", s2.getPoints());
//			GpxCreator.createGpx("e:/ld/res", res.get(0).getPoints());
//		}
		assertEquals(testId, expectedNumPoints, res.get(0).getPoints().size());
		// TODO: test shape size
	}
	Coord getPoint(int lat, int lon){
		Coord co = map.get(lat*1000+lon);
		assert co != null;
		return co;
	}
}
