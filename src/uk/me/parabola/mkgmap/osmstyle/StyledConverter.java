/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: Feb 17, 2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.Exit;
import uk.me.parabola.imgfmt.app.net.NODHeader;
import uk.me.parabola.imgfmt.app.trergn.ExtTypeAttributes;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.AreaClipper;
import uk.me.parabola.mkgmap.general.Clipper;
import uk.me.parabola.mkgmap.general.LineAdder;
import uk.me.parabola.mkgmap.general.LineClipper;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapExitPoint;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.general.RoadNetwork;
import uk.me.parabola.mkgmap.reader.osm.CoordPOI;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.RestrictionRelation;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.mkgmap.reader.polish.PolishMapDataSource;

/**
 * Convert from OSM to the mkgmap intermediate format using a style.
 * A style is a collection of files that describe the mappings to be used
 * when converting.
 *
 * @author Steve Ratcliffe
 */
public class StyledConverter implements OsmConverter {
	private static final Logger log = Logger.getLogger(StyledConverter.class);

	private final String[] nameTagList;

	private final MapCollector collector;

	private Clipper clipper = Clipper.NULL_CLIPPER;
	private Area bbox;

	// restrictions associates lists of turn restrictions with the
	// Coord corresponding to the restrictions' 'via' node
	private final Map<Coord, List<RestrictionRelation>> restrictions = new IdentityHashMap<Coord, List<RestrictionRelation>>();

	// originalWay associates Ways that have been created due to
	// splitting or clipping with the Ways that they were derived
	// from
	private final Map<Way, Way> originalWay = new HashMap<Way, Way>();

	// limit arc lengths to what can currently be handled by RouteArc
	private final int MAX_ARC_LENGTH = 25000;

	private final int MAX_POINTS_IN_WAY = 200;

	private final int MAX_NODES_IN_WAY = 16;

	private final double MIN_DISTANCE_BETWEEN_NODES = 5.5;

	// nodeIdMap maps a Coord into a nodeId
	private final Map<Coord, Integer> nodeIdMap = new IdentityHashMap<Coord, Integer>();
	private int nextNodeId = 1;
	
	private final Rule wayRules;
	private final Rule nodeRules;
	private final Rule relationRules;

	private boolean ignoreMaxspeeds;
	private boolean driveOnLeft;
	private boolean driveOnRight;
	private boolean checkRoundabouts;

	class AccessMapping {
		private final String type;
		private final int index;
		AccessMapping(String type, int index) {
			this.type = type;
			this.index = index;
		}
	}

	private final AccessMapping[] accessMap = {
		new AccessMapping("access",     RoadNetwork.NO_MAX), // must be first in list
		new AccessMapping("bicycle",    RoadNetwork.NO_BIKE),
		new AccessMapping("foot",       RoadNetwork.NO_FOOT),
		new AccessMapping("hgv",        RoadNetwork.NO_TRUCK),
		new AccessMapping("motorcar",   RoadNetwork.NO_CAR),
		new AccessMapping("motorcycle", RoadNetwork.NO_CAR),
		new AccessMapping("psv",        RoadNetwork.NO_BUS),
		new AccessMapping("taxi",       RoadNetwork.NO_TAXI),
		new AccessMapping("emergency",  RoadNetwork.NO_EMERGENCY),
		new AccessMapping("delivery",   RoadNetwork.NO_DELIVERY),
		new AccessMapping("goods",      RoadNetwork.NO_DELIVERY),
	};

	private LineAdder lineAdder = new LineAdder() {
		public void add(MapLine element) {
			if (element instanceof MapRoad)
				collector.addRoad((MapRoad) element);
			else
				collector.addLine(element);
		}
	};

	public StyledConverter(Style style, MapCollector collector, Properties props) {
		this.collector = collector;

		nameTagList = style.getNameTagList();
		wayRules = style.getWayRules();
		nodeRules = style.getNodeRules();
		relationRules = style.getRelationRules();
		ignoreMaxspeeds = props.getProperty("ignore-maxspeeds") != null;
		driveOnLeft = props.getProperty("drive-on-left") != null;
		NODHeader.setDriveOnLeft(driveOnLeft);
		driveOnRight = props.getProperty("drive-on-right") != null;
		checkRoundabouts = props.getProperty("check-roundabouts") != null;

		LineAdder overlayAdder = style.getOverlays(lineAdder);
		if (overlayAdder != null)
			lineAdder = overlayAdder;
	}

	private static Pattern commaPattern = Pattern.compile(",");

