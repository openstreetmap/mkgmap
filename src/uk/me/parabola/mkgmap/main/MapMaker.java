/*
 * Copyright (C) 2006 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 16-Dec-2006
 */
package uk.me.parabola.mkgmap.main;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.map.Map;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.CommandArgs;
import uk.me.parabola.mkgmap.build.MapBuilder;
import uk.me.parabola.mkgmap.combiners.OverviewBuilder;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.reader.plugin.MapReader;

/**
 * Main routine for the command line map-making utility.
 *
 * @author Steve Ratcliffe
 */
public class MapMaker implements MapProcessor {
	private static final Logger log = Logger.getLogger(MapMaker.class);
	private Sort sort;
	private final boolean createOverviewFiles;

	public MapMaker(boolean createOverviewFiles) {
		this.createOverviewFiles = createOverviewFiles;
	}

	public String makeMap(CommandArgs args, String filename) {
		try {
			LoadableMapDataSource src = loadFromFile(args, filename);
			sort = args.getSort();
			log.info("Making Road Name POIs for", filename);
			makeRoadNamePOIS(args, src);
			if (createOverviewFiles){
				if (src.overviewMapLevels() != null){
					makeMap(args, src, OverviewBuilder.OVERVIEW_PREFIX);
				} else {
					String fname = OverviewBuilder.getOverviewImgName(args.getMapname());
					File f = new File(fname);
					if (f.exists()) {
						if (f.isFile() )
							f.delete();
						else {
							// TODO: error message ?
						}
					}
				}
			}
			return makeMap(args, src, "");
		} catch (FormatException e) {
			System.err.println("Bad file format: " + filename);
			System.err.println(e.getMessage());
			return filename;
		} catch (FileNotFoundException e) {
			System.err.println("Could not open file: " + filename);
			return filename;
		}
	}

	/**
	 * Make a map from the given map data source.
	 *
	 * @param args User supplied arguments.
	 * @param src The data source to load.
	 * @param mapNameExt 
	 * @return The output filename for the map.
	 */
	private String makeMap(CommandArgs args, LoadableMapDataSource src, String mapNamePrefix) {

		if (src.getBounds().isEmpty())
			return null;

		FileSystemParam params = new FileSystemParam();
		params.setBlockSize(args.getBlockSize());
		params.setMapDescription(args.getDescription());
		log.info("Started making", args.getMapname(), "(" + args.getDescription() + ")");
		try {
			Map map = Map.createMap(mapNamePrefix + args.getMapname(), args.getOutputDir(), params, args.getMapname(), sort);
			setOptions(map, args);

			MapBuilder builder = new MapBuilder();
			builder.config(args.getProperties());
			if (args.getProperties().getProperty("route", false)){
				if(! OverviewBuilder.OVERVIEW_PREFIX.equals(mapNamePrefix))
					builder.setDoRoads(true);
			}
			builder.makeMap(map, src);

			// Collect information on map complete.
			String outName = map.getFilename();
			log.info("finished making map", outName, "closing");
			map.close();
			return outName;
		} catch (FileExistsException e) {
			throw new MapFailedException("File exists already", e);
		} catch (FileNotWritableException e) {
			throw new MapFailedException("Could not create or write to file", e);
		}
	}

	/**
	 * Set options from the command line.
	 *
	 * @param map The map to modify.
	 * @param args The command line arguments.
	 */
	private void setOptions(Map map, CommandArgs args) {
		map.config(args.getProperties());

		String s = args.getCharset();
		if (s != null)
			map.setLabelCharset(s, args.isForceUpper());

		Sort sort = args.getSort();
		map.setSort(sort);
	}

	/**
	 * Load up from the file.  It is not necessary for the map reader to completely
	 * read the whole file in at once, it could pull in map-features as needed.
	 *
	 * @param args The user supplied parameters.
	 * @param name The filename or resource name to be read.
	 * @return A LoadableMapDataSource that will be used to construct the map.
	 * @throws FileNotFoundException For non existing files.
	 * @throws FormatException When the file format is not valid.
	 */
	private LoadableMapDataSource loadFromFile(CommandArgs args, String name) throws
			FileNotFoundException, FormatException
	{
		LoadableMapDataSource src = MapReader.createMapReader(name);
		src.config(args.getProperties());
		log.info("Started loading", name);
		src.load(name);
		log.info("Finished loading", name);
		return src;
	}

