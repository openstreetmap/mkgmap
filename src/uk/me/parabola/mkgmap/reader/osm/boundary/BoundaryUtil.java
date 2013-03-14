/*
 * Copyright (C) 2006, 2011.
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

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Tags;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.EnhancedProperties;
import uk.me.parabola.util.Java2DConverter;
import uk.me.parabola.util.MultiHashMap;

public class BoundaryUtil {
	private static final Logger log = Logger.getLogger(BoundaryUtil.class);

	private static final int UNKNOWN_DATA_FORMAT = 0;
	private static final int LEGACY_DATA_FORMAT = 1;
	private static final int RAW_DATA_FORMAT_V1 = 2;
	private static final int QUADTREE_DATA_FORMAT_V1 = 3;
	
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

			List<BoundaryElement> bElements = new ArrayList<BoundaryElement>();
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
	 * Wrapper for {@link #loadQuadTrees(String, String, uk.me.parabola.imgfmt.app.Area, EnhancedProperties)}
	 * @param boundaryDirName a directory name or zip file containing the *.bnd file
	 * @param boundaryFileName the *.bnd file name
	 * @return
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
	 * @return
	 */
	public static Map<String,BoundaryQuadTree> loadQuadTrees (String boundaryDirName, 
			List<String> boundaryFileNames, 
			uk.me.parabola.imgfmt.app.Area searchBbox, EnhancedProperties props){
		Map<String,BoundaryQuadTree>  trees = new HashMap<String,BoundaryQuadTree>();
		File boundaryDir = new File(boundaryDirName);
		BoundaryQuadTree bqt;
			if (boundaryDir.isDirectory()){
				for (String boundaryFileName: boundaryFileNames){
					log.info("loading boundary file:", boundaryFileName);
					// no support for nested directories
					File boundaryFile = new File(boundaryDir, boundaryFileName);
					try {
						if (boundaryFile.exists()){
							InputStream stream = new FileInputStream(boundaryFile);
							bqt = BoundaryUtil.loadQuadTreeFromStream(stream, boundaryFileName, searchBbox, props);
							if (bqt != null)
								trees.put(boundaryFileName,bqt);
						}
					} catch (IOException exp) {
						log.error("Cannot load boundary file " +  boundaryFileName + "." + exp);
					}
					
				}
			} else if (boundaryDirName.endsWith(".zip")) {
				String  currentFileName = "";
				try{
					ZipFile zipFile = new ZipFile(boundaryDir);

					for (String boundaryFileName : boundaryFileNames){
						log.info("loading boundary file:", boundaryFileName);
						currentFileName = boundaryFileName;
						// direct access  
						ZipEntry entry = zipFile.getEntry(boundaryFileName);
						if (entry != null){ 
							bqt = BoundaryUtil.loadQuadTreeFromStream(zipFile.getInputStream(entry), 
									boundaryFileName, searchBbox, props);
							if (bqt != null)
								trees.put(boundaryFileName,bqt);
						}
					}
					zipFile.close();
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
		Path2D.Double path = new Path2D.Double();
		int windingRule = inpStream.readInt();
		path.setWindingRule(windingRule);
		int type = inpStream.readInt(); 
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
			return new Area(path);
		}
		return null;
	}
	
	/**
	 * code to read the area info from a stream in legacy format 
	 * @param inpStream the already opened DataInputStream
	 * @param id  the boundary Id (used for meaningful messages)
	 * @return the Area (can be empty)
	 * @throws IOException
	 */
	public static Area readAreaLegacyFormat(DataInputStream inpStream, String id) throws IOException{
		int noBElems = inpStream.readInt();
	
		// the first area is always an outer area and will be assigned to the variable
		Area area = null;
	
		for (int i = 0; i < noBElems; i++) {
			boolean outer = inpStream.readBoolean();
			int noCoords = inpStream.readInt();
			log.debug("No of coords",noCoords);
			List<Coord> points = new ArrayList<Coord>(noCoords);
			for (int c = 0; c < noCoords; c++) {
				int lat = inpStream.readInt();
				int lon = inpStream.readInt();
				points.add(new Coord(lat, lon));
			}
	
			Area elemArea = Java2DConverter.createArea(points);
			if (outer) {
				if (area == null) {
					area = elemArea;
				} else {
					area.add(elemArea);
				}
			} else {
				if (area == null) {
					log.warn("Boundary: " + id);
					log.warn("Outer way is tagged incosistently as inner way. Ignoring it.");
					log.warn("Points: "+points);
				} else {
					area.subtract(elemArea);
				}
			}
		}
		if (area == null)
			area = new Area();
		return area;
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
		List<Boundary> boundaryList = new ArrayList<Boundary>();

		try {
			while (true) {
				int minLat = inpStream.readInt();
				int minLong = inpStream.readInt();
				int maxLat = inpStream.readInt();
				int maxLong = inpStream.readInt();
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
	 * Read boundary info saved in legacy format. 
	 * @param inpStream the already opened DataInputStream
	 * @param fname the related file name of the *.bnd file
	 * @param bbox a bounding box. Data outside of this box is ignored.
	 * @return
	 * @throws IOException
	 */
	private static List<Boundary> readStreamLegacyFormat(
			DataInputStream inpStream, String fname,
			uk.me.parabola.imgfmt.app.Area bbox) throws IOException			{
		List<Boundary> boundaryList = new ArrayList<Boundary>();

		try {
			while (true) {
				int minLat = inpStream.readInt();
				int minLong = inpStream.readInt();
				int maxLat = inpStream.readInt();
				int maxLong = inpStream.readInt();
				log.debug("Next boundary. Lat min:",minLat,"max:",maxLat,"Long min:",minLong,"max:",maxLong);
				uk.me.parabola.imgfmt.app.Area rBbox = new uk.me.parabola.imgfmt.app.Area(
						minLat, minLong, maxLat, maxLong);
				int bSize = inpStream.readInt();
				log.debug("Size:",bSize);

				if ( bbox == null || bbox.intersects(rBbox)) {
					log.debug("Bbox intersects. Load the boundary");
					Tags tags = new Tags();
					int noOfTags = inpStream.readInt();
					String id = "?";
					for (int i = 0; i < noOfTags; i++) {
						String name = inpStream.readUTF();
						String value = inpStream.readUTF();
						// boundary.id was always saved together with the other tags
						if (name.equals("mkgmap:boundaryid")){  
							id = value;								
							continue;
						}
						if (name.equals("mkgmap:lies_in") == false) // ignore info from older preparer version 
							tags.put(name, value.intern()); 					
					}
					Area area = null;
					area = readAreaLegacyFormat(inpStream, id);
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
		List<String> names = new ArrayList<String>();
		for (int latSplit = BoundaryUtil.getSplitBegin(bbox.getMinLat()); latSplit <= BoundaryUtil
				.getSplitBegin(bbox.getMaxLat()); latSplit += BoundaryUtil.RASTER) {
			for (int lonSplit = BoundaryUtil.getSplitBegin(bbox.getMinLong()); lonSplit <= BoundaryUtil
					.getSplitBegin(bbox.getMaxLong()); lonSplit += BoundaryUtil.RASTER) {
				names.add("bounds_"+ getKey(latSplit, lonSplit) + ".bnd");
			}
		}
		return names;
	}

	/** 
	 * return the available *.bnd files in dirName, either dirName has to a directory or a zip file.   
	 * @param dirName : path to a directory or a zip file containing the *.bnd files
	 * @return
	 */
	public static List<String> getBoundaryDirContent(String dirName) {
		List<String> names = new ArrayList<String>();
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
				try {
					ZipFile zipFile = new ZipFile(boundaryDir);
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
					zipFile.close();
					if (!isFlat){
						log.error("boundary zip file contains directories. Files in directories will be ignored." + dirName);			
					}
				} catch (IOException ioe) {
					System.err.println("Unhandled exception:");
					ioe.printStackTrace();
				}
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
	 * @param boundaryFile the boundary file
	 * @return the bounding box
	 */
	public static uk.me.parabola.imgfmt.app.Area getBbox(String boundaryFileName) {
		String filename = new String(boundaryFileName);
		// cut off the extension
		filename = filename.substring(0,filename.length()-4);
		String[] fParts = filename.split(Pattern.quote("_"));
		
		int lat = Integer.valueOf(fParts[1]);
		int lon = Integer.valueOf(fParts[2]);
		
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
		uk.me.parabola.imgfmt.app.Area qtBbox = BoundaryUtil.getBbox(fname);
		try {
			DataInputStream inpStream = new DataInputStream(
					new BufferedInputStream(stream, 1024 * 1024));

			try {
				// 1st read the mkgmap release the boundary file is created by
				String mkgmapRel = "?";
				String firstId = inpStream.readUTF();
				int format = UNKNOWN_DATA_FORMAT;
				long createTime = inpStream.readLong();
				if ("BND".equals(firstId) == false){
					if (firstId.endsWith("_raw" ) || firstId.endsWith("_quadtree")){
						// none-trunk version formats
					}
					else
						format = LEGACY_DATA_FORMAT;
					mkgmapRel = firstId;
				}
				else {
					int headerLength = inpStream.readInt();
					byte[] header = new byte[headerLength];
					int bytesRead = 0;
					while (bytesRead < headerLength) {
						int nBytes = inpStream.read(header, bytesRead, headerLength-bytesRead);
						if (nBytes<0) {
							throw new IOException("Cannot read header with size "+headerLength);
						} else {
							bytesRead += nBytes;
						}
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
				}
				if (format == UNKNOWN_DATA_FORMAT)
					throw new FormatException("Unsupported file format ");

				if (log.isDebugEnabled()) {
					log.debug("File created by mkgmap release",mkgmapRel,"at",new Date(createTime));
				}
				
				
				if (format == QUADTREE_DATA_FORMAT_V1)
					bqt = new BoundaryQuadTree(inpStream, qtBbox, searchBbox, props);
				else{ 
					List<Boundary> boundaryList;
					if (format == RAW_DATA_FORMAT_V1)
						boundaryList = readStreamRawFormat(inpStream, fname,searchBbox);
					else 
						boundaryList = readStreamLegacyFormat(inpStream, fname,searchBbox);
					if (boundaryList == null || boundaryList.isEmpty())
						return null;
					boundaryList = mergePostalCodes(boundaryList);
					bqt = new BoundaryQuadTree(qtBbox, boundaryList, props);
				}
			} catch (EOFException exp) {
				// it's always thrown at the end of the file
				//				log.error("Got EOF at the end of the file");
			} 
			catch (FormatException exp) {
				log.error("Failed to read boundary file " + fname + " " + exp.getMessage());
			} 
			inpStream.close();
		} finally {
			if (stream != null)
				stream.close();
		}
		return bqt;
	}
	
	/**
	 * Merges boundaries with the same postal code.
	 * @param boundaries a list of boundaries
	 * @return the boundary list with postal code areas merged
	 */
	private static List<Boundary> mergePostalCodes(List<Boundary> boundaries) {
		List<Boundary> mergedList = new ArrayList<Boundary>(boundaries.size());
		
		MultiHashMap<String, Boundary> equalPostalCodes = new MultiHashMap<String, Boundary>();
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
				if ("administrative".equals(boundaryVal)) {
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
					return false;					
				}  else {
					return false;
				}
			} else {
				return false;
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
				return false;
			} else {
				return false;
			}
		} else {
			return false;
		}
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
	 * Add the path of an area to an existing path. 
	 * @param path
	 * @param area
	 */
	public static void addToPath (Path2D.Double path, Area area){
		PathIterator pit = area.getPathIterator(null);
		double[] res = new double[6];
		path.setWindingRule(pit.getWindingRule());
		while (!pit.isDone()) {
			int type = pit.currentSegment(res);
			switch (type) {
			case PathIterator.SEG_LINETO:
				path.lineTo(res[0],res[1]);
				break;
			case PathIterator.SEG_MOVETO: 
				path.moveTo(res[0],res[1]);
				break;
			case PathIterator.SEG_CLOSE:
				path.closePath();
				break;
			default:
				log.error("Unsupported path iterator type " + type
						+ ". This is an mkgmap error.");
			}

			pit.next();
		}
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

		}
