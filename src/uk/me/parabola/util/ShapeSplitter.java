package uk.me.parabola.util;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;

import uk.me.parabola.log.Logger;

public class ShapeSplitter {
	private static final Logger log = Logger.getLogger(ShapeSplitter.class);
	private static final int LEFT = 0;
	private static final int TOP = 1;
	private static final int RIGHT = 2;
	private static final int BOTTOM= 3;

	
	/**
	 * Clip a given shape with a given rectangle. 
	 * @param shape the subject shape to clip
	 * @param clippingRect the clipping rectangle
	 * @return the intersection of the shape and the rectangle 
	 * or null if they don't intersect. 
	 * The intersection may contain dangling edges. 
	 */
	public static Path2D.Double clipShape (Shape shape, Rectangle2D clippingRect) {
		double minX = Double.POSITIVE_INFINITY,minY = Double.POSITIVE_INFINITY, 
				maxX = Double.NEGATIVE_INFINITY,maxY = Double.NEGATIVE_INFINITY;
		PathIterator pit = shape.getPathIterator(null);
		double[] points = new double[512];
		int num = 0;
		Path2D.Double result = null;
		double[] res = new double[6];
		while (!pit.isDone()) {
			int type = pit.currentSegment(res);
			double x = res[0];
			double y = res[1];
			if (x < minX) minX = x;
			if (x > maxX) maxX = x;
			if (y < minY) minY = y;
			if (y > maxY) maxY = y;
			switch (type) {
			case PathIterator.SEG_LINETO:
			case PathIterator.SEG_MOVETO:
				if (num  + 2 >= points.length) {
					points = Arrays.copyOf(points, points.length * 2);
				}
				points[num++] = x;
				points[num++] = y;
				break;
			case PathIterator.SEG_CLOSE:
				Path2D.Double segment = null;
				if (clippingRect.contains(minX, minY) == false || clippingRect.contains(maxX,maxY) == false){
					Rectangle2D.Double bbox = new Rectangle2D.Double(minX,minY,maxX-minX,maxY-minY);
					segment = clipSinglePathWithSutherlandHodgman (points, num, clippingRect, bbox);
				} else 
					segment = pointsToPath2D(points, num);
				if (segment != null){
					if (result == null)
						result = segment;
					else 
						result.append(segment, false);
				}
				num = 0;
				minX = minY = Double.POSITIVE_INFINITY; 
				maxX = maxY = Double.NEGATIVE_INFINITY;
				break;
			default:
				log.error("Unsupported path iterator type " + type
						+ ". This is an mkgmap error.");
			}
	
			pit.next();
		}
		return result;
	}