	public GType makeGTypeFromTags(Element element) {
		String[] vals = commaPattern.split(element.getTag("mkgmap:gtype"));

		if(vals.length < 3) {
			log.error("OSM element " + element.getId() + " has bad mkgmap:gtype value (should be 'kind,code,minres,[maxres],[roadclass],[roadspeed])");
			log.error("  where kind is " + GType.POINT + "=point, " + GType.POLYLINE + "=polyline, " + GType.POLYGON + "=polygon");
			return null;
		}

		String name = element.getTag("name");
		if(name != null)
			element.setName(PolishMapDataSource.unescape(name));

		for(int i = 0; i < vals.length; ++i)
			vals[i] = vals[i].trim();

		int kind = 0;
		try {
			kind = Integer.decode(vals[0]);
		}
		catch (NumberFormatException nfe) {
			log.error("OSM element " + element.getId() + " has bad value for kind: " + vals[0]);
			return null;
		}

		if(kind != GType.POINT &&
		   kind != GType.POLYLINE &&
		   kind != GType.POLYGON) {
			log.error("OSM element " + element.getId() + " has bad value for kind, is " + kind + " but should be " + GType.POINT + ", " + GType.POLYLINE + " or " + GType.POLYGON);
			return null;
		}

		try {
			Integer.decode(vals[1]);
		}
		catch (NumberFormatException nfe) {
			log.error("OSM element " + element.getId() + " has bad value for type: " + vals[1]);
			return null;
		}

		GType gt = new GType(kind, vals[1]);

		try {
			gt.setMinResolution(Integer.decode(vals[2]));
		}
		catch (NumberFormatException nfe) {
			log.error("OSM element " + element.getId() + " has bad value for minres: " + vals[2]);
		}

		if(vals.length >= 4 && vals[3].length() > 0) {
			try {
				gt.setMaxResolution(Integer.decode(vals[3]));
			}
			catch (NumberFormatException nfe) {
				log.error("OSM element " + element.getId() + " has bad value for maxres tag: " + vals[3]);
			}
		}

		if(vals.length >= 5 && vals[4].length() > 0) {
			try {
				gt.setRoadClass(Integer.decode(vals[4]));
			}
			catch (NumberFormatException nfe) {
				log.error("OSM element " + element.getId() + " has bad value for roadclass: " + vals[4]);
			}
		}

		if(vals.length >= 6 && vals[5].length() > 0) {
			try {
				gt.setRoadClass(Integer.decode(vals[5]));
			}
			catch (NumberFormatException nfe) {
				log.error("OSM element " + element.getId() + " has bad value for roadspeed: " + vals[5]);
			}
		}

		return gt;
	}

	/**
	 * This takes the way and works out what kind of map feature it is and makes
	 * the relevant call to the mapper callback.
	 * <p>
	 * As a few examples we might want to check for the 'highway' tag, work out
	 * if it is an area of a park etc.
	 *
	 * @param way The OSM way.
	 */
	public void convertWay(Way way) {
		if (way.getPoints().size() < 2)
			return;

		GType foundType = null;
		if(way.getTag("mkgmap:gtype") != null) {
			foundType = makeGTypeFromTags(way);
			if(foundType == null)
				return;
		}
		else {
			preConvertRules(way);

			foundType = wayRules.resolveType(way);
			if (foundType == null)
				return;

			postConvertRules(way, foundType);
		}

		if (foundType.getFeatureKind() == GType.POLYLINE) {
		    if(foundType.isRoad() &&
			   !MapElement.hasExtendedType(foundType.getType()))
				addRoad(way, foundType);
		    else
				addLine(way, foundType);
		}
		else
			addShape(way, foundType);
	}

	/**
	 * Takes a node (that has its own identity) and converts it from the OSM
	 * type to the Garmin map type.
	 *
	 * @param node The node to convert.
	 */
	public void convertNode(Node node) {

		GType foundType = null;
		if(node.getTag("mkgmap:gtype") != null) {
			foundType = makeGTypeFromTags(node);
			if(foundType == null)
				return;
		}
		else {
			preConvertRules(node);

			foundType = nodeRules.resolveType(node);
			if (foundType == null)
				return;

			postConvertRules(node, foundType);
		}

		addPoint(node, foundType);
	}

	/**
	 * Rules to run before converting the element.
	 */
	private void preConvertRules(Element el) {
		if (nameTagList == null)
			return;

		for (String t : nameTagList) {
			String val = el.getTag(t);
			if (val != null) {
				el.addTag("name", val);
				break;
			}
		}
	}

	/**
	 * Built in rules to run after converting the element.
	 */
	private void postConvertRules(Element el, GType type) {
		// Set the name from the 'name' tag or failing that from
		// the default_name.
		el.setName(el.getTag("name"));
		if (el.getName() == null)
			el.setName(type.getDefaultName());
	}

	/**
	 * Set the bounding box for this map.  This should be set before any other
	 * elements are converted if you want to use it. All elements that are added
	 * are clipped to this box, new points are added as needed at the boundry.
	 *
	 * If a node or a way falls completely outside the boundry then it would be
	 * ommited.  This would not normally happen in the way this option is typically
	 * used however.
	 *
	 * @param bbox The bounding area.
	 */
	public void setBoundingBox(Area bbox) {
		this.clipper = new AreaClipper(bbox);
		this.bbox = bbox;
	}

	/**
	 * Run the rules for this relation.  As this is not an end object, then
	 * the only useful rules are action rules that set tags on the contained
	 * ways or nodes.  Every rule should probably start with 'type=".."'.
	 *
	 * @param relation The relation to convert.
	 */
	public void convertRelation(Relation relation) {
		// Relations never resolve to a GType and so we ignore the return
		// value.
		relationRules.resolveType(relation);

		if(relation instanceof RestrictionRelation) {
			RestrictionRelation rr = (RestrictionRelation)relation;
			if(rr.isValid()) {
				List<RestrictionRelation> lrr = restrictions.get(rr.getViaCoord());
				if(lrr == null) {
					lrr = new ArrayList<RestrictionRelation>();
					restrictions.put(rr.getViaCoord(), lrr);
				}
				lrr.add(rr);
			}
		}
	}

