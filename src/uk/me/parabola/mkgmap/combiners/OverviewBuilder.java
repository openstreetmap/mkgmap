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
import java.util.ArrayList;
import java.util.Arrays;
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
import uk.me.parabola.mkgmap.reader.overview.OverviewMapDataSource;
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
	private OverviewMapDataSource overviewSource;
	private String areaName;
	private String overviewMapname;
	private String overviewMapnumber;
	private String outputDir;		
	private Integer codepage;
	private Integer encodingType;
	private List<String[]> copyrightMsgs = new ArrayList<String[]>();
	private List<String[]> licenseInfos = new ArrayList<String[]>();
	private LevelInfo[] wantedLevels;
	private Area bounds;
	private EnhancedProperties demProps = new EnhancedProperties();
	


	public OverviewBuilder() {
		this.overviewSource = new OverviewMapDataSource();
	}

	public void init(CommandArgs args) {
		areaName = args.get("area-name", "Overview Map");
		overviewMapname = args.get("overview-mapname", "osmmap");
		overviewMapnumber = args.get("overview-mapnumber", "63240000");
		
		outputDir = args.getOutputDir();
		demProps.setProperty("dem", args.getProperties().getProperty("dem"));
		demProps.setProperty("dem-dists", args.getProperties().getProperty("overview-dem-dist"));
		if ("0".equals(args.getProperties().getProperty("overview-dem-dist")))
			demProps.clear();
				
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
		overviewSource.addBackground();
		calcLevels();
		writeOverviewMap();
		bounds = overviewSource.getBounds();
		overviewSource = null; // release memory
	}

	@Override
	public String getFilename() {
		return Utils.joinPath(outputDir, overviewMapname, "img");
	}

	private void calcLevels() {
		List<MapShape> shapes = overviewSource.getShapes();
		int maxRes = 16; // we can write a 0x4a polygon for planet in res 16
		if (wantedLevels != null)
			maxRes = wantedLevels[wantedLevels.length-1].getBits();
		int maxSize = 0xffff << (24 - maxRes); 
		for (MapShape s : shapes){
			if (s.getType() != 0x4a)
				continue;
			int maxDimPoly = s.getBounds().getMaxDimension();
			if (maxDimPoly > maxSize){
				int oldMaxRes = maxRes; 
				while (maxDimPoly > maxSize){
					maxRes--;
					maxSize = 0xffff << (24 - maxRes);
				}
				String[] name = s.getName().split("\u001d");
				String msg = "Tile selection (0x4a) polygon for "; 
				if (name != null && name.length == 2)
					msg += "tile " + name[1].trim();
				else 
					msg += s.getBounds(); 
				log.error(msg,"cannot be written in level 0 resolution",oldMaxRes + ", using",maxRes,"instead");
				
			}
		}
		if (wantedLevels == null)
			setRes(maxRes);
		else {
			// make sure that the wanted levels for the overview map
			// can store the largest 0x4a polygon at level 0
			int n = wantedLevels.length-1;
			while (n > 0 && wantedLevels[n].getBits() > maxRes)
				n--;
			if (n > 0){
				int l = 0;
				while (n >= 0){
					wantedLevels[n] = new LevelInfo(l++, wantedLevels[n].getBits());
					n--;
				}
				wantedLevels = Arrays.copyOfRange(wantedLevels, 0, l);
				overviewSource.setMapLevels(wantedLevels);
			} else
				setRes(maxRes);	
		}
	}

	/**
	 * Write out the overview map.
	 */
	private void writeOverviewMap() {
		if (overviewSource.mapLevels() == null)
			return;
		
		MapBuilder mb = new MapBuilder();
		mb.setEnableLineCleanFilters(false);

		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(512);
		params.setMapDescription(areaName);
		mb.setCopyrights(creMsgList(copyrightMsgs));
		mb.setMapInfo(creMsgList(licenseInfos));
		
		
		try {
			if (codepage == null){
				codepage = 0; // should not happen
			}
			Sort sort = SrtTextReader.sortForCodepage(codepage);
			Map map = Map.createMap(overviewMapname, outputDir, params, overviewMapnumber, sort);
			if (!demProps.isEmpty()) {
				map.config(demProps);
				mb.config(demProps);
			}
			
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

			String[] msgs = mapReader.getCopyrights();
			boolean found = false;
			for (String[] block : copyrightMsgs) {
				if (Arrays.deepEquals(block, msgs)){
					found = true;
					break;
				}
			}
			if (!found )
				copyrightMsgs.add(msgs);
			
			msgs = finfo.getLicenseInfo();
			found = false;
			for (String[] block : licenseInfos) {
				if (Arrays.deepEquals(block, msgs)){
					found = true;
					break;
				}
			}
			if (!found )
				licenseInfos.add(msgs);
			
			
			Zoom[] levels = mapReader.getLevels();
			if (wantedLevels == null){
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
				wantedLevels = mapLevels;
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
		Area bounds = overviewSource.getBounds();
		Zoom[] levels = mapReader.getLevels();
		for (int l = 1; l < levels.length; l++){
			int min = levels[l].getLevel();
			int res = levels[l].getResolution();
			List<Point> pointList = mapReader.pointsForLevel(min, MapReader.WITH_EXT_TYPE_DATA);
			for (Point point: pointList) {
				if (log.isDebugEnabled())
					log.debug("got point", point);
				if (bounds.contains(point.getLocation()) == false){
					if (log.isDebugEnabled())
						log.debug(point, "dropped, is outside of tile boundary");
					continue;
				}
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
		Zoom[] levels = mapReader.getLevels();
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
		Zoom[] levels = mapReader.getLevels();
		for (int l = 1; l < levels.length; l++){
			int min = levels[l].getLevel();
			int res = levels[l].getResolution();
			List<Polygon> list = mapReader.shapesForLevel(min, MapReader.WITH_EXT_TYPE_DATA);
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
		// Create the tile coverage rectangle
		MapShape bg = new MapShape();
		bg.setType(0x4a);
		bg.setPoints(points);
		bg.setMinResolution(0);
		bg.setName(finfo.getDescription() + '\u001d' + finfo.getMapname());

		overviewSource.addShape(bg); 
	}

	public Area getBounds() {
		if (bounds != null)
			return bounds;
		if (overviewSource != null)
			return overviewSource.getBounds();
		return new Area(1, 1, -1, -1); // return invalid bbox
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
	
	private List<String> creMsgList(List<String[]> msgs){
		ArrayList< String> list = new ArrayList<String>();
		for (int i = 0; i < msgs.size(); i++){
			String[] block = msgs.get(i);
			for (String s : block){
				list.add(s);
			} 
			if (i < msgs.size()-1){
				// separate blocks 
				list.add("");
			}
		}
		return list;
	}

	/**
	 * Set the highest resolution
	 * @param resolution
	 */
	private void setRes(int resolution) {
		LevelInfo[] mapLevels = new LevelInfo[1];
		mapLevels[0] = new LevelInfo(0, resolution);
		overviewSource.setMapLevels(mapLevels); 
	}

}