	/**
	 * Convert a list of longitude+latitude values to a Path2D.Double
	 * @param points the pairs
	 * @return the path or null if the path describes a point or line. 
	 */
	public static Path2D.Double pointsToPath2D(double[] points, int num) {
		if (num < 2)
			return null;
		if (points[0] == points[num-2] && points[1] == points[num-1])
			num -= 2;
		if (num < 6)
			return null;
		Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO, num / 2 + 2);
		double lastX = points[0], lastY = points[1];
		path.moveTo(lastX, lastY);
		int numOut = 1;
		for (int i = 2; i < num; ){
			double x = points[i++], y = points[i++];
			if (x != lastX || y != lastY){
				path.lineTo(x,y);
				lastX = x; lastY = y;
				++numOut;
			}
		}
		if (numOut < 3)
			return null;
		path.closePath();
		return path;
	}

	//    The Sutherland�Hodgman algorithm Pseudo-Code: 	 
	//	  List outputList = subjectPolygon;
	//	  for (Edge clipEdge in clipPolygon) do
	//	     List inputList = outputList;
	//	     outputList.clear();
	//	     Point S = inputList.last;
	//	     for (Point E in inputList) do
	//	        if (E inside clipEdge) then
	//	           if (S not inside clipEdge) then
	//	              outputList.add(ComputeIntersection(S,E,clipEdge));
	//	           end if
	//	           outputList.add(E);
	//	        else if (S inside clipEdge) then
	//	           outputList.add(ComputeIntersection(S,E,clipEdge));
	//	        end if
	//	        S = E;
	//	     done
	//	  done

	/**
	 * Clip a single path with a given rectangle using the Sutherland-Hodgman algorithm. This is much faster compared to
	 * the area.intersect method, but may create dangling edges. 
	 * @param points	a list of longitude+latitude pairs  
	 * @param num the nnumber of valid values in points 
	 * @param clippingRect the clipping rectangle 
	 * @param bbox the bounding box of the path 
	 * @return the clipped path as a Path2D.Double or null if the result is empty  
	 */
	public static Path2D.Double clipSinglePathWithSutherlandHodgman (double[] points, int num, Rectangle2D clippingRect, Rectangle2D.Double bbox) {
		if (num <= 2 || bbox.intersects(clippingRect) == false){
			return null;
		}
		
		int countVals = num;
		if (points[0] == points[num-2] && points[1] == points[num-1]){
			countVals -= 2;
		}
		double[] outputList = points;
		double[] input;
		
		double leftX = clippingRect.getMinX();
		double rightX = clippingRect.getMaxX();
		double lowerY = clippingRect.getMinY();
		double upperY = clippingRect.getMaxY();
		boolean eIsIn = false, sIsIn = false;
		for (int side = LEFT; side <= BOTTOM; side ++){
			if (countVals < 6)
				return null; // ignore point or line 
			
			boolean skipTestForThisSide;
			switch(side){
			case LEFT: skipTestForThisSide = (bbox.getMinX() >= leftX); break;
			case TOP: skipTestForThisSide = (bbox.getMaxY()  < upperY); break;
			case RIGHT: skipTestForThisSide = (bbox.getMaxX()  < rightX); break;
			default: skipTestForThisSide = (bbox.getMinY() >= lowerY); 
			}
			if (skipTestForThisSide)
				continue;
			
			input = outputList;
			outputList = new double[countVals + 16];
			double sLon = 0,sLat = 0;
			double pLon = 0,pLat = 0; // intersection
			int posIn = countVals - 2; 
			int posOut = 0;
			for (int i = 0; i < countVals+2; i+=2){
				if (posIn >=  countVals)
					posIn = 0;
				double eLon = input[posIn++];
				double eLat = input[posIn++];
				switch (side){
				case LEFT: eIsIn =  (eLon >= leftX); break;
				case TOP: eIsIn =  (eLat < upperY); break;
				case RIGHT: eIsIn =  (eLon < rightX); break;
				default: eIsIn =  (eLat >= lowerY); 
				}
				if (i > 0){
					if (eIsIn != sIsIn){
						// compute intersection
						double slope;
						if (eLon != sLon)
							slope = (eLat - sLat) / (eLon-sLon);
						else slope = 1;
	
						switch (side){
						case LEFT: 
							pLon = leftX;
							pLat = slope *(leftX-sLon) + sLat;
							break;
						case RIGHT: 
							pLon = rightX;
							pLat = slope *(rightX-sLon) + sLat; 
							break;
	
						case TOP: 
							if (eLon != sLon)
								pLon =  sLon + (upperY - sLat) / slope;
							else 
								pLon =  sLon;
							pLat = upperY;
							break;
						default: // BOTTOM
							if (eLon != sLon)
								pLon =  sLon + (lowerY - sLat) / slope;
							else 
								pLon =  sLon;
							pLat = lowerY;
							break;
	
						}
					}
					int toAdd = 0;
					if (eIsIn){
						if (!sIsIn){
							toAdd += 2;
						}
						toAdd += 2;
					}
					else {
						if (sIsIn){
							toAdd += 2;
						}
					}
					if (posOut + toAdd >= outputList.length) {
						// unlikely
						outputList = Arrays.copyOf(outputList, outputList.length * 2);
					}
					if (eIsIn){
						if (!sIsIn){
							outputList[posOut++] = pLon;
							outputList[posOut++] = pLat;
						}
						outputList[posOut++] = eLon;
						outputList[posOut++] = eLat;
					}
					else {
						if (sIsIn){
							outputList[posOut++] = pLon;
							outputList[posOut++] = pLat;
						}
					}
				}
				// S = E
				sLon = eLon; sLat = eLat;
				sIsIn = eIsIn;
			}
			countVals = posOut;
		}
		return pointsToPath2D(outputList, countVals);
	}

}