	private void addLine(Way way, GType gt) {
		MapLine line = new MapLine();
		elementSetup(line, gt, way);
		line.setPoints(way.getPoints());

		if (way.isBoolTag("oneway"))
			line.setDirection(true);

		clipper.clipLine(line, lineAdder);
	}

	private void addShape(Way way, GType gt) {
		MapShape shape = new MapShape();
		elementSetup(shape, gt, way);
		shape.setPoints(way.getPoints());

		clipper.clipShape(shape, collector);
		
		GType pointType = nodeRules.resolveType(way);
		
		if(pointType != null)
			shape.setPoiType(pointType.getType());
	}

	private void addPoint(Node node, GType gt) {
		if (!clipper.contains(node.getLocation()))
			return;

		// to handle exit points we use a subclass of MapPoint
		// to carry some extra info (a reference to the
		// motorway associated with the exit)
		MapPoint mp;
		int type = gt.getType();
		if(type >= 0x2000 && type < 0x2800) {
			String ref = node.getTag(Exit.TAG_ROAD_REF);
			String id = node.getTag("osm:id");
			if(ref != null) {
				String to = node.getTag(Exit.TAG_TO);
				MapExitPoint mep = new MapExitPoint(ref, to);
				String fd = node.getTag(Exit.TAG_FACILITY);
				if(fd != null)
					mep.setFacilityDescription(fd);
				if(id != null)
					mep.setOSMId(id);
				mp = mep;
			}
			else {
				mp = new MapPoint();
				log.warn("Motorway exit " + node.getName() + " (" + node.getLocation().toOSMURL() + ") has no motorway! (either make the exit share a node with the motorway or specify the motorway ref with a " + Exit.TAG_ROAD_REF + " tag)");
			}
		}
		else {
			mp = new MapPoint();
		}
		elementSetup(mp, gt, node);
		mp.setLocation(node.getLocation());

		collector.addPoint(mp);
	}

	private String combineRefs(Element element) {
		String ref = element.getTag("ref");
		String int_ref = element.getTag("int_ref");
		if(int_ref != null) {
			if(ref == null)
				ref = int_ref;
			else
				ref += ";" + int_ref;
		}
		String nat_ref = element.getTag("nat_ref");
		if(nat_ref != null) {
			if(ref == null)
				ref = nat_ref;
			else
				ref += ";" + nat_ref;
		}
		String reg_ref = element.getTag("reg_ref");
		if(reg_ref != null) {
			if(ref == null)
				ref = reg_ref;
			else
				ref += ";" + reg_ref;
		}

		return ref;
	}

	private void elementSetup(MapElement ms, GType gt, Element element) {
		String name = element.getName();
		String refs = combineRefs(element);
		if(name == null && refs != null) {
			// use first ref as name
			name = refs.split(";")[0].trim();
		}
		if(name != null)
			ms.setName(name);
		if(refs != null)
			ms.setRef(refs);
		ms.setType(gt.getType());
		ms.setMinResolution(gt.getMinResolution());
		ms.setMaxResolution(gt.getMaxResolution());
		
		// Now try to get some address info for POIs
		
		String city         = element.getTag("addr:city");
		String zip          = element.getTag("addr:postcode");
		String street 	    = element.getTag("addr:street");
		String houseNumber  = element.getTag("addr:housenumber");
		String phone        = element.getTag("phone");
		String isIn         = element.getTag("is_in");
		String country      = element.getTag("is_in:country");
		String region       = element.getTag("is_in:county");
		
		if(country != null)
			country = element.getTag("addr:country");

		if(zip == null)
		  zip = element.getTag("openGeoDB:postal_codes");
		
		if(city == null)
		  city = element.getTag("openGeoDB:sort_name");
		
		if(city != null)
		  ms.setCity(city);
		  
		if(zip != null)
		  ms.setZip(zip);
		  
		if(street != null)
		  ms.setStreet(street);		  

		if(houseNumber != null)
		  ms.setHouseNumber(houseNumber);
		  
		if(isIn != null)
		  ms.setIsIn(isIn);		  
			
		if(phone != null)
		  ms.setPhone(phone);	

		if(country != null)
		  ms.setCountry(country);	

		if(region != null)
		  ms.setRegion(region);			

		if(MapElement.hasExtendedType(gt.getType()))
			ms.setExtTypeAttributes(new ExtTypeAttributes(element.getTagsWithPrefix("mkgmap:xt-", true), "OSM id " + element.getId()));
	}

