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
package uk.me.parabola.mkgmap.reader.hgt;

import java.awt.geom.Rectangle2D;
import java.util.logging.Level;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;

/**
 * Very basic routine to find the correct hgt reader for a given coordinate.
 * @author Gerd Petermann
 *
 */
public class HGTConverter {
	private static final Logger log = Logger.getLogger(HGTConverter.class);
	protected final static double FACTOR = 45.0d / (1<<29);
	private short[] noHeights = { HGTReader.UNDEF };
	private HGTReader[][] readers;
	private final int minLat32;
	private final int minLon32;
	private final int res;
	private final java.awt.geom.Area demArea;
	private short outsidePolygonHeight = HGTReader.UNDEF;
	private int lastRow = -1;
	private int pointsDistanceLat;
	private int pointsDistanceLon;
	private boolean useComplexInterpolation;
	private final double[][] eleArray = new double[4][4];
	private int statPoints;
	private int statBicubic;
	private int statBilinear;
	private int statVoid;
	private int statRdrNull;
	private int statRdrRes;
	
	private InterpolationMethod interpolationMethod = InterpolationMethod.Bicubic;

	public enum InterpolationMethod {
		/** faster, smoothing, less precise */
		Bilinear , 
		/** slower, higher precision */
		Bicubic, 
		/** bicubic for high resolution, else bilinear */ 
		Automatic
	};
	
	/**
	 * Class to extract elevation information from SRTM files in hgt format.
	 * @param path a comma separated list of directories which may contain *.hgt files.
	 * @param bbox the bounding box of the tile for which the DEM information is needed.
	 * @param demPolygonMapUnits optional bounding polygon which describes the area for 
	 * which elevation should be read from hgt files.
	 * @param interpolationMethod 
	 */
	public HGTConverter(String path, Area bbox, java.awt.geom.Area demPolygonMapUnits, double extra) {
		// make bigger box for interpolation or aligning of areas
		int minLat = (int) Math.floor(Utils.toDegrees(bbox.getMinLat()) - extra);
		int minLon = (int) Math.floor(Utils.toDegrees(bbox.getMinLong()) - extra);
		int maxLat = (int) Math.ceil(Utils.toDegrees(bbox.getMaxLat()) + extra);
		int maxLon = (int) Math.ceil(Utils.toDegrees(bbox.getMaxLong()) + extra);

		if (minLat < -90) minLat = -90;
		if (maxLat > 90) maxLat = 90;
		if (minLon < -180) minLon = -180;
		if (maxLon > 180) maxLon = 180;

		minLat32 = Utils.toMapUnit(minLat) * 256; 
		minLon32 = Utils.toMapUnit(minLon) * 256; 
		// create matrix of readers 
		int dimLat = maxLat - minLat;
		int dimLon = maxLon - minLon;
		readers = new HGTReader[dimLat][dimLon];
		demArea = demPolygonMapUnits;
		int maxRes = -1;
		for (int row = 0; row < dimLat; row++) {
			int lat = row + minLat;
			for (int col = 0; col < dimLon; col++) {
				int lon =  col + minLon;
				Area rdrBbox = new Area(lat, lon, lat+1.0, lon+1.0); 
				int testMode = intersectsPoly(rdrBbox);
				if (testMode != 0) {
					HGTReader rdr = new HGTReader(lat, lon, path);
					readers[row][col] = rdr;
					maxRes = Math.max(maxRes, rdr.getRes()); 
				} 
			}
		}
		res = maxRes; // we use the highest available res
		return;
	}

	/**
	 * Allows to change the interpolation method for complex interpolations.
	 * @param interpolationMethod
	 */
	public void setInterpolationMethod(InterpolationMethod interpolationMethod) {
		this.interpolationMethod = interpolationMethod;
		useComplexInterpolation = (interpolationMethod != InterpolationMethod.Bilinear);
	}

