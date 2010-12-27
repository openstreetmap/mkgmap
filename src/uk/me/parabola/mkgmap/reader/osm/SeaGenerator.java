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
package uk.me.parabola.mkgmap.reader.osm;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LineClipper;
import uk.me.parabola.mkgmap.osmstyle.StyleImpl;
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
	private boolean floodblocker = false;
	private int fbGap = 40;
	private double fbRatio = 0.5d;
	private int fbThreshold = 20;
	private boolean fbDebug = false;

	private ElementSaver saver;

	private List<Way> shoreline = new ArrayList<Way>();
	private boolean roadsReachBoundary; // todo needs setting somehow
	private boolean generateSeaBackground = true;

	private String[] coastlineFilenames;
	private StyleImpl fbRules;
	
	/**
	 * Sort out options from the command line.
	 * Returns true only if the option to generate the sea is active, so that
	 * the whole thing is omitted if not used.
	 */
	public boolean init(ElementSaver saver, EnhancedProperties props) {
		this.saver = saver;
		String gs = props.getProperty("generate-sea", null);
		boolean generateSea = gs != null;
		if(generateSea) {
			for(String o : gs.split(",")) {
				if("no-mp".equals(o) ||
						"polygon".equals(o) ||
						"polygons".equals(o))
					generateSeaUsingMP = false;
				else if("multipolygon".equals(o))
					generateSeaUsingMP = true;
				else if(o.startsWith("close-gaps="))
					maxCoastlineGap = (int)Double.parseDouble(o.substring(11));
				else if("no-sea-sectors".equals(o))
					allowSeaSectors = false;
				else if("extend-sea-sectors".equals(o)) {
					allowSeaSectors = false;
					extendSeaSectors = true;
				}
				else if(o.startsWith("land-tag="))
					landTag = o.substring(9).split("=");
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

		return generateSea;
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
				if (coastlineFilenames == null)
					shoreline.add(way);
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
					if (coastlineFilenames == null)
						shoreline.add(way);
				}
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
			} else {
				List<Coord> points = w.getPoints();
				beginMap.put(points.get(0), w);
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
		SortedMap<EdgeHit, Way> hitMap = findIntesectionPoints(shoreline, seaBounds, seaRelation);

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
				if (clipped.size() > 0) {
					// the LineClipper sometimes returns unjoined clips
					// need to rejoin them here
					log.info(clipped.size(),"clippings. Try to join them.");
					List<Way> clippedWays = new ArrayList<Way>(clipped.size());
					for (List<Coord> clippedPoints : clipped) {
						clippedWays.add(new Way(FakeIdGenerator.makeFakeId(), clippedPoints));
					}
					clippedWays = joinWays(clippedWays);
					if (clippedWays.size() != clipped.size()) {
						clipped = new ArrayList<List<Coord>>(clippedWays.size());
						for (Way w : clippedWays) {
							clipped.add(w.getPoints());
						}
					}
					log.info(clipped.size(),"joined clippings.");
				}
				toBeRemoved.add(segment);
				for (List<Coord> pts : clipped) {
					long id = FakeIdGenerator.makeFakeId();
					Way shore = new Way(id, pts);
					toBeAdded.add(shore);
				}
			}
		}

		log.info("clipping: adding ", toBeAdded.size(), ", removing ", toBeRemoved.size());
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

	private boolean createInnerWays(Area seaBounds, List<Way> islands, SortedMap<EdgeHit, Way> hitMap) {
		NavigableSet<EdgeHit> hits = (NavigableSet<EdgeHit>) hitMap.keySet();
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
			if (w.clockwise()) {
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
	private SortedMap<EdgeHit, Way> findIntesectionPoints(List<Way> shoreline, Area seaBounds, Relation seaRelation) {
		assert !generateSeaUsingMP || seaRelation != null;

		SortedMap<EdgeHit, Way> hitMap = new TreeMap<EdgeHit, Way>();
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