	void addRoad(Way way, GType gt) {

		String oneWay = way.getTag("oneway");
		if("-1".equals(oneWay) || "reverse".equals(oneWay)) {
			// it's a oneway street in the reverse direction
			// so reverse the order of the nodes and change
			// the oneway tag to "yes"
			way.reverse();
			way.addTag("oneway", "yes");
			if("roundabout".equals(way.getTag("junction")))
				log.warn("Roundabout " + way.getId() + " has reverse oneway tag (" + way.getPoints().get(0).toOSMURL() + ")");
		}

		if("roundabout".equals(way.getTag("junction"))) {
			List<Coord> points = way.getPoints();
			// if roundabout checking is enabled and roundabout has at
			// least 3 points and it has not been marked as "don't
			// check", check its direction
			if(checkRoundabouts &&
			   way.getPoints().size() > 3 &&
			   !way.isBoolTag("mkgmap:no-dir-check")) {
				Coord centre = way.getCofG();
				int dir = 0;
				// check every third segment
				for(int i = 0; (i + 1) < points.size(); i += 3) {
					Coord pi = points.get(i);
					Coord pi1 = points.get(i + 1);
					// don't check segments that are very short
					if(pi.quickDistance(centre) > 2.5 &&
					   pi.quickDistance(pi1) > 2.5) {
						// determine bearing from segment that starts with
						// point i to centre of roundabout
						double a = pi.bearingTo(pi1);
						double b = pi.bearingTo(centre) - a;
						while(b > 180)
							b -= 360;
						while(b < -180)
							b += 360;
						// if bearing to centre is between 15 and 165
						// degrees consider it trustworthy
						if(b >= 15 && b < 165)
							++dir;
						else if(b <= -15 && b > -165)
							--dir;
					}
				}
				if(dir != 0) {
					boolean clockwise = dir > 0;
					if(points.get(0) == points.get(points.size() - 1)) {
						// roundabout is a loop
						if(!driveOnLeft && !driveOnRight) {
							if(clockwise) {
								log.info("Roundabout " + way.getId() + " is clockwise so assuming vehicles should drive on left side of road (" + centre.toOSMURL() + ")");
								driveOnLeft = true;
								NODHeader.setDriveOnLeft(true);
							}
							else {
								log.info("Roundabout " + way.getId() + " is anti-clockwise so assuming vehicles should drive on right side of road (" + centre.toOSMURL() + ")");
								driveOnRight = true;
							}
						}
						if(driveOnLeft && !clockwise ||
						   driveOnRight && clockwise) {
							log.warn("Roundabout " + way.getId() + " direction is wrong - reversing it (see " + centre.toOSMURL() + ")");
							way.reverse();
						}
					}
					else if(driveOnLeft && !clockwise ||
							driveOnRight && clockwise) {
						// roundabout is a line
						log.warn("Roundabout segment " + way.getId() + " direction looks wrong (see " + points.get(0).toOSMURL() + ")");
					}
				}
				else
					log.info("Roundabout segment " + way.getId() + " direction unknown (see " + points.get(0).toOSMURL() + ")");
			}

			String frigFactorTag = way.getTag("mkgmap:frig_roundabout");
			if(frigFactorTag != null) {
				// do special roundabout frigging to make gps
				// routing prompt use the correct exit number
				double frigFactor = 0.25; // default
				try {
					frigFactor = Double.parseDouble(frigFactorTag);
				}
				catch (NumberFormatException nfe) {
					// relax, tag was probably not a number anyway
				}
				frigRoundabout(way, frigFactor);
			}
		}

		// process any Coords that have a POI associated with them
		if("true".equals(way.getTag("mkgmap:way-has-pois"))) {
			List<Coord> points = way.getPoints();

			// for highways, see if its name is set by a POI located
			// at the first point
			if(points.size() > 1 && points.get(0) instanceof CoordPOI) {
				String highwayKind = way.getTag("highway");
				if(highwayKind != null) {
					Node poiNode = ((CoordPOI)points.get(0)).getNode();
					String nameFromPoi = poiNode.getTag(highwayKind + "_name");
					if(nameFromPoi != null) {
						way.setName(nameFromPoi);
						log.info(highwayKind + " " + way.getId() + " named '" + way.getName() + "'");
					}
				}
			}

			// at this time, we are only looking for POIs that have
			// the "access" tag defined - if they do, copy the access
			// permissions to the way - what we want to achieve is
			// modifying the way's access permissions where it passes
			// through the POI without affecting the rest of the way
			// too much - to that end we split the way before and
			// after the POI - if necessary, extra points are inserted
			// before and after the POI to limit the size of the
			// affected region

			final double stubSegmentLength = 25; // metres
			for(int i = 0; i < points.size(); ++i) {
				Coord p = points.get(i);
				// check if this POI modifies access and if so, split
				// the way at the following point (if any) and then
				// copy its access restrictions to the way
				if(p instanceof CoordPOI) {
					CoordPOI cp = (CoordPOI)p;
					Node node = cp.getNode();
					if(node.getTag("access") != null) {
						// if this or the next point are not the last
						// points in the way, split at the next point
						// taking care not to produce a short arc
						if((i + 1) < points.size()) {
							Coord p1 = points.get(i + 1);
							// check if the next point is further away
							// than we would like
							double dist = p.distance(p1);
							if(dist >= (2 * stubSegmentLength)) {
								// insert a new point after the POI to
								// make a short stub segment
								p1 = p.makeBetweenPoint(p1, stubSegmentLength / dist);
								points.add(i + 1, p1);
							}

							// now split the way at the next point to
							// limit the region that has restricted
							// access
							if(!p.equals(p1) &&
							   ((i + 2) == points.size() ||
								!p1.equals(points.get(i + 2)))) {
								Way tail = splitWayAt(way, i + 1);
								// recursively process tail of way
								addRoad(tail, gt);
							}
						}

						// make the POI a node so that the region with
						// restricted access is split into two as far
						// as routing is concerned - this should stop
						// routing across the POI when the start point
						// is within the restricted region and the
						// destination point is outside of the
						// restricted region on the other side of the
						// POI

						// however, this still doesn't stop routing
						// across the POI when both the start and end
						// points are either side of the POI and both
						// are in the restricted region
						p.incHighwayCount();

						// copy all of the POI's access restrictions
						// to the way segment
						for (AccessMapping anAccessMap : accessMap) {
							String accessType = anAccessMap.type;
							String accessModifier = node.getTag(accessType);
							if(accessModifier != null)
								way.addTag(accessType, accessModifier);
						}
					}
				}

				// check if the next point modifies access and if so,
				// split the way either here or at a new point that's
				// closer to the POI taking care not to introduce a
				// short arc
				if((i + 1) < points.size()) {
					Coord p1 = points.get(i + 1);
					if(p1 instanceof CoordPOI) {
						CoordPOI cp = (CoordPOI)p1;
						Node node = cp.getNode();
						if(node.getTag("access") != null) {
							// check if this point is further away
							// from the POI than we would like
							double dist = p.distance(p1);
							if(dist >= (2 * stubSegmentLength)) {
								// insert a new point to make a short
								// stub segment
								p1 = p1.makeBetweenPoint(p, stubSegmentLength / dist);
								points.add(i + 1, p1);
								// as p1 is now no longer a CoordPOI,
								// the split below will be deferred
								// until the next iteration of the
								// loop (which is what we want!)
							}

							// now split the way here if it is not the
							// first point in the way
							if(p1 instanceof CoordPOI &&
							   i > 0 &&
							   !p.equals(points.get(i - 1)) &&
							   !p.equals(p1)) {
								Way tail = splitWayAt(way, i);
								// recursively process tail of road
								addRoad(tail, gt);
							}
						}
					}
				}
			}
		}

		// if there is a bounding box, clip the way with it

		List<Way> clippedWays = null;

		if(bbox != null) {
			List<List<Coord>> lineSegs = LineClipper.clip(bbox, way.getPoints());

			if (lineSegs != null) {

				clippedWays = new ArrayList<Way>();

				for (List<Coord> lco : lineSegs) {
					Way nWay = new Way(way.getId());
					nWay.setName(way.getName());
					nWay.copyTags(way);
					for(Coord co : lco) {
						nWay.addPoint(co);
						if(co.getOnBoundary()) {
							// this point lies on a boundary
							// make sure it becomes a node
							co.incHighwayCount();
						}
					}
					clippedWays.add(nWay);
					// associate the original Way
					// to the new Way
					Way origWay = originalWay.get(way);
					if(origWay == null)
						origWay = way;
					originalWay.put(nWay, origWay);
				}
			}
		}

		if(clippedWays != null) {
			for(Way cw : clippedWays) {
				while(cw.getPoints().size() > MAX_POINTS_IN_WAY) {
					Way tail = splitWayAt(cw, MAX_POINTS_IN_WAY - 1);
					addRoadAfterSplittingLoops(cw, gt);
					cw = tail;
				}
				addRoadAfterSplittingLoops(cw, gt);
			}
		}
		else {
			// no bounding box or way was not clipped
			while(way.getPoints().size() > MAX_POINTS_IN_WAY) {
				Way tail = splitWayAt(way, MAX_POINTS_IN_WAY - 1);
				addRoadAfterSplittingLoops(way, gt);
				way = tail;
			}
			addRoadAfterSplittingLoops(way, gt);
		}
	}

