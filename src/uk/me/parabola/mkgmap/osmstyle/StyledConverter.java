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
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
import uk.me.parabola.imgfmt.app.Exit;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.net.AccessTagsAndBits;
import uk.me.parabola.imgfmt.app.net.GeneralRouteRestriction;
import uk.me.parabola.imgfmt.app.trergn.ExtTypeAttributes;
import uk.me.parabola.imgfmt.app.trergn.MapObject;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.build.LocatorConfig;
import uk.me.parabola.mkgmap.filters.LineSizeSplitterFilter;
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
import uk.me.parabola.mkgmap.osmstyle.housenumber.HousenumberGenerator;
import uk.me.parabola.mkgmap.reader.osm.CoordPOI;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.FakeIdGenerator;
import uk.me.parabola.mkgmap.reader.osm.FeatureKind;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.RestrictionRelation;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.TagDict;
import uk.me.parabola.mkgmap.reader.osm.Tags;
import uk.me.parabola.mkgmap.reader.osm.TypeResult;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.EnhancedProperties;
import uk.me.parabola.util.MultiHashMap;

/**
 * Convert from OSM to the mkgmap intermediate format using a style.
 * A style is a collection of files that describe the mappings to be used
 * when converting.
 *
 * @author Steve Ratcliffe
 */
public class StyledConverter implements OsmConverter {
	private static final Logger log = Logger.getLogger(StyledConverter.class);
	private static final Logger roadLog = Logger.getLogger(StyledConverter.class.getName()+".roads");

	private final NameFinder nameFinder; 

	private final MapCollector collector;

	private Clipper clipper = Clipper.NULL_CLIPPER;
	private Area bbox = Area.PLANET;

	private final List<RestrictionRelation> restrictions = new ArrayList<>();
	private final MultiHashMap<Long, RestrictionRelation> wayRelMap = new MultiHashMap<>();
	
	private Map<Node, List<Way>> poiRestrictions = new LinkedHashMap<>();
	 
	private final List<Relation> throughRouteRelations = new ArrayList<>();

	// limit line length to avoid problems with portions of really
	// long lines being assigned to the wrong subdivision
	private static final int MAX_LINE_LENGTH = 40000;

	// limit arc lengths to what can be handled by RouteArc
	private static final int MAX_ARC_LENGTH = 20450000; // (1 << 22) * 16 / 3.2808 ~ 20455030*/

	private static final int MAX_NODES_IN_WAY = 64; // possibly could be increased

	// nodeIdMap maps a Coord into a CoordNode
	private IdentityHashMap<Coord, CoordNode> nodeIdMap = new IdentityHashMap<>();

	public final static String WAY_POI_NODE_IDS = "mkgmap:way-poi-node-ids";
	private final HashMap<Integer, Map<String,MapPoint>> pointMap;

	private List<ConvertedWay> roads = new ArrayList<>();
	private List<ConvertedWay> lines = new ArrayList<>();
	private HashMap<Long, ConvertedWay> modifiedRoads = new HashMap<>();
	private HashSet<Long> deletedRoads = new HashSet<>();

	private int nextNodeId = 1;
	private int nextRoadId = 1;
	
	private HousenumberGenerator housenumberGenerator;
	
	private final Rule wayRules;
	private final Rule nodeRules;
	private final Rule lineRules;
	private final Rule polygonRules;
	private Style style;

	private String driveOn;
	private Boolean driveOnLeft;
	private int numDriveOnLeftRoads;
	private int numDriveOnRightRoads;
	private int numDriveOnSideUnknown;
	private int numRoads;
	
	private String countryAbbr;

	private final boolean checkRoundabouts;
	private int reportDeadEnds; 
	private final boolean linkPOIsToWays;
	private final boolean mergeRoads;
	private final boolean routable;
	private final Tags styleOptionTags;
	private final static String STYLE_OPTION_PREF = "mkgmap:option:";
	private final PrefixSuffixFilter prefixSuffixFilter;
	
	private LineAdder lineAdder = new LineAdder() {
		public void add(MapLine element) {
			if (element instanceof MapRoad){
				prefixSuffixFilter.filter((MapRoad) element);
				collector.addRoad((MapRoad) element);
			}
			else
				collector.addLine(element);
		}
	};
	
	public StyledConverter(Style style, MapCollector collector, EnhancedProperties props) {
		this.collector = collector;

		nameFinder = new NameFinder(props);
		this.style = style;
		pointMap = new HashMap<>();
		wayRules = style.getWayRules();
		nodeRules = style.getNodeRules();
		lineRules = style.getLineRules();
		polygonRules = style.getPolygonRules();
		// perform legacy test, older versions of mkgmap used to set mkgmap:dest_hint=true
		// newer version will set it to a reasonable destination string
		if (lineRules.containsExpression("$mkgmap:dest_hint='true'")){
			log.error("At least one 'lines' rule in the style contains the expression mkgmap:dest_hint=true, it should be changed to mkgmap:dest_hint=*");
		}
		housenumberGenerator = new HousenumberGenerator(props);
		
		driveOn = props.getProperty("drive-on", null);
		if (driveOn == null)
			driveOn = "detect,right";
		switch (driveOn) {
		case "left":
			driveOnLeft = true; 
			break;
		case "right":
			driveOnLeft = false; 
			break;
		case "detect":
		case "detect,left":
		case "detect,right":
			break;
		default:
			throw new ExitException("invalid parameters for option drive-on:"+driveOn);
		}
		countryAbbr = props.getProperty("country-abbr", null);
		if (countryAbbr != null)
			countryAbbr = countryAbbr.toUpperCase();
			
		checkRoundabouts = props.getProperty("check-roundabouts",false);
		reportDeadEnds = props.getProperty("report-dead-ends", 1);  
		
		LineAdder overlayAdder = style.getOverlays(lineAdder);
		if (overlayAdder != null)
			lineAdder = overlayAdder;
		linkPOIsToWays = props.getProperty("link-pois-to-ways", false);
		
		// undocumented option - usually used for debugging only
		mergeRoads = props.getProperty("no-mergeroads", false) == false;
		routable = props.containsKey("route");
		String styleOption= props.getProperty("style-option",null);
		styleOptionTags = parseStyleOption(styleOption);
		prefixSuffixFilter = new PrefixSuffixFilter(props);
	}

	/**
	 * Handle style option parameter. Create tags which are added to each element
	 * before style processing starts. Cross-check usage of the options with the style.
	 * @param styleOption the user option 
	 * @return Tags instance created from the option.
	 */
	private Tags parseStyleOption(String styleOption) {
		Tags styleTags = new Tags();
		if (styleOption != null) {
			// expected: --style-option=car;farms=more;admin5=10
			String[] tags = styleOption.split(";");
			for (String t : tags) {
				String[] pair = t.split("=");
				String optionKey = pair[0];
				String tagKey = STYLE_OPTION_PREF + optionKey;
				if (!style.getUsedTags().contains(tagKey)) {
					System.err.println("Warning: Option style-options sets tag not used in style: '" 
							+ optionKey + "' (gives " + tagKey + ")");
				} else {
					String val = (pair.length == 1) ? "true" : pair[1];
					String old = styleTags.put(tagKey, val);
					if (old != null)
						log.error("duplicate tag key", optionKey, "in style option", styleOption);
				}
			}
		}
		// flag options used in style but not specified in --style-option
		if (style.getUsedTags() != null) {
			for (String s : style.getUsedTags()) {
				if (s != null && s.startsWith(STYLE_OPTION_PREF)) {
					if (styleTags.get(s) == null) {
						System.err.println("Warning: Option style-options doesn't specify '" 
								+ s.replaceFirst(STYLE_OPTION_PREF, "") + "' (for " + s + ")");
					}
				}
			}
		}
		return styleTags;
	}	

	/** One type result for ways to avoid recreating one for each way. */ 
	private final WayTypeResult wayTypeResult = new WayTypeResult();
	private class WayTypeResult implements TypeResult 
	{
		private Way way;
		/** flag if the rule was fired */
		private boolean matched;
		
		public void setWay(Way way) {
			this.way = way;
			this.matched = false;
		}
		
		public void add(Element el, GType type) {
			this.matched = true;
			if (type.isContinueSearch()) {
				// If not already copied, do so now
				if (el == way) 
					el = way.copy();
			}
			postConvertRules(el, type);
			if (type.isRoad() == false)
				housenumberGenerator.addWay((Way)el);
			addConvertedWay((Way) el, type);
		}