	/**
	 * Return elevation in meter for a point given in DEM units (32 bit res).
	 * @param lat32
	 * @param lon32
	 * @return height in m or Short.MIN_VALUE if value is invalid 
	 */
	protected short getElevation(int lat32, int lon32) {
		int row = (int) ((lat32 - minLat32) * FACTOR);
		int col = (int) ((lon32 - minLon32) * FACTOR);

		HGTReader rdr = readers[row][col];
		if (rdr == null) {
			// no reader : ocean or missing file
			return outsidePolygonHeight;
		}
		int res = rdr.getRes();
		rdr.prepRead();
		if (res <= 0)
			return 0; // assumed to be an area in the ocean
		lastRow = row;

		double scale  = res * FACTOR;
		
		double y1 = (lat32 - minLat32) * scale - row * res;
		double x1 = (lon32 - minLon32) * scale - col * res;
		int xLeft = (int) x1;
		int yBottom = (int) y1;
		double qx = x1 - xLeft;
		double qy = y1 - yBottom;

		short h = HGTReader.UNDEF;

		statPoints++;
		if (useComplexInterpolation) {
			// bicubic (Catmull-Rom) interpolation with 16 points
			boolean filled = fillArray(rdr, row, col, xLeft, yBottom);
			if (filled) {
				h = (short) Math.round(bicubicInterpolation(eleArray, qx, qy));
				statBicubic++;
			}
		}

		if (h == HGTReader.UNDEF) {
			// use bilinear interpolation if bicubic not available
			int xRight = xLeft + 1;
			int yTop = yBottom + 1;

			int hLT = rdr.ele(xLeft, yTop);
			int hRT = rdr.ele(xRight, yTop);
			int hLB = rdr.ele(xLeft, yBottom);
			int hRB = rdr.ele(xRight, yBottom);
			
			h = interpolatedHeight(qx, qy, hLT, hRT, hRB, hLB);
			statBilinear++;
			if (h == HGTReader.UNDEF) statVoid++;
		}

		if (h == HGTReader.UNDEF && log.isLoggable(Level.WARNING)) {
			double lon = lon32 * FACTOR;
			double lat = lat32 * FACTOR;
			Coord c = new Coord(lat, lon);
			log.warn("height interpolation returns void at", c.toDegreeString());
		}
		return h;
	}