	void addRoadAfterSplittingLoops(Way way, GType gt) {

		// check if the way is a loop or intersects with itself

		boolean wayWasSplit = true; // aka rescan required

		while(wayWasSplit) {
			List<Coord> wayPoints = way.getPoints();
			int numPointsInWay = wayPoints.size();

			wayWasSplit = false; // assume way won't be split

			// check each point in the way to see if it is the same
			// point as a following point in the way (actually the
			// same object not just the same coordinates)
			for(int p1I = 0; !wayWasSplit && p1I < (numPointsInWay - 1); p1I++) {
				Coord p1 = wayPoints.get(p1I);
				for(int p2I = p1I + 1; !wayWasSplit && p2I < numPointsInWay; p2I++) {
					if(p1 == wayPoints.get(p2I)) {
						// way is a loop or intersects itself 
						// attempt to split it into two ways

						// start at point before intersection point
						// check that splitting there will not produce
						// a zero length arc - if it does try the
						// previous point(s)
						int splitI = p2I - 1;
						while(splitI > p1I &&
							  !safeToSplitWay(wayPoints, splitI, p1I, p2I)) {
								log.info("Looped way " + getDebugName(way) + " can't safely split at point[" + splitI + "], trying the preceeding point");
							--splitI;
						}

						if(splitI == p1I) {
							log.warn("Splitting looped way " + getDebugName(way) + " would make a zero length arc, so it will have to be pruned");
							do {
								log.warn("  Pruning point[" + p2I + "]");
								wayPoints.remove(p2I);
								// next point to inspect has same index
								--p2I;
								// but number of points has reduced
								--numPointsInWay;

								// if wayPoints[p2I] is the last point
								// in the way and it is so close to p1
								// that a short arc would be produced,
								// loop back and prune it
							} while(p2I > p1I &&
									(p2I + 1) == numPointsInWay &&
									p1.equals(wayPoints.get(p2I)));
						}
						else {
							// split the way before the second point
							log.info("Splitting looped way " + getDebugName(way) + " at point[" + splitI + "] - it has " + (numPointsInWay - splitI - 1 ) + " following segment(s).");
							Way loopTail = splitWayAt(way, splitI);
							// recursively check (shortened) head for
							// more loops
							addRoadAfterSplittingLoops(way, gt);
							// now process the tail of the way
							way = loopTail;
							wayWasSplit = true;
						}
					}
				}
			}

			if(!wayWasSplit) {
				// no split required so make road from way
				addRoadWithoutLoops(way, gt);
			}
		}
	}