		/**
		 * Retrieves if a rule of the style matched and the way is converted.
		 * @return {@code true} way is converted; {@code false} way is not converted
		 */
		public boolean isMatched() {
			return matched;
		}
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
	private final static short styleFilterTagKey = TagDict.getInstance().xlate("mkgmap:stylefilter");
	private final static short makeCycleWayTagKey = TagDict.getInstance().xlate("mkgmap:make-cycle-way");
	private long lastRoadId = 0; 
	private int lineCacheId = 0;
	private BitSet routingWarningWasPrinted = new BitSet();
	public void convertWay(final Way way) {
		if (way.getPoints().size() < 2 || way.getTagCount() == 0){
			// no tags or no points => nothing to convert
			removeRestrictionsWithWay(Level.WARNING, way, "is ignored");
			return;
		}
		preConvertRules(way);

		String styleFilterTag = way.getTag(styleFilterTagKey);
		Rule rules;
		if ("polyline".equals(styleFilterTag))
			rules = lineRules;
		else if ("polygon".equals(styleFilterTag))
			rules = polygonRules;
		else {
			if (way.isClosedInOSM() && !way.isComplete() && !way.hasIdenticalEndPoints())
				way.getPoints().add(way.getPoints().get(0));
			
			if (way.hasIdenticalEndPoints() == false || way.getPoints().size() < 4)
				rules = lineRules;
			else
				rules = wayRules;
		}
		Way cycleWay = null;
		String cycleWayTag = way.getTag(makeCycleWayTagKey);
		if ("yes".equals(cycleWayTag)){
			way.deleteTag("mkgmap:make-cycle-way");
			cycleWay = makeCycleWay(way);
			way.addTag("bicycle", "no"); // make sure that bicycles are using the added bicycle way 
		}
		wayTypeResult.setWay(way);
		lineCacheId = rules.resolveType(lineCacheId, way, wayTypeResult);
		if (wayTypeResult.isMatched() == false) {
			// no match found but we have to keep it for house number processing
			housenumberGenerator.addWay(way);
		}
		if (cycleWay != null){
			wayTypeResult.setWay(cycleWay);
			lineCacheId = rules.resolveType(lineCacheId, cycleWay, wayTypeResult);
			if (wayTypeResult.isMatched() == false) {
				// no match found but we have to keep it for house number processing
				housenumberGenerator.addWay(cycleWay);
			}
		}
		if (lastRoadId != way.getId()){
			// this way was not added to the roads list
			removeRestrictionsWithWay(Level.WARNING, way, "is not routable");
		} else {
			// way was added as road, check if we also have non-routable lines for the way
			// which have to be skipped by WrongAngleFixer
			for (int i = lines.size()-1; i >= 0; --i){
				ConvertedWay cw = lines.get(i); 
				if (cw.getWay().getId() == way.getId()){
					cw.setOverlay(true);
					int lineType = cw.getGType().getType();
					if (GType.isSpecialRoutableLineType(lineType) && cw.getGType().getMinLevel() == 0){
						if (!routingWarningWasPrinted.get(lineType)){
							log.error("routable type", GType.formatType(cw.getGType().getType()),
							"is used with a non-routable way which was also added as a routable way. This leads to routing errors.",
							"Try --check-styles to check the style.");
							routingWarningWasPrinted.set(lineType);
						}
					}
				}
				else 
					break;
			}
		}
	}

	private int lineIndex = 0;
	private final static short onewayTagKey = TagDict.getInstance().xlate("oneway"); 
	private void addConvertedWay(Way way, GType foundType) {
		if (foundType.getFeatureKind() == FeatureKind.POLYGON){ 
			addShape(way, foundType);
			return;
		}
		
		boolean wasReversed = false;
		String oneWay = way.getTag(onewayTagKey);
		if (oneWay != null){
			if("-1".equals(oneWay) || "reverse".equals(oneWay)) {
				// it's a oneway street in the reverse direction
				// so reverse the order of the nodes and change
				// the oneway tag to "yes"
				way.reverse();
				wasReversed = true;
				way.addTag(onewayTagKey, "yes");
			}

			if (way.tagIsLikeYes(onewayTagKey)) {
				way.addTag(onewayTagKey, "yes");
				if (foundType.isRoad() && checkFixmeCoords(way) )
					way.addTag("mkgmap:dead-end-check", "false");
			} else 
				way.deleteTag(onewayTagKey);
		}
		ConvertedWay cw = new ConvertedWay(lineIndex++, way, foundType);
		cw.setReversed(wasReversed);
		if (cw.isRoad()){
			roads.add(cw);
			numRoads++;
			if (cw.isFerry() == false){
				String country = way.getTag(countryTagKey);
				if (country != null) {
					boolean drivingSideIsLeft =LocatorConfig.get().getDriveOnLeftFlag(country); 
					if (drivingSideIsLeft)
						numDriveOnLeftRoads++;
					else
						numDriveOnRightRoads++;
					if (driveOnLeft != null && drivingSideIsLeft != driveOnLeft)
						log.warn("wrong driving side",way.toBrowseURL());
					if (log.isDebugEnabled())
						log.debug("assumed driving side is",(drivingSideIsLeft ? "left" : "right"),way.toBrowseURL());
				} else
					numDriveOnSideUnknown++;
			}
			if (cw.isRoundabout()) {
				if (wasReversed)
					log.warn("Roundabout", way.getId(), "has reverse oneway tag (" + way.getPoints().get(0).toOSMURL() + ")");
			}
			lastRoadId = way.getId();
		}
		else 
			lines.add(cw);
	}

	/** One type result for nodes to avoid recreating one for each node. */ 
	private NodeTypeResult nodeTypeResult = new NodeTypeResult();
	private class NodeTypeResult implements TypeResult {
		private Node node;
		/** flag if the rule was fired */
		private boolean matched;
		
		public void setNode(Node node) {
			this.node = node;
			this.matched = false;
		}
		
		public void add(Element el, GType type) {
			this.matched = true;
			if (type.isContinueSearch()) {
				// If not already copied, do so now
				if (el == node) 
					el = node.copy();
			}
			
			postConvertRules(el, type);
			housenumberGenerator.addNode((Node)el);
			addPoint((Node) el, type);
		}

		/**
		 * Retrieves if a rule of the style matched and the node is converted.
		 * @return {@code true} node is converted; {@code false} node is not converted
		 */
		public boolean isMatched() {
			return matched;
		}
	}

	/**
	 * Takes a node (that has its own identity) and converts it from the OSM
	 * type to the Garmin map type.
	 *
	 * @param node The node to convert.
	 */
	public void convertNode(final Node node) {
		if (node.getTagCount() == 0) {
			// no tags => nothing to convert
			return;
		}

		preConvertRules(node);

		nodeTypeResult.setNode(node);
		nodeRules.resolveType(node, nodeTypeResult);
		if (nodeTypeResult.isMatched() == false) {
			// no match found but we have to keep it for house number processing
			housenumberGenerator.addNode(node);
		}
	}
	

	/**
	 * Rules to run before converting the element.
	 */
	private void preConvertRules(Element el) {
		// add tags given with --style-option
		if (styleOptionTags != null && styleOptionTags.size() > 0) {
			Iterator<Entry<Short, String>> iter = styleOptionTags.entryShortIterator();
			while (iter.hasNext()) {
				Entry<Short, String> tag = iter.next();
				el.addTag(tag.getKey(), tag.getValue());
			}
		}
		
		nameFinder.setNameWithNameTagList(el);
	}

	/**
	 * Construct a cycleway that has the same points as an existing way.  Used for separate
	 * cycle lanes.
	 * @param way The original way.
	 * @return The new way, which will have the same points and have suitable cycle tags.
	 */
	private static Way makeCycleWay(Way way) {
		Way cycleWay = new Way(way.getId(), way.getPoints());
		cycleWay.copyTags(way);

		cycleWay.addTag("access", "no");
		cycleWay.addTag("bicycle", "yes");
		cycleWay.addTag("mkgmap:synthesised", "yes");
		cycleWay.addTag(onewayTagKey, "no");
		// remove explicit access tags 
		cycleWay.deleteTag("foot");
		cycleWay.deleteTag("motorcar");
		cycleWay.deleteTag("goods");
		cycleWay.deleteTag("hgv");
		cycleWay.deleteTag("bus");
		cycleWay.deleteTag("taxi");
		cycleWay.deleteTag("emergency");
		cycleWay.deleteTag("vehicle");
		cycleWay.deleteTag("motor_vehicle");
		cycleWay.deleteTag("carpool");
		cycleWay.deleteTag("motorcycle");
		cycleWay.deleteTag("psv");
		cycleWay.deleteTag("truck");
		return cycleWay;
	}
	
