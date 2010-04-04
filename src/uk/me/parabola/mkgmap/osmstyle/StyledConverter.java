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
import java.util.Collection;
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
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.net.NODHeader;
import uk.me.parabola.imgfmt.app.trergn.ExtTypeAttributes;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.filters.LineSizeSplitterFilter;
import uk.me.parabola.mkgmap.filters.LineSplitterFilter;
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
import uk.me.parabola.mkgmap.reader.osm.TypeResult;
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

	private final List<Relation> throughRouteRelations = new ArrayList<Relation>();

	// originalWay associates Ways that have been created due to
	// splitting or clipping with the Ways that they were derived
	// from
	private final Map<Way, Way> originalWay = new HashMap<Way, Way>();

	// limit line length to avoid problems with portions of really
	// long lines being assigned to the wrong subdivision
	private final int MAX_LINE_LENGTH = 40000;

	// limit arc lengths to what can be handled by RouteArc
	private final int MAX_ARC_LENGTH = 75000;

	private final int MAX_POINTS_IN_WAY = LineSplitterFilter.MAX_POINTS_IN_LINE;

	private final int MAX_POINTS_IN_ARC = MAX_POINTS_IN_WAY;

	private final int MAX_NODES_IN_WAY = 64; // possibly could be increased

	private final double MIN_DISTANCE_BETWEEN_NODES = 5.5;

	// nodeIdMap maps a Coord into a nodeId
	private final Map<Coord, Integer> nodeIdMap = new IdentityHashMap<Coord, Integer>();
	private int nextNodeId = 1;
	
	private final Rule wayRules;
	private final Rule nodeRules;
	private final Rule relationRules;

	private final boolean ignoreMaxspeeds;
	private boolean driveOnLeft;
	private boolean driveOnRight;
	private final boolean checkRoundabouts;
	private static final Pattern ENDS_IN_MPH_PATTERN = Pattern.compile(".*mph");
	private static final Pattern REMOVE_MPH_PATTERN = Pattern.compile("[ \t]*mph");
	private static final Pattern REMOVE_KPH_PATTERN = Pattern.compile("[ \t]*kmh");
	private static final Pattern SEMI_PATTERN = Pattern.compile(";");

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
		new AccessMapping("carpool",    RoadNetwork.NO_CARPOOL),
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

	///**
	// * There may be tags that are not referenced directly in the style file
	// * that are used.  This includes the name-tag-list option.  Attempt
	// * to add such tags here.
	// * @param style
	// */
	//private void addExtraUsedTags(Style style) {
	//	String s = style.getOption("extra-used-tags");
	//	Collection<String> usedTags = new HashSet<String>();
	//	if (s != null)
	//		usedTags.addAll(Arrays.asList(s.split(" ")));
	//	if (nameTagList != null)
	//		usedTags.addAll(Arrays.asList(nameTagList));
	//
	//	System.out.println("extra " + usedTags);
	//	((RuleSet) wayRules).addUsedTags(usedTags);
	//	((RuleSet) nodeRules).addUsedTags(usedTags);
	//	((RuleSet) relationRules).addUsedTags(usedTags);
	//}

	private static final Pattern commaPattern = Pattern.compile(",");

	private GType makeGTypeFromTags(Element element) {
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

		int kind;
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
	public void convertWay(final Way way) {
		if (way.getPoints().size() < 2)
			return;

		if(way.getTag("mkgmap:gtype") != null) {
			GType foundType = makeGTypeFromTags(way);
			if(foundType == null)
				return;
			addConvertedWay(way, foundType);
			return;
		}

		preConvertRules(way);

		wayRules.resolveType(way, new TypeResult() {
			public void add(Element el, GType type) {
				if (type.isContinueSearch()) {
					// If not already copied, do so now
					if (el == way)
						el = way.copy();

					// Not sure if this is needed as this really is a completely new way.
					// originalWay.put(el, way);
				}
				postConvertRules(el, type);
				addConvertedWay((Way) el, type);
			}
		});
	}

	private void addConvertedWay(Way way, GType foundType) {
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
	public void convertNode(final Node node) {

		if(node.getTag("mkgmap:gtype") != null) {
			GType foundType = makeGTypeFromTags(node);
			if(foundType == null)
				return;

			addPoint(node, foundType);
			return;
		}

		preConvertRules(node);

		nodeRules.resolveType(node, new TypeResult() {
			public void add(Element el, GType type) {
				if (type.isContinueSearch()) {
					// If not already copied, do so now
					if (el == node)
						el = node.copy();

					// Not sure if this is needed as this really is a completely new way.
					// originalWay.put(el, way);
				}
				postConvertRules(el, type);
				addPoint((Node) el, type);
			}
		});
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

	public void end() {
		Collection<List<RestrictionRelation>> lists = restrictions.values();
		for (List<RestrictionRelation> l : lists) {

			for (RestrictionRelation rr : l) {
				rr.addRestriction(collector);
			}
		}

		for(Relation relation : throughRouteRelations) {
			Node node = null;
			Way w1 = null;
			Way w2 = null;
			for(Map.Entry<String,Element> member : relation.getElements()) {
				if(member.getValue() instanceof Node) {
					if(node == null)
						node = (Node)member.getValue();
					else
						log.warn("Through route relation " + relation.toBrowseURL() + " has more than 1 node");
				}
				else if(member.getValue() instanceof Way) {
					Way w = (Way)member.getValue();
					if(w1 == null)
						w1 = w;
					else if(w2 == null)
						w2 = w;
					else
						log.warn("Through route relation " + relation.toBrowseURL() + " has more than 2 ways");
				}
			}

			Coord junctionPoint = null;
			Integer nodeId = null;
			if(node == null)
				log.warn("Through route relation " + relation.toBrowseURL() + " is missing the junction node");
			else {
				junctionPoint = node.getLocation();
				if(bbox != null && !bbox.contains(junctionPoint)) {
					// junction is outside of the tile - ignore it
					continue;
				}
				nodeId = nodeIdMap.get(junctionPoint);
				if(nodeId == null)
					log.warn("Through route relation " + relation.toBrowseURL() + " junction node at " + junctionPoint.toOSMURL() + " is not a routing node");
			}

			if(w1 == null || w2 == null)
				log.warn("Through route relation " + relation.toBrowseURL() + " should reference 2 ways that meet at the junction node");

			if(nodeId != null && w1 != null && w2 != null)
				collector.addThroughRoute(nodeId, w1.getId(), w2.getId());
		}
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
		relationRules.resolveType(relation, TypeResult.NULL_RESULT);

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
		else if("through_route".equals(relation.getTag("type"))) {
			throughRouteRelations.add(relation);
		}
	}

	private void addLine(Way way, GType gt) {
		List<Coord> wayPoints = way.getPoints();
		List<Coord> points = new ArrayList<Coord>(wayPoints.size());
		double lineLength = 0;
		Coord lastP = null;
		for(Coord p : wayPoints) {
			points.add(p);
			if(lastP != null) {
				lineLength += p.distance(lastP);
				if(lineLength >= MAX_LINE_LENGTH) {
					log.info("Splitting line " + way.toBrowseURL() + " at " + p.toOSMURL() + " to limit its length to " + (long)lineLength + "m");
					addLine(way, gt, points);
					points = new ArrayList<Coord>(wayPoints.size() - points.size() + 1);
					points.add(p);
					lineLength = 0;
				}
			}
			lastP = p;
		}

		if(points.size() > 1)
			addLine(way, gt, points);
	}

	private void addLine(Way way, GType gt, List<Coord> points) {
		MapLine line = new MapLine();
		elementSetup(line, gt, way);
		line.setPoints(points);

		if (way.isBoolTag("oneway"))
			line.setDirection(true);

		clipper.clipLine(line, lineAdder);
	}

	private void addShape(Way way, GType gt) {
		final MapShape shape = new MapShape();
		elementSetup(shape, gt, way);
		shape.setPoints(way.getPoints());

		clipper.clipShape(shape, collector);
		
		nodeRules.resolveType(way, new TypeResult() {
			public void add(Element el, GType type) {
				if(type != null)
			shape.setPoiType(type.getType());
			}
		});
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
		String ref = Label.squashSpaces(element.getTag("ref"));
		String int_ref = Label.squashSpaces(element.getTag("int_ref"));
		if(int_ref != null) {
			if(ref == null)
				ref = int_ref;
			else
				ref += ";" + int_ref;
		}
		String nat_ref = Label.squashSpaces(element.getTag("nat_ref"));
		if(nat_ref != null) {
			if(ref == null)
				ref = nat_ref;
			else
				ref += ";" + nat_ref;
		}
		String reg_ref = Label.squashSpaces(element.getTag("reg_ref"));
		if(reg_ref != null) {
			if(ref == null)
				ref = reg_ref;
			else
				ref += ";" + reg_ref;
		}

		return ref;
	}

	private void elementSetup(MapElement ms, GType gt, Element element) {
		String name = Label.squashSpaces(element.getName());
		String refs = combineRefs(element);
		
		// Insert display_name as first ref.
		// This causes display_name to be displayed in routing 
		// directions, instead of only the ref.
		String displayName = Label.squashSpaces(element.getTag("display_name"));

		if (displayName != null) {
			// substitute '/' for ';' in display_name to avoid it
			// getting split below
			displayName = displayName.replace(";","/");
			if (refs == null)
				refs = displayName;
			else
				refs = displayName + ";" + refs;
		}

		if(name == null && refs != null) {
			// use first ref as name
			name = SEMI_PATTERN.split(refs)[0].trim();
		}
		else if(name != null) {
			// remove leading spaces (don't use trim() to avoid zapping
			// shield codes)
			char leadingCode = 0;
			if(name.length() > 1 &&
			   name.charAt(0) < 0x20 &&
			   name.charAt(1) == ' ') {
				leadingCode = name.charAt(0);
				name = name.substring(2);
			}
				
			while(name.length() > 0 && name.charAt(0) == ' ')
				name = name.substring(1);

			if(leadingCode != 0)
				name = new Character(leadingCode) + name;
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

		if(MapElement.hasExtendedType(gt.getType())) {
			// pass attributes with mkgmap:xt- prefix (strip prefix)
			Map<String,String> xta = element.getTagsWithPrefix("mkgmap:xt-", true);
			// also pass all attributes with seamark: prefix (no strip prefix)
			xta.putAll(element.getTagsWithPrefix("seamark:", false));
			ms.setExtTypeAttributes(new ExtTypeAttributes(xta, "OSM id " + element.getId()));
		}
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
			   way.getPoints().size() > 2 &&
			   !way.isBoolTag("mkgmap:no-dir-check") &&
			   !way.isNotBoolTag("mkgmap:dir-check")) {
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

			// now look for POIs that modify the way's road class or
			// speed
			for(int i = 0; i < points.size(); ++i) {
				Coord p = points.get(i);
				if(p instanceof CoordPOI) {
					CoordPOI cp = (CoordPOI)p;
					Node node = cp.getNode();
					String roadClass = node.getTag("mkgmap:road-class");
					String roadSpeed = node.getTag("mkgmap:road-speed");
					if(roadClass != null || roadSpeed != null) {
						// if the way has more than one point
						// following this one, split the way at the
						// next point to limit the size of the
						// affected region
						if((i + 2) < points.size() &&
						   safeToSplitWay(points, i + 1, i, points.size() - 1)) {
							Way tail = splitWayAt(way, i + 1);
							// recursively process tail of way
							addRoad(tail, gt);
						}
						// we can't modify the road class or type in
						// the GType as that's global so for now just
						// transfer the tags to the way
						if(roadClass != null) {
							way.addTag("mkgmap:road-class", roadClass);
							String val = node.getTag("mkgmap:road-class-min");
							if(val != null)
								way.addTag("mkgmap:road-class-min", val);
							val = node.getTag("mkgmap:road-class-max");
							if(val != null)
								way.addTag("mkgmap:road-class-max", val);
						}
						if(roadSpeed != null) {
							way.addTag("mkgmap:road-speed", roadSpeed);
							String val = node.getTag("mkgmap:road-speed-min");
							if(val != null)
								way.addTag("mkgmap:road-speed-min", val);
							val = node.getTag("mkgmap:road-speed-max");
							if(val != null)
								way.addTag("mkgmap:road-speed-max", val);
						}
					}
				}

				// if this isn't the first (or last) point in the way
				// and the next point modifies the way's speed/class,
				// split the way at this point to limit the size of
				// the affected region
				if(i > 0 &&
				   (i + 1) < points.size() &&
				   points.get(i + 1) instanceof CoordPOI) {
					CoordPOI cp = (CoordPOI)points.get(i + 1);
					Node node = cp.getNode();
					if(node.getTag("mkgmap:road-class") != null ||
					   node.getTag("mkgmap:road-speed") != null) {
						if(safeToSplitWay(points, i, i - 1, points.size() - 1)) {
							Way tail = splitWayAt(way, i);
							// recursively process tail of way
							addRoad(tail, gt);
						}
					}
				}
			}

			// now look for POIs that have the "access" tag defined -
			// if they do, copy the access permissions to the way -
			// what we want to achieve is modifying the way's access
			// permissions where it passes through the POI without
			// affecting the rest of the way too much - to that end we
			// split the way before and after the POI - if necessary,
			// extra points are inserted before and after the POI to
			// limit the size of the affected region

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
				// make sure the way has nodes at each end
				cw.getPoints().get(0).incHighwayCount();
				cw.getPoints().get(cw.getPoints().size() - 1).incHighwayCount();
				addRoadAfterSplittingLoops(cw, gt);
			}
		}
		else {
			// no bounding box or way was not clipped

			// make sure the way has nodes at each end
			way.getPoints().get(0).incHighwayCount();
			way.getPoints().get(way.getPoints().size() - 1).incHighwayCount();
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
							log.warn("Splitting looped way " + getDebugName(way) + " would make a zero length arc, so it will have to be pruned at " + wayPoints.get(p2I).toOSMURL());
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
							log.info("Splitting looped way " + getDebugName(way) + " at " + wayPoints.get(splitI).toOSMURL() + " - it has " + (numPointsInWay - splitI - 1 ) + " following segment(s).");
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
	// created) to split a way at a given position - assumes that the
	// floor and ceiling points will become nodes even if they are not
	// yet
	//
	// points - the way's points
	// pos - the position we are testing
	// floor - lower limit of points to test (inclusive)
	// ceiling - upper limit of points to test (inclusive)

	boolean safeToSplitWay(List<Coord> points, int pos, int floor, int ceiling) {
		Coord candidate = points.get(pos);
		// avoid running off the ends of the list
		if(floor < 0)
			floor = 0;
		if(ceiling >= points.size())
			ceiling = points.size() - 1;
		// test points after pos
		for(int i = pos + 1; i <= ceiling; ++i) {
			Coord p = points.get(i);
			if(i == ceiling || p.getHighwayCount() > 1) {
				// point is going to be a node
				if(candidate.equals(p)) {
					// coordinates are equal, that's too close
					return false;
				}
				// no need to test further
				break;
			}
			else if(!candidate.equals(p)) {
				// no need to test further
				break;
			}
		}
		// test points before pos
		for(int i = pos - 1; i >= floor; --i) {
			Coord p = points.get(i);
			if(i == floor || p.getHighwayCount() > 1) {
				// point is going to be a node
				if(candidate.equals(p)) {
					// coordinates are equal, that's too close
					return false;
				}
				// no need to test further
				break;
			}
			else if(!candidate.equals(p)) {
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

		// collect the Way's nodes and also split the way if any
		// inter-node arc length becomes excessive
		double arcLength = 0;
		int numPointsInArc = 0;
		// track the dimensions of the way's bbox so that we can
		// detect if it would be split by the LineSizeSplitterFilter
		class WayBBox {
			int minLat = Integer.MAX_VALUE;
			int maxLat = Integer.MIN_VALUE;
			int minLon = Integer.MAX_VALUE;
			int maxLon = Integer.MIN_VALUE;

			void addPoint(Coord co) {
				int lat = co.getLatitude();
				if(lat < minLat)
					minLat = lat;
				if(lat > maxLat)
					maxLat = lat;
				int lon = co.getLongitude();
				if(lon < minLon)
					minLon = lon;
				if(lon > maxLon)
					maxLon = lon;
			}

			boolean tooBig() {
				return LineSizeSplitterFilter.testDims(maxLat - minLat,
													   maxLon - minLon) > 1.0;
			}
		}

		WayBBox wayBBox = new WayBBox();

		for(int i = 0; i < points.size(); ++i) {
			Coord p = points.get(i);

			wayBBox.addPoint(p);

			// flag that's set true when we back up to a previous node
			// while finding a good place to split the line
			boolean splitAtPreviousNode = false;

			// check if we should split the way at this point to limit
			// the arc length between nodes
			if((i + 1) < points.size()) {
				Coord nextP = points.get(i + 1);
				double d = p.distance(nextP);
				int numPointsRemaining = points.size() - i;
				// get arc size as a proportion of the max allowed - a
				// value greater than 1.0 indicate that the bbox is
				// too large in at least one dimension
				double arcProp = LineSizeSplitterFilter.testDims(nextP.getLatitude() -
																 p.getLatitude(),
																 nextP.getLongitude() -
																 p.getLongitude());
				if(arcProp > 1.0 || d > MAX_ARC_LENGTH) {
					nextP = p.makeBetweenPoint(nextP, 0.95 * Math.min(1 / arcProp, MAX_ARC_LENGTH / d));
					nextP.incHighwayCount();
					points.add(i + 1, nextP);
					double newD = p.distance(nextP);
					log.info("Way " + debugWayName + " contains a segment that is " + (int)d + "m long but I am adding a new point to reduce its length to " + (int)newD + "m");
					d = newD;
				}

				wayBBox.addPoint(nextP);

				if((arcLength + d) > MAX_ARC_LENGTH) {
					assert i > 0 : "long arc segment was not split";
					assert trailingWay == null : "trailingWay not null #1";
					trailingWay = splitWayAt(way, i);
					// this will have truncated the current Way's
					// points so the loop will now terminate
					log.info("Splitting way " + debugWayName + " at " + points.get(i).toOSMURL() + " to limit arc length to " + (long)arcLength + "m");
				}
				else if(wayBBox.tooBig()) {
					assert i > 0 : "arc segment with big bbox not split";
					assert trailingWay == null : "trailingWay not null #2";
					trailingWay = splitWayAt(way, i);
					// this will have truncated the current Way's
					// points so the loop will now terminate
					log.info("Splitting way " + debugWayName + " at " + points.get(i).toOSMURL() + " to limit the size of its bounding box");
				}
				else if(numPointsInArc >= MAX_POINTS_IN_ARC &&
						p.getHighwayCount() < 2) {
					// we have to introduce a node here or earlier
					// search backwards for a safe place
					int nodeI = i;
					int npia = numPointsInArc;
					while(nodeI > 0 &&
						  !safeToSplitWay(points, nodeI, i - numPointsInArc - 1, points.size() - 1)) {
						--nodeI;
						--npia;
					}
					// make point into a node
					p = points.get(nodeI);
					p.incHighwayCount();
					log.info("Making node in " + debugWayName + " at " + p.toOSMURL() + " to limit number of points in arc to " + npia + ", way has " + (points.size() - nodeI) + " more points");
					i = nodeI; // hack alert! modify loop index
					arcLength = p.distance(points.get(i + 1));
					numPointsInArc = 1;
				}
				else if(i > 0 &&
						(i + numPointsRemaining + 1) > MAX_POINTS_IN_WAY &&
						numPointsRemaining <= MAX_POINTS_IN_WAY &&
						p.getHighwayCount() > 1) {
					// if there happens to be no more nodes following
					// this one, the way will have to be split
					// somewhere otherwise it will be too long so may
					// as well split it here
					log.info("Splitting way " + debugWayName + " at " + points.get(i).toOSMURL() + " (using an existing node) to limit number of points in this way to " + (i + 1) + ", way has " + numPointsRemaining + " more points");
					assert trailingWay == null : "trailingWay not null #5";
					trailingWay = splitWayAt(way, i);
					// this will have truncated the current Way's
					// points so the loop will now terminate
				}
				else if(i >= (MAX_POINTS_IN_WAY-1)) {
					// we have to split the way here or earlier
					// search backwards for a safe place to split the way
					int splitI = i;
					while(splitI > 0 &&
						  points.get(splitI).getHighwayCount() < 2 &&
						  !safeToSplitWay(points, splitI, i - numPointsInArc - 1, points.size() - 1))
						--splitI;
					if(points.get(i).getHighwayCount() > 1) {
						// the current point is going to be a node
						// anyway so split right here
						log.info("Splitting way " + debugWayName + " at " + points.get(i).toOSMURL() + " (would be a node anyway) to limit number of points in this way to " + (i + 1) + ", way has " + (points.size() - i) + " more points");
						assert trailingWay == null : "trailingWay not null #6a";
						trailingWay = splitWayAt(way, i);
						// this will have truncated the current Way's
						// points so the loop will now terminate
					}
					else if(points.get(splitI).getHighwayCount() > 1) {
						// we have found an existing node, use that
						log.info("Splitting way " + debugWayName + " at " + points.get(splitI).toOSMURL() + " (using an existing node) to limit number of points in this way to " + (splitI + 1) + ", way has " + (points.size() - splitI) + " more points");
						assert trailingWay == null : "trailingWay not null #6b";
						trailingWay = splitWayAt(way, splitI);
						// this will have truncated the current Way's
						// points so the loop will now terminate
						p = points.get(splitI);
						i = splitI; // hack alert! modify loop index
						// note that we have split the line at a node
						// that has already been processed
						splitAtPreviousNode = true;
					}
					else if(splitI > 0) {
						log.info("Splitting way " + debugWayName + " at " + points.get(splitI).toOSMURL() + " (making a new node) to limit number of points in this way to " + (splitI + 1) + ", way has " + (points.size() - splitI) + " more points");
						assert trailingWay == null : "trailingWay not null #6c";
						trailingWay = splitWayAt(way, splitI);
						// this will have truncated the current Way's
						// points so the loop will now terminate
						p = points.get(splitI);
						i = splitI; // hack alert! modify loop index
					}
					else {
						log.error("Way " + debugWayName + " at " + points.get(i).toOSMURL() + " has too many points (" + points.size() + ") but I can't find a safe place to split the way - something's badly wrong here!");
						return;
					}
				}
				else {
					if(p.getHighwayCount() > 1) {
						// point is a node so zero arc length
						arcLength = 0;
						numPointsInArc = 0;
					}

					arcLength += d;
					++numPointsInArc;
				}
			}

			if(p.getHighwayCount() > 1) {
				// this point is a node connecting highways
				Integer nodeId = nodeIdMap.get(p);
				if(nodeId == null) {
					// assign a node id
					nodeIdMap.put(p, nextNodeId++);
				}

				if(splitAtPreviousNode) {
					// consistency check - this node index should
					// already be recorded
					assert nodeIndices.contains(i) : debugWayName + " has backed up to point " + i + " but can't find a node for that point " + p.toOSMURL();
				}
				else {
					// add this index to node Indexes (should not
					// already be there)
					assert !nodeIndices.contains(i) : debugWayName + " has multiple nodes for point " + i + " new node is " + p.toOSMURL();
					nodeIndices.add(i);
				}

				if((i + 1) < points.size() &&
				   nodeIndices.size() == MAX_NODES_IN_WAY) {
					// this isn't the last point in the way so split
					// it here to avoid exceeding the max nodes in way
					// limit
					assert trailingWay == null : "trailingWay not null #7";
					trailingWay = splitWayAt(way, i);
					// this will have truncated the current Way's
					// points so the loop will now terminate
					log.info("Splitting way " + debugWayName + " at " + points.get(i).toOSMURL() + " as it has at least " + MAX_NODES_IN_WAY + " nodes");
				}
			}
		}

		MapLine line = new MapLine();
		elementSetup(line, gt, way);
		line.setPoints(points);

		MapRoad road = new MapRoad(way.getId(), line);

		boolean doFlareCheck = true;
		if("roundabout".equals(way.getTag("junction"))) {
			road.setRoundabout(true);
			doFlareCheck = false;
		}

		if(way.isBoolTag("mkgmap:synthesised")) {
			road.setSynthesised(true);
			doFlareCheck = false;
		}

		if(way.isNotBoolTag("mkgmap:flare-check")) {
			doFlareCheck = false;
		}
		else if(way.isBoolTag("mkgmap:flare-check")) {
			doFlareCheck = true;
		}
		road.doFlareCheck(doFlareCheck);

		road.setLinkRoad(gt.getType() == 0x08 || gt.getType() == 0x09);

		// set road parameters 

		// road class (can be overridden by mkgmap:road-class tag)
		int roadClass = gt.getRoadClass();
		String val = way.getTag("mkgmap:road-class");
		if(val != null) {
			if(val.startsWith("-")) {
				roadClass -= Integer.decode(val.substring(1));
			}
			else if(val.startsWith("+")) {
				roadClass += Integer.decode(val.substring(1));
			}
			else {
				roadClass = Integer.decode(val);
			}
			val = way.getTag("mkgmap:road-class-max");
			int roadClassMax = 4;
			if(val != null)
				roadClassMax = Integer.decode(val);
			val = way.getTag("mkgmap:road-class-min");

			int roadClassMin = 0;
			if(val != null)
				roadClassMin = Integer.decode(val);
			if(roadClass > roadClassMax)
				roadClass = roadClassMax;
			else if(roadClass < roadClassMin)
				roadClass = roadClassMin;
			log.info("POI changing road class of " + way.getName() + " (" + way.getId() + ") to " + roadClass + " at " + points.get(0));
		}
		road.setRoadClass(roadClass);

		// road speed (can be overridden by maxspeed (OSM) tag or
		// mkgmap:road-speed tag)
		int roadSpeed = gt.getRoadSpeed();
		if(!ignoreMaxspeeds) {
			// maxspeed attribute overrides default for road type
			String maxSpeed = way.getTag("maxspeed");
			if(maxSpeed != null) {
				int rs = getSpeedIdx(maxSpeed);
				if(rs >= 0)
					roadSpeed = rs;
				log.debug(debugWayName + " maxspeed=" + maxSpeed + ", speedIndex=" + roadSpeed);
			}
		}
		val = way.getTag("mkgmap:road-speed");
		if(val != null) {
			if(val.startsWith("-")) {
				roadSpeed -= Integer.decode(val.substring(1));
			}
			else if(val.startsWith("+")) {
				roadSpeed += Integer.decode(val.substring(1));
			}
			else {
				roadSpeed = Integer.decode(val);
			}
			val = way.getTag("mkgmap:road-speed-max");
			int roadSpeedMax = 7;
			if(val != null)
				roadSpeedMax = Integer.decode(val);
			val = way.getTag("mkgmap:road-speed-min");

			int roadSpeedMin = 0;
			if(val != null)
				roadSpeedMin = Integer.decode(val);
			if(roadSpeed > roadSpeedMax)
				roadSpeed = roadSpeedMax;
			else if(roadSpeed < roadSpeedMin)
				roadSpeed = roadSpeedMin;
			log.info("POI changing road speed of " + way.getName() + " (" + way.getId() + ") to " + roadSpeed + " at " + points.get(0));
		}
		road.setSpeed(roadSpeed);
		
		if (way.isBoolTag("oneway")) {
			road.setDirection(true);
			road.setOneway();
			road.doDeadEndCheck(!way.isNotBoolTag("mkgmap:dead-end-check"));
		}

		String highwayType = way.getTag("highway");
		if(highwayType == null) {
			// it's a routable way but not a highway (e.g. a ferry)
			// use the value of the route tag as the highwayType for
			// the purpose of testing for access restrictions
			highwayType = way.getTag("route");
		}

		boolean[] noAccess = new boolean[RoadNetwork.NO_MAX];
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
				log.debug(type + " is not allowed in " + highwayType + " " + debugWayName);
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
				log.debug(type + " is allowed in " + highwayType + " " + debugWayName);
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

		if(way.isBoolTag("mkgmap:carpool")) {
			// to make a way into a "carpool lane" all access disable
			// bits must be set except for CARPOOL and EMERGENCY (BUS
			// can also be clear)
			road.setNoThroughRouting();
			for (int j = 1; j < accessMap.length; ++j)
				noAccess[accessMap[j].index] = true;
			noAccess[RoadNetwork.NO_CARPOOL] = false;
			noAccess[RoadNetwork.NO_EMERGENCY] = false;
			noAccess[RoadNetwork.NO_BUS] = false;
		}

		road.setAccess(noAccess);

		if(way.isBoolTag("toll"))
			road.setToll();

		// by default, ways are paved
		if(way.isBoolTag("mkgmap:unpaved"))
			road.paved(false);

		// by default, way's are not ferry routes
		if(way.isBoolTag("mkgmap:ferry"))
			road.ferry(true);

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
				assert nodeId != null : "Way " + debugWayName + " node " + i + " (point index " + n + ") at " + coord.toOSMURL() + " yields a null node id";
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
		highWayCounts[0] = wayPoints.get(0).getHighwayCount();
		int middleLat = 0;
		int middleLon = 0;
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
		
		if (ENDS_IN_MPH_PATTERN.matcher(speedTag).matches()) {
			// Check if it is a limit in mph
			speedTag = REMOVE_MPH_PATTERN.matcher(speedTag).replaceFirst("");
			factor = 1.61;
		} else
			speedTag = REMOVE_KPH_PATTERN.matcher(speedTag).replaceFirst("");  // get rid of kmh just in case

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