	/**
	 * Fill 16 values of HGT near required coordinates
	 * can use HGTreaders near the current one
	 */
	private boolean fillArray(HGTReader rdr, int row, int col, int xLeft, int yBottom) {
		int res = rdr.getRes();
		int minX = 0;
		int minY = 0;
		int maxX = 3;
		int maxY = 3;
		boolean inside = true;

		// check borders
		if (xLeft == 0) {
			if (col <= 0)
				return false;
			minX = 1;
			inside = false;
		} else if (xLeft == res - 1) {
			if (col + 1 >= readers[0].length)
				return false;
			maxX = 2;
			inside = false;
		}
		if (yBottom == 0) {
			if (row <= 0)
				return false;
			minY = 1;
			inside = false;
		} else if (yBottom == res - 1) {
			if (row + 1 >= readers.length)
				return false;
			maxY = 2;
			inside = false;
		}

		// fill data from current reader
		short h;
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				h = rdr.ele(xLeft + x - 1, yBottom + y - 1);
				if (h == HGTReader.UNDEF)
					return false;
				eleArray[x][y] = h;
			}
		}

		if (inside) // no need to check borders again
			return true;

		// fill data from adjacent readers, down and up
		if (xLeft > 0 && xLeft < res - 1) {
			if (yBottom == 0) { // bottom edge
				HGTReader rdrBB = prepReader(res, row - 1, col);
				if (rdrBB == null)
					return false;
				for (int x = 0; x <= 3; x++) {
					h = rdrBB.ele(xLeft + x - 1, res - 1);
					if (h == HGTReader.UNDEF)
						return false;
					eleArray[x][0] = h;
				}
			} else if (yBottom == res - 1) { // top edge
				HGTReader rdrTT = prepReader(res, row + 1, col);
				if (rdrTT == null)
					return false;
				for (int x = 0; x <= 3; x++) {
					h = rdrTT.ele(xLeft + x - 1, 1);
					if (h == HGTReader.UNDEF)
						return false;
					eleArray[x][3] = h;
				}
			}
		}

		// fill data from adjacent readers, left and right
		if (yBottom > 0 && yBottom < res - 1) {
			if (xLeft == 0) { // left edgge
				HGTReader rdrLL = prepReader(res, row, col - 1);
				if (rdrLL == null)
					return false;
				for (int y = 0; y <= 3; y++) {
					h = rdrLL.ele(res - 1, yBottom + y - 1);
					if (h == HGTReader.UNDEF)
						return false;
					eleArray[0][y] = h;
				}
			} else if (xLeft == res - 1) { // right edge
				HGTReader rdrRR = prepReader(res, row, col + 1);
				if (rdrRR == null)
					return false;
				for (int y = 0; y <= 3; y++) {
					h = rdrRR.ele(1, yBottom + y - 1);
					if (h == HGTReader.UNDEF)
						return false;
					eleArray[3][y] = h;
				}
			}
		}

		// fill data from adjacent readers, corners
		if (xLeft == 0) {
			if (yBottom == 0) { // left bottom corner
				HGTReader rdrLL = prepReader(res, row, col - 1);
				if (rdrLL == null)
					return false;
				for (int y = 1; y <= 3; y++) {
					h = rdrLL.ele(res - 1, yBottom + y - 1);
					if (h == HGTReader.UNDEF)
						return false;
					eleArray[0][y] = h;
				}

				HGTReader rdrBB = prepReader(res, row - 1, col);
				if (rdrBB == null)
					return false;
				for (int x = 1; x <= 3; x++) {
					h = rdrBB.ele(xLeft + x - 1, res - 1);
					if (h == HGTReader.UNDEF)
						return false;
					eleArray[x][0] = h;
				}

				HGTReader rdrLB = prepReader(res, row - 1, col - 1);
				if (rdrLB == null)
					return false;
				h = rdrLB.ele(res - 1, res - 1);
				if (h == HGTReader.UNDEF)
					return false;
				eleArray[0][0] = h;
			} else if (yBottom == res - 1) { // left top corner
				HGTReader rdrLL = prepReader(res, row, col - 1);
				if (rdrLL == null)
					return false;
				for (int y = 0; y <= 2; y++) {
					h = rdrLL.ele(res - 1, yBottom + y - 1);
					if (h == HGTReader.UNDEF)
						return false;
					eleArray[0][y] = h;
				}

				HGTReader rdrTT = prepReader(res, row + 1, col);
				if (rdrTT == null)
					return false;
				for (int x = 1; x <= 3; x++) {
					h = rdrTT.ele(xLeft + x - 1, 1);
					if (h == HGTReader.UNDEF)
						return false;
					eleArray[x][3] = h;
				}

				HGTReader rdrLT = prepReader(res, row + 1, col - 1);
				if (rdrLT == null)
					return false;
				h = rdrLT.ele(res - 1, 1);
				if (h == HGTReader.UNDEF)
					return false;
				eleArray[0][3] = h;
			}
		} else if (xLeft == res - 1) {
			if (yBottom == 0) { // right bottom corner
				HGTReader rdrRR = prepReader(res, row, col + 1);
				if (rdrRR == null)
					return false;
				for (int y = 1; y <= 3; y++) {
					h = rdrRR.ele(1, yBottom + y - 1);
					if (h == HGTReader.UNDEF)
						return false;
					eleArray[3][y] = h;
				}

				HGTReader rdrBB = prepReader(res, row - 1, col);
				if (rdrBB == null)
					return false;
				for (int x = 0; x <= 2; x++) {
					h = rdrBB.ele(xLeft + x - 1, res - 1);
					if (h == HGTReader.UNDEF)
						return false;
					eleArray[x][0] = h;
				}

				HGTReader rdrRB = prepReader(res, row - 1, col + 1);
				if (rdrRB == null)
					return false;
				h = rdrRB.ele(1, res - 1);
				if (h == HGTReader.UNDEF)
					return false;
				eleArray[3][0] = h;
			} else if (yBottom == res - 1) { // right top corner
				HGTReader rdrRR = prepReader(res, row, col + 1);
				if (rdrRR == null)
					return false;
				for (int y = 0; y <= 2; y++) {
					h = rdrRR.ele(1, yBottom + y - 1);
					if (h == HGTReader.UNDEF)
						return false;
					eleArray[3][y] = h;
				}

				HGTReader rdrTT = prepReader(res, row + 1, col);
				if (rdrTT == null)
					return false;
				for (int x = 0; x <= 2; x++) {
					h = rdrTT.ele(xLeft + x - 1, 1);
					if (h == HGTReader.UNDEF)
						return false;
					eleArray[x][3] = h;
				}

				HGTReader rdrRT = prepReader(res, row + 1, col + 1);
				if (rdrRT == null)
					return false;
				h = rdrRT.ele(1, 1);
				if (h == HGTReader.UNDEF)
					return false;
				eleArray[3][3] = h;
			}
		}

		// all 16 values present
		return true;
	}

	/**
	 * 
	 */
	private HGTReader prepReader(int res, int row, int col) {
		if (row >= readers.length) {
			log.error("invalid array index for row", row);
			return null;
		}
		if (col >= readers[row].length) {
			log.error("invalid array index for col", row);
			return null;
		}
		HGTReader rdr = readers[row][col];

		if (rdr == null) {
			statRdrNull++;
			return null;
		}

		// do not use if different resolution
		if (res != rdr.getRes()) {
			statRdrRes++;
			return null;
		}

		rdr.prepRead();
		if (row > lastRow)
			lastRow = row;
	
		return rdr;
	}

	/**
	 * Try to free java heap memory allocated by the readers.
	 */
	public void freeMem() {
		log.info("trying to free mem for hgt buffers");
		// top to bottom
		for (int i = readers.length - 1; i > lastRow; i--) {
			for (HGTReader r : readers[i]) {
				if (r != null) {
					r.freeBuf();
				}
			}
		}
	}

	/**
	 * Interpolate the height of point p from the 4 closest values in the hgt matrix.
	 * Bilinear interpolation with single node restore
	 * @param qx value from 0 .. 1 gives relative x position in matrix 
	 * @param qy value from 0 .. 1 gives relative y position in matrix
	 * @param hlt height left top
	 * @param hrt height right top
	 * @param hrb height right bottom
	 * @param hlb height left bottom
	 * @return the interpolated height
	 */
	private static short interpolatedHeight(double qx, double qy, int hlt, int hrt, int hrb, int hlb) {
		// extrapolate single node height if requested point is not near
		// for multiple missing nodes, return the height of the neares node
		if (hlb == HGTReader.UNDEF) {
			if (hrb == HGTReader.UNDEF || hlt == HGTReader.UNDEF || hrt == HGTReader.UNDEF) {
				if (hrt != HGTReader.UNDEF && hlt != HGTReader.UNDEF && qy > 0.5D)	//top edge
					return (short) Math.round((1.0D - qx) * hlt + qx * hrt);
				if (hrt != HGTReader.UNDEF && hrb != HGTReader.UNDEF && qx > 0.5D)	//right edge
					return (short) Math.round((1.0D - qy) * hrb + qy * hrt);
				//if (hlt != HGTReader.UNDEF && hrb != HGTReader.UNDEF && qx + qy > 0.5D && gx + qy < 1.5D)	//diagonal
				// nearest value
				return (short)((qx < 0.5D)? ((qy < 0.5D)? hlb: hlt): ((qy < 0.5D)? hrb: hrt));
			}
			if (qx + qy < 0.4D)	// point is near missing value
				return HGTReader.UNDEF;
			hlb = hlt + hrb - hrt;
		} else if (hrt == HGTReader.UNDEF) {
			if (hlb == HGTReader.UNDEF || hrb == HGTReader.UNDEF || hlt == HGTReader.UNDEF) {
				if (hlb != HGTReader.UNDEF && hrb != HGTReader.UNDEF && qy < 0.5D)	//lower edge
					return (short) Math.round((1.0D - qx) * hlb + qx * hrb);
				if (hlb != HGTReader.UNDEF && hlt != HGTReader.UNDEF && qx < 0.5D)	//left edge
					return (short) Math.round((1.0D - qy) * hlb + qy * hlt);
				//if (hlt != HGTReader.UNDEF && hrb != HGTReader.UNDEF && qx + qy > 0.5D && gx + qy < 1.5D)	//diagonal
				// nearest value
				return (short) ((qx < 0.5D) ? ((qy < 0.5D) ? hlb : hlt) : ((qy < 0.5D) ? hrb : hrt));
			}
			if (qx + qy > 1.6D)	// point is near missing value
				return HGTReader.UNDEF;
			hrt = hlt + hrb - hlb;
		} else if (hrb == HGTReader.UNDEF) {
			if (hlb == HGTReader.UNDEF || hlt == HGTReader.UNDEF || hrt == HGTReader.UNDEF) {
				if (hlt != HGTReader.UNDEF && hrt != HGTReader.UNDEF && qy > 0.5D)	//top edge
					return (short) Math.round((1.0D - qx) * hlt + qx * hrt);
				if (hlt != HGTReader.UNDEF && hlb != HGTReader.UNDEF && qx < 0.5D)	//left edge
					return (short) Math.round((1.0D - qy) * hlb + qy * hlt);
				//if (hlb != HGTReader.UNDEF && hrt != HGTReader.UNDEF && qy > qx - 0.5D && qy < qx + 0.5D)	//diagonal
				// nearest value
				return (short) ((qx < 0.5D) ? ((qy < 0.5D) ? hlb : hlt) : ((qy < 0.5D) ? hrb : hrt));
			}
			if (qy < qx - 0.4D)	// point is near missing value 
				return HGTReader.UNDEF;
			hrb = hlb + hrt - hlt;
		} else if (hlt == HGTReader.UNDEF) {
			if (hlb == HGTReader.UNDEF || hrb == HGTReader.UNDEF || hrt == HGTReader.UNDEF) {
				if (hrb != HGTReader.UNDEF && hlb != HGTReader.UNDEF && qy < 0.5D)	//lower edge
					return (short) Math.round((1.0D - qx) * hlb + qx * hrb);
				if (hrb != HGTReader.UNDEF && hrt != HGTReader.UNDEF && qx > 0.5D)	//right edge
					return (short) Math.round((1.0D - qy) * hrb + qy * hrt);
				//if (hlb != HGTReader.UNDEF && hrt != HGTReader.UNDEF && qy > qx - 0.5D && qy < qx + 0.5D)	//diagonal
				// nearest value
				return (short) ((qx < 0.5D) ? ((qy < 0.5D) ? hlb : hlt) : ((qy < 0.5D) ? hrb : hrt));
			}
			if (qy > qx + 0.6D)	// point is near missing value
				return HGTReader.UNDEF;
			hlt = hlb + hrt - hrb;
		}

		// bilinera interpolation
		double hxt = (1.0D - qx)*hlt + qx*hrt;
		double hxb = (1.0D - qx)*hlb + qx*hrb;
		return (short) Math.round((1.0D - qy) * hxb + qy * hxt);
	}