	/**
	 * Built in rules to run after converting the element.
	 */
	private static void postConvertRules(Element el, GType type) {
		// Set the default_name if no name is set
		if (type.getDefaultName() != null && el.getName() == null)
			el.addTag("mkgmap:label:1", type.getDefaultName());
	}

	/**
	 * Set the bounding box for this map.  This should be set before any other
	 * elements are converted if you want to use it. All elements that are added
	 * are clipped to this box, new points are added as needed at the boundary.
	 *
	 * If a node or a way falls completely outside the boundary then it would be
	 * omitted.  This would not normally happen in the way this option is typically
	 * used however.
	 *
	 * @param bbox The bounding area, must not be null.
	 */
	public void setBoundingBox(Area bbox) {
		this.clipper = new AreaClipper(bbox);
		this.bbox = bbox;

		// we calculate our own bounding box, now let the collector know about it.
		collector.addToBounds(new Coord(bbox.getMinLat(), bbox.getMinLong()));
		collector.addToBounds(new Coord(bbox.getMaxLat(), bbox.getMaxLong()));
	}

	/**
	 * Remove all restriction relations that are invalid if the way will not appear
	 * in the NOD file.
	 * @param logLevel 
	 * @param way the way that was removed
	 * @param reason explanation for the removal
	 */
	private void removeRestrictionsWithWay(Level logLevel, Way way, String reason){
		List<RestrictionRelation> rrList = wayRelMap.get(way.getId());
		for (RestrictionRelation rr : rrList){
			if (rr.isValidWithoutWay(way.getId()) == false){
				if (log.isLoggable(logLevel)){
					log.log(logLevel, "restriction",rr.toBrowseURL()," is ignored because referenced way",way.toBrowseURL(),reason);
				}
				rr.setInvalid();
				restrictions.remove(rr);
			}
		}
	}

	/**
	 * Merges roads with identical attributes (GType, OSM tags) to reduce the size of the 
	 * road network.
	 */
	private void mergeRoads() {
		if (mergeRoads == false) { 
			log.info("Merging roads is disabled");
			return;
		}
		
		RoadMerger merger = new RoadMerger();
		roads = merger.merge(roads, restrictions, throughRouteRelations);
	}
	
	public void end() {
		pointMap.clear();
		style.reportStats();
		driveOnLeft = calcDrivingSide();
		
		setHighwayCounts();
		findUnconnectedRoads();
		rotateClosedWaysToFirstNode();
		filterCoordPOI();
		WrongAngleFixer wrongAngleFixer = new WrongAngleFixer(bbox);
		wrongAngleFixer.optimizeWays(roads, lines, modifiedRoads, deletedRoads, restrictions);

		// make sure that copies of modified roads have equal points 
		for (ConvertedWay line : lines){
			if (!line.isValid())
				continue; 
			Way way = line.getWay();
			if (deletedRoads.contains(way.getId())){
				line.getPoints().clear();
				continue;
			}
			if (!line.isOverlay())
				continue;
			ConvertedWay modWay = modifiedRoads.get(way.getId());
			if (modWay != null){
				List<Coord> points = line.getPoints();
				points.clear();
				points.addAll(modWay.getPoints());
				if (modWay.isReversed() != line.isReversed())
					Collections.reverse(points);
			} 
		}
		for (Long wayId: deletedRoads){
			if (wayRelMap.containsKey(wayId)){
				// may happen e.g. when very short way is leading to nowhere 
				log.warn("Way that is used in valid restriction relation was removed, id:",wayId);
			}
		}
		deletedRoads = null;
		modifiedRoads = null;

		mergeRoads();
		
		resetHighwayCounts();
		setHighwayCounts();
		
		for (ConvertedWay cw : lines){
			if (cw.isValid())
				addLine(cw.getWay(), cw.getGType());
		}
		lines = null;
		if (roadLog.isInfoEnabled()) {
			roadLog.info("Flags: oneway,no-emergency, no-delivery, no-throughroute, no-truck, no-bike, no-foot, carpool, no-taxi, no-bus, no-car");
			roadLog.info(String.format("%19s %4s %11s %6s %6s %6s %s", "Road-OSM-Id","Type","Flags", "Class", "Speed", "Points", "Labels"));
		}
		// add the roads after the other lines
		for (ConvertedWay cw : roads){
			if (cw.isValid())
				addRoad(cw);
		}
		housenumberGenerator.generate(lineAdder, nextNodeId);
		housenumberGenerator = null;
		
		if (routable)
			createRouteRestrictionsFromPOI();
		poiRestrictions = null;
		if (routable){
			for (RestrictionRelation rr : restrictions) {
				rr.addRestriction(collector, nodeIdMap);
			}
		}
		roads = null;
		if (routable){
			for(Relation relation : throughRouteRelations) {
				Node node = null;
				Way w1 = null;
				Way w2 = null;
				for(Map.Entry<String,Element> member : relation.getElements()) {
					if(member.getValue() instanceof Node) {
						if(node == null)
							node = (Node)member.getValue();
						else
							log.warn("Through route relation", relation.toBrowseURL(), "has more than 1 node");
					}
					else if(member.getValue() instanceof Way) {
						Way w = (Way)member.getValue();
						if(w1 == null)
							w1 = w;
						else if(w2 == null)
							w2 = w;
						else
							log.warn("Through route relation", relation.toBrowseURL(), "has more than 2 ways");
					}
				}

				CoordNode coordNode = null;
				if(node == null)
					log.warn("Through route relation", relation.toBrowseURL(), "is missing the junction node");
				else {
					Coord junctionPoint = node.getLocation();
					if(bbox != null && !bbox.contains(junctionPoint)) {
						// junction is outside of the tile - ignore it
						continue;
					}
					coordNode = nodeIdMap.get(junctionPoint);
					if(coordNode == null)
						log.warn("Through route relation", relation.toBrowseURL(), "junction node at", junctionPoint.toOSMURL(), "is not a routing node");
				}

				if(w1 == null || w2 == null)
					log.warn("Through route relation", relation.toBrowseURL(), "should reference 2 ways that meet at the junction node");

				if(coordNode != null && w1 != null && w2 != null)
					collector.addThroughRoute(coordNode.getId(), w1.getId(), w2.getId());
			}
		}
		// return memory to GC
		nodeIdMap = null;
		throughRouteRelations.clear();
		restrictions.clear();
		
	}

	/**
	 * Check the counters and verify the driveOn value to calculate
	 * the drive on left flag. 
	 */
	private Boolean calcDrivingSide() {
		Boolean dol = null;
		log.info("Found", numRoads, "roads",
				numDriveOnLeftRoads, "in drive-on-left country,",
				numDriveOnRightRoads, "in drive-on-right country, and",
				numDriveOnSideUnknown, " with unknwon country");
		if (numDriveOnLeftRoads> 0 &&  numDriveOnRightRoads > 0)
			log.error("Attention: Tile contains both drive-on-left (" + numDriveOnLeftRoads + 
					") and drive-on-right roads (" + numDriveOnRightRoads + ")");
		if (driveOn.startsWith("detect")) {
			if (numDriveOnSideUnknown > numRoads * 0.05){
				// warn if more than 5% of the roads are in unknown area
				log.warn("Found", numDriveOnSideUnknown, "roads with unknown country and driving side");
			}
			if (numDriveOnLeftRoads > numDriveOnRightRoads + numDriveOnSideUnknown) {
				dol = true;
			} else if (numDriveOnRightRoads > numDriveOnLeftRoads + numDriveOnSideUnknown) {
				dol = false;
			} else {
				if (driveOn.endsWith("left"))
					dol = true;
				else 
					dol = false;
			}
			log.info("detected value for driving on left flag is:",dol);			
		} else {
			dol = ("left".equals(driveOn));
			// warn if user given flag is obviously wrong
			if ("left".equals(driveOn) && numDriveOnLeftRoads == 0 && numDriveOnRightRoads > 0)
				log.warn("The drive-on-left flag is set but tile contains only drive-on-right roads");
			if ("right".equals(driveOn) && numDriveOnRightRoads == 0 && numDriveOnLeftRoads > 0)
				log.warn("The drive-on-left flag is NOT set used but tile contains only drive-on-left roads");
		}		
		assert dol != null;
		return dol;
	}

