/*
 * Copyright (C) 2010.
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
package uk.me.parabola.mkgmap.combiners;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.map.Map;
import uk.me.parabola.imgfmt.app.map.MapReader;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.trergn.Point;
import uk.me.parabola.imgfmt.app.trergn.Polygon;
import uk.me.parabola.imgfmt.app.trergn.Polyline;
import uk.me.parabola.imgfmt.app.trergn.Zoom;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.CommandArgs;
import uk.me.parabola.mkgmap.build.MapBuilder;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.srt.SrtTextReader;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Build the overview map.  This is a low resolution map that covers the whole
 * of a map set.  It also contains polygons that correspond to the areas
 * covered by the individual map tiles.
 *
 * @author Steve Ratcliffe
 */
public class OverviewBuilder implements Combiner {
	Logger log = Logger.getLogger(OverviewBuilder.class);
	public static final String OVERVIEW_PREFIX = "ovm_";
	private final OverviewMap overviewSource;
	private String areaName;
	private String overviewMapname;
	private String overviewMapnumber;
	private Zoom[] levels;
	private String outputDir;		
	private Integer codepage;
	private Integer encodingType;
	private String copyrightMsg;
	private String licenseFileName;


	public OverviewBuilder(OverviewMap overviewSource) {
		this.overviewSource = overviewSource;
	}

	public void init(CommandArgs args) {
		areaName = args.get("area-name", "Overview Map");
		overviewMapname = args.get("overview-mapname", "osmmap");
		overviewMapnumber = args.get("overview-mapnumber", "63240000");
		outputDir = args.getOutputDir();
		copyrightMsg = args.getProperties().getProperty("copyright-message",
				"OpenStreetMap.org contributors. See: http://wiki.openstreetmap.org/index.php/Attribution");
		licenseFileName = args.get("license-file", null);
	}

	public void onMapEnd(FileInfo finfo) {
		if (!finfo.isImg())
			return;

		try {
			readFileIntoOverview(finfo);
		} catch (FileNotFoundException e) {
			throw new MapFailedException("Could not read detail map " + finfo.getFilename(), e);
		}
	}

	public void onFinish() {
		addBackground();
		writeOverviewMap();
	}
	
	/**
	 * Add background polygon that covers the whole area of the overview map. 
	 */
	private void addBackground() {
		MapShape background = new MapShape();
		background.setType(0x4b); // background type
		background.setMinResolution(0); // On all levels
		background.setPoints(overviewSource.getBounds().toCoords());

		overviewSource.addShape(background);
	}

	/**
	 * Write out the overview map.
	 */
	private void writeOverviewMap() {
		if (overviewSource.mapLevels() == null)
			return;
		MapBuilder mb = new MapBuilder();
		if (licenseFileName != null){
			EnhancedProperties props = new EnhancedProperties();
			props.put("license-file", licenseFileName);
			mb.config(props);
		}
		mb.setEnableLineCleanFilters(false);

		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(512);
		params.setMapDescription(areaName);
		overviewSource.addCopyright(copyrightMsg);

		try {
			if (codepage == null){
				codepage = 0; // should not happen
			}
			Sort sort = SrtTextReader.sortForCodepage(codepage);
			Map map = Map.createMap(overviewMapname, outputDir, params, overviewMapnumber, sort);
			
			if (encodingType != null){
				map.getLblFile().setEncoder(encodingType, codepage);
			}
			mb.makeMap(map, overviewSource);
			map.close();
		} catch (FileExistsException e) {
			throw new ExitException("Could not create overview map", e);
		} catch (FileNotWritableException e) {
			throw new ExitException("Could not write to overview map", e);
		}
	}

	/**
	 * Add an individual .img file to the overview map.
	 *
	 * @param finfo Information about an individual map.
	 */
	private void readFileIntoOverview(FileInfo finfo) throws FileNotFoundException {
		addMapCoverageArea(finfo);

		MapReader mapReader = null;
		String filename = finfo.getFilename();
		if (codepage == null){
			codepage = finfo.getCodePage();
		} 
		if (codepage != finfo.getCodePage()){
			System.err.println("WARNING: input file " + filename + " has different code page " + finfo.getCodePage());
		}

		try{
			mapReader = new MapReader(filename);

			if (encodingType == null){
				encodingType = mapReader.getEncodingType();
			} 
			if (encodingType != mapReader.getEncodingType()){
				System.err.println("WARNING: input file " + filename + " has different charset type " + encodingType);
			}
			
			
			levels = mapReader.getLevels();
			if (overviewSource.mapLevels() == null){
				LevelInfo[] mapLevels;
				if (isOverviewImg(filename)){
					mapLevels = new LevelInfo[levels.length-1]; 
					for (int i = 1; i < levels.length; i++){
						mapLevels[i-1] = new LevelInfo(levels[i].getLevel(), levels[i].getResolution());
					}
				} else {
					mapLevels = new LevelInfo[1];
					mapLevels[0] = new LevelInfo(levels[1].getLevel(), levels[1].getResolution());
				}
				overviewSource.setMapLevels(mapLevels);
			}
			if (isOverviewImg(filename)){
				readPoints(mapReader);
				readLines(mapReader);
				readShapes(mapReader);
			}
		} catch (FileNotFoundException e) {
			throw new ExitException("Could not open " + filename + " when creating overview file");
		} finally {
			Utils.closeFile(mapReader);
		}
	}

