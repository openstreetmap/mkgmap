/**
 * 
 */
package uk.me.parabola.mkgmap.general;


import java.util.Arrays;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * @author ben
 */
public class PointInShapeTest {

	private MapShape square;
	private MapShape triangle;
	private MapShape line;
	private final int squareSize = 4;

	/**
	 * @throws Exception
	 */
	@Before
	public void setUp() throws Exception {
		// Square
		List<Coord> points = Arrays.asList(
				new Coord(0, 0),
				new Coord(0, squareSize),
				new Coord(squareSize, squareSize),
				new Coord(squareSize, 0),
				new Coord(0,0) 
				);
		square = new MapShape();
		square.setPoints(points);
		
		// Triangle
		points = Arrays.asList(
				new Coord(0,0),
				new Coord(4,4),
				new Coord(8,0),
				new Coord(0,0) 
				);
		triangle = new MapShape();
		triangle.setPoints(points);
		
		// Line
		points = Arrays.asList(
				new Coord(2,5),
				new Coord(12,1)
				);
		line = new MapShape();
		line.setPoints(points);
	}

	@Test
	public void testLinePointsInsideSquare() {
		
		// inside square, 1 unit from corners
		List<Coord> points = Arrays.asList(
				new Coord(1, squareSize/2),
				new Coord(squareSize/2, squareSize - 1),
				new Coord(squareSize - 1, squareSize/2),
				new Coord(squareSize/2, 1) 
				);
		for (Coord coord : points) {
			assertTrue("point (" + coord.getLatitude() + ", " + coord.getLongitude() + ") should be inside square",	
					square.contains(coord));
		}
		
		// on the line
		points = Arrays.asList(
				new Coord(0, squareSize/2),
				new Coord(squareSize/2, squareSize),
				new Coord(squareSize, squareSize/2),
				new Coord(squareSize/2, 0) 
				);
		for (Coord coord : points) {
			assertTrue("point (" + coord.getLatitude() + ", " + coord.getLongitude() + ") should be outside square",	
					square.contains(coord));
		}
	}
	
	@Test
	public void testLinePointsOutsideSquare() {
		
		// outside square, 1 unit from line
		List<Coord> points = Arrays.asList(
				new Coord(-1, squareSize/2),
				new Coord(squareSize/2, squareSize + 1),
				new Coord(squareSize + 1, squareSize/2),
				new Coord(squareSize/2, -1) 
				);
		for (Coord coord : points) {
			assertFalse("point (" + coord.getLatitude() + ", " + coord.getLongitude() + ") should be outside square",	
					square.contains(coord));
		}
	}
	
	@Test
	public void testCornerPointsInsideSquare() {
		// corner points
		for (Coord cornerpoint : square.getPoints()) {
			Coord co = new Coord(cornerpoint.getLatitude(), cornerpoint.getLongitude());
			assertTrue("corner point (" + co.getLatitude() + ", " + co.getLongitude() + ") should be outside square",
					square.contains(co));
		}
		
		// sub shape
		for (Coord cornerpoint : square.getPoints()) {
			int xadd = cornerpoint.getLatitude() > 0 ? -1 : 1;
			int yadd = cornerpoint.getLongitude() > 0 ? -1 : 1;
			int x = cornerpoint.getLatitude() + xadd;
			int y = cornerpoint.getLongitude() + yadd;
			Coord co = new Coord(x, y);
			assertTrue("point (" + x + ", " + y + ") should be inside square", square.contains(co));
		}
		
		// tests above / below corner points, on the outside edge
		for (Coord cornerpoint : square.getPoints()) {
			int xadd = cornerpoint.getLatitude() > 0 ? -1 : 1;
			int x = cornerpoint.getLatitude() + xadd;
			int y = cornerpoint.getLongitude();
			Coord co = new Coord(x, y);
			assertTrue("point (" + x + ", " + y + ") should be outside square",	square.contains(co));
		}
		
		// tests to the right / left side of corner points, on square edge
		for (Coord cornerpoint : square.getPoints()) {
			int yadd = cornerpoint.getLongitude() > 0 ? -1 : 1;
			int x = cornerpoint.getLatitude();
			int y = cornerpoint.getLongitude() + yadd;
			Coord co = new Coord(x, y);
			assertTrue("point (" + x + ", " + y + ") should be outside square", square.contains(co));
		}
	}
	