	/**
	 * Try to make sure that closed ways start with a point that is 
	 * also part of another road. This reduces the number of nodes
	 * a little bit.
	 * 
	 */
	private void rotateClosedWaysToFirstNode() {
		for (ConvertedWay cw: roads){
			if (!cw.isValid())
				continue;
			Way way = cw.getWay();
			List<Coord> points = way.getPoints();
			if (points.size() < 3)
				continue;
			if (points.get(0) != points.get(points.size()-1))
				continue;
			// this is a closed way 
			Coord p0 = points.get(0);
			if (p0.getHighwayCount() > 2)
				continue;
			
			for (int i = 1; i < points.size() - 1;i++){
				Coord p = points.get(i);
				if (p.getHighwayCount() > 1){
					p.incHighwayCount(); // this will be the new first + last point
					// first point connects only last point, remove last
					points.remove(points.size()-1);
					p0.decHighwayCount();
					Collections.rotate(points, -i);
					points.add(points.get(0)); // close again
					modifiedRoads.put(way.getId(), cw); 
					break;
				}
			}
		}
	}

	/**
	 * Check if roundabout has correct direction. Set driveOnRight or
	 * driveOnLeft is not yet set.
	 * 
	 */
	private void checkRoundabout(ConvertedWay cw) {
		if (cw.isRoundabout() == false)
			return;
		Way way = cw.getWay();
		List<Coord> points = way.getPoints();
		// if roundabout checking is enabled and roundabout has at
		// least 3 points and it has not been marked as "don't
		// check", check its direction
		if (checkRoundabouts && points.size() > 2
				&& !way.tagIsLikeYes("mkgmap:no-dir-check")
				&& !way.tagIsLikeNo("mkgmap:dir-check")) {
			Coord centre = way.getCofG();
			int dir = 0;
			// check every third segment
			for (int i = 0; (i + 1) < points.size(); i += 3) {
				Coord pi = points.get(i);
				Coord pi1 = points.get(i + 1);
				// TODO: check if high prec coords allow to use smaller
				// distance
				// don't check segments that are very short
				if (pi.distance(centre) > 2.5 && pi.distance(pi1) > 2.5) {
					// determine bearing from segment that starts with
					// point i to centre of roundabout
					double a = pi.bearingTo(pi1);
					double b = pi.bearingTo(centre) - a;
					while (b > 180)
						b -= 360;
					while (b < -180)
						b += 360;
					// if bearing to centre is between 15 and 165
					// degrees consider it trustworthy
					if (b >= 15 && b < 165)
						++dir;
					else if (b <= -15 && b > -165)
						--dir;
				}
			}
			if (dir == 0)
				log.info("Roundabout segment " + way.getId()
						+ " direction unknown (see "
						+ points.get(0).toOSMURL() + ")");
			else {
				boolean clockwise = dir > 0;
				if (points.get(0) == points.get(points.size() - 1)) {
					// roundabout is a loop
					if (driveOnLeft == true && !clockwise || driveOnLeft == false && clockwise) {
						log.warn("Roundabout "
								+ way.getId()
								+ " direction is wrong - reversing it (see "
								+ centre.toOSMURL() + ")");
						way.reverse();
					}
				} else if (driveOnLeft == true && !clockwise || driveOnLeft == false && clockwise) {
					// roundabout is a line
					log.warn("Roundabout segment " + way.getId()
							+ " direction looks wrong (see "
							+ points.get(0).toOSMURL() + ")");
				}
			}
		}
	}
	
	
	/**
	 * If POI changes access restrictions (e.g. bollards), create corresponding
	 * route restrictions so that only allowed vehicles/pedestrians are routed
	 * through this point.
	 */
	private void createRouteRestrictionsFromPOI() {
		Iterator<Map.Entry<Node, List<Way>>> iter = poiRestrictions.entrySet().iterator();
		while (iter.hasNext()){
			Map.Entry<Node, List<Way>> entry = iter.next();
			Node node = entry.getKey();
			Coord p = node.getLocation();
			// list of ways that are connected to the poi
			List<Way> wayList = entry.getValue();

			byte exceptMask = AccessTagsAndBits.evalAccessTags(node);
			Map<Integer,CoordNode> otherNodeIds = new LinkedHashMap<>();
			CoordNode viaNode = null;
			boolean viaIsUnique = true;
			for (Way way : wayList) {
				CoordNode lastNode = null;
				for (Coord co: way.getPoints()){
					// not 100% fail safe: points may have been replaced before
					if (co instanceof CoordNode == false)
						continue;
					CoordNode cn = (CoordNode) co;
					if (p.highPrecEquals(cn)){
						if (viaNode == null)
							viaNode = cn;
						else if (viaNode != cn){
							log.error("Found multiple points with equal coords as CoordPOI at " + p.toOSMURL());
							// if we ever get here we can add code to identify the exact node 
							viaIsUnique = false;
						}
						if (lastNode != null)
							otherNodeIds.put(lastNode.getId(),lastNode);
					} else {
						if (p.highPrecEquals(lastNode))
							otherNodeIds.put(cn.getId(),cn);
					}
					lastNode = cn;
				}
			}
			if (viaNode == null){
				log.error("Did not find CoordPOI node at " + p.toOSMURL() + " in ways " + wayList);
				continue;
			}
			if (viaIsUnique == false){
				log.error("Found multiple points with equal coords as CoordPOI at " + p.toOSMURL());
				continue;
			}
			if (otherNodeIds.size() < 2){
				log.info("Access restriction in POI node " + node.toBrowseURL() + " was ignored, has no effect on any connected way");
				continue;
			}
			GeneralRouteRestriction rr = new GeneralRouteRestriction("no_through", exceptMask, "CoordPOI at " + p.toOSMURL());
			rr.setViaNodes(Arrays.asList(viaNode));
			int added = collector.addRestriction(rr);
			if (added == 0){
				log.info("Access restriction in POI node " + node.toBrowseURL() + " was ignored, has no effect on any connected way");
			} else { 
				log.info("Access restriction in POI node", node.toBrowseURL(), "was translated to",added,"route restriction(s)");
			}
			if (wayList.size() > 1 && added > 2){
				log.warn("Access restriction in POI node", node.toBrowseURL(), "affects routing on multiple ways");
			}
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
		if (relation.getTagCount() == 0) {
			// no tags => nothing to convert
			return;
		}

		housenumberGenerator.addRelation(relation);

		// relation rules are not applied here because they are applied
		// earlier by the RelationStyleHook
		if(relation instanceof RestrictionRelation) {
			RestrictionRelation rr = (RestrictionRelation)relation;
			if(rr.isValid()) {
				restrictions.add(rr);
				for (long id : rr.getWayIds())
					wayRelMap.add(id, rr);
			}
		}
		else if("through_route".equals(relation.getTag("type"))) {
			throughRouteRelations.add(relation);
		}
	}
	
	private void addLine(Way way, GType gt) {
		addLine(way, gt, -1);
	}
	
	private void addLine(Way way, GType gt, int replType) {
		List<Coord> wayPoints = way.getPoints();
		List<Coord> points = new ArrayList<>(wayPoints.size());
		double lineLength = 0;
		Coord lastP = null;
		for (Coord p : wayPoints) {
			if (p.highPrecEquals(lastP))
				continue;
			
			points.add(p);
			if(lastP != null) {
				lineLength += p.distance(lastP);
				if(lineLength >= MAX_LINE_LENGTH) {
					if (log.isInfoEnabled())
						log.info("Splitting line", way.toBrowseURL(), "at", p.toOSMURL(), "to limit its length to", (long)lineLength + "m");
					addLine(way, gt, replType, points);
					points = new ArrayList<>(wayPoints.size() - points.size() + 1);
					points.add(p);
					lineLength = 0;
				}
			}
			lastP = p;
		}

		if(points.size() > 1)
			addLine(way, gt, replType, points);
	}

	private void addLine(Way way, GType gt, int replType, List<Coord> points) {
		MapLine line = new MapLine();
		elementSetup(line, gt, way);
		if (replType >= 0)
			line.setType(replType);
		line.setPoints(points);

		
		if (way.tagIsLikeYes(onewayTagKey))
			line.setDirection(true);

		clipper.clipLine(line, lineAdder);
	}

	private void addShape(Way way, GType gt) {
		// This is deceptively simple. At the time of writing, splitter only retains points that are within
		// the tile and some distance around it.  Therefore a way that is closed in reality may not be closed
		// as we see it in its incomplete state.
		//
		if (!way.hasIdenticalEndPoints() && way.hasEqualEndPoints())
			log.error("shape is not closed with identical points " + way.getId());
		if (!way.hasIdenticalEndPoints())
			return;
		// TODO: split self intersecting polygons?
		final MapShape shape = new MapShape(way.getId());
		elementSetup(shape, gt, way);
		shape.setPoints(way.getPoints());

		long areaVal = 0;
		String tagStringVal = way.getTag(drawLevelTagKey);
		if (tagStringVal != null) {
			try {
				areaVal = Integer.parseInt(tagStringVal);
				if (areaVal < 1 || areaVal > 100) {
					log.error("mkgmap:drawLevel must be in range 1..100, not", areaVal);
					areaVal = 0;
				} else if (areaVal <= 50)
					areaVal = Long.MAX_VALUE - areaVal; // 1 => MAX_VALUE-1, 50 => MAX_VALUE-50
				else
					areaVal = 101 - areaVal; // 51 => 50, 100 => 1
			} catch (NumberFormatException e) {
				log.error("mkgmap:drawLevel invalid integer:", tagStringVal);
			}
		}
		if (areaVal == 0)
			areaVal = way.getFullArea();
		shape.setFullArea(areaVal);

		clipper.clipShape(shape, collector);
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
			String id = node.getTag("mkgmap:osmid");
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
				if ("motorway_junction".equals(node.getTag("highway")))
					log.warn("Motorway exit", node.getName(), "(" + node.toBrowseURL() + ") has no (motorway) ref! (either make the exit share a node with the motorway or specify the motorway ref with a", Exit.TAG_ROAD_REF, "tag)");
			}
		}
		else {
			mp = new MapPoint();
		}
		elementSetup(mp, gt, node);
		mp.setLocation(node.getLocation());