	/**
	 * Read the points from the .img file and add them to the overview map.
	 * We read from the least detailed level (apart from the empty one).
	 *
	 * @param mapReader Map reader on the detailed .img file.
	 */
	private void readPoints(MapReader mapReader) {
		for (int l = 1; l < levels.length; l++){
			int min = levels[l].getLevel();
			int res = levels[l].getResolution();
			List<Point> pointList = mapReader.pointsForLevel(min, MapReader.WITH_EXT_TYPE_DATA);
			for (Point point: pointList) {
				if (log.isDebugEnabled())
					log.debug("got point", point);
				MapPoint mp = new MapPoint();
				mp.setType(point.getType());
				mp.setName(point.getLabel().getText());
				mp.setMaxResolution(res); 
				mp.setMinResolution(res);  
				mp.setLocation(point.getLocation());
				overviewSource.addPoint(mp);
			}
		}
	}

	/**
	 * Read the lines from the .img file and add them to the overview map.
	 * We read from the least detailed level (apart from the empty one).
	 *
	 * @param mapReader Map reader on the detailed .img file.
	 */
	private void readLines(MapReader mapReader) {
		for (int l = 1; l < levels.length; l++){
			int min = levels[l].getLevel();
			int res = levels[l].getResolution();
			List<Polyline> lineList = mapReader.linesForLevel(min);
			//System.out.println(lineList.size() + " lines in lowest resolution " + levels[1].getResolution());
			for (Polyline line : lineList) {
				if (log.isDebugEnabled())
					log.debug("got line", line);
				MapLine ml = new MapLine();

				List<Coord> points = line.getPoints();
				if (log.isDebugEnabled())			
					log.debug("line point list", points);
				if (points.size() < 2)
					continue;

				ml.setType(line.getType());
				if (line.getLabel() != null)
					ml.setName(line.getLabel().getText());
				ml.setMaxResolution(res); 
				ml.setMinResolution(res);  
				ml.setPoints(points);

				overviewSource.addLine(ml);
			}
		}
	}

	/**
	 * Read the polygons from the .img file and add them to the overview map.
	 * We read from the least detailed level (apart from the empty one).
	 *
	 * @param mapReader Map reader on the detailed .img file.
	 */
	private void readShapes(MapReader mapReader) {
		for (int l = 1; l < levels.length; l++){
			int min = levels[l].getLevel();
			int res = levels[l].getResolution();
			List<Polygon> list = mapReader.shapesForLevel(min);
			for (Polygon shape : list) {
				if (log.isDebugEnabled())
					log.debug("got polygon", shape);
				if (shape.getType() == 0x4b){
					// ignore existing background polygons as we will add our own
					continue;
				}
				MapShape ms = new MapShape();

				List<Coord> points = shape.getPoints();
				if (log.isDebugEnabled())			
					log.debug("polygon point list", points);

				if (points.size() < 3)
					continue;

				ms.setType(shape.getType());
				if (shape.getLabel() != null)
					ms.setName(shape.getLabel().getText());
				ms.setMaxResolution(res); 
				ms.setMinResolution(res);  
				ms.setPoints(points);

				overviewSource.addShape(ms);
			}
		}
	}

	/**
	 * Add an area that shows the area covered by a detailed map.  This can
	 * be an arbitary shape, although at the current time we only support
	 * rectangles.
	 *
	 * @param finfo Information about a detail map.
	 */
	private void addMapCoverageArea(FileInfo finfo) {
		Area bounds = finfo.getBounds();
		List<Coord> points = bounds.toCoords();
		
		for (Coord co: points){
			overviewSource.addToBounds(co);
		}
		// Create the background rectangle
		MapShape bg = new MapShape();
		bg.setType(0x4a);
		bg.setPoints(points);
		bg.setMinResolution(0);
		bg.setName(finfo.getDescription() + '\u001d' + finfo.getMapname());

		overviewSource.addShape(bg); 
	}

	public Area getBounds() {
		return overviewSource.getBounds();
	}

	/**
	 * Check if the the file name points to a partly overview img file  
	 * @param name full path or just a name 
	 * @return true if the name points to a partly overview img file
	 */
	public static boolean isOverviewImg (String name){
		return new File(name).getName().startsWith(OVERVIEW_PREFIX);
	}
	/**
	 * Add the prefix to the file name.
	 * @param name filename 
	 * @return filename of the corresponding overview img file
	 */
	public static String getOverviewImgName (String name){
		File f = new File(name);
		return new File(f.getParent(),OverviewBuilder.OVERVIEW_PREFIX + f.getName()).getAbsolutePath();
	}

	public static String getMapName(String name) {
		String fname = new File(name).getName();
		if (fname.startsWith(OVERVIEW_PREFIX))
			return fname.substring(OVERVIEW_PREFIX.length());
		else return name;
	}
	
}