	// safeToSplitWay() returns true if it safe (no short arcs will be
	// created) to split a way at a given position
	//
	// points - the way's points
	// pos - the position we are testing
	// floor - lower limit of points to test (inclusive)
	// ceiling - upper limit of points to test (inclusive)

	boolean safeToSplitWay(List<Coord> points, int pos, int floor, int ceiling) {
		Coord candidate = points.get(pos);
		// test points after pos
		for(int i = pos + 1; i <= ceiling; ++i) {
			Coord p = points.get(i);
			if(p.getHighwayCount() > 1) {
				// point is going to be a node
				if(candidate.equals(p))
					return false;
				// no need to test further
				break;
			}
		}
		// test points before pos
		for(int i = pos - 1; i >= floor; --i) {
			Coord p = points.get(i);
			if(p.getHighwayCount() > 1) {
				// point is going to be a node
				if(candidate.equals(p))
					return false;
				// no need to test further
				break;
			}
		}

		return true;
	}

	String getDebugName(Way way) {
		String name = way.getName();
		if(name == null)
			name = way.getTag("ref");
		if(name == null)
			name = "";
		else
			name += " ";
		return name + "(OSM id " + way.getId() + ")";
	}

	void addRoadWithoutLoops(Way way, GType gt) {
		List<Integer> nodeIndices = new ArrayList<Integer>();
		List<Coord> points = way.getPoints();
		Way trailingWay = null;
		String debugWayName = getDebugName(way);

		// make sure the way has nodes at each end
		points.get(0).incHighwayCount();
		points.get(points.size() - 1).incHighwayCount();

		// collect the Way's nodes and also split the way if any
		// inter-node arc length becomes excessive
		double arcLength = 0;
		for(int i = 0; i < points.size(); ++i) {
			Coord p = points.get(i);

			// check if we should split the way at this point to limit
			// the arc length between nodes
			if((i + 1) < points.size()) {
				double d = p.distance(points.get(i + 1));
				if(d > MAX_ARC_LENGTH) {
					double fraction = 0.99 * MAX_ARC_LENGTH / d;
					Coord extrap = p.makeBetweenPoint(points.get(i + 1), fraction);
					extrap.incHighwayCount();
					points.add(i + 1, extrap);
					double newD = p.distance(extrap);
					log.info("Way " + debugWayName + " contains a segment that is " + (int)d + "m long so I am adding a point to reduce its length to " + (int)newD + "m");
					d = newD;
				}

				if((arcLength + d) > MAX_ARC_LENGTH) {
					assert i > 0;
					trailingWay = splitWayAt(way, i);
					// this will have truncated the current Way's
					// points so the loop will now terminate
					log.info("Splitting way " + debugWayName + " at " + points.get(i).toDegreeString() + " to limit arc length to " + (long)arcLength + "m");
				}
				else {
					if(p.getHighwayCount() > 1)
						// point is a node so zero arc length
						arcLength = 0;

					arcLength += d;
				}
			}

			if(p.getHighwayCount() > 1) {
				// this point is a node connecting highways
				Integer nodeId = nodeIdMap.get(p);
				if(nodeId == null) {
					// assign a node id
					nodeIdMap.put(p, nextNodeId++);
				}
				nodeIndices.add(i);

				if((i + 1) < points.size() &&
				   nodeIndices.size() == MAX_NODES_IN_WAY) {
					// this isn't the last point in the way so split
					// it here to avoid exceeding the max nodes in way
					// limit
					trailingWay = splitWayAt(way, i);
					// this will have truncated the current Way's
					// points so the loop will now terminate
					log.info("Splitting way " + debugWayName + " at " + points.get(i).toDegreeString() + " as it has at least " + MAX_NODES_IN_WAY + " nodes");
				}
			}
		}

		MapLine line = new MapLine();
		elementSetup(line, gt, way);
		line.setPoints(points);

		MapRoad road = new MapRoad(way.getId(), line);

		// set road parameters.
		road.setRoadClass(gt.getRoadClass());
		if (way.isBoolTag("oneway")) {
			road.setDirection(true);
			road.setOneway();
		}

		int speedIdx = -1;
		if(!ignoreMaxspeeds) {
			// maxspeed attribute overrides default for road type
			String maxSpeed = way.getTag("maxspeed");
			if(maxSpeed != null) {
				speedIdx = getSpeedIdx(maxSpeed);
				log.info(debugWayName + " maxspeed=" + maxSpeed + ", speedIndex=" + speedIdx);
			}
		}

		road.setSpeed(speedIdx >= 0? speedIdx : gt.getRoadSpeed());

		boolean[] noAccess = new boolean[RoadNetwork.NO_MAX];
		String highwayType = way.getTag("highway");
		if(highwayType == null) {
			// it's a routable way but not a highway (e.g. a ferry)
			// use the value of the route tag as the highwayType for
			// the purpose of testing for access restrictions
			highwayType = way.getTag("route");
		}

		for (AccessMapping anAccessMap : accessMap) {
			int index = anAccessMap.index;
			String type = anAccessMap.type;
			String accessTagValue = way.getTag(type);
			if (accessTagValue == null)
				continue;
			if (accessExplicitlyDenied(accessTagValue)) {
				if (index == RoadNetwork.NO_MAX) {
					// everything is denied access
					for (int j = 1; j < accessMap.length; ++j)
						noAccess[accessMap[j].index] = true;
				} else {
					// just the specific vehicle class is denied
					// access
					noAccess[index] = true;
				}
				log.info(type + " is not allowed in " + highwayType + " " + debugWayName);
			} else if (accessExplicitlyAllowed(accessTagValue)) {
				if (index == RoadNetwork.NO_MAX) {
					// everything is allowed access
					for (int j = 1; j < accessMap.length; ++j)
						noAccess[accessMap[j].index] = false;
				} else {
					// just the specific vehicle class is allowed
					// access
					noAccess[index] = false;
				}
				log.info(type + " is allowed in " + highwayType + " " + debugWayName);
			}
			else if (accessTagValue.equalsIgnoreCase("destination")) {
				if (type.equals("motorcar") ||
				    type.equals("motorcycle")) {
					road.setNoThroughRouting();
				} else if (type.equals("access")) {
					log.info("access=destination only affects routing for cars in " + highwayType + " " + debugWayName);
					road.setNoThroughRouting();
				} else {
					log.info(type + "=destination ignored in " + highwayType + " " + debugWayName);
				}
			} else if (accessTagValue.equalsIgnoreCase("unknown")) {
				// implicitly allow access
			} else {
				log.info("Ignoring unsupported access tag value " + type + "=" + accessTagValue + " in " + highwayType + " " + debugWayName);
			}
		}

		road.setAccess(noAccess);

		if(way.isBoolTag("toll"))
			road.setToll();

		Way origWay = originalWay.get(way);
		if(origWay == null)
			origWay = way;

		int numNodes = nodeIndices.size();
		road.setNumNodes(numNodes);

		if(numNodes > 0) {
			// replace Coords that are nodes with CoordNodes
			boolean hasInternalNodes = false;
			CoordNode lastCoordNode = null;
			List<RestrictionRelation> lastRestrictions = null;
			for(int i = 0; i < numNodes; ++i) {
				int n = nodeIndices.get(i);
				if(n > 0 && n < points.size() - 1)
					hasInternalNodes = true;
				Coord coord = points.get(n);
				Integer nodeId = nodeIdMap.get(coord);
				boolean boundary = coord.getOnBoundary();
				if(boundary) {
					log.info("Way " + debugWayName + "'s point #" + n + " at " + points.get(n).toDegreeString() + " is a boundary node");
				}

				CoordNode thisCoordNode = new CoordNode(coord.getLatitude(), coord.getLongitude(), nodeId, boundary);
				points.set(n, thisCoordNode);

				// see if this node plays a role in any turn
				// restrictions

				if(lastRestrictions != null) {
					// the previous node was the location of one or
					// more restrictions
					for(RestrictionRelation rr : lastRestrictions) {
						if(rr.getToWay().equals(origWay)) {
							rr.setToNode(thisCoordNode);
						}
						else if(rr.getFromWay().equals(origWay)) {
							rr.setFromNode(thisCoordNode);
						}
						else {
							rr.addOtherNode(thisCoordNode);
						}
					}
				}

				List<RestrictionRelation> theseRestrictions = restrictions.get(coord);
				if(theseRestrictions != null) {
					// this node is the location of one or more
					// restrictions
					for(RestrictionRelation rr : theseRestrictions) {
						rr.setViaNode(thisCoordNode);
						if(rr.getToWay().equals(origWay)) {
							if(lastCoordNode != null)
								rr.setToNode(lastCoordNode);
						}
						else if(rr.getFromWay().equals(origWay)) {
							if(lastCoordNode != null)
								rr.setFromNode(lastCoordNode);
						}
						else if(lastCoordNode != null) {
							rr.addOtherNode(lastCoordNode);
						}
					}
				}

				lastRestrictions = theseRestrictions;
				lastCoordNode = thisCoordNode;
			}

			road.setStartsWithNode(nodeIndices.get(0) == 0);
			road.setInternalNodes(hasInternalNodes);
		}

		lineAdder.add(road);

		if(trailingWay != null)
			addRoadWithoutLoops(trailingWay, gt);
	}