		boolean dupPOI = checkDuplicatePOI(mp);
		if (dupPOI){
			if (log.isInfoEnabled()){
				if (FakeIdGenerator.isFakeId(node.getId()))
					log.info("ignoring duplicate POI with type",GType.formatType(type),mp.getName(),"for generated element with id",node.getId(),"at",mp.getLocation().toDegreeString());
				else 
					log.info("ignoring duplicate POI with type",GType.formatType(type),mp.getName(),"for element",node.toBrowseURL());
			}
			return;
		}
		
		collector.addPoint(mp);
	}

	/**
	 * Check if we already have added a point with the same type + name and equal location.
	 * @param mp
	 * @return
	 */
	private boolean checkDuplicatePOI(MapPoint mp) {
		Map<String, MapPoint> typeMap = pointMap.get(mp.getType());
		if (typeMap == null){
			typeMap = new HashMap<>();
			pointMap.put(mp.getType(), typeMap);
		}
		MapPoint old = typeMap.get(mp.getName());
		if (old == null){
			typeMap.put(mp.getName(), mp);
		} else {
			if (old.getLocation().equals(mp.getLocation()))
				return true;
		}
		return false;
	}

	private static final short[] labelTagKeys = {
		TagDict.getInstance().xlate("mkgmap:label:1"),
		TagDict.getInstance().xlate("mkgmap:label:2"),
		TagDict.getInstance().xlate("mkgmap:label:3"),
		TagDict.getInstance().xlate("mkgmap:label:4"),
	};
	private static final short highResOnlyTagKey = TagDict.getInstance().xlate("mkgmap:highest-resolution-only");
	private static final short skipSizeFilterTagKey = TagDict.getInstance().xlate("mkgmap:skipSizeFilter");
	private static final short drawLevelTagKey = TagDict.getInstance().xlate("mkgmap:drawLevel");

	private static final short countryTagKey = TagDict.getInstance().xlate("mkgmap:country");
	private static final short regionTagKey = TagDict.getInstance().xlate("mkgmap:region");
	private static final short cityTagKey = TagDict.getInstance().xlate("mkgmap:city");
	private static final short postal_codeTagKey = TagDict.getInstance().xlate("mkgmap:postal_code");
	private static final short streetTagKey = TagDict.getInstance().xlate("mkgmap:street");
	private static final short housenumberTagKey = TagDict.getInstance().xlate("mkgmap:housenumber");
	private static final short phoneTagKey = TagDict.getInstance().xlate("mkgmap:phone");
	private static final short is_inTagKey = TagDict.getInstance().xlate("mkgmap:is_in");
	
	private static void elementSetup(MapElement ms, GType gt, Element element) {
		String[] labels = new String[4];
		int noLabels = 0;
		for (int labelNo = 0; labelNo < 4; labelNo++) {
			String label1 = element.getTag(labelTagKeys[labelNo]);
			String label = Label.squashSpaces(label1);
			if (label != null) {
				labels[noLabels] = label;
				noLabels++;
			} 
		}

		if (labels[0] != null) {
			ms.setLabels(labels);
		}
		ms.setType(gt.getType());
		ms.setMinResolution(gt.getMinResolution());
		ms.setMaxResolution(gt.getMaxResolution());

		if (element.tagIsLikeYes(highResOnlyTagKey)){
			ms.setMinResolution(ms.getMaxResolution());
		}
		
		if (ms instanceof MapLine && element.tagIsLikeYes(skipSizeFilterTagKey)){
			((MapLine)ms).setSkipSizeFilter(true);
		}
		
		// Now try to get some address info for POIs
		
		String country      = element.getTag(countryTagKey);
		String region       = element.getTag(regionTagKey);
		String city         = element.getTag(cityTagKey);
		String zip          = element.getTag(postal_codeTagKey);
		String street 	    = element.getTag(streetTagKey);
		String houseNumber  = element.getTag(housenumberTagKey);
		String phone        = element.getTag(phoneTagKey);
		String isIn         = element.getTag(is_inTagKey);

		if(country != null)
			ms.setCountry(country);

		if(region != null)
			ms.setRegion(region);
		
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


		
		if(MapObject.hasExtendedType(gt.getType())) {
			// pass attributes with mkgmap:xt- prefix (strip prefix)
			Map<String,String> xta = element.getTagsWithPrefix("mkgmap:xt-", true);
			// also pass all attributes with seamark: prefix (no strip prefix)
			xta.putAll(element.getTagsWithPrefix("seamark:", false));
			ms.setExtTypeAttributes(new ExtTypeAttributes(xta, "OSM id " + element.getId()));
		}
	}