	void makeRoadNamePOIS(CommandArgs args, LoadableMapDataSource src) {
		String rnp = args.get("road-name-pois", null);
		// are road name POIS wanted?
		if(rnp != null) {
			rnp = rnp.toUpperCase();
			int rnpt = 0x640a; // Garmin type 'Locale'
			if(rnp.length() > 0) {
				// override type code
				rnpt = Integer.decode(rnp);
			}
			// collect lists of roads that have the same name
			java.util.Map<String, List<MapRoad>> namedRoads = new HashMap<String, List<MapRoad>>();
			for(MapLine l : src.getLines()) {
				if(l.isRoad()) {
					MapRoad r = (MapRoad)l;
					String rn = r.getName();
					if(rn != null) {
						List<MapRoad> rl = namedRoads.get(rn);
						if(rl == null) {
							rl = new ArrayList<MapRoad>();
							namedRoads.put(rn, rl);
						}
						rl.add(r);
					}
				}
			}

			// generate a POI for each named road

			// sort by name and coordinate of first point so that
			// the order is always the same for the same input
			List<SortKey<MapRoad>> rnpRoads = new ArrayList<SortKey<MapRoad>>();
			for(List<MapRoad> lr : findConnectedRoadsWithSameName(namedRoads)) {
				// connected roads are not ordered so just use first in list
				MapRoad r = lr.get(0);
				String key = r.getName();
				List<Coord> points = r.getPoints();
				if(!points.isEmpty())
					key += "_" + points.get(0);
				rnpRoads.add(sort.createSortKey(r, key));
			}
			Collections.sort(rnpRoads);
			for(SortKey<MapRoad> sr : rnpRoads)
				src.getPoints().add(makeRoadNamePOI(sr.getObject(), rnpt));
		}
	}

	private boolean roadsAreJoined(MapLine r1, MapLine r2) {
		if (r1.getBounds().intersects(r2.getBounds()) == false)  {
			return false;
		}

		if(r1 != r2) {
			for(Coord c1 : r1.getPoints()) {
				for(Coord c2 : r2.getPoints()) {
					if(c1 == c2 || c1.equals(c2))
						return true;
				}
			}
		}
		return false;
	}

	// hairy function to build a set of lists - each list contains
	// the roads that have the same name and are connected

	private Set<List<MapRoad>> findConnectedRoadsWithSameName(java.util.Map<String, List<MapRoad>> namedRoads) {
		// roadGroups is a set to avoid duplicate groups
		Set<List<MapRoad>> roadGroups = new HashSet<List<MapRoad>>();

		// loop over the lists of roads that have the same name
		for(List<MapRoad> allRoadsWithSameName : namedRoads.values()) {
			// for each road that has the same name, keep track of its group
			java.util.Map<MapRoad,List<MapRoad>> roadGroupMap = new HashMap<MapRoad,List<MapRoad>>();

			// loop over all of the roads with the same name
			for(int i = 0; i < allRoadsWithSameName.size(); ++i) {
				boolean roadWasJoined = false;
				for(int j = 0; j < allRoadsWithSameName.size(); ++j) {
					if(i != j) {
						// see if these two roads are joined
						MapRoad ri = allRoadsWithSameName.get(i);
						MapRoad rj = allRoadsWithSameName.get(j);
						if(roadsAreJoined(ri, rj)) {
							// yes, they are joined so put both in a group
							// and associate the group with each road
							roadWasJoined = true;
							List<MapRoad> groupi = roadGroupMap.get(ri);
							List<MapRoad> groupj = roadGroupMap.get(rj);
							if(groupi == null) {
								// ri is not in a group yet
								if(groupj == null) {
									// neither is rj so make a new group
									groupi = new ArrayList<MapRoad>();
									groupi.add(ri);
									groupi.add(rj);
									roadGroupMap.put(ri, groupi);
									roadGroupMap.put(rj, groupi);
								}
								else {
									// add ri to groupj
									groupj.add(ri);
									roadGroupMap.put(ri, groupj);
								}
							}
							else if(groupj == null) {
								// add rj to groupi
								groupi.add(rj);
								roadGroupMap.put(rj, groupi);
							}
							else if(groupi != groupj) {
								// ri and rj are in separate groups so put
								// all the roads in groupj into groupi
								for(MapRoad r : groupj)
									roadGroupMap.put(r, groupi);
								groupi.addAll(groupj);
							}
						}
					}
				}
				if(!roadWasJoined) {
					// make a group with just one entry
					MapRoad ri = allRoadsWithSameName.get(i);
					List<MapRoad>group = new ArrayList<MapRoad>();
					group.add(ri);
					roadGroupMap.put(ri, group);
				}
			}

			// now add the new group(s) to the final result
			for(List<MapRoad> r : roadGroupMap.values())
				roadGroups.add(r);
		}
		return roadGroups;
	}

	private MapPoint makeRoadNamePOI(MapRoad road, int type) {
		List<Coord> points = road.getPoints();
		int numPoints = points.size();
		Coord coord;
		if ((numPoints & 1) == 0) {
			int i2 = numPoints / 2;
			int i1 = i2 - 1;
			//TODO: use makeBetweenPoint()?
			coord = new Coord((points.get(i1).getLatitude() +
					   points.get(i2).getLatitude()) / 2,
					  (points.get(i1).getLongitude() +
					   points.get(i2).getLongitude()) / 2);
		} else {
			coord = points.get(numPoints / 2);
		}

		String name = road.getName();
		MapPoint rnp = new MapPoint();

		rnp.setName(name);
		rnp.setRoadNamePOI(true);
		rnp.setType(type);
		rnp.setLocation(coord);
		return rnp;
	}
}