	// split a Way at the specified point and return the new Way (the
	// original Way is truncated)

	Way splitWayAt(Way way, int index) {
		Way trailingWay = new Way(way.getId());
		List<Coord> wayPoints = way.getPoints();
		int numPointsInWay = wayPoints.size();

		for(int i = index; i < numPointsInWay; ++i)
			trailingWay.addPoint(wayPoints.get(i));

		// ensure split point becomes a node
		wayPoints.get(index).incHighwayCount();

		// copy the way's name and tags to the new way
		trailingWay.setName(way.getName());
		trailingWay.copyTags(way);

		// remove the points after the split from the original way
		// it's probably more efficient to remove from the end first
		for(int i = numPointsInWay - 1; i > index; --i)
			wayPoints.remove(i);

		// associate the original Way to the new Way
		Way origWay = originalWay.get(way);
		if(origWay == null)
			origWay = way;
		originalWay.put(trailingWay, origWay);

		return trailingWay;
	}

	// function to add points between adjacent nodes in a roundabout
	// to make gps use correct exit number in routing instructions
	void frigRoundabout(Way way, double frigFactor) {
		List<Coord> wayPoints = way.getPoints();
		int origNumPoints = wayPoints.size();

		if(origNumPoints < 3) {
			// forget it!
			return;
		}

		int[] highWayCounts = new int[origNumPoints];
		int middleLat = 0;
		int middleLon = 0;
		highWayCounts[0] = wayPoints.get(0).getHighwayCount();
		for(int i = 1; i < origNumPoints; ++i) {
			Coord p = wayPoints.get(i);
			middleLat += p.getLatitude();
			middleLon += p.getLongitude();
			highWayCounts[i] = p.getHighwayCount();
		}
		middleLat /= origNumPoints - 1;
		middleLon /= origNumPoints - 1;
		Coord middleCoord = new Coord(middleLat, middleLon);

		// account for fact that roundabout joins itself
		--highWayCounts[0];
		--highWayCounts[origNumPoints - 1];

		for(int i = origNumPoints - 2; i >= 0; --i) {
			Coord p1 = wayPoints.get(i);
			Coord p2 = wayPoints.get(i + 1);
			if(highWayCounts[i] > 1 && highWayCounts[i + 1] > 1) {
				// both points will be nodes so insert a new point
				// between them that (approximately) falls on the
				// roundabout's perimeter
				int newLat = (p1.getLatitude() + p2.getLatitude()) / 2;
				int newLon = (p1.getLongitude() + p2.getLongitude()) / 2;
				// new point has to be "outside" of existing line
				// joining p1 and p2 - how far outside is determined
				// by the ratio of the distance between p1 and p2
				// compared to the distance of p1 from the "middle" of
				// the roundabout (aka, the approx radius of the
				// roundabout) - the higher the value of frigFactor,
				// the further out the point will be
				double scale = 1 + frigFactor * p1.distance(p2) / p1.distance(middleCoord);
				newLat = (int)((newLat - middleLat) * scale) + middleLat;
				newLon = (int)((newLon - middleLon) * scale) + middleLon;
				Coord newPoint = new Coord(newLat, newLon);
				double d1 = p1.distance(newPoint);
				double d2 = p2.distance(newPoint);
				double maxDistance = 100;
				if(d1 >= MIN_DISTANCE_BETWEEN_NODES && d1 <= maxDistance &&
				   d2 >= MIN_DISTANCE_BETWEEN_NODES && d2 <= maxDistance) {
				    newPoint.incHighwayCount();
				    wayPoints.add(i + 1, newPoint);
				}
			}
		}
	}