	/**
	 * Add a way to the road network. May call itself recursively and
	 * might truncate the way if splitting is required. 
	 * @param way the way
	 * @param gt the type assigned by the style
	 */
	private void addRoad(ConvertedWay cw) {
		Way way = cw.getWay();
		if (way.getPoints().size() < 2){
			log.warn("road has < 2 points",way.getId(),"(discarding)");
			return;
		}

		
		checkRoundabout(cw);

		// process any Coords that have a POI associated with them
		final double stubSegmentLength = 25; // metres
		String wayPOI = way.getTag(WAY_POI_NODE_IDS);
		if (wayPOI != null) {
			List<Coord> points = way.getPoints();
			
			// look for POIs that modify the way's road class or speed
			// or contain access restrictions
			// This could be e.g. highway=traffic_signals that reduces the
			// road speed to cause a short increase of travelling time
			// or a barrier
			for(int i = 0; i < points.size(); ++i) {
				Coord p = points.get(i);
				if (p instanceof CoordPOI && ((CoordPOI) p).isUsed()) {
					CoordPOI cp = (CoordPOI) p;
					Node node = cp.getNode();
					if (wayPOI.contains("["+node.getId()+"]")){
						log.debug("POI",node.getId(),"changes way",way.getId());

						// make sure that we create nodes for all POI that 
						// are converted to RouteRestrictions
						if(p.getHighwayCount() < 2 && cp.getConvertToViaInRouteRestriction() && (i != 0 && i != points.size()-1))
							p.incHighwayCount();
						
						String roadClass = node.getTag("mkgmap:road-class");
						String roadSpeed = node.getTag("mkgmap:road-speed");
						if(roadClass != null || roadSpeed != null) {
							// find good split point after POI
							Coord splitPoint;
							double segmentLength = 0;
							int splitPos = i+1;
							while( splitPos+1 < points.size()){
								splitPoint = points.get(splitPos);
								segmentLength += splitPoint.distance(points.get(splitPos - 1));
								if (splitPoint.getHighwayCount() > 1
										|| segmentLength > stubSegmentLength - 5)
									break;
								splitPos++;
							}
							if (segmentLength > stubSegmentLength + 10){
								// insert a new point after the POI to
								// make a short stub segment
								splitPoint = points.get(splitPos);
								Coord prev = points.get(splitPos-1);
								double dist = splitPoint.distance(prev);
								double neededLength = stubSegmentLength - (segmentLength - dist);
								
								splitPoint = prev.makeBetweenPoint(splitPoint, neededLength / dist);
								double newDist = splitPoint.distance(prev);
								segmentLength += newDist - dist;
								splitPoint.incHighwayCount();
								points.add(splitPos, splitPoint);
							}
							if((splitPos + 1) < points.size() && way.isViaWay() == false &&
									safeToSplitWay(points, splitPos, i, points.size() - 1)) {
								Way tail = splitWayAt(way, splitPos);
								// recursively process tail of way
								addRoad(new ConvertedWay(cw, tail));
							}
							boolean classChanged = cw.recalcRoadClass(node);
							if (classChanged && log.isInfoEnabled()){
								log.info("POI changing road class of", way.toBrowseURL(), "to", cw.getRoadClass(), "at", points.get(0).toOSMURL()); 								
							}
							boolean speedChanged = cw.recalcRoadSpeed(node);
							if (speedChanged && log.isInfoEnabled()){
								log.info("POI changing road speed of", way.toBrowseURL(), "to", cw.getRoadSpeed(), "at" , points.get(0).toOSMURL());
							}
						}
					}
				}

				// if this isn't the last point in the way
				// and the next point modifies the way's speed/class,
				// split the way at this point to limit the size of
				// the affected region
				if (i + 1 < points.size()
						&& points.get(i + 1) instanceof CoordPOI) {
					CoordPOI cp = (CoordPOI) points.get(i + 1);
					Node node = cp.getNode();
					if (cp.isUsed() && wayPOI.contains("["+node.getId()+"]")){
						if (node.getTag("mkgmap:road-class") != null
								|| node.getTag("mkgmap:road-speed") != null) {
							// find good split point before POI
							double segmentLength = 0;
							int splitPos = i;
							Coord splitPoint;
							while( splitPos >= 0){
								splitPoint = points.get(splitPos);
								segmentLength += splitPoint.distance(points.get(splitPos + 1));
								if (splitPoint.getHighwayCount() >= 2
										|| segmentLength > stubSegmentLength - 5)
									break;
								--splitPos;			
							}
							if (segmentLength > stubSegmentLength + 10){
								// insert a new point before the POI to
								// make a short stub segment
								splitPoint = points.get(splitPos);
								Coord prev = points.get(splitPos+1);
								double dist = splitPoint.distance(prev);
								double neededLength = stubSegmentLength - (segmentLength - dist);
								splitPoint = prev.makeBetweenPoint(splitPoint, neededLength / dist);
								segmentLength += splitPoint.distance(prev) - dist;
								splitPoint.incHighwayCount();
								splitPos++;
								points.add(splitPos, splitPoint);
							}
							if(splitPos > 0 &&
									safeToSplitWay(points, splitPos, 0, points.size()-1)) {
								Way tail = splitWayAt(way, splitPos);
								// recursively process tail of way
								addRoad(new ConvertedWay(cw, tail));
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
				if (lineSegs.isEmpty()){
					removeRestrictionsWithWay(Level.WARNING, way, "ends on tile boundary, restriction is ignored");
				}
				clippedWays = new ArrayList<>();

				for (List<Coord> lco : lineSegs) {
					Way nWay = new Way(way.getId());
					nWay.copyTags(way);
					for(Coord co : lco) {
						nWay.addPoint(co);
						if(co.getOnBoundary() || co.getOnCountryBorder()) {
							// this point lies on a boundary
							// make sure it becomes a node
							co.incHighwayCount();
						}
					}
					clippedWays.add(nWay);
				}
			}
		}

		if(clippedWays != null) {
			for(Way clippedWay : clippedWays) {
				addRoadAfterSplittingLoops(new ConvertedWay(cw, clippedWay));
			}
		}
		else {
			// no bounding box or way was not clipped
			addRoadAfterSplittingLoops(cw);
		}
	}

	private void addRoadAfterSplittingLoops(ConvertedWay cw) {
		Way way = cw.getWay();
		// make sure the way has nodes at each end
		way.getPoints().get(0).incHighwayCount();
		way.getPoints().get(way.getPoints().size() - 1).incHighwayCount();

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
				if (p1.getHighwayCount() < 2)
					continue;
				int niceSplitPos = -1;
				for(int p2I = p1I + 1; !wayWasSplit && p2I < numPointsInWay; p2I++) {
					Coord p2 = wayPoints.get(p2I);
					if (p1 != p2){
						if (p2.getHighwayCount() > 1)
							niceSplitPos = p2I;
					} else {
						// way is a loop or intersects itself 
						// attempt to split it into two ways

						// start at point before intersection point
						// check that splitting there will not produce
						// a zero length arc - if it does try the
						// previous point(s)
						int splitI;
						if (niceSplitPos >= 0 && safeToSplitWay(wayPoints, niceSplitPos, p1I, p2I))
							// prefer to split at a point that is going to be a node anyway
							splitI = niceSplitPos;
						else {
							splitI = p2I - 1;
						while(splitI > p1I &&
							  !safeToSplitWay(wayPoints, splitI, p1I, p2I)) {
							if (log.isInfoEnabled())
								log.info("Looped way", way.getDebugName(), "can't safely split at point[" + splitI + "], trying the preceeding point");
							--splitI;
						}
						}
						if(splitI == p1I) {
							log.warn("Splitting looped way", way.getDebugName(), "would make a zero length arc, so it will have to be pruned at", wayPoints.get(p2I).toOSMURL());
							do {
								log.warn("  Pruning point[" + p2I + "]");
								wayPoints.remove(p2I);
								// next point to inspect has same index
								--p2I;
								// but number of points has reduced
								--numPointsInWay;

								if (p2I + 1 == numPointsInWay) 
									wayPoints.get(p2I).incHighwayCount();
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
							if (log.isInfoEnabled())
								log.info("Splitting looped way", way.getDebugName(), "at", wayPoints.get(splitI).toOSMURL(), "- it has", (numPointsInWay - splitI - 1 ), "following segment(s).");
							Way loopTail = splitWayAt(way, splitI);
							
							ConvertedWay next = new ConvertedWay(cw, loopTail);
							
							// recursively check (shortened) head for
							// more loops
							addRoadAfterSplittingLoops(cw);
							// now process the tail of the way
							cw = next;
							way = loopTail;
							wayWasSplit = true;
						}
					}
				}
			}

			if(!wayWasSplit) {
				// no split required so make road from way
				addRoadWithoutLoops(cw);
			}
		}
	}

	
	/**
	 * safeToSplitWay() returns true if it is safe (no short arcs will be
	 * created) to split a way at a given position - assumes that the
	 * floor and ceiling points will become nodes even if they are not
	 * yet.
	 * @param points	the way's points
	 * @param pos	the position we are testing
	 * @param floor lower limit of points to test (inclusive)
	 * @param ceiling upper limit of points to test (inclusive)
	 * @return true if is OK to split as pos
	 */
	private static boolean safeToSplitWay(List<Coord> points, int pos, int floor, int ceiling) {
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
		}

		return true;
	}

	private void addRoadWithoutLoops(ConvertedWay cw) {
		Way way = cw.getWay();
		List<Integer> nodeIndices = new ArrayList<>();
		List<Coord> points = way.getPoints();
		if (points.size() < 2){
			log.warn("road has < 2 points",way.getId(),"(discarding)");
			return;
		}
		Way trailingWay = null;
		String debugWayName = way.getDebugName();

		// collect the Way's nodes and also split the way if any
		// inter-node arc length becomes excessive
		double arcLength = 0;
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
													   maxLon - minLon) >= 1.0;
			}
		}

		WayBBox wayBBox = new WayBBox();
		
