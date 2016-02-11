/*
 * Copyright (C) 2006, 2013.
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
package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Tags;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.EnhancedProperties;
import uk.me.parabola.util.Java2DConverter;
import uk.me.parabola.util.MultiHashMap;
import uk.me.parabola.util.ShapeSplitter;

public class BoundaryUtil {
	private static final Logger log = Logger.getLogger(BoundaryUtil.class);

	private static final int UNKNOWN_DATA_FORMAT = 0;
	private static final int RAW_DATA_FORMAT_V1 = 2;
	private static final int QUADTREE_DATA_FORMAT_V1 = 3;
	public static final double MIN_DIMENSION = 0.0000001;
	/**
	 * Calculate the polygons that describe the area.
	 * @param area the Area instance
	 * @param id an id that is used to create meaningful messages, typically a boundary Id
	 * @return A list of BoundaryElements (can be empty)
	 */
	public static List<BoundaryElement> splitToElements(Area area, String id) {
		if (area.isEmpty()) {
			return Collections.emptyList();
		}

		Area testArea = area;
		boolean tryAgain = true;
		while (true){
			List<List<Coord>> areaElements = Java2DConverter.areaToShapes(testArea);

			if (areaElements.isEmpty()) {
				// this may happen if a boundary overlaps a raster tile in a very small area
				// so that it is has no dimension
				log.debug("Area has no dimension. Area:",area.getBounds());
				return Collections.emptyList();
			}

			List<BoundaryElement> bElements = new ArrayList<>();
			for (List<Coord> singleElement : areaElements) {
				if (singleElement.size() <= 3) {
					// need at least 4 items to describe a polygon
					continue;
				}
				boolean outer = Way.clockwise(singleElement);
				bElements.add(new BoundaryElement(outer, singleElement));
			}

			if (bElements.isEmpty()) {
				// should not happen because empty polygons should be removed by
				// the Java2DConverter
				log.error("Empty boundary elements list after conversion. Area: "+area.getBounds());
				return Collections.emptyList();
			}


			// reverse the list because it starts with the inner elements first and
			// we need the other way round
			Collections.reverse(bElements);

			if (bElements.get(0).isOuter())
				return bElements;

			// result is not usable if first element is not outer 
			if (tryAgain == false){
				// cannot convert this area
				log.error(" first element is not outer. "+ bElements.get(0));
				
				//createJavaCodeSnippet(area);
				//String fname = "bnd_gpx/first_not_outer" + id ;
				//GpxCreator.createGpx(fname, bElements.get(0).getPoints());
				return Collections.emptyList();
			}
			// try converting the area with rounded float values
			Path2D.Float path = new Path2D.Float(area);
			testArea = new Area(path);
			tryAgain = false;
		}
	}

	/**
	 * Wrapper for {@link #loadQuadTrees(String, List, uk.me.parabola.imgfmt.app.Area, EnhancedProperties)}
	 * @param boundaryDirName a directory name or zip file containing the *.bnd file
	 * @param boundaryFileName the *.bnd file name
	 * @return the quadtree or null in case of errors
	 */
	public static BoundaryQuadTree loadQuadTree (String boundaryDirName, 
			String boundaryFileName){
		Map<String,BoundaryQuadTree> trees = loadQuadTrees (boundaryDirName, Collections.singletonList(boundaryFileName), null, null);
		return trees.get(boundaryFileName);
	}
	
	/**
	 * Create a BoundaryQuadTree for each file listed in boundaryFileNames. 
	 * @param boundaryDirName a directory name or zip file containing the *.bnd file
	 * @param boundaryFileNames the list of *.bnd file names
	 * @param searchBbox null or a bounding box. Data outside of this box is ignored.
	 * @param props null or the properties to be used for the locator
	 * @return a map with quadtrees which can be empty
	 */
	public static Map<String,BoundaryQuadTree> loadQuadTrees (String boundaryDirName, 
			List<String> boundaryFileNames, 
			uk.me.parabola.imgfmt.app.Area searchBbox, EnhancedProperties props){
		Map<String,BoundaryQuadTree>  trees = new HashMap<>();
		File boundaryDir = new File(boundaryDirName);
		BoundaryQuadTree bqt;
		if (boundaryDir.isDirectory()){
			for (String boundaryFileName: boundaryFileNames){
				log.info("loading boundary file:", boundaryFileName);
				// no support for nested directories
				File boundaryFile = new File(boundaryDir, boundaryFileName);
				if (boundaryFile.exists()){
					try(InputStream stream = new FileInputStream(boundaryFile)){
						bqt = BoundaryUtil.loadQuadTreeFromStream(stream, boundaryFileName, searchBbox, props);
						if (bqt != null)
							trees.put(boundaryFileName,bqt);
					} catch (IOException exp) {
						log.error("Cannot load boundary file " +  boundaryFileName + "." + exp);
					}
				}					
			}
		} else if (boundaryDirName.endsWith(".zip")) {
			String  currentFileName = "";
			try(ZipFile zipFile = new ZipFile(boundaryDir)){
				for (String boundaryFileName : boundaryFileNames){
					log.info("loading boundary file:", boundaryFileName);
					currentFileName = boundaryFileName;
					// direct access  
					ZipEntry entry = zipFile.getEntry(boundaryFileName);
					if (entry != null){ 
						try(InputStream stream = zipFile.getInputStream(entry)){
							bqt = BoundaryUtil.loadQuadTreeFromStream(stream, boundaryFileName, searchBbox, props);
							if (bqt != null)
								trees.put(boundaryFileName,bqt);
						}
					}
				}
			} catch (IOException exp) {
				log.error("Cannot load boundary file " + currentFileName + "." + exp);
			}
		} else{ 
			log.error("Cannot read " + boundaryDirName);
		}
		return trees;
	}

	
	/**
	 * read path iterator info from stream and create Area. 
	 * Data is stored with varying length doubles.
	 * @param inpStream the already opened DataInputStream 
	 * @return a new Area object or null if not successful 
	 * @throws IOException
	 */
	public static Area readAreaAsPath(DataInputStream inpStream) throws IOException{
		double[] res = new double[2];
		Path2D.Double path = new Path2D.Double(PathIterator.WIND_NON_ZERO, 1024);
		int windingRule = inpStream.readInt();
		path.setWindingRule(windingRule);
		int type = inpStream.readInt(); 
		double minX = Double.MAX_VALUE,maxX = Double.MIN_VALUE;
		double minY = Double.MAX_VALUE,maxY = Double.MIN_VALUE;
		while (type >= 0) {
			switch (type) {
			case PathIterator.SEG_LINETO:
				int len = inpStream.readInt();
				while(len > 0){
					for (int ii = 0; ii < 2; ii++){
						double delta = readVarDouble(inpStream);
						if (delta == BoundarySaver.RESET_DELTA)
							res[ii] = readVarDouble(inpStream);
						else
							res[ii] = res[ii] + delta;
					}
					if (res[0] < minX)
						minX = res[0];
					if (res[0] > maxX)
						maxX = res[0];
					if (res[1] < minY)
						minY = res[1];
					if (res[1] > maxY)
						maxY = res[1];
					path.lineTo(res[0],res[1]);
					--len;
				}
				break;
			case PathIterator.SEG_MOVETO:
				for (int ii = 0; ii < 2; ii++){
					double delta = readVarDouble(inpStream);
					if (delta == BoundarySaver.RESET_DELTA)
						res[ii] = readVarDouble(inpStream);
					else
						res[ii] = res[ii] + delta;
				}
				if (res[0] < minX)
					minX = res[0];
				if (res[0] > maxX)
					maxX = res[0];
				if (res[1] < minY)
					minY = res[1];
				if (res[1] > maxY)
					maxY = res[1];
				path.moveTo(res[0],res[1]);
				break;
			case PathIterator.SEG_CLOSE:
				path.closePath();
				break;
			default:
				log.error("Unsupported path iterator type " + type
						+ ". This is an mkgmap error.");
				return null;
			}

			type = inpStream.readInt();
		}
		if (type != -1){
			log.error("Final type value != -1: " + type);
		}
		else{
			if (maxX - minX >= MIN_DIMENSION || maxY - minY >= MIN_DIMENSION)
				return new Area(path);
			else {
				// ignore micro area caused by rounding errors in awt area routines
			}
				
		}
		return null;
	}
	
	/**
	 * Read boundary info saved in RAW_DATA_FORMAT 
	 * (written by 1st pass of preparer)
	 * @param inpStream the already opened DataInputStream
	 * @param fname the related file name of the *.bnd file
	 * @param bbox a bounding box. Data outside of this box is ignored.
	 * @return
	 * @throws IOException
	 */
	private static List<Boundary> readStreamRawFormat(
			DataInputStream inpStream, String fname,
			uk.me.parabola.imgfmt.app.Area bbox) throws IOException			{
		List<Boundary> boundaryList = new ArrayList<>();

		try {
			while (true) {
				int minLat = inpStream.readInt();
				int minLong = inpStream.readInt();
				int maxLat = inpStream.readInt();
				int maxLong = inpStream.readInt();
				if (log.isDebugEnabled())
					log.debug("Next boundary. Lat min:",minLat,"max:",maxLat,"Long min:",minLong,"max:",maxLong);
				uk.me.parabola.imgfmt.app.Area rBbox = new uk.me.parabola.imgfmt.app.Area(
						minLat, minLong, maxLat, maxLong);
				int bSize = inpStream.readInt();
				log.debug("Size:",bSize);

				if ( bbox == null || bbox.intersects(rBbox)) {
					log.debug("Bbox intersects. Load the boundary");
					String id = inpStream.readUTF();
					Tags tags = new Tags();
					int noOfTags = inpStream.readInt();
					for (int i = 0; i < noOfTags; i++) {
						String name = inpStream.readUTF();
						String value = inpStream.readUTF();
						tags.put(name, value.intern()); 					
					}
					Area area = readAreaAsPath(inpStream);
					if (area != null) {
						Boundary boundary = new Boundary(area, tags,id);
						boundaryList.add(boundary);
					} else {
						log.warn("Boundary "+tags+" does not contain any valid area in file " + fname);
					}

				} else {
					log.debug("Bbox does not intersect. Skip",bSize);
					inpStream.skipBytes(bSize);
				}
			}
		} catch (EOFException exp) {
			// it's always thrown at the end of the file
			// log.error("Got EOF at the end of the file");
		}  		
		return boundaryList;
	}

	/**
	 * For a given bounding box, calculate the list of file names that have to be read 
	 * @param bbox the bounding box
	 * @return a List with the names
	 */
	public static List<String> getRequiredBoundaryFileNames(uk.me.parabola.imgfmt.app.Area bbox) {
		List<String> names = new ArrayList<>();
		for (int latSplit = getSplitBegin(bbox.getMinLat()); latSplit <= BoundaryUtil
				.getSplitBegin(bbox.getMaxLat()); latSplit += RASTER) {
			for (int lonSplit = getSplitBegin(bbox.getMinLong()); lonSplit <= BoundaryUtil
					.getSplitBegin(bbox.getMaxLong()); lonSplit += RASTER) {
				names.add("bounds_"+ getKey(latSplit, lonSplit) + ".bnd");
			}
		}
		return names;
	}

	/** 
	 * Check content of directory or zip file with precompiled boundary data, 
	 * dirName Name has to be a directory or a zip file.
	 * @param dirName : path to a directory or a zip file containing the *.bnd files
	 * @return the available *.bnd files in dirName.
	 */
	public static List<String> getBoundaryDirContent(String dirName) {
		List<String> names = new ArrayList<>();
		File boundaryDir = new File(dirName);
		if (!boundaryDir.exists())
			log.error("boundary directory/zip does not exist: " + dirName);
		else{		
			if (boundaryDir.isDirectory()){
				// boundaryDir.list() is much quicker than boundaryDir.listFiles(FileFilter)
				String[] allNames = boundaryDir.list();
				for (String name: allNames){
					if (name.endsWith(".bnd"))
						names.add(name);
				}
			}
			else if (boundaryDir.getName().endsWith(".zip")){
				try (ZipFile zipFile = new ZipFile(boundaryDir)){
					Enumeration<? extends ZipEntry> entries = zipFile.entries();
					boolean isFlat = true;
					while(entries.hasMoreElements()) {
						ZipEntry entry = entries.nextElement();
						if (entry.isDirectory()){
							isFlat = false;
						}
						if (entry.getName().endsWith(".bnd"))
							names.add(entry.getName());
					}
					if (!isFlat){
						log.error("boundary zip file contains directories. Files in directories will be ignored." + dirName);			
					}
				} catch (IOException ioe) {
					log.error("Cannot read",dirName,ioe);
//					ioe.printStackTrace();
					throw new ExitException("Failed to read required file " + dirName);				}
			}
		}
		return names;
	}
	
	public static final int RASTER = 50000;

	public static int getSplitBegin(int value) {
		int rem = value % RASTER;
		if (rem == 0) {
			return value;
		} else if (value >= 0) {
			return value - rem;
		} else {
			return value - RASTER - rem;
		}
	}

	public static int getSplitEnd(int value) {
		int rem = value % RASTER;
		if (rem == 0) {
			return value;
		} else if (value >= 0) {
			return value + RASTER - rem;
		} else {
			return value - rem;
		}
	}

	public static String getKey(int lat, int lon) {
		return lat + "_" + lon;
	}
	
	/**
	 * Retrieve the bounding box of the given boundary file.
	 * @param boundaryFileName the name of the boundary file
	 * @return the bounding box
	 */
	public static uk.me.parabola.imgfmt.app.Area getBbox(String boundaryFileName) {
		String filename = new String(boundaryFileName);
		// cut off the extension
		filename = filename.substring(0,filename.length()-4);
		String[] fParts = filename.split(Pattern.quote("_"));
		
		int lat = Integer.parseInt(fParts[1]);
		int lon = Integer.parseInt(fParts[2]);
		
		return new uk.me.parabola.imgfmt.app.Area(lat, lon, lat+RASTER, lon+RASTER);
	}

	/**
	 * Create and fill a BoundaryQuadTree. Read the header of the stream to detect
	 * the proper reading routine for the different supported formats.
	 * @param stream an already opened InputStream
	 * @param fname the file name of the corresponding *.bnd file
	 * @param searchBbox a bounding box or null. If not null, area info outside of this
	 * bounding box is ignored. 
	 * @param props properties to be used or null 
	 * @return on success it returns a new BoundaryQuadTree, else null 
	 * @throws IOException
	 */
	private static BoundaryQuadTree loadQuadTreeFromStream(InputStream stream, 
			String fname,
			uk.me.parabola.imgfmt.app.Area searchBbox, 
			EnhancedProperties props)throws IOException{
		BoundaryQuadTree bqt = null;
		uk.me.parabola.imgfmt.app.Area qtBbox = getBbox(fname);
		try (DataInputStream inpStream = new DataInputStream(new BufferedInputStream(stream, 1024 * 1024))){
			try {
				// 1st read the mkgmap release the boundary file is created by
				String mkgmapRel = "?";
				String firstId = inpStream.readUTF();
				if ("BND".equals(firstId) == false){
					throw new FormatException("Unsupported boundary data type "+firstId);
				}

				int format = UNKNOWN_DATA_FORMAT;
				long createTime = inpStream.readLong();
				int headerLength = inpStream.readInt();
				byte[] header = new byte[headerLength];
				int bytesRead = 0;
				while (bytesRead < headerLength) {
					int nBytes = inpStream.read(header, bytesRead, headerLength-bytesRead);
					if (nBytes<0) {
						throw new IOException("Cannot read header with size "+headerLength);
					}
					bytesRead += nBytes;
				}
					
				ByteArrayInputStream rawHeaderStream = new ByteArrayInputStream(header);
				DataInputStream headerStream =new DataInputStream(rawHeaderStream);
				String dataFormat = (rawHeaderStream.available() > 0 ? headerStream.readUTF() : "RAW");
				int recordVersion = (rawHeaderStream.available() > 0 ? headerStream.readInt() : RAW_DATA_FORMAT_V1);
				mkgmapRel = (rawHeaderStream.available() > 0 ? headerStream.readUTF() : "unknown");
				
				if ("RAW".equals(dataFormat) && recordVersion == 1)
					format = RAW_DATA_FORMAT_V1;
				else if ("QUADTREE".equals(dataFormat) && recordVersion == 1)
					format = QUADTREE_DATA_FORMAT_V1;

				if (log.isDebugEnabled()) {
					log.debug("File created by mkgmap release",mkgmapRel,"at",new Date(createTime));
				}
				
				switch (format) {
				case QUADTREE_DATA_FORMAT_V1:
					bqt = new BoundaryQuadTree(inpStream, qtBbox, searchBbox, props);
					break;
				case RAW_DATA_FORMAT_V1:
					List<Boundary> boundaryList = readStreamRawFormat(inpStream, fname,searchBbox);
					if (boundaryList == null || boundaryList.isEmpty())
						return null;
					boundaryList = mergePostalCodes(boundaryList);
					bqt = new BoundaryQuadTree(qtBbox, boundaryList, props);
					break;
				default:
					throw new FormatException("Unsupported boundary file format: "+format);
				}
			} catch (EOFException exp) {
				// it's always thrown at the end of the file
				//				log.error("Got EOF at the end of the file");
			} 
			catch (FormatException exp) {
				log.error("Failed to read boundary file " + fname + " " + exp.getMessage());
			} 
		} 
		return bqt;
	}
	
	/**
	 * Merges boundaries with the same postal code.
	 * @param boundaries a list of boundaries
	 * @return the boundary list with postal code areas merged
	 */
	private static List<Boundary> mergePostalCodes(List<Boundary> boundaries) {
		List<Boundary> mergedList = new ArrayList<>(boundaries.size());
		
		MultiHashMap<String, Boundary> equalPostalCodes = new MultiHashMap<>();
		for (Boundary boundary : boundaries) {
			String postalCode = getPostalCode(boundary.getTags());
			if (postalCode == null) {
				// no postal code boundary
				mergedList.add(boundary);
			} else {
				// postal code boundary => merge it later
				equalPostalCodes.add(postalCode, boundary);
			}
		}

		for (Entry<String, List<Boundary>> postCodeBoundary : equalPostalCodes
				.entrySet()) {
			if (postCodeBoundary.getValue().size() == 1) {
				// nothing to merge
				mergedList.addAll(postCodeBoundary.getValue());
				continue;
			}
			
			// there are more than 2 boundaries with the same post code
			// => merge them
			Area newPostCodeArea = new Area();
			for (Boundary b : postCodeBoundary.getValue()) {
				newPostCodeArea.add(b.getArea());
				
				// remove the post code tags from the original boundary
				if (b.getTags().get("postal_code") != null) {
					b.getTags().remove("postal_code");
				} else if ("postal_code".equals(b.getTags().get("boundary"))) {
					b.getTags().remove("boundary");
					b.getTags().remove("name");
				}
				
				// check if the boundary contains other boundary information
				if (isAdministrativeBoundary(b)) {
					mergedList.add(b);
				} else {
					log.info("Boundary", b.getId(), b.getTags(), "contains no more boundary tags. Skipping it.");
				}
			}
			
			Tags postalCodeTags = new Tags();
			postalCodeTags.put("postal_code", postCodeBoundary.getKey());
			Boundary postalCodeBoundary = new Boundary(newPostCodeArea, postalCodeTags, "p"+postCodeBoundary.getKey());

			log.info("Merged", postCodeBoundary.getValue().size(), "postal code boundaries for postal code", postCodeBoundary.getKey());
			mergedList.add(postalCodeBoundary);
		}
		
		return mergedList;
	}
	
	/**
	 * Checks if the given boundary contains tags for an administrative boundary.
	 * @param b a boundary
	 * @return <code>true</code> administrative boundary or postal code; 
	 * <code>false</code> element cannot be used for precompiled bounds 
	 */
	public static boolean isAdministrativeBoundary(Boundary b) {
		
		if (b.getId().startsWith("r")) {
			String type = b.getTags().get("type");
			
			if ("boundary".equals(type) || "multipolygon".equals(type)) {
				String boundaryVal = b.getTags().get("boundary");
				if ("administrative".equals(boundaryVal) == false) 
					return false;
				// for boundary=administrative the admin_level must be set
				if (b.getTags().get("admin_level") == null) {
					return false;
				}
				// and a name must be set (check only for a tag containing name
				Iterator<Entry<String,String>> tagIterator = b.getTags().entryIterator();
				while (tagIterator.hasNext()) {
					Entry<String,String> tag  = tagIterator.next();
					if (tag.getKey().contains("name")) {
						return true;
					}
				}
				// does not contain a name tag => do not use it
			}
		} else if (b.getId().startsWith("w")) {
			// the boundary tag must be "administrative" or "postal_code"
			String boundaryVal = b.getTags().get("boundary");
			if ("administrative".equals(boundaryVal)) {
				// for boundary=administrative the admin_level must be set
				if (b.getTags().get("admin_level") == null) {
					return false;
				}
				// and a name must be set (check only for a tag containing name)
				Iterator<Entry<String,String>> tagIterator = b.getTags().entryIterator();
				while (tagIterator.hasNext()) {
					Entry<String,String> tag  = tagIterator.next();
					if (tag.getKey().contains("name")) {
						return true;
					}
				}
				// does not contain a name tag => do not use it
			}
		} 
		return false;
	}
	
	
	private static String getPostalCode(Tags tags) {
		String zip = tags.get("postal_code");
		if (zip == null) {
			if ("postal_code".equals(tags.get("boundary"))){
				String name = tags.get("name"); 
				if (name != null) {
					String[] nameParts = name.split(Pattern.quote(" "));
					if (nameParts.length > 0) {
						zip = nameParts[0].trim();
					}
				}
			}
		}
		return zip;
	}
	/**
	 * Helper to ease the reporting of errors. Creates a java code snippet 
	 * that can be compiled to have the same area.
	 * @param area the area for which the code should be produced
	 */
	public static void createJavaCodeSnippet(Area area) {
		double[] res = new double[6];
		PathIterator pit = area.getPathIterator(null);
		System.out.println("Path2D.Double path = new Path2D.Double();");
		System.out.println("path.setWindingRule(" + pit.getWindingRule() + ");");
		while (!pit.isDone()) {
			int type = pit.currentSegment(res);
			switch (type) {
			case PathIterator.SEG_LINETO:
				System.out.println("path.lineTo(" + res[0] + "d, " + res[1] + "d);");
				break;
			case PathIterator.SEG_MOVETO: 
				System.out.println("path.moveTo(" + res[0] + "d, " + res[1] + "d);");
				break;
			case PathIterator.SEG_CLOSE:
				System.out.println("path.closePath();");
				break;
			default:
				log.error("Unsupported path iterator type " + type
						+ ". This is an mkgmap error.");
			}

			pit.next();
		}
		System.out.println("Area area = new Area(path);");
		
	}

	/**
	 * read a varying length double. See BoundarySaver.writeVarDouble().
	 * @param inp the already opened DataInputStream
	 * @return the extracted double value
	 * @throws IOException
	 */
	static double readVarDouble(DataInputStream inp) throws IOException{
		byte b;
		long res = 0;
		long toShift = 64 - 7;
		while (((b = inp.readByte()) & 0x80) != 0){ // more bytes will follow
			res |= (b & 0x7f);
			toShift -= 7;
			if (toShift > 0)
				res <<= 7;
		}
		if (toShift > 0){
			res |= b;
			res <<= toShift;
		}
		else {
			// special case: all 64 bits were written, 64 = 9*7 + 1
			res <<= 1;
			res |= 1;
		}
		return Double.longBitsToDouble(res);
	}

	/**
	 * Raster a given area. This is the non-recursive public method.
	 * @param areaToSplit the area
	 * @return a map with the divided shapes 
	 */
	public static Map<String, Shape> rasterArea(Area areaToSplit) {
		return rasterShape(areaToSplit, new HashMap<String, Shape>());
	}

	/**
	 * Raster a given shape. This method calls itself recursively.
	 * @param shapeToSplit the shape
	 * @param splits a map that will contain the resulting shapes
	 * @return a reference to the map 
	 */
	private static Map<String, Shape> rasterShape(Shape shapeToSplit, Map<String, Shape> splits) {
		double minX = Double.POSITIVE_INFINITY,minY = Double.POSITIVE_INFINITY, 
				maxX = Double.NEGATIVE_INFINITY,maxY = Double.NEGATIVE_INFINITY;
		PathIterator pit = shapeToSplit.getPathIterator(null);
		double[] points = new double[512];
		double[] res = new double[6];
		int num = 0;
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
				int sMinLong = getSplitBegin((int)Math.round(minX));
				int sMinLat = getSplitBegin((int)Math.round(minY));
				int sMaxLong = getSplitEnd((int)Math.round(maxX));
				int sMaxLat = getSplitEnd((int)Math.round(maxY));
	
				int dLon = sMaxLong- sMinLong;
				int dLat = sMaxLat - sMinLat;
				Rectangle2D.Double bbox = new Rectangle2D.Double(minX,minY,maxX-minX,maxY-minY);
				if (dLon > RASTER || dLat > RASTER) {
					// split into two halves
					Rectangle clip1,clip2;
					if (dLon > dLat) {
						int midLon = getSplitEnd(sMinLong+dLon/2);
						clip1 = new Rectangle(sMinLong, sMinLat, midLon-sMinLong, dLat);
						clip2 = new Rectangle(midLon, sMinLat, sMaxLong-midLon, dLat);
					} else {
						int midLat = getSplitEnd(sMinLat+dLat/2);
						clip1 = new Rectangle(sMinLong, sMinLat, dLon, midLat-sMinLat);
						clip2 = new Rectangle(sMinLong, midLat, dLon, sMaxLat-midLat);
					}
	
					// intersect with the both halves
					// and split both halves recursively
					Path2D.Double clippedPath = ShapeSplitter.clipSinglePathWithSutherlandHodgman (points, num, clip1, bbox);
					if (clippedPath != null)
						rasterShape(clippedPath, splits);
					clippedPath = ShapeSplitter.clipSinglePathWithSutherlandHodgman (points, num, clip2, bbox);
					if (clippedPath != null)
						rasterShape(clippedPath, splits);
				} 
				else {
					String key = getKey(sMinLat, sMinLong);
					// no need to split, path fits into one tile
					Path2D.Double segment = ShapeSplitter.pointsToPath2D(points, num);
					if (segment != null){
						Path2D.Double path = (Path2D.Double) splits.get(key);
						if (path == null)
							splits.put(key, segment);
						else 
							path.append(segment, false);
					}
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
		return splits;
	}
}