	@Test
	public void testCornerPointsOutsideSquare() {
		
		// tests above / below corner points, outside square
		for (Coord cornerpoint : square.getPoints()) {
			int yadd = cornerpoint.getLongitude() > 0 ? 1 : -1;
			int x = cornerpoint.getLatitude();
			int y = cornerpoint.getLongitude() + yadd;
			Coord co = new Coord(x, y);
			assertFalse("point (" + x + ", " + y + ") should be outside square",	square.contains(co));
		}
		
		// tests to the right / left side of corner points, outside square
		for (Coord cornerpoint : square.getPoints()) {
			int xadd = cornerpoint.getLatitude() > 0 ? 1 : -1;
			int x = cornerpoint.getLatitude() + xadd;
			int y = cornerpoint.getLongitude();
			Coord co = new Coord(x, y);
			assertFalse("point (" + x + ", " + y + ") should be outside square", square.contains(co));
		}
		
		// super shape
		for (Coord cornerpoint : square.getPoints()) {
			int xadd = cornerpoint.getLatitude() > 0 ? 1 : -1;
			int yadd = cornerpoint.getLongitude() > 0 ? 1 : -1;
			int x = cornerpoint.getLatitude() + xadd;
			int y = cornerpoint.getLongitude() + yadd;
			Coord co = new Coord(x, y);
			assertFalse("point (" + x + ", " + y + ") should be outside square", square.contains(co));
		}
	}
	
	
	@Test
	public void testLinePointsInsideTriangle() {
		// inside triangle, above / below lines
		List<Coord> points = Arrays.asList(
				new Coord(2,1),
				new Coord(6,1),
				new Coord(4,1)
				);
		for (Coord coord : points) {
			assertTrue("point (" + coord.getLatitude() + ", " + coord.getLongitude() + ") should be inside triangle",	
					triangle.contains(coord));
		}
		
		// on lines
		points = Arrays.asList(
				new Coord(2,2),
				new Coord(6,2),
				new Coord(4,0)
				);
		for (Coord coord : points) {
			assertTrue("point (" + coord.getLatitude() + ", " + coord.getLongitude() + ") should be outside triangle",	
					triangle.contains(coord));
		}
	}
	
	@Test
	public void testLinePointsOutsideTriangle() {
		// outside triangle, above / below lines
		List<Coord> points = Arrays.asList(
				new Coord(2,3),
				new Coord(6,3),
				new Coord(4,-1)
				);
		for (Coord coord : points) {
			assertFalse("point (" + coord.getLatitude() + ", " + coord.getLongitude() + ") should be outside triangle",	
					triangle.contains(coord));
		}
	}
	
	@Test
	public void testCornerPointsInsideTriangle() {
		// corner points
		for (Coord cornerpoint : triangle.getPoints()) {
			assertTrue("point (" + cornerpoint.getLatitude() + ", " + cornerpoint.getLongitude() + ") should be outside triangle",
					triangle.contains(cornerpoint));
		}
		
		// sub shape
		List<Coord> points = Arrays.asList(
				new Coord(2,1),
				new Coord(4,3),
				new Coord(6,1)
				);
		for (Coord coord : points) {
			assertTrue("point (" + coord.getLatitude() + ", " + coord.getLongitude() + ") should be inside triangle", 
					triangle.contains(coord));
		}
		
		// beside points, on edge
		points = Arrays.asList(
				new Coord(1,0),
				new Coord(7,0)
				);
		for (Coord coord : points) {
			assertTrue("point (" + coord.getLatitude() + ", " + coord.getLongitude() + ") should be outside triangle",	
					triangle.contains(coord));
		}
	}
	
	@Test
	public void testCornerPointsOutsideTriangle() {
		// above points
		for (Coord coord : triangle.getPoints()) {
			Coord co = new Coord(coord.getLatitude(), coord.getLongitude() + 1);
			assertFalse("point (" + co.getLatitude() + ", " + co.getLongitude() + ") should be outside triangle",	
					triangle.contains(co));
		}
		
		// outside triangle, beside / below lines
		List<Coord> points = Arrays.asList(
				new Coord(-1,0),
				new Coord(0,-1),
				new Coord(3,4),
				new Coord(5,4),
				new Coord(9,0),
				new Coord(8,-1)
				);
		for (Coord coord : points) {
			assertFalse("point (" + coord.getLatitude() + ", " + coord.getLongitude() + ") should be outside triangle",	
					triangle.contains(coord));
		}
		
		// super shape
		for (Coord cornerpoint : triangle.getPoints()) {
			int xadd = cornerpoint.getLatitude() > 0 ? 1 : -1;
			int yadd = cornerpoint.getLongitude() > 0 ? 1 : -1;
			int x = cornerpoint.getLatitude() + xadd;
			int y = cornerpoint.getLongitude() + yadd;
			Coord co = new Coord(x, y);
			assertFalse("point (" + x + ", " + y + ") should be outside triangle", triangle.contains(co));
		}
	}
	
	@Test
	public void testLine() {
		// midpoint
		Coord co = new Coord(7,3);
		assertFalse("point (" + co.getLatitude() + ", " + co.getLongitude() + ") should be outside line",
				line.contains(co));
	}
}