		for(int i = 0; i < points.size(); ++i) {
			Coord p = points.get(i);

			wayBBox.addPoint(p);

			// check if we should split the way at this point to limit
			// the arc length between nodes
			if((i + 1) < points.size()) {
				Coord nextP = points.get(i + 1);
				double d = p.distance(nextP);
				for (;;){
					int dlat = Math.abs(nextP.getLatitude() - p.getLatitude());
					int dlon = Math.abs(nextP.getLongitude() - p.getLongitude());
					if (d > MAX_ARC_LENGTH || Math.max(dlat,  dlon) >= LineSizeSplitterFilter.MAX_SIZE){
						double frac = Math.min(0.5,	 0.95 * (MAX_ARC_LENGTH / d));
						nextP = p.makeBetweenPoint(nextP, frac);
						nextP.incHighwayCount();
						points.add(i + 1, nextP);
						double newD = p.distance(nextP);
						if (log.isInfoEnabled())
							log.info("Way", debugWayName, "contains a segment that is", (int)d + "m long but I am adding a new point to reduce its length to", (int)newD + "m");
						d = newD;
					} else 
						break;
				}
				
				wayBBox.addPoint(nextP);

				if((arcLength + d) > MAX_ARC_LENGTH) {
					if (i <= 0)
						log.error("internal error: long arc segment was not split", debugWayName);
					assert i > 0 : "long arc segment was not split";
					assert trailingWay == null : "trailingWay not null #1";
					trailingWay = splitWayAt(way, i);
					// this will have truncated the current Way's
					// points so the loop will now terminate
					if (log.isInfoEnabled())
						log.info("Splitting way", debugWayName, "at", points.get(i).toOSMURL(), "to limit arc length to", (long)arcLength + "m");
				}
				else if(wayBBox.tooBig()) {
					if (i <= 0)
						log.error("internal error: arc segment with big bbox not split", debugWayName);
					assert i > 0 : "arc segment with big bbox not split";
					assert trailingWay == null : "trailingWay not null #2";
					trailingWay = splitWayAt(way, i);
					// this will have truncated the current Way's
					// points so the loop will now terminate
					if (log.isInfoEnabled())
						log.info("Splitting way", debugWayName, "at", points.get(i).toOSMURL(), "to limit the size of its bounding box");
				}
				else {
					if(p.getHighwayCount() > 1) {
						// point is a node so zero arc length
						arcLength = 0;
					}

					arcLength += d;
				}
			}
			if(p.getHighwayCount() > 1 || p.getOnCountryBorder()) {
				// this point is a node connecting highways
				CoordNode coordNode = nodeIdMap.get(p);
				if(coordNode == null) {
					// assign a node id
					coordNode = new CoordNode(p, nextNodeId++, p.getOnBoundary(), p.getOnCountryBorder());
					nodeIdMap.put(p, coordNode);
				}
				
				if (p instanceof CoordPOI){
					// check if this poi should be converted to a route restriction
					CoordPOI cp = (CoordPOI) p;
					if (cp.getConvertToViaInRouteRestriction()){
						String wayPOI = way.getTag(WAY_POI_NODE_IDS);
						if (wayPOI != null && wayPOI.contains("[" + cp.getNode().getId() + "]")){
							byte nodeAccess = AccessTagsAndBits.evalAccessTags(cp.getNode());
							if (nodeAccess != cw.getAccess()){
								List<Way> wayList = poiRestrictions.get(cp.getNode());
								if (wayList == null){
									wayList = new ArrayList<>();
									poiRestrictions.put(cp.getNode(), wayList);
								}
								wayList.add(way);
							}
						}
					}
				}
				
				// add this index to node Indexes (should not already be there)
				assert !nodeIndices.contains(i) : debugWayName + " has multiple nodes for point " + i + " new node is " + p.toOSMURL();
				nodeIndices.add(i);

				if((i + 1) < points.size() &&
				   nodeIndices.size() == MAX_NODES_IN_WAY) {
					// this isn't the last point in the way so split
					// it here to avoid exceeding the max nodes in way
					// limit
					assert trailingWay == null : "trailingWay not null #7";
					trailingWay = splitWayAt(way, i);
					// this will have truncated the current Way's
					// points so the loop will now terminate
					if (log.isInfoEnabled())
						log.info("Splitting way", debugWayName, "at", points.get(i).toOSMURL(), "as it has at least", MAX_NODES_IN_WAY, "nodes");
				}
			}
		}

		MapLine line = new MapLine();
		elementSetup(line, cw.getGType(), way);
		line.setPoints(points);
		MapRoad road = new MapRoad(nextRoadId++, way.getId(), line);
		if (routable == false)
			road.skipAddToNOD(true);
		
		boolean doFlareCheck = true;
		
		if (cw.isRoundabout()){
			road.setRoundabout(true);
			doFlareCheck = false;
		}

		if(way.tagIsLikeYes("mkgmap:synthesised")) {
			road.setSynthesised(true);
			doFlareCheck = false;
		}

		if(way.tagIsLikeNo("mkgmap:flare-check")) {
			doFlareCheck = false;
		}
		else if(way.tagIsLikeYes("mkgmap:flare-check")) {
			doFlareCheck = true;
		}
		road.doFlareCheck(doFlareCheck);

		// set road parameters 

		// copy road class and road speed
		road.setRoadClass(cw.getRoadClass());
		road.setSpeed(cw.getRoadSpeed());
		
		if (cw.isOneway()) {
			road.setDirection(true);
			road.setOneway();
		}

		road.setAccess(cw.getAccess());
		
		// does the road have a carpool lane?
		if (cw.isCarpool())
			road.setCarpoolLane();

		if (cw.isThroughroute() == false)
			road.setNoThroughRouting();

		if(cw.isToll())
			road.setToll();

		// by default, ways are paved
		if(cw.isUnpaved())
			road.paved(false);

		// by default, way's are not ferry routes
		if(cw.isFerry())
			road.ferry(true);

		int numNodes = nodeIndices.size();
		if (way.isViaWay() && numNodes > 2){
			List<RestrictionRelation> rrList = wayRelMap.get(way.getId());
			for (RestrictionRelation rr : rrList){
				rr.updateViaWay(way, nodeIndices);
			}
		}

		if(numNodes > 0) {
			// replace Coords that are nodes with CoordNodes
			for(int i = 0; i < numNodes; ++i) {
				int n = nodeIndices.get(i);
				Coord coord = points.get(n);
				CoordNode thisCoordNode = nodeIdMap.get(coord);
				assert thisCoordNode != null : "Way " + debugWayName + " node " + i + " (point index " + n + ") at " + coord.toOSMURL() + " yields a null coord node";
				boolean boundary = coord.getOnBoundary() || coord.getOnCountryBorder();
				if(boundary && log.isInfoEnabled()) {
					log.info("Way", debugWayName + "'s point #" + n, "at", coord.toOSMURL(), "is a boundary node");
				}
				points.set(n, thisCoordNode);
			}
		}

		if (roadLog.isInfoEnabled()) {
			// shift the bits so that they have the correct position
			int cmpAccess = (road.getRoadDef().getTabAAccess() & 0xff) + ((road.getRoadDef().getTabAAccess() & 0xc000) >> 6);
			if (road.isDirection()) {
				cmpAccess |= 1<<10;
			}
			String access = String.format("%11s",Integer.toBinaryString(cmpAccess)).replace(' ', '0');
			roadLog.info(String.format("%19d 0x%-2x %11s %6d %6d %6d %s", way.getId(), road.getType(), access, road.getRoadDef().getRoadClass(), road.getRoadDef().getRoadSpeed(), road.getPoints().size(),Arrays.toString(road.getLabels())));
		}
		
		// add the road to the housenumber generator
		// it will add the road later on to the lineAdder
		housenumberGenerator.addRoad(way, road);