//	public void stats() {
//		for (int i = 0; i < readers.length; i++) {
//			for (int j = 0; j < readers[i].length; j++) {
//				System.out.println(readers[i][j]);
//			}
//			
//		}
//	}

	public int getHighestRes() {
		return res;
	}

	/**
	 * Check if a bounding box intersects the bounding polygon. 
	 * @param bbox the bounding box
	 * @return 2 if the bounding polygon is null or if the bbox is completely inside the polygon, 1 if the
	 * bbox intersects the bounding box otherwise, 0 else.
	 */
	private int intersectsPoly(Area bbox) {
		if (demArea == null)
			return 2;
		
		Rectangle2D.Double r = new Rectangle2D.Double(bbox.getMinLong(), bbox.getMinLat(), 
				bbox.getWidth(), bbox.getHeight());

		if (demArea.contains(r))
			return 2;
		if (demArea.intersects(r))
			return 1;
		return 0;
	}

	public void setOutsidePolygonHeight(short outsidePolygonHeight) {
		this.outsidePolygonHeight = outsidePolygonHeight;
		noHeights[0]= outsidePolygonHeight;
	}

	/**
	 * If interpolation method is automatic, decide which to use for a new zoom level.
	 * @param pointDist distance between DEM points
	 */
	public void startNewLevel(int pointDist) {
		clearStat();
		pointsDistanceLat = pointDist;
		pointsDistanceLon = pointDist;
		if (InterpolationMethod.Automatic.equals(interpolationMethod)) {
			if (res > 0) {
				int distHGTx3 = (1 << 29)/(45 / 3 * res);	// 3 * HGT points distance in DEM units
				if (distHGTx3 + 20 > pointDist) {		// account for rounding of pointDist and distHGTx3
					this.useComplexInterpolation = true;			// DEM resolution is greater than 1/3 HGT resolution
					return;
				}
			}
			this.useComplexInterpolation = false;
		}
	}

	private void clearStat() {
		statPoints = 0;
		statBicubic = 0;
		statBilinear = 0;
		statVoid = 0;
		statRdrNull = 0;
		statRdrRes = 0;
	}
	public void printStat() {
		log.info("DEM points: " + statPoints + "; bicubic " + statBicubic + ", no HGT " + (statRdrNull + statRdrRes) +
				"; bilinear " + statBilinear + ", voids " + statVoid + "; distance " + pointsDistanceLat);
	}

	/**
	 * Fill array with real height values for a given upper left corner of a rectangle.
	 * @param lat32 latitude of upper left corner
	 * @param lon32 longitude of upper left corner
	 * @param height 
	 * @param width 
	 * @return either an array with a single value if rectangle is outside of the bounding polygon or
	 * a an array with one value for each point, in the order top -> down and left -> right.
	 */
	public short[] getHeights(int lat32, int lon32, int height, int width) {
		short[] realHeights = noHeights;
		
		java.awt.geom.Area testArea = null;
		if (demArea != null) {
			// we have a bounding polygon
			// create rectangle that slightly overlaps the DEM tile
			Rectangle2D r = new Rectangle2D.Double(lon32 / 256.0 - 0.01,
					(lat32 - height * pointsDistanceLat) / 256.0 - 0.01, 
					width * pointsDistanceLon / 256.0 + 0.02,
					height * pointsDistanceLat / 256.0 + 0.02);
			if (!demArea.intersects(r))
				return noHeights; // all points outside of bounding polygon
			if (!demArea.contains(r)) {
				testArea = new java.awt.geom.Area(r);
				testArea.intersect(demArea);
			}
		}

		realHeights = new short[width * height];
		int count = 0;
		int py = lat32;
		for (int y = 0; y < height; y++) {
			int px = lon32;
			for (int x = 0; x < width; x++) {
				boolean needHeight = true;
				if (testArea != null) {
					double yTest = py / 256.0;
					double xTest = px / 256.0;
					if (!testArea.contains(xTest, yTest)) {
						needHeight = false;
					}
				}
				// check for 180° problems, they cause integer overflow 
				if (lon32 > 0 && px <= 0)
					needHeight = false;
				realHeights[count++] = needHeight ? getElevation(py, px) : outsidePolygonHeight;
				// left to right
				px += pointsDistanceLon;
			}
			// top to bottom
			py -= pointsDistanceLat;
		}
		return realHeights;
	}
	
	/**
	 * Cubic interpolation for 4 points, taken from http://www.paulinternet.nl/?page=bicubic
	 * Uses Catmull–Rom spline.
	 * @author Paul Breeuwsma
	 */
	private static double cubicInterpolation(double[] p, double qx) {
		return p[1] + 0.5 * qx*(p[2] - p[0] + qx*(2.0*p[0] - 5.0*p[1] + 4.0*p[2] - p[3] + qx*(3.0*(p[1] - p[2]) + p[3] - p[0])));
	}

	/**
	 * Bicubic interpolation for 4x4 points, taken from http://www.paulinternet.nl/?page=bicubic
	 * @author Paul Breeuwsma
	 * @param p 4x4 matrix -1,0,1,2 * -1,0,1,2 with given values  
	 * @param qx value from 0 .. 1 gives relative x position in matrix 
	 * @param qy value from 0 .. 1 gives relative y position in matrix
	 */
	private static double bicubicInterpolation(double[][] p, double qx, double qy) {
		final double[] arr = new double[4];

		arr[0] = cubicInterpolation(p[0], qy);
		arr[1] = cubicInterpolation(p[1], qy);
		arr[2] = cubicInterpolation(p[2], qy);
		arr[3] = cubicInterpolation(p[3], qy);
		return cubicInterpolation(arr, qx);
	}

}