	private int getSpeedIdx(String tag) {
		double factor = 1.0;
		
		String speedTag = tag.toLowerCase().trim();
		
		if (speedTag.matches(".*mph")) {
			// Check if it is a limit in mph
			speedTag = speedTag.replaceFirst("[ \t]*mph", "");
			factor = 1.61;
		} else
			speedTag = speedTag.replaceFirst("[ \t]*kmh", "");  // get rid of kmh just in case

		double kmh;
		try {
			kmh = Integer.parseInt(speedTag) * factor;
		} catch (Exception e) {
			return -1;
		}
		
		if(kmh > 110)
			return 7;
		if(kmh > 90)
			return 6;
		if(kmh > 80)
			return 5;
		if(kmh > 60)
			return 4;
		if(kmh > 40)
			return 3;
		if(kmh > 20)
			return 2;
		if(kmh > 10)
			return 1;
		else
			return 0;
	}

	protected boolean accessExplicitlyAllowed(String val) {
		if (val == null)
			return false;

		return (val.equalsIgnoreCase("yes") ||
			val.equalsIgnoreCase("designated") ||
			val.equalsIgnoreCase("permissive"));
	}

	protected boolean accessExplicitlyDenied(String val) {
		if (val == null)
			return false;

		return (val.equalsIgnoreCase("no") ||
			val.equalsIgnoreCase("private"));
	}
}