		if(trailingWay != null)
			addRoadWithoutLoops(new ConvertedWay(cw, trailingWay));
	}

	/**
	 * Check if the first or last of the coords of the way has the fixme flag set
	 * @param way the way to check 
	 * @return true if fixme flag was found
	 */
	private static boolean checkFixmeCoords(Way way) {
		if (way.getPoints().get(0).isFixme())
			return true;
		if (way.getPoints().get(way.getPoints().size()-1).isFixme())
			return true;
		return false;
	}

	/**
	 * split a Way at the specified point and return the new Way (the
	 * original Way is truncated, both ways will contain the split point)
	 * @param way the way to split
	 * @param index the split position. 
	 * @return the trailing part of the way
	 */
	private static Way splitWayAt(Way way, int index) {
		if (way.isViaWay()){
			log.warn("via way of restriction is split, restriction will be ignored",way);
		}
		Way trailingWay = new Way(way.getId());
		List<Coord> wayPoints = way.getPoints();
		int numPointsInWay = wayPoints.size();

		for(int i = index; i < numPointsInWay; ++i)
			trailingWay.addPoint(wayPoints.get(i));

		// ensure split point becomes a node
		wayPoints.get(index).incHighwayCount();

		// copy the way's name and tags to the new way
		trailingWay.copyTags(way);

		// remove the points after the split from the original way
		// it's probably more efficient to remove from the end first
		for(int i = numPointsInWay - 1; i > index; --i)
			wayPoints.remove(i);
		
		return trailingWay;
	}


	/**
	 * Increment the highway counter for each coord of each road.
	 * As a result, all road junctions have a count > 1. 
	 */
	private void setHighwayCounts(){
		log.info("Maintaining highway counters");
		long lastId = 0;
		List<Way> dupIdHighways = new ArrayList<>();
		for (ConvertedWay cw :roads){
			if (!cw.isValid())
				continue;
			Way way = cw.getWay();
			if (way.getId() == lastId) {
				log.debug("Road with identical id:", way.getId());
				dupIdHighways.add(way);
				continue;
			}
			lastId = way.getId();
			List<Coord> points = way.getPoints();
			for (Coord p:points){
				p.incHighwayCount();
			}
		}
		
		// go through all duplicated highways and increase the highway counter of all crossroads
		
		for (Way way : dupIdHighways) {
			List<Coord> points = way.getPoints();
			// increase the highway counter of the first and last point
			points.get(0).incHighwayCount();
			points.get(points.size()-1).incHighwayCount();
			
			// for all other points increase the counter only if other roads are connected
			for (int i = 1; i <  points.size()-1; i++) {
				Coord p = points.get(i);
				if (p.getHighwayCount() > 1) {
					// this is a crossroads - mark that the duplicated way is also part of it 
					p.incHighwayCount();
				}
			}
		}
	}

	
	/**
	 * Increment the highway counter for each coord of each road.
	 * As a result, all road junctions have a count > 1. 
	 */
	private void resetHighwayCounts(){
		log.info("Resetting highway counters");
		long lastId = 0;
		for (ConvertedWay cw :roads){
			if (!cw.isValid())
				continue;
			Way way = cw.getWay();
			if (way.getId() == lastId) {
				continue;
			}
			lastId = way.getId();
			List<Coord> points = way.getPoints();
			for (Coord p:points){
				p.resetHighwayCount();
			}
		}
	}

	/**
	 * Detect roads that do not share any node with another road.
	 * If such a road has the mkgmap:set_unconnected_type tag, add it as line, not as a road. 
	 */
	private void findUnconnectedRoads(){
		Map<Coord, HashSet<Way>> connectors = new IdentityHashMap<>(roads.size()*2);
		
		// for dead-end-check only: will contain ways with loops (also simply closed ways)
		HashSet<Way> selfConnectors = new HashSet<>();
		
		// collect nodes that might connect roads
		long lastId = 0;
		for (ConvertedWay cw :roads){
			Way way = cw.getWay();
			if (way.getId() == lastId)
				continue;
			lastId = way.getId();
			for (Coord p:way.getPoints()){
				if (p.getHighwayCount() > 1){
					HashSet<Way> ways = connectors.get(p);
					if (ways == null){
						ways = new HashSet<>();
						connectors.put(p, ways);
					}
					boolean wasNew = ways.add(way);
					if (!wasNew && reportDeadEnds > 0)
						selfConnectors.add(way);
				}
			}
		}
		
		// find roads that are not connected
		// count downwards because we are removing elements
		Iterator<ConvertedWay> iter = roads.iterator();
		while(iter.hasNext()){
			ConvertedWay cw = iter.next();
			if (!cw.isValid())
				continue;
			Way way = cw.getWay();
			if(reportDeadEnds > 0){
				// report dead ends of oneway roads 
				if (cw.isOneway() && !way.tagIsLikeNo("mkgmap:dead-end-check")) {
					List<Coord> points = way.getPoints();
					int[] pointsToCheck = {0, points.size()-1};
					if (points.get(pointsToCheck[0]) == points.get(pointsToCheck[1]))
						continue; // skip closed way
					for (int pos: pointsToCheck ){
						boolean isDeadEnd = true;
						boolean isDeadEndOfMultipleWays = true;
						Coord p = points.get(pos);
						if (bbox.contains(p) == false || p.getOnBoundary())
							isDeadEnd = false;  // we don't know enough about possible connections 
						else if (p.getHighwayCount() < 2){
							isDeadEndOfMultipleWays = false;
						} else {
							HashSet<Way> ways = connectors.get(p);
							if (ways.size() <= 1)
								isDeadEndOfMultipleWays = false;
							for (Way connectedWay: ways){
								if (!isDeadEnd)
									break;
								if (way == connectedWay){
									if (selfConnectors.contains(way)){
										// this might be a P-shaped oneway,
										// check if it has other exists in the loop part
										if (pos == 0){
											for (int k = pos+1; k < points.size()-1; k++){
												Coord pTest = points.get(k);
												if (pTest == p)
													break; // found no other exit
												if (pTest.getHighwayCount() > 1){
													isDeadEnd = false;
													break;
												}
											} 

										}else {
											for (int k = pos-1; k >= 0; k--){
												Coord pTest = points.get(k);
												if (pTest == p)
													break; // found no other exit
												if (pTest.getHighwayCount() > 1){
													isDeadEnd = false;
													break;
												}
											} 
										}
									}
									continue;
								}
								List<Coord> otherPoints = connectedWay.getPoints();
								Coord otherFirst = otherPoints.get(0);
								Coord otherLast = otherPoints.get(otherPoints.size()-1);
								if (otherFirst == otherLast || connectedWay.tagIsLikeYes(onewayTagKey) == false)
									isDeadEnd = false;  
								else {
									Coord pOther;
									if (pos != 0)
										pOther = otherLast;
									else
										pOther = otherFirst;
									if (p != pOther){
										// way is connected to a point on a oneway which allows going on
										isDeadEnd = false;
									}
								}
							}
						}
						
						if (isDeadEnd && (isDeadEndOfMultipleWays || reportDeadEnds > 1)){
							log.warn("Oneway road " + way.getId() + " with tags " + way.toTagString() + ((pos==0) ? " comes from":" goes to") + " nowhere at " + p.toOSMURL());
						}
					}
				}
			}
  			String replType = way.getTag("mkgmap:set_unconnected_type");
			if (replType != null){
				boolean isConnected = false;
				boolean onBoundary = false;
				for (Coord p:way.getPoints()){
					if (p.getOnBoundary())
						onBoundary = true;
					if (p.getHighwayCount() > 1){
						HashSet<Way> ways = connectors.get(p);
						if (ways != null && ways.size() > 1){
							isConnected = true;
							break;
						}
					}
				}
				if (!isConnected){
					if (onBoundary){
						log.info("road not connected to other roads but is on boundary:", way.toBrowseURL());
					} else {
						if ("none".equals(replType))
							log.info("road not connected to other roads, is ignored:", way.toBrowseURL());
						else {
							int typeNoConnection = -1;
							try{
								typeNoConnection = Integer.decode(replType);
								if (GType.isRoutableLineType(typeNoConnection)){
									typeNoConnection = -1;
									log.error("type value in mkgmap:set_unconnected_type should not be a routable type:" + replType);
								}
							} catch (NumberFormatException e){
								log.warn("invalid type value in mkgmap:set_unconnected_type:", replType);
							}
							if (typeNoConnection != -1 ){
								log.info("road not connected to other roads, added as line with type", replType + ":", way.toBrowseURL());
								addLine(way, cw.getGType(), typeNoConnection);
							} else {
								log.warn("road not connected to other roads, but replacement type is invalid. Dropped:", way.toBrowseURL());
							}
						}
						iter.remove();
					}
				}
			}
		}
	}
	
	/**
	 * Make sure that only CoordPOI which affect routing will be treated as
	 * nodes in the following routines.
	 */
	private void filterCoordPOI() {
		if (!linkPOIsToWays)
			return;
		log.info("translating CoordPOI");
		for (ConvertedWay cw: roads) {
			if (!cw.isValid())
				continue;
			Way way = cw.getWay();
			if ("true".equals(way.getTag("mkgmap:way-has-pois"))) {
				String wayPOI = "";

				List<Coord> points = way.getPoints();
				int numPoints = points.size();
				for (int i = 0;i < numPoints; i++) {
					Coord p = points.get(i);
					if (p instanceof CoordPOI){
						CoordPOI cp = (CoordPOI) p;
						Node node = cp.getNode();
						boolean usedInThisWay = false;
						byte wayAccess = cw.getAccess();
						if (node.getTag("mkgmap:road-class") != null
								|| node.getTag("mkgmap:road-speed") != null ) {
							if (wayAccess != AccessTagsAndBits.FOOT)
								usedInThisWay = true;
						}
						byte nodeAccess = AccessTagsAndBits.evalAccessTags(node);
						if(nodeAccess != (byte)0xff){
							// barriers etc. 
							if ((wayAccess & nodeAccess) != wayAccess){
								// node is more restrictive
								if (p.getHighwayCount() >= 2 || (i != 0 && i != numPoints-1)){
									usedInThisWay = true;
									cp.setConvertToViaInRouteRestriction(true);
								}
								else {
									log.info("POI node", node.getId(), "with access restriction is ignored, it is not connected to other routable ways");
								}
							} else 
								log.info("Access restriction in POI node", node.toBrowseURL(), "was ignored for way", way.toBrowseURL());
						}
						if (usedInThisWay){
							cp.setUsed(true);
							wayPOI += "["+ node.getId()+"]";
						}
					}
				} 
				if (wayPOI.isEmpty()) {
					way.deleteTag("mkgmap:way-has-pois");
					log.info("ignoring CoordPOI(s) for way", way.toBrowseURL(), "because routing is not affected.");
				}
				else {
					way.addTag(WAY_POI_NODE_IDS, wayPOI);
				}
			}
		}
	}

	@Override
	public Boolean getDriveOnLeft(){
		assert roads == null : "getDriveOnLeft() should be called after end()";
		return driveOnLeft;
	}
}

