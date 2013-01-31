/*
 * Copyright (C) 2010-2012.
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
package uk.me.parabola.mkgmap.reader.osm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LineClipper;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.osmstyle.StyleImpl;
import uk.me.parabola.mkgmap.reader.osm.xml.Osm5PrecompSeaDataSource;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Code to generate sea polygons from the coastline ways.
 *
 * Currently there are a number of different options.
 * Should pick one that works well and make it the default.
 *
 */
public class SeaGenerator extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger.getLogger(SeaGenerator.class);

	private boolean generateSeaUsingMP = true;
	private int maxCoastlineGap;
	private boolean allowSeaSectors = true;
	private boolean extendSeaSectors;
	private String[] landTag = { "natural", "land" };
	private boolean floodblocker;
	private int fbGap = 40;
	private double fbRatio = 0.5d;
	private int fbThreshold = 20;
	private boolean fbDebug;

	private ElementSaver saver;

	private List<Way> shoreline = new ArrayList<Way>();
	private boolean roadsReachBoundary; // todo needs setting somehow
	private boolean generateSeaBackground = true;

	private String[] coastlineFilenames;
	private StyleImpl fbRules;
	
	/** The size (lat and long) of the precompiled sea tiles */
	public final static int PRECOMP_RASTER = 1 << 15;
	
	/**
	 * The directory of the precompiled sea tiles or <code>null</code> if
	 * precompiled sea should not be used.
	 */
	private File precompSeaDir;

	private static final byte SEA_TILE = 's';
	private static final byte LAND_TILE = 'l';
	private static final byte MIXED_TILE = 'm';
	
	/**
	 * The index is a grid [lon][lat]. Each element defines the content of one precompiled 
	 * sea tile which are {@link #SEA_TYPE}, {@link #LAND_TYPE}, or {@link #MIXED_TYPE}, or 0 for unknown
	 */
	private static ThreadLocal<byte[][]> precompIndex = new ThreadLocal<byte[][]>();
	private static ThreadLocal<String> precompSeaExt = new ThreadLocal<String>();
	private static ThreadLocal<String> precompSeaPrefix = new ThreadLocal<String>();
	// useful constants defining the min/max map units of the precompiled sea tiles
	private static final int MIN_LAT = Utils.toMapUnit(-90.0);
	private static final int MAX_LAT = Utils.toMapUnit(90.0);
	private static final int MIN_LON = Utils.toMapUnit(-180.0);
	private static final int MAX_LON = Utils.toMapUnit(180.0);
	private final static Pattern keySplitter = Pattern.compile(Pattern.quote("_"));
	

	private static final List<Class<? extends LoadableMapDataSource>> precompSeaLoader;

	static {
		String[] sources = {
				"uk.me.parabola.mkgmap.reader.osm.bin.OsmBinPrecompSeaDataSource",
				// must be last as it is the default
				"uk.me.parabola.mkgmap.reader.osm.xml.Osm5PrecompSeaDataSource", };

		precompSeaLoader = new ArrayList<Class<? extends LoadableMapDataSource>>();

		for (String source : sources) {
			try {
				@SuppressWarnings({ "unchecked" })
				Class<? extends LoadableMapDataSource> c = (Class<? extends LoadableMapDataSource>) Class
						.forName(source);
				precompSeaLoader.add(c);
			} catch (ClassNotFoundException e) {
				// not available, try the rest
			} catch (NoClassDefFoundError e) {
				// not available, try the rest
			}
		}
	}
	
	
	/**
	 * Sort out options from the command line.
	 * Returns true only if the option to generate the sea is active, so that
	 * the whole thing is omitted if not used.
	 */
	public boolean init(ElementSaver saver, EnhancedProperties props) {
		this.saver = saver;

		// check if the precomp-sea option is set
		String precompSea = props.getProperty("precomp-sea", null);
		if (precompSea != null) {
			precompSeaDir = new File(precompSea);
			if (precompSeaDir.exists()) {
				if (precompIndex.get() == null) {
					File indexFile = new File(precompSeaDir, "index.txt.gz");
					if (indexFile.exists() == false) {
						// check if the unzipped index file exists
						indexFile = new File(precompSeaDir, "index.txt");
					}
					
					if (indexFile.exists()) {
						int indexWidth = (getPrecompTileStart(MAX_LON) - getPrecompTileStart(MIN_LON)) / PRECOMP_RASTER;
						int indexHeight = (getPrecompTileStart(MAX_LAT) - getPrecompTileStart(MIN_LAT)) / PRECOMP_RASTER;
						
						try {
							InputStream fileStream = new FileInputStream(indexFile);
							if (indexFile.getName().endsWith(".gz")) {
								fileStream = new GZIPInputStream(fileStream);
							}
							LineNumberReader indexReader = new LineNumberReader(
									new InputStreamReader(fileStream));
							Pattern csvSplitter = Pattern.compile(Pattern
									.quote(";"));
							String indexLine = null;
							
							byte[][] indexGrid = new byte[indexWidth+1][indexHeight+1];
							boolean detectExt = true; 
							String prefix = null;
							String ext = null;
							
							while ((indexLine = indexReader.readLine()) != null) {
								if (indexLine.startsWith("#")) {
									// comment
									continue;
								}
								String[] items = csvSplitter.split(indexLine);
								if (items.length != 2) {
									log.warn("Invalid format in index file name:",
											indexLine);
									continue;
								}
								String precompKey = items[0];
								byte type = updatePrecompSeaTileIndex(precompKey, items[1], indexGrid);
								if (type == '?'){
									log.warn("Invalid format in index file name:",
											indexLine);
									continue;
								}
								if (type == MIXED_TILE){
									// make sure that all file names are using the same name scheme
									int prePos = items[1].indexOf(items[0]);
									if (prePos >= 0){
										if (detectExt){
											prefix = items[1].substring(0, prePos);
											ext = items[1].substring(prePos+items[0].length());
											detectExt = false;
										} else {
											StringBuilder sb = new StringBuilder(prefix);
											sb.append(precompKey);
											sb.append(ext);												
											if (items[1].equals(sb.toString()) == false){
												log.warn("Unexpected file name in index file:",
													 indexLine);
											}
										}
									}
								}

							}
							indexReader.close();
							precompIndex.set(indexGrid);
							precompSeaPrefix.set(prefix);
							precompSeaExt.set(ext);
							
						} catch (IOException exp) {
							log.error("Cannot read index file " + indexFile,
									exp);
							precompIndex.set(null);
							precompSea = null;
						}
					} else {
						log.error("Disable precompiled sea due to missing index.txt file in precompiled sea directory "
							+ precompSeaDir);
						System.err.println("Disable precompiled sea due to missing index.txt file in precompiled sea directory "
							+ precompSeaDir);
						precompIndex.set(null);
						precompSeaDir = null;
					}
				}
			} else {
				log.error("Directory with precompiled sea does not exist: "
						+ precompSeaDir);
				System.err.println("Directory with precompiled sea does not exist: "
						+ precompSeaDir);
				precompSeaDir = null;
			}
		}
		
		String gs = props.getProperty("generate-sea", null);
		boolean generateSea = gs != null || precompSea != null;
		if (gs != null) {
			for(String o : gs.split(",")) {
				if("no-mp".equals(o) ||
						"polygon".equals(o) ||
						"polygons".equals(o))
					generateSeaUsingMP = false;
				else if("multipolygon".equals(o))
					generateSeaUsingMP = true;
				else if(o.startsWith("land-tag="))
					landTag = o.substring(9).split("=");
				else if (precompSea == null) {
					// the other options are valid only if not using precompiled sea data
					if(o.startsWith("close-gaps="))
						maxCoastlineGap = (int)Double.parseDouble(o.substring(11));
					else if("no-sea-sectors".equals(o))
						allowSeaSectors = false;
					else if("extend-sea-sectors".equals(o)) {
						allowSeaSectors = false;
						extendSeaSectors = true;
					}
					else if("floodblocker".equals(o)) 
						floodblocker = true;
					else if(o.startsWith("fbgap="))
						fbGap = (int)Double.parseDouble(o.substring("fbgap=".length()));
					else if(o.startsWith("fbratio="))
						fbRatio = Double.parseDouble(o.substring("fbratio=".length()));
					else if(o.startsWith("fbthres="))
						fbThreshold = (int)Double.parseDouble(o.substring("fbthres=".length()));
					else if("fbdebug".equals(o)) 
						fbDebug = true;
				}
				else if(o.isEmpty())
					continue;
				else {
					if(!"help".equals(o))
						System.err.println("Unknown sea generation option '" + o + "'");
					System.err.println("Known sea generation options are:");
					System.err.println("  multipolygon        use a multipolygon (default)");
					System.err.println("  polygons | no-mp    use polygons rather than a multipolygon");
					System.err.println("  no-sea-sectors      disable use of \"sea sectors\"");
					System.err.println("  extend-sea-sectors  extend coastline to reach border");
					System.err.println("  land-tag=TAG=VAL    tag to use for land polygons (default natural=land)");
					System.err.println("  close-gaps=NUM      close gaps in coastline that are less than this distance (metres)");
					System.err.println("  floodblocker        enable the floodblocker (for multipolgon only)");
					System.err.println("  fbgap=NUM           points closer to the coastline are ignored for flood blocking (default 40)");
					System.err.println("  fbthres=NUM         min points contained in a polygon to be flood blocked (default 20)");
					System.err.println("  fbratio=NUM         min ratio (points/area size) for flood blocking (default 0.5)");
				}
			}
			
			// init floodblocker and coastlinefile loader only 
			// if precompSea is not set
			if (precompSea == null) {
				if (floodblocker) {
					try {
						fbRules = new StyleImpl(null, "floodblocker");
					} catch (FileNotFoundException e) {
						log.error("Cannot load file floodblocker rules. Continue floodblocking disabled.");
						floodblocker = false;
					}
				}

				String coastlineFileOpt = props.getProperty("coastlinefile", null);
				if (coastlineFileOpt != null) {
					coastlineFilenames = coastlineFileOpt.split(",");
					CoastlineFileLoader.getCoastlineLoader().setCoastlineFiles(
							coastlineFilenames);
					CoastlineFileLoader.getCoastlineLoader().loadCoastlines();
					log.info("Coastlines loaded");
				} else {
					coastlineFilenames = null;
				}
			}
		}

		return generateSea;
	}
	
	/**
	 * Retrieves the start value of the precompiled tile.
	 * @param value the value for which the start value is calculated
	 * @return the tile start value
	 */
	public static int getPrecompTileStart(int value) {
		int rem = value % PRECOMP_RASTER;
		if (rem == 0) {
			return value;
		} else if (value >= 0) {
			return value - rem;
		} else {
			return value - PRECOMP_RASTER - rem;
		}
	}

	/**
	 * Retrieves the end value of the precompiled tile.
	 * @param value the value for which the end value is calculated
	 * @return the tile end value
	 */
	public static int getPrecompTileEnd(int value) {
		int rem = value % PRECOMP_RASTER;
		if (rem == 0) {
			return value;
		} else if (value >= 0) {
			return value + PRECOMP_RASTER - rem;
		} else {
			return value - rem;
		}
	}	
	
	public Set<String> getUsedTags() {
		HashSet<String> usedTags = new HashSet<String>();
		if (coastlineFilenames == null) {
			usedTags.add("natural");
		}
		if (floodblocker) {
			usedTags.addAll(fbRules.getUsedTags());
		}
		
		if (log.isDebugEnabled())
			log.debug("Sea generator used tags: "+usedTags);
		
		return usedTags;
	}

	/**
	 * Test to see if the way is part of the shoreline and if it is
	 * we save it.
	 * @param way The way to test.
	 */
	public void onAddWay(Way way) {
		String natural = way.getTag("natural");
		if(natural != null) {
			if("coastline".equals(natural)) {
				way.deleteTag("natural");
				if (coastlineFilenames == null && precompSeaDir == null)
					shoreline.add(way);
				
				if (precompSeaDir != null) {
					// add a copy of this way to be able to draw the coastline which is not possible with precompiled sea
					Way coastlineWay = new Way(FakeIdGenerator.makeFakeId(), way.getPoints());
					coastlineWay.addTag("natural", "coastline");
					// tag that this way is used as line only
					coastlineWay.addTag(MultiPolygonRelation.STYLE_FILTER_TAG, MultiPolygonRelation.STYLE_FILTER_LINE);
					saver.addWay(coastlineWay);
				}
			} else if (natural.contains(";")) {
				// cope with compound tag value
				String others = null;
				boolean foundCoastline = false;
				for(String n : natural.split(";")) {
					if("coastline".equals(n.trim()))
						foundCoastline = true;
					else if(others == null)
						others = n;
					else
						others += ";" + n;
				}

				if(foundCoastline) {
					way.deleteTag("natural");
					if(others != null)
						way.addTag("natural", others);
					if (coastlineFilenames == null && precompSeaDir == null)
						shoreline.add(way);
					
					if (precompSeaDir != null) {
						// add a copy of this way to be able to draw the coastline which is not possible with precompiled sea
						Way coastlineWay = new Way(FakeIdGenerator.makeFakeId(), way.getPoints());
						coastlineWay.addTag("natural", "coastline");
						// tag that this way is used as line only
						coastlineWay.addTag(MultiPolygonRelation.STYLE_FILTER_TAG, MultiPolygonRelation.STYLE_FILTER_LINE);
						saver.addWay(coastlineWay);
					}
				}
			}
		}
	}

	/**
	 * Creates a reader for the given filename of the precomiled sea tile.
	 * @param filename precompiled sea tile 
	 * @return the reader for the tile
	 */
	private static OsmMapDataSource createTileReader(String filename) {
		for (Class<? extends LoadableMapDataSource> loader : precompSeaLoader) {
			try {
				LoadableMapDataSource src = loader.newInstance();
				if (filename != null && src instanceof OsmMapDataSource
						&& src.isFileSupported(filename))
					return (OsmMapDataSource) src;
			} catch (InstantiationException e) {
				// try the next one.
			} catch (IllegalAccessException e) {
				// try the next one.
			} catch (NoClassDefFoundError e) {
				// try the next one
			}
		}

		// Give up and assume it is in the XML format. If it isn't we will get
		// an error soon enough anyway.
		return new Osm5PrecompSeaDataSource();
	}
	
	/**
	 * Loads the precomp sea tile with the given filename.
	 * @param filename the filename of the precomp sea tile
	 * @return all ways of the tile
	 * @throws FileNotFoundException if the tile could not be found
	 */
	private Collection<Way> loadPrecompTile(String filename)
			throws FileNotFoundException {
		OsmMapDataSource src = createTileReader(filename);
		src.config(new EnhancedProperties());
		log.info("Started loading coastlines from", filename);
		src.load(filename);
		log.info("Finished loading coastlines from", filename);
		return src.getElementSaver().getWays().values();
	}
	
	/**
	 * Calculates the key names of the precompiled sea tiles for the bounding box.
	 * The key names are compiled of {@code lat+"_"+lon}.
	 * @return the key names for the bounding box
	 */
	private List<String> getPrecompKeyNames() {
		Area bounds = saver.getBoundingBox();
		List<String> precompKeys = new ArrayList<String>();
		for (int lat = getPrecompTileStart(bounds.getMinLat()); lat < getPrecompTileEnd(bounds
				.getMaxLat()); lat += PRECOMP_RASTER) {
			for (int lon = getPrecompTileStart(bounds.getMinLong()); lon < getPrecompTileEnd(bounds
					.getMaxLong()); lon += PRECOMP_RASTER) {
				precompKeys.add(lat+"_"+lon);
			}
		}
		return precompKeys;
	}
	
	/**
	 * Get the tile name from the index. 
	 * @param precompKey The key name is compiled of {@code lat+"_"+lon}. 
	 * @return either "land" or "sea" or a file name or null
	 */
	private String getTileName(String precompKey){
		byte[][] index = precompIndex.get();
		String[] tileCoords = keySplitter.split(precompKey);
		int lat = Integer.valueOf(tileCoords[0]); 
		int lon = Integer.valueOf(tileCoords[1]); 
		int latIndex = (MAX_LAT-lat) / PRECOMP_RASTER;
		int lonIndex = (MAX_LON-lon) / PRECOMP_RASTER;
		byte type = index[lonIndex][latIndex]; 
		switch (type){
		case SEA_TILE: return "sea"; 
		case LAND_TILE: return "land"; 
		case MIXED_TILE: return precompSeaPrefix.get() + precompKey + precompSeaExt.get(); 
		default:  return null;
		}
	}
	

	/**
	 * Update the index grid for the element identified by precompKey. 
	 * @param precompKey The key name is compiled of {@code lat+"_"+lon}. 
	 * @param fileName either "land", "sea", or a file name containing OSM data
	 * @param indexGrid the previously allocated index grid  
	 * @return the byte that was saved in the index grid 
	 */
	private byte updatePrecompSeaTileIndex (String precompKey, String fileName, byte[][] indexGrid){
		String[] tileCoords = keySplitter.split(precompKey);
		byte type = '?';
		if (tileCoords.length == 2){
			int lat = Integer.valueOf(tileCoords[0]); 
			int lon = Integer.valueOf(tileCoords[1]); 
			int latIndex = (MAX_LAT - lat) / PRECOMP_RASTER;
			int lonIndex = (MAX_LON - lon) / PRECOMP_RASTER;

			if ("sea".equals(fileName))
				type = SEA_TILE;
			else if ("land".equals(fileName))
				type = LAND_TILE;
			else 
				type = MIXED_TILE;

			indexGrid[lonIndex][latIndex] = type;
		}
		return type;
	}
	
	/**
	 * Loads the precompiled sea tiles and adds the data to the 
	 * element saver.
	 */
	private void addPrecompSea() {
		log.info("Load precompiled sea tiles");

		// flag if all tiles contains sea or way only
		// this is important for polygon processing
		boolean distinctTilesOnly = true;
		
		List<Way> landWays = new ArrayList<Way>();
		List<Way> seaWays = new ArrayList<Way>();
		// get the index with assignment key => sea/land/tilename
		
		
		for (String precompKey : getPrecompKeyNames()) {
			String tileName = getTileName(precompKey);
			
			if (tileName == null ) {
				log.error("Precompile sea tile "+precompKey+" is missing in the index. Skipping.");
				continue;
			}
			
			if ("sea".equals(tileName) || "land".equals(tileName)) {
				// the whole precompiled tile is filled with either land or sea
				// => create a rectangle that covers the whole precompiled tile 
				Way w = new Way(FakeIdGenerator.makeFakeId());
				w.addTag("natural", tileName);
				String[] tileCoords = keySplitter.split(precompKey);
				int minLat = Integer.valueOf(tileCoords[0]);
				int minLon = Integer.valueOf(tileCoords[1]);
				int maxLat = minLat + PRECOMP_RASTER;
				int maxLon = minLon + PRECOMP_RASTER;
				w.addPoint(new Coord(minLat,minLon));
				w.addPoint(new Coord(minLat,maxLon));
				w.addPoint(new Coord(maxLat,maxLon));
				w.addPoint(new Coord(maxLat,minLon));
				w.addPoint(new Coord(minLat,minLon));
				
				if ("sea".equals(tileName)) {
					seaWays.add(w);
				} else {
					landWays.add(w);
				}
				
			} else {
				distinctTilesOnly = false;
				String precompTile = new File(precompSeaDir,tileName).getAbsolutePath();
				try {
					Collection<Way> seaPrecompWays = loadPrecompTile(precompTile);
					
					if (log.isDebugEnabled())
						log.debug(seaPrecompWays.size(), "precomp sea ways from",
							precompTile, "loaded.");

					for (Way w : seaPrecompWays) {
						// set a new id to be sure that the precompiled ids do not
						// interfere with the ids of this run
						w.setId(FakeIdGenerator.makeFakeId());
					
						if ("land".equals(w.getTag("natural"))) {
							landWays.add(w);
						} else {
							seaWays.add(w);
						}
					}
				} catch (FileNotFoundException exp) {
					log.error("Preompiled sea tile " + precompTile + " not found.");
				} catch (Exception exp) {
					log.error(exp);
					exp.printStackTrace();
				}
			}
		}
		
		// check if the land tags need to be changed
		if (landTag != null && ("natural".equals(landTag[0]) && "land".equals(landTag[1])) == false) {
			for (Way w : landWays) {
				w.deleteTag("natural");
				w.addTag(landTag[0], landTag[1]);
			}
		}
		
		if (generateSeaUsingMP || distinctTilesOnly) {
			// when using multipolygons use the data directly from the precomp files 
			// also with polygons if all tiles are using either sea or land only
			for (Way w : landWays) {
				saver.addWay(w);
			}
			for (Way w : seaWays) {
				saver.addWay(w);
			}
		} else {
			// using polygons
			
			Area bounds = saver.getBoundingBox();
			// first add the complete bounding box as sea
			Way sea = new Way(FakeIdGenerator.makeFakeId());
			sea.addPoint(new Coord(bounds.getMinLat(), bounds.getMinLong()));
			sea.addPoint(new Coord(bounds.getMinLat(), bounds.getMaxLong()));
			sea.addPoint(new Coord(bounds.getMaxLat(), bounds.getMaxLong()));
			sea.addPoint(new Coord(bounds.getMaxLat(), bounds.getMinLong()));
			sea.addPoint(new Coord(bounds.getMinLat(), bounds.getMinLong()));
			sea.addTag("natural", "sea");
			
			for (Way w : landWays) {
				saver.addWay(w);
			}
		}
	}

	
	
	/**
	 * Joins the given segments to closed ways as good as possible.
	 * @param segments a list of closed and unclosed ways
	 * @return a list of ways completely joined
	 */
	public static ArrayList<Way> joinWays(Collection<Way> segments) {
		ArrayList<Way> joined = new ArrayList<Way>((int)Math.ceil(segments.size()*0.5));
		Map<Coord, Way> beginMap = new HashMap<Coord, Way>();

		for (Way w : segments) {
			if (w.isClosed()) {
				joined.add(w);
			} else if (w.getPoints() != null && w.getPoints().size() > 1){
				List<Coord> points = w.getPoints();
				beginMap.put(points.get(0), w);
			} else {
				log.info("Discard coastline way",w.getId(),"because consists of less than 2 points");
			}
		}
		segments.clear();

		int merged = 1;
		while (merged > 0) {
			merged = 0;
			for (Way w1 : beginMap.values()) {
				if (w1.isClosed()) {
					// this should not happen
					log.error("joinWays2: Way "+w1+" is closed but contained in the begin map");
					joined.add(w1);
					beginMap.remove(w1.getPoints().get(0));
					merged=1;
					break;
				}

				List<Coord> points1 = w1.getPoints();
				Way w2 = beginMap.get(points1.get(points1.size() - 1));
				if (w2 != null) {
					log.info("merging: ", beginMap.size(), w1.getId(),
							w2.getId());
					List<Coord> points2 = w2.getPoints();
					Way wm;
					if (FakeIdGenerator.isFakeId(w1.getId())) {
						wm = w1;
					} else {
						wm = new Way(FakeIdGenerator.makeFakeId());
						wm.getPoints().addAll(points1);
						beginMap.put(points1.get(0), wm);
					}
					wm.getPoints().addAll(points2.subList(1, points2.size()));
					beginMap.remove(points2.get(0));
					merged++;
					
					if (wm.isClosed()) {
						joined.add(wm);
						beginMap.remove(wm.getPoints().get(0));
					}
					break;
				}
			}
		}
		log.info(joined.size(),"closed ways.",beginMap.size(),"unclosed ways.");
		joined.addAll(beginMap.values());
		return joined;
	}

	/**
	 * All done, process the saved shoreline information and construct the polygons.
	 */
	public void end() {
		// precompiled sea has highest priority
		// if it is set do not perform any other algorithm
		if (precompSeaDir != null) {
			addPrecompSea();
			return;
		}

		Area seaBounds = saver.getBoundingBox();
		if (coastlineFilenames == null) {
			log.info("Shorelines before join", shoreline.size());
			shoreline = joinWays(shoreline);
		} else {
			shoreline.addAll(CoastlineFileLoader.getCoastlineLoader()
					.getCoastlines(seaBounds));
			log.info("Shorelines from extra file:", shoreline.size());
		}

		int closedS = 0;
		int unclosedS = 0;
		for (Way w : shoreline) {
			if (w.isClosed()) {
				closedS++;
			} else {
				unclosedS++;
			}
		}
		log.info("Closed shorelines", closedS);
		log.info("Unclosed shorelines", unclosedS);

		// clip all shoreline segments
		clipShorlineSegments(shoreline, seaBounds);

		log.info("generating sea, seaBounds=", seaBounds);
		int minLat = seaBounds.getMinLat();
		int maxLat = seaBounds.getMaxLat();
		int minLong = seaBounds.getMinLong();
		int maxLong = seaBounds.getMaxLong();
		Coord nw = new Coord(minLat, minLong);
		Coord ne = new Coord(minLat, maxLong);
		Coord sw = new Coord(maxLat, minLong);
		Coord se = new Coord(maxLat, maxLong);

		if(shoreline.isEmpty()) {
			// no sea required
			// even though there is no sea, generate a land
			// polygon so that the tile's background colour will
			// match the land colour on the tiles that do contain
			// some sea
			long landId = FakeIdGenerator.makeFakeId();
			Way land = new Way(landId);
			land.addPoint(nw);
			land.addPoint(sw);
			land.addPoint(se);
			land.addPoint(ne);
			land.addPoint(nw);
			land.addTag(landTag[0], landTag[1]);
			// no matter if the multipolygon option is used it is
			// only necessary to create a land polygon
			saver.addWay(land);
			// nothing more to do
			return;
		}

		long multiId = FakeIdGenerator.makeFakeId();
		Relation seaRelation = null;
		if(generateSeaUsingMP) {
			log.debug("Generate seabounds relation",multiId);
			seaRelation = new GeneralRelation(multiId);
			seaRelation.addTag("type", "multipolygon");
			seaRelation.addTag("natural", "sea");
		}

		List<Way> islands = new ArrayList<Way>();

		// handle islands (closed shoreline components) first (they're easy)
		handleIslands(shoreline, seaBounds, islands);

		// the remaining shoreline segments should intersect the boundary
		// find the intersection points and store them in a SortedMap
		NavigableMap<EdgeHit, Way> hitMap = findIntesectionPoints(shoreline, seaBounds, seaRelation);

		// now construct inner ways from these segments
		boolean shorelineReachesBoundary = createInnerWays(seaBounds, islands, hitMap);

		if(!shorelineReachesBoundary && roadsReachBoundary) {
			// try to avoid tiles being flooded by anti-lakes or other
			// bogus uses of natural=coastline
			generateSeaBackground = false;
		}

		List<Way> antiIslands = removeAntiIslands(seaRelation, islands);
		if (islands.isEmpty()) {
			// the tile doesn't contain any islands so we can assume
			// that it's showing a land mass that contains some
			// enclosed sea areas - in which case, we don't want a sea
			// coloured background
			generateSeaBackground = false;
		}

		if (generateSeaBackground) {
			// the background is sea so all anti-islands should be
			// contained by land otherwise they won't be visible

			for (Way ai : antiIslands) {
				boolean containedByLand = false;
				for(Way i : islands) {
					if(i.containsPointsOf(ai)) {
						containedByLand = true;
						break;
					}
				}

				if (!containedByLand) {
					// found an anti-island that is not contained by
					// land so convert it back into an island
					ai.deleteTag("natural");
					ai.addTag(landTag[0], landTag[1]);
					if (generateSeaUsingMP) {
						// create a "inner" way for the island
						assert seaRelation != null;
						seaRelation.addElement("inner", ai);
					} 
					log.warn("Converting anti-island starting at", ai.getPoints().get(0).toOSMURL() , "into an island as it is surrounded by water");
				}
			}

			long seaId = FakeIdGenerator.makeFakeId();
			Way sea = new Way(seaId);
			// the sea background area must be a little bigger than all
			// inner land areas. this is a workaround for a mp shortcoming:
			// mp is not able to combine outer and inner if they intersect
			// or have overlaying lines
			// the added area will be clipped later by the style generator
			sea.addPoint(new Coord(nw.getLatitude() - 1,
					nw.getLongitude() - 1));
			sea.addPoint(new Coord(sw.getLatitude() + 1,
					sw.getLongitude() - 1));
			sea.addPoint(new Coord(se.getLatitude() + 1,
					se.getLongitude() + 1));
			sea.addPoint(new Coord(ne.getLatitude() - 1,
					ne.getLongitude() + 1));
			sea.addPoint(new Coord(nw.getLatitude() - 1,
					nw.getLongitude() - 1));
			sea.addTag("natural", "sea");

			log.info("sea: ", sea);
			saver.addWay(sea);
			if(generateSeaUsingMP) {
				assert seaRelation != null;
				seaRelation.addElement("outer", sea);
			}
		} else {
			// background is land

			// generate a land polygon so that the tile's
			// background colour will match the land colour on the
			// tiles that do contain some sea
			long landId = FakeIdGenerator.makeFakeId();
			Way land = new Way(landId);
			land.addPoint(nw);
			land.addPoint(sw);
			land.addPoint(se);
			land.addPoint(ne);
			land.addPoint(nw);
			land.addTag(landTag[0], landTag[1]);
			saver.addWay(land);
			if (generateSeaUsingMP) {
				seaRelation.addElement("inner", land);
			}
		}

		if (generateSeaUsingMP) {
			SeaPolygonRelation coastRel = saver.createSeaPolyRelation(seaRelation);
			coastRel.setFloodBlocker(floodblocker);
			if (floodblocker) {
				coastRel.setFloodBlockerGap(fbGap);
				coastRel.setFloodBlockerRatio(fbRatio);
				coastRel.setFloodBlockerThreshold(fbThreshold);
				coastRel.setFloodBlockerRules(fbRules.getWayRules());
				coastRel.setLandTag(landTag[0], landTag[1]);
				coastRel.setDebug(fbDebug);
			}
			saver.addRelation(coastRel);
		}
		
		shoreline = null;
	}

	/**
	 * Clip the shoreline ways to the bounding box of the map.
	 * @param shoreline All the the ways making up the coast.
	 * @param bounds The map bounds.
	 */
	private void clipShorlineSegments(List<Way> shoreline, Area bounds) {
		List<Way> toBeRemoved = new ArrayList<Way>();
		List<Way> toBeAdded = new ArrayList<Way>();
		for (Way segment : shoreline) {
			List<Coord> points = segment.getPoints();
			List<List<Coord>> clipped = LineClipper.clip(bounds, points);
			if (clipped != null) {
				log.info("clipping", segment);
				toBeRemoved.add(segment);
				for (List<Coord> pts : clipped) {
					long id = FakeIdGenerator.makeFakeId();
					Way shore = new Way(id, pts);
					toBeAdded.add(shore);
				}
			}
		}

		log.info("clipping: adding", toBeAdded.size(), ", removing", toBeRemoved.size());
		shoreline.removeAll(toBeRemoved);
		shoreline.addAll(toBeAdded);
	}

	/**
	 * Pick out the islands and save them for later. They are removed from the
	 * shore line list and added to the island list.
	 *
	 * @param shoreline The collected shore line ways.
	 * @param seaBounds The map boundary.
	 * @param islands The islands are saved to this list.
	 */
	private void handleIslands(List<Way> shoreline, Area seaBounds, List<Way> islands) {
		Iterator<Way> it = shoreline.iterator();
		while (it.hasNext()) {
			Way w = it.next();
			if (w.isClosed()) {
				log.info("adding island", w);
				islands.add(w);
				it.remove();
			}
		}

		closeGaps(shoreline, seaBounds);
		// there may be more islands now
		it = shoreline.iterator();
		while (it.hasNext()) {
			Way w = it.next();
			if (w.isClosed()) {
				log.debug("island after concatenating");
				islands.add(w);
				it.remove();
			}
		}
	}

	private boolean createInnerWays(Area seaBounds, List<Way> islands, NavigableMap<EdgeHit, Way> hitMap) {
		NavigableSet<EdgeHit> hits = hitMap.navigableKeySet();
		boolean shorelineReachesBoundary = false;
		while (!hits.isEmpty()) {
			long id = FakeIdGenerator.makeFakeId();
			Way w = new Way(id);
			saver.addWay(w);

			EdgeHit hit =  hits.first();
			EdgeHit hFirst = hit;
			do {
				Way segment = hitMap.get(hit);
				log.info("current hit:", hit);
				EdgeHit hNext;
				if (segment != null) {
					// add the segment and get the "ending hit"
					log.info("adding:", segment);
					for(Coord p : segment.getPoints())
						w.addPointIfNotEqualToLastPoint(p);
					hNext = getEdgeHit(seaBounds, segment.getPoints().get(segment.getPoints().size()-1));
				} else {
					w.addPointIfNotEqualToLastPoint(hit.getPoint(seaBounds));
					hNext = hits.higher(hit);
					if (hNext == null)
						hNext = hFirst;

					Coord p;
					if (hit.compareTo(hNext) < 0) {
						log.info("joining: ", hit, hNext);
						for (int i=hit.edge; i<hNext.edge; i++) {
							EdgeHit corner = new EdgeHit(i, 1.0);
							p = corner.getPoint(seaBounds);
							log.debug("way: ", corner, p);
							w.addPointIfNotEqualToLastPoint(p);
						}
					} else if (hit.compareTo(hNext) > 0) {
						log.info("joining: ", hit, hNext);
						for (int i=hit.edge; i<4; i++) {
							EdgeHit corner = new EdgeHit(i, 1.0);
							p = corner.getPoint(seaBounds);
							log.debug("way: ", corner, p);
							w.addPointIfNotEqualToLastPoint(p);
						}
						for (int i=0; i<hNext.edge; i++) {
							EdgeHit corner = new EdgeHit(i, 1.0);
							p = corner.getPoint(seaBounds);
							log.debug("way: ", corner, p);
							w.addPointIfNotEqualToLastPoint(p);
						}
					}
					w.addPointIfNotEqualToLastPoint(hNext.getPoint(seaBounds));
				}
				hits.remove(hit);
				hit = hNext;
			} while (!hits.isEmpty() && !hit.equals(hFirst));

			if (!w.isClosed())
				w.getPoints().add(w.getPoints().get(0));
			log.info("adding non-island landmass, hits.size()=" + hits.size());
			islands.add(w);
			shorelineReachesBoundary = true;
		}
		return shorelineReachesBoundary;
	}

	/**
	 * An 'anti-island' is something that has been detected as an island, but the water
	 * is on the inside.  I think you would call this a lake.
	 * @param seaRelation The relation holding the sea.  Only set if we are using multi-polygons for
	 * the sea.
	 * @param islands The island list that was found earlier.
	 * @return The so-called anti-islands.
	 */
	private List<Way> removeAntiIslands(Relation seaRelation, List<Way> islands) {
		List<Way> antiIslands = new ArrayList<Way>();
		for (Way w : islands) {

			if (!FakeIdGenerator.isFakeId(w.getId())) {
				Way w1 = new Way(FakeIdGenerator.makeFakeId());
				w1.getPoints().addAll(w.getPoints());
				// only copy the name tags
				for(String tag : w)
					if(tag.equals("name") || tag.endsWith(":name"))
						w1.addTag(tag, w.getTag(tag));
				w = w1;
			}

			// determine where the water is
			if (Way.clockwise(w.getPoints())) {
				// water on the inside of the poly, it's an
				// "anti-island" so tag with natural=water (to
				// make it visible above the land)
				w.addTag("natural", "water");
				antiIslands.add(w);
				saver.addWay(w);
			} else {
				// water on the outside of the poly, it's an island
				w.addTag(landTag[0], landTag[1]);
				saver.addWay(w);
				if(generateSeaUsingMP) {
					// create a "inner" way for each island
					seaRelation.addElement("inner", w);
				} 
			}
		}

		islands.removeAll(antiIslands);
		return antiIslands;
	}

	/**
	 * Find the points where the remaining shore line segments intersect with the
	 * map boundary.
	 *
	 * @param shoreline The remaining shore line segments.
	 * @param seaBounds The map boundary.
	 * @param seaRelation If we are using a multi-polygon, this is it. Otherwise it will be null.
	 * @return A map of the 'hits' where the shore line intersects the boundary.
	 */
	private NavigableMap<EdgeHit, Way> findIntesectionPoints(List<Way> shoreline, Area seaBounds, Relation seaRelation) {
		assert !generateSeaUsingMP || seaRelation != null;

		NavigableMap<EdgeHit, Way> hitMap = new TreeMap<EdgeHit, Way>();
		for (Way w : shoreline) {
			List<Coord> points = w.getPoints();
			Coord pStart = points.get(0);
			Coord pEnd = points.get(points.size()-1);

			EdgeHit hStart = getEdgeHit(seaBounds, pStart);
			EdgeHit hEnd = getEdgeHit(seaBounds, pEnd);
			if (hStart == null || hEnd == null) {

				/*
				 * This problem occurs usually when the shoreline is cut by osmosis (e.g. country-extracts from geofabrik)
				 * There are two possibilities to solve this problem:
				 * 1. Close the way and treat it as an island. This is sometimes the best solution (Germany: Usedom at the
				 *    border to Poland)
				 * 2. Create a "sea sector" only for this shoreline segment. This may also be the best solution
				 *    (see German border to the Netherlands where the shoreline continues in the Netherlands)
				 * The first choice may lead to "flooded" areas, the second may lead to "triangles".
				 *
				 * Usually, the first choice is appropriate if the segment is "nearly" closed.
				 */
				double length = 0;
				Coord p0 = pStart;
				for (Coord p1 : points.subList(1, points.size()-1)) {
					length += p0.distance(p1);
					p0 = p1;
				}
				boolean nearlyClosed = pStart.distance(pEnd) < 0.1 * length;

				if (nearlyClosed) {
					// close the way
					points.add(pStart);
					
					if(!FakeIdGenerator.isFakeId(w.getId())) {
						Way w1 = new Way(FakeIdGenerator.makeFakeId());
						w1.getPoints().addAll(w.getPoints());
						// only copy the name tags
						for(String tag : w)
							if(tag.equals("name") || tag.endsWith(":name"))
								w1.addTag(tag, w.getTag(tag));
						w = w1;
					}
					w.addTag(landTag[0], landTag[1]);
					saver.addWay(w);
					if(generateSeaUsingMP)
					{						
						seaRelation.addElement("inner", w);
					}
				} else if(allowSeaSectors) {
					long seaId = FakeIdGenerator.makeFakeId();
					Way sea = new Way(seaId);
					sea.getPoints().addAll(points);
					sea.addPoint(new Coord(pEnd.getLatitude(), pStart.getLongitude()));
					sea.addPoint(pStart);
					sea.addTag("natural", "sea");
					log.info("sea: ", sea);
					saver.addWay(sea);
					if(generateSeaUsingMP)
						seaRelation.addElement("outer", sea);
					generateSeaBackground = false;
				} else if (extendSeaSectors) {
					// create additional points at next border to prevent triangles from point 2
					if (null == hStart) {
						hStart = getNextEdgeHit(seaBounds, pStart);
						w.getPoints().add(0, hStart.getPoint(seaBounds));
					}
					if (null == hEnd) {
						hEnd = getNextEdgeHit(seaBounds, pEnd);
						w.getPoints().add(hEnd.getPoint(seaBounds));
					}
					log.debug("hits (second try): ", hStart, hEnd);
					hitMap.put(hStart, w);
					hitMap.put(hEnd, null);
				} else {
					// show the coastline even though we can't produce
					// a polygon for the land
					w.addTag("natural", "coastline");
					saver.addWay(w);
				}
			} else {
				log.debug("hits: ", hStart, hEnd);
				hitMap.put(hStart, w);
				hitMap.put(hEnd, null);
			}
		}
		return hitMap;
	}

	/**
	 * Specifies where an edge of the bounding box is hit.
	 */
	private static class EdgeHit implements Comparable<EdgeHit>
	{
		private final int edge;
		private final double t;

		EdgeHit(int edge, double t) {
			this.edge = edge;
			this.t = t;
		}

		public int compareTo(EdgeHit o) {
			if (edge < o.edge)
				return -1;
			else if (edge > o.edge)
				return +1;
			else if (t > o.t)
				return +1;
			else if (t < o.t)
				return -1;
			else
				return 0;
		}

		public boolean equals(Object o) {
			if (o instanceof EdgeHit) {
				EdgeHit h = (EdgeHit) o;
				return (h.edge == edge && Double.compare(h.t, t) == 0);
			} else
				return false;
		}

		private Coord getPoint(Area a) {
			log.info("getPoint: ", this, a);
			switch (edge) {
			case 0:
				return new Coord(a.getMinLat(), (int) (a.getMinLong() + t * (a.getMaxLong()-a.getMinLong())));

			case 1:
				return new Coord((int)(a.getMinLat() + t * (a.getMaxLat()-a.getMinLat())), a.getMaxLong());

			case 2:
				return new Coord(a.getMaxLat(), (int)(a.getMaxLong() - t * (a.getMaxLong()-a.getMinLong())));

			case 3:
				return new Coord((int)(a.getMaxLat() - t * (a.getMaxLat()-a.getMinLat())), a.getMinLong());

			default:
				throw new MapFailedException("illegal state");
			}
		}

		public String toString() {
			return "EdgeHit " + edge + "@" + t;
		}
	}

	private EdgeHit getEdgeHit(Area a, Coord p) {
		return getEdgeHit(a, p, 10);
	}

	private EdgeHit getEdgeHit(Area a, Coord p, int tolerance) {
		int lat = p.getLatitude();
		int lon = p.getLongitude();
		int minLat = a.getMinLat();
		int maxLat = a.getMaxLat();
		int minLong = a.getMinLong();
		int maxLong = a.getMaxLong();

		log.info(String.format("getEdgeHit: (%d %d) (%d %d %d %d)", lat, lon, minLat, minLong, maxLat, maxLong));
		if (lat <= minLat+tolerance) {
			return new EdgeHit(0, ((double)(lon - minLong))/(maxLong-minLong));
		} else if (lon >= maxLong-tolerance) {
			return new EdgeHit(1, ((double)(lat - minLat))/(maxLat-minLat));
		} else if (lat >= maxLat-tolerance) {
			return new EdgeHit(2, ((double)(maxLong - lon))/(maxLong-minLong));
		} else if (lon <= minLong+tolerance) {
			return new EdgeHit(3, ((double)(maxLat - lat))/(maxLat-minLat));
		} else
			return null;
	}

	/**
	 * Find the nearest edge for supplied Coord p.
	 */
	private EdgeHit getNextEdgeHit(Area a, Coord p)
	{
		int lat = p.getLatitude();
		int lon = p.getLongitude();
		int minLat = a.getMinLat();
		int maxLat = a.getMaxLat();
		int minLong = a.getMinLong();
		int maxLong = a.getMaxLong();

		log.info(String.format("getNextEdgeHit: (%d %d) (%d %d %d %d)", lat, lon, minLat, minLong, maxLat, maxLong));
		// shortest distance to border (init with distance to southern border)
		int min = lat - minLat;
		// number of edge as used in getEdgeHit.
		// 0 = southern
		// 1 = eastern
		// 2 = northern
		// 3 = western edge of Area a
		int i = 0;
		// normalized position at border (0..1)
		double l = ((double)(lon - minLong))/(maxLong-minLong);
		// now compare distance to eastern border with already known distance
		if (maxLong - lon < min) {
			// update data if distance is shorter
			min = maxLong - lon;
			i = 1;
			l = ((double)(lat - minLat))/(maxLat-minLat);
		}
		// same for northern border
		if (maxLat - lat < min) {
			min = maxLat - lat;
			i = 2;
			l = ((double)(maxLong - lon))/(maxLong-minLong);
		}
		// same for western border
		if (lon - minLong < min) {
			i = 3;
			l = ((double)(maxLat - lat))/(maxLat-minLat);
		}
		// now created the EdgeHit for found values
		return new EdgeHit(i, l);
	}

	private void closeGaps(List<Way> ways, Area bounds) {

		// join up coastline segments whose end points are less than
		// maxCoastlineGap metres apart
		if (maxCoastlineGap > 0) {
			boolean changed = true;
			while (changed) {
				changed = false;
				for (Way w1 : ways) {
					if(w1.isClosed())
						continue;
					List<Coord> points1 = w1.getPoints();
					Coord w1e = points1.get(points1.size() - 1);
					if(bounds.onBoundary(w1e))
						continue;
					Way nearest = null;
					double smallestGap = Double.MAX_VALUE;
					for (Way w2 : ways) {
						if(w1 == w2 || w2.isClosed())
							continue;
						List<Coord> points2 = w2.getPoints();
						Coord w2s = points2.get(0);
						if(bounds.onBoundary(w2s))
							continue;
						double gap = w1e.distance(w2s);
						if(gap < smallestGap) {
							nearest = w2;
							smallestGap = gap;
						}
					}
					if (nearest != null && smallestGap < maxCoastlineGap) {
						Coord w2s = nearest.getPoints().get(0);
						log.warn("Bridging " + (int)smallestGap + "m gap in coastline from " + w1e.toOSMURL() + " to " + w2s.toOSMURL());
						Way wm;
						if (FakeIdGenerator.isFakeId(w1.getId())) {
							wm = w1;
						} else {
							wm = new Way(FakeIdGenerator.makeFakeId());
							ways.remove(w1);
							ways.add(wm);
							wm.getPoints().addAll(points1);
							wm.copyTags(w1);
						}
						wm.getPoints().addAll(nearest.getPoints());
						ways.remove(nearest);
						// make a line that shows the filled gap
						Way w = new Way(FakeIdGenerator.makeFakeId());
						w.addTag("natural", "mkgmap:coastline-gap");
						w.addPoint(w1e);
						w.addPoint(w2s);
						saver.addWay(w);
						changed = true;
						break;
					}
				}
			}
		}
	}
}
