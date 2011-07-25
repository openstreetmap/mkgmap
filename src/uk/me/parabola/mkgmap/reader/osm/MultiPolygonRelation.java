package uk.me.parabola.mkgmap.reader.osm;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.Java2DConverter;

/**
 * Representation of an OSM Multipolygon Relation.<br/>
 * The different way of the multipolygon are joined to polygons and inner
 * polygons are cut out from the outer polygons.
 * 
 * @author WanMil
 */
public class MultiPolygonRelation extends Relation {
	private static final Logger log = Logger
			.getLogger(MultiPolygonRelation.class);

	public static final String STYLE_FILTER_TAG = "mkgmap:stylefilter";
	public static final String STYLE_FILTER_LINE = "polyline";
	public static final String STYLE_FILTER_POLYGON = "polygon";
	
	private final Map<Long, Way> tileWayMap;
	private final Map<Long, String> roleMap = new HashMap<Long, String>();
	private Map<Long, Way> mpPolygons = new HashMap<Long, Way>();
	
	
	protected ArrayList<BitSet> containsMatrix;
	protected ArrayList<JoinedWay> polygons;
	protected Set<JoinedWay> intersectingPolygons;
	
	protected Set<Way> outerWaysForLineTagging;
	protected Map<String, String> outerTags;

	private final uk.me.parabola.imgfmt.app.Area bbox;
	protected Area bboxArea;
	
	/** 
	 * A point that has a lower or equal squared distance from 
	 * a line is treated as if it lies one the line.<br/>
	 * 1.0d is very exact. 2.0d covers rounding problems when converting
	 * OSM locations to mkgmap internal format. A larger value 
	 * is more tolerant against imprecise OSM data.
	 */
	private static final double OVERLAP_TOLERANCE_DISTANCE = 2.0d;
	
	/**
	 * Create an instance based on an existing relation. We need to do this
	 * because the type of the relation is not known until after all its tags
	 * are read in.
	 * 
	 * @param other
	 *            The relation to base this one on.
	 * @param wayMap
	 *            Map of all ways.
	 * @param bbox
	 *            The bounding box of the tile
	 */
	public MultiPolygonRelation(Relation other, Map<Long, Way> wayMap,
			uk.me.parabola.imgfmt.app.Area bbox) {
		this.tileWayMap = wayMap;
		this.bbox = bbox;

		setId(other.getId());
		setName(other.getName());
		copyTags(other);

		if (log.isDebugEnabled()) {
			log.debug("Construct multipolygon", toBrowseURL(), toTagString());
		}

		for (Map.Entry<String, Element> pair : other.getElements()) {
			String role = pair.getKey();
			Element el = pair.getValue();
			if (log.isDebugEnabled()) {
				log.debug(" ", role, el.toBrowseURL(), el.toTagString());
			}
			addElement(role, el);
			roleMap.put(el.getId(), role);
		}
	}
	

	
	/**
	 * Retrieves the mp role of the given element.
	 * 
	 * @param element
	 *            the element
	 * @return the role of the element
	 */
	protected String getRole(Element element) {
		String role = roleMap.get(element.getId());
		if (role != null && ("outer".equals(role) || "inner".equals(role))) {
			return role;
		}

		return null;
	}

	/**
	 * Try to join the two ways.
	 * 
	 * @param joinWay
	 *            the way to which tempWay is added in case both ways could be
	 *            joined and checkOnly is false.
	 * @param tempWay
	 *            the way to be added to joinWay
	 * @param checkOnly
	 *            <code>true</code> checks only and does not perform the join
	 *            operation
	 * @return <code>true</code> if tempWay way is (or could be) joined to
	 *         joinWay
	 */
	private boolean joinWays(JoinedWay joinWay, JoinedWay tempWay,
			boolean checkOnly) {
		// use == or equals as comparator??
		if (joinWay.getPoints().get(0) == tempWay.getPoints().get(0)) {
			if (!checkOnly) {
				for (Coord point : tempWay.getPoints().subList(1,
						tempWay.getPoints().size())) {
					joinWay.addPoint(0, point);
				}
				joinWay.addWay(tempWay);
			}
			return true;
		} else if (joinWay.getPoints().get(joinWay.getPoints().size() - 1) == tempWay
				.getPoints().get(0)) {
			if (!checkOnly) {
				for (Coord point : tempWay.getPoints().subList(1,
						tempWay.getPoints().size())) {
					joinWay.addPoint(point);
				}
				joinWay.addWay(tempWay);
			}
			return true;
		} else if (joinWay.getPoints().get(0) == tempWay.getPoints().get(
				tempWay.getPoints().size() - 1)) {
			if (!checkOnly) {
				int insertIndex = 0;
				for (Coord point : tempWay.getPoints().subList(0,
						tempWay.getPoints().size() - 1)) {
					joinWay.addPoint(insertIndex, point);
					insertIndex++;
				}
				joinWay.addWay(tempWay);
			}
			return true;
		} else if (joinWay.getPoints().get(joinWay.getPoints().size() - 1) == tempWay
				.getPoints().get(tempWay.getPoints().size() - 1)) {
			if (!checkOnly) {
				int insertIndex = joinWay.getPoints().size();
				for (Coord point : tempWay.getPoints().subList(0,
						tempWay.getPoints().size() - 1)) {
					joinWay.addPoint(insertIndex, point);
				}
				joinWay.addWay(tempWay);
			}
			return true;
		}
		return false;
	}

	/**
	 * Combine a list of way segments to a list of maximally joined ways
	 * 
	 * @param segments
	 *            a list of closed or unclosed ways
	 * @return a list of closed ways
	 */
	protected ArrayList<JoinedWay> joinWays(List<Way> segments) {
		// TODO check if the closed polygon is valid and implement a
		// backtracking algorithm to get other combinations

		ArrayList<JoinedWay> joinedWays = new ArrayList<JoinedWay>();
		if (segments == null || segments.isEmpty()) {
			return joinedWays;
		}

		// go through all segments and categorize them to closed and unclosed
		// list
		ArrayList<JoinedWay> unclosedWays = new ArrayList<JoinedWay>();
		for (Way orgSegment : segments) {
			JoinedWay jw = new JoinedWay(orgSegment);
			roleMap.put(jw.getId(), getRole(orgSegment));
			if (orgSegment.isClosed()) {
				joinedWays.add(jw);
			} else {
				unclosedWays.add(jw);
			}
		}

		while (!unclosedWays.isEmpty()) {
			JoinedWay joinWay = unclosedWays.remove(0);

			// check if the current way is already closed or if it is the last
			// way
			if (joinWay.isClosed() || unclosedWays.isEmpty()) {
				joinedWays.add(joinWay);
				continue;
			}

			boolean joined = false;

			// if we have a way that could be joined but which has a wrong role
			// then store it here and check in the end if it's working
			JoinedWay wrongRoleWay = null;
			String joinRole = getRole(joinWay);

			// go through all ways and check if there is a way that can be
			// joined with it
			// in this case join the two ways
			// => add all points of tempWay to joinWay, remove tempWay and put
			// joinWay to the beginning of the list
			// (not optimal but understandable - can be optimized later)
			for (JoinedWay tempWay : unclosedWays) {
				if (tempWay.isClosed()) {
					continue;
				}

				String tempRole = getRole(tempWay);
				// if a role is not 'inner' or 'outer' then it is used as
				// universal
				// check if the roles of the ways are matching
				if ((!"outer".equals(joinRole) && !"inner"
						.equals(joinRole))
						|| (!"outer".equals(tempRole) && !"inner"
						.equals(tempRole))
						|| (joinRole != null && joinRole.equals(tempRole))) {
					// the roles are matching => try to join both ways
					joined = joinWays(joinWay, tempWay, false);
				} else {
					// the roles are not matching => test if both ways would
					// join

					// as long as we don't have an alternative way with wrong
					// role
					// or if the alternative way is shorter then check if
					// the way with the wrong role could be joined
					if (wrongRoleWay == null
							|| wrongRoleWay.getPoints().size() < tempWay
									.getPoints().size()) {
						if (joinWays(joinWay, tempWay, true)) {
							// save this way => maybe we will use it in the end
							// if we don't find any other way
							wrongRoleWay = tempWay;
						}
					}
				}

				if (joined) {
					// we have joined the way
					unclosedWays.remove(tempWay);
					break;
				}
			}

			if (!joined && wrongRoleWay != null) {

				log.warn("Join ways with different roles. Multipolygon: "
						+ toBrowseURL());
				log.warn("Way1 Role:", getRole(joinWay));
				logWayURLs(Level.WARNING, "-", joinWay);
				log.warn("Way2 Role:", getRole(wrongRoleWay));
				logWayURLs(Level.WARNING, "-", wrongRoleWay);

				joined = joinWays(joinWay, wrongRoleWay, false);
				if (joined) {
					// we have joined the way
					unclosedWays.remove(wrongRoleWay);
					break;
				}
			}

			if (joined) {
				if (joinWay.isClosed()) {
					// it's closed => don't process it again
					joinedWays.add(joinWay);
				} else if (unclosedWays.isEmpty()) {
					// no more ways to join with
					// it's not closed but we cannot join it more
					joinedWays.add(joinWay);
				} else {
					// it is not yet closed => process it once again
					unclosedWays.add(0, joinWay);
				}
			} else {
				// it's not closed but we cannot join it more
				joinedWays.add(joinWay);
			}
		}

		return joinedWays;
	}

	/**
	 * Try to close all unclosed ways in the given list of ways.
	 * 
	 * @param wayList
	 *            a list of ways
	 */
	protected void closeWays(ArrayList<JoinedWay> wayList) {
		for (JoinedWay way : wayList) {
			if (way.isClosed() || way.getPoints().size() < 3) {
				continue;
			}
			Coord p1 = way.getPoints().get(0);
			Coord p2 = way.getPoints().get(way.getPoints().size() - 1);

			if (bbox.insideBoundary(p1) == false
					&& bbox.insideBoundary(p2) == false) {
				// both points lie outside the bbox or on the bbox

				// check if both points are on the same side of the bounding box
				if ((p1.getLatitude() <= bbox.getMinLat() && p2.getLatitude() <= bbox
						.getMinLat())
						|| (p1.getLatitude() >= bbox.getMaxLat() && p2
								.getLatitude() >= bbox.getMaxLat())
						|| (p1.getLongitude() <= bbox.getMinLong() && p2
								.getLongitude() <= bbox.getMinLong())
						|| (p1.getLongitude() >= bbox.getMaxLong() && p2
								.getLongitude() >= bbox.getMaxLong())) {
					// they are on the same side outside of the bbox
					// so just close them without worrying about if
					// they intersect itself because the intersection also
					// is outside the bbox
					way.closeWayArtificially();
					log.info("Endpoints of way", way,
							"are both outside the bbox. Closing it directly.");
					continue;
				}
			}
			
			Line2D closingLine = new Line2D.Float(p1.getLongitude(), p1
					.getLatitude(), p2.getLongitude(), p2.getLatitude());

			boolean intersects = false;
			Coord lastPoint = null;
			// don't use the first and the last point
			// the closing line can intersect only in one point or complete.
			// Both isn't interesting for this check
			for (Coord thisPoint : way.getPoints().subList(1,
					way.getPoints().size() - 1)) {
				if (lastPoint != null) {
					if (closingLine.intersectsLine(lastPoint.getLongitude(),
							lastPoint.getLatitude(), thisPoint.getLongitude(),
							thisPoint.getLatitude())) {
						intersects = true;
						break;
					}
				}
				lastPoint = thisPoint;
			}

			if (!intersects) {
				// close the polygon
				// the new way segment does not intersect the rest of the
				// polygon
				log.info("Closing way", way);
				log.info("from", way.getPoints().get(0).toOSMURL());
				log.info("to", way.getPoints().get(way.getPoints().size() - 1)
						.toOSMURL());
				// mark this ways as artificially closed
				way.closeWayArtificially();
			}
		}
	}

	
	protected static class ConnectionData {
		public Coord c1;
		public Coord c2;
		public JoinedWay w1;
		public JoinedWay w2;
		// sometimes the connection of both points cannot be done directly but with an intermediate point 
		public Coord imC;
		public double distance;
		
		public ConnectionData() {
			
		}
	}
	
	protected boolean connectUnclosedWays(List<JoinedWay> allWays) {
		List<JoinedWay> unclosed = new ArrayList<JoinedWay>();

		for (JoinedWay w : allWays) {
			if (w.isClosed() == false) {
				unclosed.add(w);
			}
		}
		// try to connect ways lying outside or on the bbox
		if (unclosed.size() >= 2) {
			log.debug("Checking",unclosed.size(),"unclosed ways for connections outside the bbox");
			Map<Coord, JoinedWay> outOfBboxPoints = new HashMap<Coord, JoinedWay>();
			
			// check all ways for endpoints outside or on the bbox
			for (JoinedWay w : unclosed) {
				Coord c1 = w.getPoints().get(0);
				if (bbox.insideBoundary(c1)==false) {
					log.debug("Point",c1,"of way",w.getId(),"outside bbox");
					outOfBboxPoints.put(c1, w);
				}

				Coord c2 = w.getPoints().get(w.getPoints().size()-1);
				if (bbox.insideBoundary(c2)==false) {
					log.debug("Point",c2,"of way",w.getId(),"outside bbox");
					outOfBboxPoints.put(c2, w);
				}
			}
			
			if (outOfBboxPoints.size() < 2) {
				log.debug(outOfBboxPoints.size(),"point outside the bbox. No connection possible.");
				return false;
			}
			
			List<ConnectionData> coordPairs = new ArrayList<ConnectionData>();
			ArrayList<Coord> coords = new ArrayList<Coord>(outOfBboxPoints.keySet());
			for (int i = 0; i < coords.size(); i++) {
				for (int j = i + 1; j < coords.size(); j++) {
					ConnectionData cd = new ConnectionData();
					cd.c1 = coords.get(i);
					cd.c2 = coords.get(j);
					cd.w1 = outOfBboxPoints.get(cd.c1);					
					cd.w2 = outOfBboxPoints.get(cd.c2);					
					
					if (lineCutsBbox(cd.c1, cd.c2 )) {
						// Check if the way can be closed with one additional point
						// outside the bounding box.
						// The additional point is combination of the coords of both endpoints.
						// It works if the lines from the endpoints to the additional point does
						// not cut the bounding box.
						// This can be removed when the splitter guarantees to provide logical complete
						// multi-polygons.
						Coord edgePoint1 = new Coord(cd.c1.getLatitude(), cd.c2
								.getLongitude());
						Coord edgePoint2 = new Coord(cd.c2.getLatitude(), cd.c1
								.getLongitude());

						if (lineCutsBbox(cd.c1, edgePoint1) == false
								&& lineCutsBbox(edgePoint1, cd.c2) == false) {
							cd.imC = edgePoint1;
						} else if (lineCutsBbox(cd.c1, edgePoint2) == false
								&& lineCutsBbox(edgePoint2, cd.c2) == false) {
							cd.imC = edgePoint1;
						} else {
							// both endpoints are on opposite sides of the bounding box
							// automatically closing such points would create wrong polygons in most cases
							continue;
						}
						cd.distance = cd.c1.distance(cd.imC) + cd.imC.distance(cd.c2);
					} else {
						cd.distance = cd.c1.distance(cd.c2);
					}
					coordPairs.add(cd);
				}
			}
			
			if (coordPairs.isEmpty()) {
				log.debug("All potential connections cross the bbox. No connection possible.");
				return false;
			} else {
				// retrieve the connection with the minimum distance
				ConnectionData minCon = Collections.min(coordPairs,
						new Comparator<ConnectionData>() {
							public int compare(ConnectionData o1,
									ConnectionData o2) {
								return Double.compare(o1.distance, o2.distance);
							}
						});

				if (minCon.w1 == minCon.w2) {
					log.debug("Close a gap in way",minCon.w1);
					if (minCon.imC != null)
						minCon.w1.getPoints().add(minCon.imC);
					minCon.w1.closeWayArtificially();
				} else {
					log.debug("Connect", minCon.w1, "with", minCon.w2);

					if (minCon.w1.getPoints().get(0).equals(minCon.c1)) {
						Collections.reverse(minCon.w1.getPoints());
					}
					if (minCon.w2.getPoints().get(0).equals(minCon.c2) == false) {
						Collections.reverse(minCon.w2.getPoints());
					}

					minCon.w1.getPoints().addAll(minCon.w2.getPoints());
					minCon.w1.addWay(minCon.w2);
					allWays.remove(minCon.w2);
					return true;
				}
			}
		}
		return false;
	}

	
	/**
	 * Removes all ways non closed ways from the given list (
	 * <code>{@link Way#isClosed()} == false</code>)
	 * 
	 * @param wayList
	 *            list of ways
	 */
	protected void removeUnclosedWays(ArrayList<JoinedWay> wayList) {
		Iterator<JoinedWay> it = wayList.iterator();
		boolean firstWarn = true;
		while (it.hasNext()) {
			JoinedWay tempWay = it.next();
			if (!tempWay.isClosed()) {
				// warn only if the way intersects the bounding box 
				boolean inBbox = tempWay.intersects(bbox);
				if (inBbox) {
					if (firstWarn) {
						log.warn(
							"Cannot join the following ways to closed polygons. Multipolygon",
							toBrowseURL());
						firstWarn = false;
					}
					logWayURLs(Level.WARNING, "- way:", tempWay);
					logFakeWayDetails(Level.WARNING, tempWay);
				}

				it.remove();
				
				if (inBbox) {
					String role = getRole(tempWay);
					if (role == null || "".equals(role) || "outer".equals(role)) {
						// anyhow add the ways to the list for line tagging
						outerWaysForLineTagging.addAll(tempWay.getOriginalWays());
					}
				}
			}
		}
	}

	/**
	 * Removes all ways that are completely outside the bounding box. 
	 * This reduces error messages from problems on the tile bounds.
	 * @param wayList list of ways
	 */
	protected void removeWaysOutsideBbox(ArrayList<JoinedWay> wayList) {
		ListIterator<JoinedWay> wayIter = wayList.listIterator();
		while (wayIter.hasNext()) {
			JoinedWay w = wayIter.next();
			boolean remove = true;
			// check all points
			for (Coord c : w.getPoints()) {
				if (bbox.contains(c)) {
					// if one point is in the bounding box the way should not be removed
					remove = false;
					break;
				}
			}

			if (remove) {
				// check if the polygon contains the complete bounding box
				if (w.getBounds().contains(bboxArea.getBounds())) {
					remove = false;
				}
			}
			
			if (remove) {
				if (log.isDebugEnabled()) {
					log.debug("Remove way", w.getId(),
						"because it is completely outside the bounding box.");
				}
				wayIter.remove();
			}
		}
	}

	/**
	 * Find all polygons that are not contained by any other polygon.
	 * 
	 * @param candidates
	 *            all polygons that should be checked
	 * @param roleFilter
	 *            an additional filter
	 * @return all polygon indexes that are not contained by any other polygon
	 */
	private BitSet findOutmostPolygons(BitSet candidates, BitSet roleFilter) {
		BitSet realCandidates = ((BitSet) candidates.clone());
		realCandidates.and(roleFilter);
		return findOutmostPolygons(realCandidates);
	}

	/**
	 * Finds all polygons that are not contained by any other polygons and that match
	 * to the given role. All polygons with index given by <var>candidates</var>
	 * are used.
	 * 
	 * @param candidates
	 *            indexes of the polygons that should be used
	 * @return the bits of all outermost polygons are set to true
	 */
	protected BitSet findOutmostPolygons(BitSet candidates) {
		BitSet outmostPolygons = new BitSet();

		// go through all candidates and check if they are contained by any
		// other candidate
		for (int candidateIndex = candidates.nextSetBit(0); candidateIndex >= 0; candidateIndex = candidates
				.nextSetBit(candidateIndex + 1)) {
			// check if the candidateIndex polygon is not contained by any
			// other candidate polygon
			boolean isOutmost = true;
			for (int otherCandidateIndex = candidates.nextSetBit(0); otherCandidateIndex >= 0; otherCandidateIndex = candidates
					.nextSetBit(otherCandidateIndex + 1)) {
				if (contains(otherCandidateIndex, candidateIndex)) {
					// candidateIndex is not an outermost polygon because it is
					// contained by the otherCandidateIndex polygon
					isOutmost = false;
					break;
				}
			}
			if (isOutmost) {
				// this is an outermost polygon
				// put it to the bitset
				outmostPolygons.set(candidateIndex);
			}
		}

		return outmostPolygons;
	}

	protected ArrayList<PolygonStatus> getPolygonStatus(BitSet outmostPolygons,
			String defaultRole) {
		ArrayList<PolygonStatus> polygonStatusList = new ArrayList<PolygonStatus>();
		for (int polyIndex = outmostPolygons.nextSetBit(0); polyIndex >= 0; polyIndex = outmostPolygons
				.nextSetBit(polyIndex + 1)) {
			// polyIndex is the polygon that is not contained by any other
			// polygon
			JoinedWay polygon = polygons.get(polyIndex);
			String role = getRole(polygon);
			// if the role is not explicitly set use the default role
			if (role == null || "".equals(role)) {
				role = defaultRole;
			} 
			polygonStatusList.add(new PolygonStatus("outer".equals(role), polyIndex, polygon));
		}
		return polygonStatusList;
	}

	/**
	 * Creates a list of all original ways of the multipolygon. 
	 * @return all source ways
	 */
	protected List<Way> getSourceWays() {
		ArrayList<Way> allWays = new ArrayList<Way>();

		for (Map.Entry<String, Element> r_e : getElements()) {
			if (r_e.getValue() instanceof Way) {
				allWays.add((Way) r_e.getValue());
			} else {
				log.warn("Non way element", r_e.getValue().getId(),
						"in multipolygon", getId());
			}
		}
		return allWays;
	}
	
	
	// unfinishedPolygons marks which polygons are not yet processed
	protected BitSet unfinishedPolygons;

	// create bitsets which polygons belong to the outer and to the inner role
	protected BitSet innerPolygons;
	protected BitSet taggedInnerPolygons;
	protected BitSet outerPolygons;
	protected BitSet taggedOuterPolygons;

	/**
	 * Process the ways in this relation. Joins way with the role "outer" Adds
	 * ways with the role "inner" to the way with the role "outer"
	 */
	public void processElements() {
		log.info("Processing multipolygon", toBrowseURL());
		
		List<Way> allWays = getSourceWays();
		
		// check if the multipolygon itself or the non inner member ways have a tag
		// if not it does not make sense to process it and we could save the time
		boolean shouldProcess = hasStyleRelevantTags(this);
		if (shouldProcess == false) {
			for (Way w : allWays) {
				shouldProcess = hasStyleRelevantTags(w);
				if (shouldProcess) {
					break;
				}
			}
		}
		if (shouldProcess==false) {
			log.info("Do not process multipolygon",getId(),"because it has no style relevant tags.");
			return;
		}

		
		// create an Area for the bbox to clip the polygons
		bboxArea = Java2DConverter.createBoundsArea(getBbox()); 

		// join all single ways to polygons, try to close ways and remove non closed ways 
		polygons = joinWays(allWays);
		
		outerWaysForLineTagging = new HashSet<Way>();
		outerTags = new HashMap<String,String>();
		
		closeWays(polygons);

		while (connectUnclosedWays(polygons)) {
			closeWays(polygons);
		}

		removeUnclosedWays(polygons);

		// now only closed ways are left => polygons only

		// check if we have at least one polygon left
		if (polygons.isEmpty()) {
			// do nothing
			log.info("Multipolygon " + toBrowseURL()
					+ " does not contain a closed polygon.");
			tagOuterWays();
			cleanup();
			return;
		}

		removeWaysOutsideBbox(polygons);

		if (polygons.isEmpty()) {
			// do nothing
			log.info("Multipolygon", toBrowseURL(),
					 "is completely outside the bounding box. It is not processed.");
			tagOuterWays();
			cleanup();
			return;
		}

		// the intersectingPolygons marks all intersecting/overlapping polygons
		intersectingPolygons = new HashSet<JoinedWay>();
		
		// check which polygons lie inside which other polygon 
		createContainsMatrix(polygons);

		// unfinishedPolygons marks which polygons are not yet processed
		unfinishedPolygons = new BitSet(polygons.size());
		unfinishedPolygons.set(0, polygons.size());

		// create bitsets which polygons belong to the outer and to the inner role
		innerPolygons = new BitSet();
		taggedInnerPolygons = new BitSet();
		outerPolygons = new BitSet();
		taggedOuterPolygons = new BitSet();
		
		int wi = 0;
		for (Way w : polygons) {
			String role = getRole(w);
			if ("inner".equals(role)) {
				innerPolygons.set(wi);
				taggedInnerPolygons.set(wi);
			} else if ("outer".equals(role)) {
				outerPolygons.set(wi);
				taggedOuterPolygons.set(wi);
			} else {
				// unknown role => it could be both
				innerPolygons.set(wi);
				outerPolygons.set(wi);
			}
			wi++;
		}

		if (outerPolygons.isEmpty()) {
			log.warn("Multipolygon", toBrowseURL(),
				"does not contain any way tagged with role=outer or empty role.");
			cleanup();
			return;
		}

		Queue<PolygonStatus> polygonWorkingQueue = new LinkedBlockingQueue<PolygonStatus>();
		BitSet nestedOuterPolygons = new BitSet();
		BitSet nestedInnerPolygons = new BitSet();

		BitSet outmostPolygons;
		BitSet outmostInnerPolygons = new BitSet();
		boolean outmostInnerFound;
		do {
			outmostInnerFound = false;
			outmostPolygons = findOutmostPolygons(unfinishedPolygons);

			if (outmostPolygons.intersects(taggedInnerPolygons)) {
				outmostInnerPolygons.or(outmostPolygons);
				outmostInnerPolygons.and(taggedInnerPolygons);

				if (log.isDebugEnabled())
					log.debug("wrong inner polygons: " + outmostInnerPolygons);
				// do not process polygons tagged with role=inner but which are
				// not contained by any other polygon
				unfinishedPolygons.andNot(outmostInnerPolygons);
				outmostPolygons.andNot(outmostInnerPolygons);
				outmostInnerFound = true;
			}
		} while (outmostInnerFound);
		
		if (!outmostPolygons.isEmpty()) {
			polygonWorkingQueue.addAll(getPolygonStatus(outmostPolygons, "outer"));
		}

		boolean outmostPolygonProcessing = true;
		
	
		while (!polygonWorkingQueue.isEmpty()) {

			// the polygon is not contained by any other unfinished polygon
			PolygonStatus currentPolygon = polygonWorkingQueue.poll();

			// this polygon is now processed and should not be used by any
			// further step
			unfinishedPolygons.clear(currentPolygon.index);

			BitSet polygonContains = new BitSet();
			polygonContains.or(containsMatrix.get(currentPolygon.index));
			// use only polygon that are contained by the polygon
			polygonContains.and(unfinishedPolygons);
			// polygonContains is the intersection of the unfinished and
			// the contained polygons

			// get the holes
			// these are all polygons that are in the main polygon
			// and that are not contained by any other polygon
			boolean holesOk;
			BitSet holeIndexes;
			do {
				holeIndexes = findOutmostPolygons(polygonContains);
				holesOk = true;

				if (currentPolygon.outer) {
					// for role=outer only role=inner is allowed
					if (holeIndexes.intersects(taggedOuterPolygons)) {
						BitSet addOuterNestedPolygons = new BitSet();
						addOuterNestedPolygons.or(holeIndexes);
						addOuterNestedPolygons.and(taggedOuterPolygons);
						nestedOuterPolygons.or(addOuterNestedPolygons);
						holeIndexes.andNot(addOuterNestedPolygons);
						// do not process them
						unfinishedPolygons.andNot(addOuterNestedPolygons);
						polygonContains.andNot(addOuterNestedPolygons);
						
						// recalculate the holes again to get all inner polygons 
						// in the nested outer polygons
						holesOk = false;
					}
				} else {
					// for role=inner both role=inner and role=outer is supported
					// although inner in inner is not officially allowed
					if (holeIndexes.intersects(taggedInnerPolygons)) {
						// process inner in inner but issue a warning later
						BitSet addInnerNestedPolygons = new BitSet();
						addInnerNestedPolygons.or(holeIndexes);
						addInnerNestedPolygons.and(taggedInnerPolygons);
						nestedInnerPolygons.or(addInnerNestedPolygons);
					}
				}
			} while (!holesOk);

			ArrayList<PolygonStatus> holes = getPolygonStatus(holeIndexes, 
				(currentPolygon.outer ? "inner" : "outer"));

			// these polygons must all be checked for holes
			polygonWorkingQueue.addAll(holes);

			if (currentPolygon.outer) {
				// add the original ways to the list of ways that get the line tags of the mp
				// the joined ways may be changed by the auto closing algorithm
				outerWaysForLineTagging.addAll(currentPolygon.polygon.getOriginalWays());
			}
			
			// check if the polygon is an outer polygon or 
			// if there are some holes
			boolean processPolygon = currentPolygon.outer
					|| (holes.isEmpty()==false);

			if (processPolygon) {
				List<Way> singularOuterPolygons;
				if (holes.isEmpty()) {
					singularOuterPolygons = Collections
							.singletonList((Way) new JoinedWay(currentPolygon.polygon));
				} else {
					List<Way> innerWays = new ArrayList<Way>(holes.size());
					for (PolygonStatus polygonHoleStatus : holes) {
						innerWays.add(polygonHoleStatus.polygon);
					}

					singularOuterPolygons = cutOutInnerPolygons(currentPolygon.polygon,
						innerWays);
				}
				
				if (singularOuterPolygons.isEmpty()==false) {
					// handle the tagging 
					if (currentPolygon.outer && hasTags(this)) {
						// use the tags of the multipolygon
						for (Way p : singularOuterPolygons) {
							// overwrite all tags
							p.copyTags(this);
							p.deleteTag("type");
						}
						// remove the multipolygon tags in the original ways of the current polygon
						removeTagsInOrgWays(this, currentPolygon.polygon);
					} else {
						// use the tags of the original ways
						currentPolygon.polygon.mergeTagsFromOrgWays();
						for (Way p : singularOuterPolygons) {
							// overwrite all tags
							p.copyTags(currentPolygon.polygon);
						}
						// remove the current polygon tags in its original ways
						removeTagsInOrgWays(currentPolygon.polygon, currentPolygon.polygon);
					}
				
					if (currentPolygon.outer && outmostPolygonProcessing) {
						// this is the outer most polygon - copy its tags. They will be used
						// later for tagging of the lines

						// all cut polygons have the same tags - copy them from the first polygon
						Way outerWay = singularOuterPolygons.get(0);
						for (Entry<String, String> tag : outerWay.getEntryIteratable()) {
							outerTags.put(tag.getKey(), tag.getValue());
						}
						outmostPolygonProcessing = false;
					}
					
					for (Way mpWay : singularOuterPolygons) {
						// put the cut out polygons to the
						// final way map
						if (log.isDebugEnabled())
							log.debug(mpWay.getId(),mpWay.toTagString());
					
						// mark this polygons so that only polygon style rules are applied
						mpWay.addTag(STYLE_FILTER_TAG, STYLE_FILTER_POLYGON);
					
						getMpPolygons().put(mpWay.getId(), mpWay);
					}
				}
			}
		}
		
		if (log.isLoggable(Level.WARNING) && 
				(outmostInnerPolygons.cardinality()+unfinishedPolygons.cardinality()+nestedOuterPolygons.cardinality()+nestedInnerPolygons.cardinality() >= 1)) {
			log.warn("Multipolygon", toBrowseURL(), "contains errors.");

			BitSet outerUnusedPolys = new BitSet();
			outerUnusedPolys.or(unfinishedPolygons);
			outerUnusedPolys.or(outmostInnerPolygons);
			outerUnusedPolys.or(nestedOuterPolygons);
			outerUnusedPolys.or(nestedInnerPolygons);
			outerUnusedPolys.or(unfinishedPolygons);
			// use only the outer polygons
			outerUnusedPolys.and(outerPolygons);
			for (JoinedWay w : getWaysFromPolygonList(outerUnusedPolys)) {
				outerWaysForLineTagging.addAll(w.getOriginalWays());
			}
			
			runIntersectionCheck(unfinishedPolygons);
			runOutmostInnerPolygonCheck(outmostInnerPolygons);
			runNestedOuterPolygonCheck(nestedOuterPolygons);
			runNestedInnerPolygonCheck(nestedInnerPolygons);
			runWrongInnerPolygonCheck(unfinishedPolygons, innerPolygons);

			// we have at least one polygon that could not be processed
			// Probably we have intersecting or overlapping polygons
			// one possible reason is if the relation overlaps the tile
			// bounds
			// => issue a warning
			List<JoinedWay> lostWays = getWaysFromPolygonList(unfinishedPolygons);
			for (JoinedWay w : lostWays) {
				log.warn("Polygon", w, "is not processed due to an unknown reason.");
				logWayURLs(Level.WARNING, "-", w);
			}
		}

		// Go through all original outer ways, create a copy, tag them
		// with the mp tags and mark them only to be used for polyline processing
		// This enables the style file to decide if the polygon information or
		// the simple line information should be used.
		for (Way orgOuterWay : outerWaysForLineTagging) {
			Way lineTagWay =  new Way(FakeIdGenerator.makeFakeId(), orgOuterWay.getPoints());
			lineTagWay.setName(orgOuterWay.getName());
			lineTagWay.addTag(STYLE_FILTER_TAG, STYLE_FILTER_LINE);
			for (Entry<String,String> tag : outerTags.entrySet()) {
				lineTagWay.addTag(tag.getKey(), tag.getValue());
				
				// remove the tag from the original way if it has the same value
				if (tag.getValue().equals(orgOuterWay.getTag(tag.getKey()))) {
					removeTagsInOrgWays(orgOuterWay, tag.getKey());
				}
			}
			
			if (log.isDebugEnabled())
				log.debug("Add line way", lineTagWay.getId(), lineTagWay.toTagString());
			tileWayMap.put(lineTagWay.getId(), lineTagWay);
		}
		
		postProcessing();
		cleanup();
	}
	
	protected void postProcessing() {
		// copy all polygons created by the multipolygon algorithm to the global way map
		tileWayMap.putAll(mpPolygons);
	}
	
	private void runIntersectionCheck(BitSet unfinishedPolys) {
		if (intersectingPolygons.isEmpty()) {
			// nothing to do
			return;
		}

		log.warn("Some polygons are intersecting. This is not allowed in multipolygons.");

		boolean oneOufOfBbox = false;
		for (JoinedWay polygon : intersectingPolygons) {
			int pi = polygons.indexOf(polygon);
			unfinishedPolys.clear(pi);

			boolean outOfBbox = false;
			for (Coord c : polygon.getPoints()) {
				if (!bbox.contains(c)) {
					outOfBbox = true;
					oneOufOfBbox = true;
					break;
				}
			}

			logWayURLs(Level.WARNING, (outOfBbox ? "*" : "-"), polygon);
		}
		
		for (JoinedWay polygon : intersectingPolygons) {
			// print out the details of the original ways
			logFakeWayDetails(Level.WARNING, polygon);
		}
		
		if (oneOufOfBbox) {
			log.warn("Some of these intersections/overlaps may be caused by incomplete data on bounding box edges (*).");
		}
	}

	private void runNestedOuterPolygonCheck(BitSet nestedOuterPolygons) {
		// just print out warnings
		// the check has been done before
		for (int wiIndex = nestedOuterPolygons.nextSetBit(0); wiIndex >= 0; wiIndex = nestedOuterPolygons
				.nextSetBit(wiIndex + 1)) {
			JoinedWay outerWay = polygons.get(wiIndex);
			log.warn("Polygon",	outerWay, "carries role outer but lies inside an outer polygon. Potentially its role should be inner.");
			logFakeWayDetails(Level.WARNING, outerWay);
		}
	}
	
	private void runNestedInnerPolygonCheck(BitSet nestedInnerPolygons) {
		// just print out warnings
		// the check has been done before
		for (int wiIndex = nestedInnerPolygons.nextSetBit(0); wiIndex >= 0; wiIndex = nestedInnerPolygons
				.nextSetBit(wiIndex + 1)) {
			JoinedWay innerWay = polygons.get(wiIndex);
			log.warn("Polygon",	innerWay, "carries role", getRole(innerWay), "but lies inside an inner polygon. Potentially its role should be outer.");
			logFakeWayDetails(Level.WARNING, innerWay);
		}
	}	
	
	private void runOutmostInnerPolygonCheck(BitSet outmostInnerPolygons) {
		// just print out warnings
		// the check has been done before
		for (int wiIndex = outmostInnerPolygons.nextSetBit(0); wiIndex >= 0; wiIndex = outmostInnerPolygons
				.nextSetBit(wiIndex + 1)) {
			JoinedWay innerWay = polygons.get(wiIndex);
			log.warn("Polygon",	innerWay, "carries role", getRole(innerWay), "but is not inside any other polygon. Potentially it does not belong to this multipolygon.");
			logFakeWayDetails(Level.WARNING, innerWay);
		}
	}

	private void runWrongInnerPolygonCheck(BitSet unfinishedPolygons,
			BitSet innerPolygons) {
		// find all unfinished inner polygons that are not contained by any
		BitSet wrongInnerPolygons = findOutmostPolygons(unfinishedPolygons, innerPolygons);
		if (log.isDebugEnabled()) {
			log.debug("unfinished", unfinishedPolygons);
			log.debug("inner", innerPolygons);
			// other polygon
			log.debug("wrong", wrongInnerPolygons);
		}
		if (!wrongInnerPolygons.isEmpty()) {
			// we have an inner polygon that is not contained by any outer polygon
			// check if
			for (int wiIndex = wrongInnerPolygons.nextSetBit(0); wiIndex >= 0; wiIndex = wrongInnerPolygons
					.nextSetBit(wiIndex + 1)) {
				BitSet containedPolygons = new BitSet();
				containedPolygons.or(unfinishedPolygons);
				containedPolygons.and(containsMatrix.get(wiIndex));

				JoinedWay innerWay = polygons.get(wiIndex);
				if (containedPolygons.isEmpty()) {
					log.warn("Polygon",	innerWay, "carries role", getRole(innerWay),
						"but is not inside any outer polygon. Potentially it does not belong to this multipolygon.");
					logFakeWayDetails(Level.WARNING, innerWay);
				} else {
					log.warn("Polygon",	innerWay, "carries role", getRole(innerWay),
						"but is not inside any outer polygon. Potentially the roles are interchanged with the following",
						(containedPolygons.cardinality() > 1 ? "ways" : "way"), ".");

					for (int wrIndex = containedPolygons.nextSetBit(0); wrIndex >= 0; wrIndex = containedPolygons
							.nextSetBit(wrIndex + 1)) {
						logWayURLs(Level.WARNING, "-", polygons.get(wrIndex));
						unfinishedPolygons.set(wrIndex);
						wrongInnerPolygons.set(wrIndex);
					}
					logFakeWayDetails(Level.WARNING, innerWay);
				}

				unfinishedPolygons.clear(wiIndex);
				wrongInnerPolygons.clear(wiIndex);
			}
		}
	}

	protected void cleanup() {
		mpPolygons = null;
		roleMap.clear();
		containsMatrix = null;
		polygons = null;
		bboxArea = null;
		intersectingPolygons = null;
		outerWaysForLineTagging = null;
		outerTags = null;
		
		unfinishedPolygons = null;
		innerPolygons = null;
		taggedInnerPolygons = null;
		outerPolygons = null;
		taggedOuterPolygons = null;

	}

	private CutPoint calcNextCutPoint(AreaCutData areaData) {
		if (areaData.innerAreas == null || areaData.innerAreas.isEmpty()) {
			return null;
		}
		
		if (areaData.innerAreas.size() == 1) {
			// make it short if there is only one inner area
			Rectangle outerBounds = areaData.outerArea.getBounds();
			CoordinateAxis axis = (outerBounds.width < outerBounds.height ? CoordinateAxis.LONGITUDE : CoordinateAxis.LATITUDE);
			CutPoint oneCutPoint = new CutPoint(axis);
			oneCutPoint.addArea(areaData.innerAreas.get(0));
			return oneCutPoint;
		}
		
		ArrayList<Area> innerStart = new ArrayList<Area>(
				areaData.innerAreas);
		
		ArrayList<CutPoint> bestCutPoints = new ArrayList<CutPoint>(CoordinateAxis.values().length);
		
		for (CoordinateAxis axis : CoordinateAxis.values()) {
			CutPoint bestCutPoint = new CutPoint(axis);
			CutPoint currentCutPoint = new CutPoint(axis);

			Collections.sort(innerStart, (axis == CoordinateAxis.LONGITUDE ? COMP_LONG_START: COMP_LAT_START));

			for (Area anInnerStart : innerStart) {
				currentCutPoint.addArea(anInnerStart);

				if (currentCutPoint.compareTo(bestCutPoint) > 0) {
					bestCutPoint = currentCutPoint.duplicate();
				}
			}
			bestCutPoints.add(bestCutPoint);
		}

		return Collections.max(bestCutPoints);
		
	}

	/**
	 * Cut out all inner polygons from the outer polygon. This will divide the outer
	 * polygon in several polygons.
	 * 
	 * @param outerPolygon
	 *            the outer polygon
	 * @param innerPolygons
	 *            a list of inner polygons
	 * @return a list of polygons that make the outer polygon cut by the inner
	 *         polygons
	 */
	private List<Way> cutOutInnerPolygons(Way outerPolygon, List<Way> innerPolygons) {
		if (innerPolygons.isEmpty()) {
			Way outerWay = new JoinedWay(outerPolygon);
			if (log.isDebugEnabled()) {
				log.debug("Way", outerPolygon.getId(), "splitted to way", outerWay.getId());
			}
			return Collections.singletonList(outerWay);
		}

		// use the java.awt.geom.Area class because it's a quick
		// implementation of what's needed

		// this list contains all non overlapping and singular areas
		// of the outerPolygon
		Queue<AreaCutData> areasToCut = new LinkedList<AreaCutData>();
		Collection<Area> finishedAreas = new ArrayList<Area>(innerPolygons.size());
		
		// create a list of Area objects from the outerPolygon (clipped to the bounding box)
		List<Area> outerAreas = createAreas(outerPolygon, true);
		
		// create the inner areas
		List<Area> innerAreas = new ArrayList<Area>(innerPolygons.size()+2);
		for (Way innerPolygon : innerPolygons) {
			// don't need to clip to the bounding box because 
			// these polygons are just used to cut out holes
			innerAreas.addAll(createAreas(innerPolygon, false));
		}

		// initialize the cut data queue
		if (innerAreas.isEmpty()) {
			// this is a multipolygon without any inner areas
			// nothing to cut
			finishedAreas.addAll(outerAreas);
		} else if (outerAreas.size() == 1) {
			// there is one outer area only
			// it is checked before that all inner areas are inside this outer area
			AreaCutData initialCutData = new AreaCutData();
			initialCutData.outerArea = outerAreas.get(0);
			initialCutData.innerAreas = innerAreas;
			areasToCut.add(initialCutData);
		} else {
			// multiple outer areas
			for (Area outerArea : outerAreas) {
				AreaCutData initialCutData = new AreaCutData();
				initialCutData.outerArea = outerArea;
				initialCutData.innerAreas = new ArrayList<Area>(innerAreas
						.size());
				for (Area innerArea : innerAreas) {
					if (outerArea.getBounds().intersects(
						innerArea.getBounds())) {
						initialCutData.innerAreas.add(innerArea);
					}
				}
				
				if (initialCutData.innerAreas.isEmpty()) {
					// this is either an error
					// or the outer area has been cut into pieces on the tile bounds
					finishedAreas.add(outerArea);
				} else {
					areasToCut.add(initialCutData);
				}
			}
		}

		while (!areasToCut.isEmpty()) {
			AreaCutData areaCutData = areasToCut.poll();
			CutPoint cutPoint = calcNextCutPoint(areaCutData);
			
			if (cutPoint == null) {
				finishedAreas.add(areaCutData.outerArea);
				continue;
			}
			
			assert cutPoint.getNumberOfAreas() > 0 : "Number of cut areas == 0 in mp "+getId();
			
			// cut out the holes
			for (Area cutArea : cutPoint.getAreas()) {
				areaCutData.outerArea.subtract(cutArea);
			}
			
			if (areaCutData.outerArea.isEmpty()) {
				// this outer area space can be abandoned
				continue;
			} 
			
			// the inner areas of the cut point have been processed
			// they are no longer needed
			for (Area cutArea : cutPoint.getAreas()) {
				ListIterator<Area> areaIter = areaCutData.innerAreas.listIterator();
				while (areaIter.hasNext()) {
					Area a = areaIter.next();
					if (a == cutArea) {
						areaIter.remove();
						break;
					}
				}
			}
			// remove all does not seem to work. It removes more than the identical areas.
//			areaCutData.innerAreas.removeAll(cutPoint.getAreas());

			if (areaCutData.outerArea.isSingular()) {
				// the area is singular
				// => no further splits necessary
				if (areaCutData.innerAreas.isEmpty()) {
					// this area is finished and needs no further cutting
					finishedAreas.add(areaCutData.outerArea);
				} else {
					// read this area to further processing
					areasToCut.add(areaCutData);
				}
			} else {
				// we need to cut the area into two halves to get singular areas
				Rectangle r1 = cutPoint.getCutRectangleForArea(areaCutData.outerArea, true);
				Rectangle r2 = cutPoint.getCutRectangleForArea(areaCutData.outerArea, false);

				// Now find the intersection of these two boxes with the
				// original polygon. This will make two new areas, and each
				// area will be one (or more) polygons.
				Area a1 = areaCutData.outerArea;
				Area a2 = (Area) a1.clone();
				a1.intersect(new Area(r1));
				a2.intersect(new Area(r2));

				if (areaCutData.innerAreas.isEmpty()) {
					finishedAreas.addAll(Java2DConverter.areaToSingularAreas(a1));
					finishedAreas.addAll(Java2DConverter.areaToSingularAreas(a2));
				} else {
					ArrayList<Area> cuttedAreas = new ArrayList<Area>();
					cuttedAreas.addAll(Java2DConverter.areaToSingularAreas(a1));
					cuttedAreas.addAll(Java2DConverter.areaToSingularAreas(a2));
					
					for (Area nextOuterArea : cuttedAreas) {
						ArrayList<Area> nextInnerAreas = null;
						// go through all remaining inner areas and check if they
						// must be further processed with the nextOuterArea 
						for (Area nonProcessedInner : areaCutData.innerAreas) {
							if (nextOuterArea.intersects(nonProcessedInner.getBounds2D())) {
								if (nextInnerAreas == null) {
									nextInnerAreas = new ArrayList<Area>();
								}
								nextInnerAreas.add(nonProcessedInner);
							}
						}
						
						if (nextInnerAreas == null || nextInnerAreas.isEmpty()) {
							finishedAreas.add(nextOuterArea);
						} else {
							AreaCutData outCutData = new AreaCutData();
							outCutData.outerArea = nextOuterArea;
							outCutData.innerAreas= nextInnerAreas;
							areasToCut.add(outCutData);
						}
					}
				}
			}
			
		}
		
		// convert the java.awt.geom.Area back to the mkgmap way
		List<Way> cuttedOuterPolygon = new ArrayList<Way>(finishedAreas.size());
		for (Area area : finishedAreas) {
			Way w = singularAreaToWay(area, FakeIdGenerator.makeFakeId());
			if (w != null) {
				w.copyTags(outerPolygon);
				cuttedOuterPolygon.add(w);
				if (log.isDebugEnabled()) {
					log.debug("Way", outerPolygon.getId(), "splitted to way", w.getId());
				}
			}
		}

		return cuttedOuterPolygon;
	}

	/**
	 * Create the areas that are enclosed by the way. Usually the result should
	 * only be one area but some ways contain intersecting lines. To handle these
	 * erroneous cases properly the method might return a list of areas.
	 * 
	 * @param w a closed way
	 * @param clipBbox true if the areas should be clipped to the bounding box; false else
	 * @return a list of enclosed ares
	 */
	private List<Area> createAreas(Way w, boolean clipBbox) {
		Area area = Java2DConverter.createArea(w.getPoints());
		if (clipBbox && !bboxArea.contains(area.getBounds())) {
			// the area intersects the bounding box => clip it
			area.intersect(bboxArea);
		}
		List<Area> areaList = Java2DConverter.areaToSingularAreas(area);
		if (log.isDebugEnabled()) {
			log.debug("Bbox clipped way",w.getId()+"=>",areaList.size(),"distinct area(s).");
		}
		return areaList;
	}

	/**
	 * Convert an area to an mkgmap way. The caller must ensure that the area is singular.
	 * Otherwise only the first part of the area is converted.
	 * 
	 * @param area
	 *            the area
	 * @param wayId
	 *            the wayid for the new way
	 * @return a new mkgmap way
	 */
	private Way singularAreaToWay(Area area, long wayId) {
		List<Coord> points = Java2DConverter.singularAreaToPoints(area);
		if (points == null || points.isEmpty()) {
			if (log.isDebugEnabled()) {
				log.debug("Empty area", wayId + ".", toBrowseURL());
			}
			return null;
		}

		return new Way(wayId, points);
	}

	protected boolean hasTags(Element element) {
		for (Map.Entry<String, String> tagEntry : element.getEntryIteratable()) {
			if ("type".equals(tagEntry.getKey()) == false) {
				// return true if there is more than one tag other than "type"
				return true;
			}
		}
		return false;
	}
	
	private boolean hasStyleRelevantTags(Element element) {
		for (Map.Entry<String, String> tagEntry : element.getEntryIteratable()) {
			String tagName = tagEntry.getKey();
			boolean isStyleRelevant = tagName.equals("type") == false
					&& tagName.startsWith("name") == false;
			if (isStyleRelevant) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Creates a matrix which polygon contains which polygon. A polygon does not
	 * contain itself.
	 * 
	 * @param polygonList
	 *            a list of polygons
	 */
	protected void createContainsMatrix(List<JoinedWay> polygonList) {
		containsMatrix = new ArrayList<BitSet>();
		for (int i = 0; i < polygonList.size(); i++) {
			containsMatrix.add(new BitSet());
		}

		long t1 = System.currentTimeMillis();

		if (log.isDebugEnabled())
			log.debug("createContainsMatrix listSize:", polygonList.size());

		// use this matrix to check which matrix element has been
		// calculated
		ArrayList<BitSet> finishedMatrix = new ArrayList<BitSet>(polygonList
				.size());

		for (int i = 0; i < polygonList.size(); i++) {
			BitSet matrixRow = new BitSet();
			// a polygon does not contain itself
			matrixRow.set(i);
			finishedMatrix.add(matrixRow);
		}

		for (int rowIndex = 0; rowIndex < polygonList.size(); rowIndex++) {
			JoinedWay potentialOuterPolygon = polygonList.get(rowIndex);
			BitSet containsColumns = containsMatrix.get(rowIndex);
			BitSet finishedCol = finishedMatrix.get(rowIndex);

			// get all non calculated columns of the matrix
			for (int colIndex = finishedCol.nextClearBit(0); colIndex >= 0
					&& colIndex < polygonList.size(); colIndex = finishedCol
					.nextClearBit(colIndex + 1)) {

				JoinedWay innerPolygon = polygonList.get(colIndex);

				if (potentialOuterPolygon.getBounds().intersects(
						innerPolygon.getBounds()))
				{
					boolean contains = contains(potentialOuterPolygon,
							innerPolygon);

					if (contains) {
						containsColumns.set(colIndex);

						// we also know that the inner polygon does not contain the
						// outer polygon
						// so we can set the finished bit for this matrix
						// element
						finishedMatrix.get(colIndex).set(rowIndex);

						// additionally we know that the outer polygon contains all
						// polygons that are contained by the inner polygon
						containsColumns.or(containsMatrix.get(colIndex));
						finishedCol.or(containsColumns);
					}
				} else {
					// both polygons do not intersect
					// we can flag both matrix elements as finished
					finishedMatrix.get(colIndex).set(rowIndex);
					finishedMatrix.get(rowIndex).set(colIndex);
				}
				// this matrix element is calculated now
				finishedCol.set(colIndex);
			}
		}

		if (log.isDebugEnabled()) {
			long t2 = System.currentTimeMillis();
			log.debug("createMatrix for", polygonList.size(), "polygons took",
				(t2 - t1), "ms");

			log.debug("Containsmatrix:");
			int i = 0;
			boolean noContained = true;
			for (BitSet b : containsMatrix) {
				if (b.isEmpty()==false) {
					log.debug(i,"contains",b);
					noContained = false;
				}
				i++;
			}
			if (noContained) {
				log.debug("Matrix is empty");
			}
		}
	}

	/**
	 * Checks if the polygon with polygonIndex1 contains the polygon with polygonIndex2.
	 * 
	 * @return true if polygon(polygonIndex1) contains polygon(polygonIndex2)
	 */
	private boolean contains(int polygonIndex1, int polygonIndex2) {
		return containsMatrix.get(polygonIndex1).get(polygonIndex2);
	}

	/**
	 * Checks if polygon1 contains polygon2.
	 * 
	 * @param polygon1
	 *            a closed way
	 * @param polygon2
	 *            a 2nd closed way
	 * @return true if polygon1 contains polygon2
	 */
	private boolean contains(JoinedWay polygon1, JoinedWay polygon2) {
		if (!polygon1.isClosed()) {
			return false;
		}
		// check if the bounds of polygon2 are completely inside/enclosed the bounds
		// of polygon1
		if (!polygon1.getBounds().contains(polygon2.getBounds())) {
			return false;
		}

		Polygon p = Java2DConverter.createPolygon(polygon1.getPoints());
		// check first if one point of polygon2 is in polygon1

		// ignore intersections outside the bounding box
		// so it is necessary to check if there is at least one
		// point of polygon2 in polygon1 ignoring all points outside the bounding box
		boolean onePointContained = false;
		boolean allOnLine = true;
		for (Coord px : polygon2.getPoints()) {
			if (p.contains(px.getLongitude(), px.getLatitude())) {
				// there's one point that is in polygon1 and in the bounding
				// box => polygon1 may contain polygon2
				onePointContained = true;
				if (!locatedOnLine(px, polygon1.getPoints())) {
					allOnLine = false;
					break;
				}
			} else if (bbox.contains(px)) {
				// we have to check if the point is on one line of the polygon1
				
				if (!locatedOnLine(px, polygon1.getPoints())) {
					// there's one point that is not in polygon1 but inside the
					// bounding box => polygon1 does not contain polygon2
					//allOnLine = false;
					return false;
				}
			}
		}
		
		if (allOnLine) {
			onePointContained = false;
			// all points of polygon2 lie on lines of polygon1
			// => the middle of each line polygon must NOT lie outside polygon1
			ArrayList<Coord> middlePoints2 = new ArrayList<Coord>(polygon2.getPoints().size());
			Coord p1 = null;
			for (Coord p2 : polygon2.getPoints()) {
				if (p1 != null) {
					int mLat = p1.getLatitude()+(int)Math.round((p2.getLatitude()-p1.getLatitude())/2d);
					int mLong = p1.getLongitude()+(int)Math.round((p2.getLongitude()-p1.getLongitude())/2d);
					Coord pm = new Coord(mLat, mLong);
					middlePoints2.add(pm);
				}
				p1 = p2;
			}
			
			for (Coord px : middlePoints2) {
				if (p.contains(px.getLongitude(), px.getLatitude())) {
					// there's one point that is in polygon1 and in the bounding
					// box => polygon1 may contain polygon2
					onePointContained = true;
					break;
				} else if (bbox.contains(px)) {
					// we have to check if the point is on one line of the polygon1
					
					if (!locatedOnLine(px, polygon1.getPoints())) {
						// there's one point that is not in polygon1 but inside the
						// bounding box => polygon1 does not contain polygon2
						return false;
					} 
				}
			}			
		}

		if (!onePointContained) {
			// no point of polygon2 is in polygon1 => polygon1 does not contain polygon2
			return false;
		}
		
		Iterator<Coord> it1 = polygon1.getPoints().iterator();
		Coord p1_1 = it1.next();

		while (it1.hasNext()) {
			Coord p1_2 = p1_1;
			p1_1 = it1.next();

			if (!polygon2.linePossiblyIntersectsWay(p1_1, p1_2)) {
				// don't check it - this segment of the outer polygon
				// definitely does not intersect the way
				continue;
			}

			int lonMin = Math.min(p1_1.getLongitude(), p1_2.getLongitude());
			int lonMax = Math.max(p1_1.getLongitude(), p1_2.getLongitude());
			int latMin = Math.min(p1_1.getLatitude(), p1_2.getLatitude());
			int latMax = Math.max(p1_1.getLatitude(), p1_2.getLatitude());

			// check all lines of way1 and way2 for intersections
			Iterator<Coord> it2 = polygon2.getPoints().iterator();
			Coord p2_1 = it2.next();

			// for speedup we divide the area around the second line into
			// a 3x3 matrix with lon(-1,0,1) and lat(-1,0,1).
			// -1 means below min lon/lat of bbox line p1_1-p1_2
			// 0 means inside the bounding box of the line p1_1-p1_2
			// 1 means above max lon/lat of bbox line p1_1-p1_2
			int lonField = p2_1.getLongitude() < lonMin ? -1 : p2_1
					.getLongitude() > lonMax ? 1 : 0;
			int latField = p2_1.getLatitude() < latMin ? -1 : p2_1
					.getLatitude() > latMax ? 1 : 0;

			int prevLonField = lonField;
			int prevLatField = latField;

			while (it2.hasNext()) {
				Coord p2_2 = p2_1;
				p2_1 = it2.next();

				int changes = 0;
				// check if the field of the 3x3 matrix has changed
				if ((lonField >= 0 && p1_1.getLongitude() < lonMin)
						|| (lonField <= 0 && p1_1.getLongitude() > lonMax)) {
					changes++;
					lonField = p1_1.getLongitude() < lonMin ? -1 : p1_1
							.getLongitude() > lonMax ? 1 : 0;
				}
				if ((latField >= 0 && p1_1.getLatitude() < latMin)
						|| (latField <= 0 && p1_1.getLatitude() > latMax)) {
					changes++;
					latField = p1_1.getLatitude() < latMin ? -1 : p1_1
							.getLatitude() > latMax ? 1 : 0;
				}

				// an intersection is possible if
				// latField and lonField has changed
				// or if we come from or go to the inner matrix field
				boolean intersectionPossible = (changes == 2)
						|| (latField == 0 && lonField == 0)
						|| (prevLatField == 0 && prevLonField == 0);

				boolean intersects = intersectionPossible
					&& linesCutEachOther(p1_1, p1_2, p2_1, p2_2);
				
				if (intersects) {
					if ((polygon1.isClosedArtificially() && !it1.hasNext())
							|| (polygon2.isClosedArtificially() && !it2.hasNext())) {
						// don't care about this intersection
						// one of the polygons is closed by this mp code and the
						// closing segment causes the intersection
						log.info("Polygon", polygon1, "may contain polygon", polygon2,
							". Ignoring artificial generated intersection.");
					} else if ((!bbox.contains(p1_1))
							|| (!bbox.contains(p1_2))
							|| (!bbox.contains(p2_1))
							|| (!bbox.contains(p2_2))) {
						// at least one point is outside the bounding box
						// we ignore the intersection because the ways may not
						// be complete
						// due to removals of the tile splitter or osmosis
						log.info("Polygon", polygon1, "may contain polygon", polygon2,
							". Ignoring because at least one point is outside the bounding box.");
					} else {
						// store them in the intersection polygons set
						// the error message will be printed out in the end of
						// the mp handling
						intersectingPolygons.add(polygon1);
						intersectingPolygons.add(polygon2);
						return false;
					}
				}

				prevLonField = lonField;
				prevLatField = latField;
			}
		}

		// don't have any intersection
		// => polygon1 contains polygon2
		return true;
	}

	/**
	 * Checks if the point p is located on one line of the given points.
	 * @param p a point
	 * @param points a list of points; all consecutive points are handled as lines
	 * @return true if p is located on one line given by points
	 */
	private boolean locatedOnLine(Coord p, List<Coord> points) {
		Coord cp1 = null;
		for (Coord cp2 : points) {
			if (p.equals(cp2)) {
				return true;
			}

			try {
				if (cp1 == null) {
					// first init
					continue;
				}

				if (p.getLongitude() < Math.min(cp1.getLongitude(), cp2
						.getLongitude())) {
					continue;
				}
				if (p.getLongitude() > Math.max(cp1.getLongitude(), cp2
						.getLongitude())) {
					continue;
				}
				if (p.getLatitude() < Math.min(cp1.getLatitude(), cp2
						.getLatitude())) {
					continue;
				}
				if (p.getLatitude() > Math.max(cp1.getLatitude(), cp2
						.getLatitude())) {
					continue;
				}

				double dist = Line2D.ptSegDistSq(cp1.getLongitude(), cp1
						.getLatitude(), cp2.getLongitude(), cp2.getLatitude(),
					p.getLongitude(), p.getLatitude());

				if (dist <= OVERLAP_TOLERANCE_DISTANCE) {
					log.debug("Point", p, "is located on line between", cp1, "and",
						cp2, ". Distance:", dist);
					return true;
				}
			} finally {
				cp1 = cp2;
			}
		}
		return false;
	}

	private boolean lineCutsBbox(Coord p1_1, Coord p1_2) {
		Coord nw = new Coord(bbox.getMaxLat(), bbox.getMinLong());
		Coord sw = new Coord(bbox.getMinLat(), bbox.getMinLong());
		Coord se = new Coord(bbox.getMinLat(), bbox.getMaxLong());
		Coord ne = new Coord(bbox.getMaxLat(), bbox.getMaxLong());
		return linesCutEachOther(nw, sw, p1_1, p1_2)
				|| linesCutEachOther(sw, se, p1_1, p1_2)
				|| linesCutEachOther(se, ne, p1_1, p1_2)
				|| linesCutEachOther(ne, nw, p1_1, p1_2);
	}

	/**
	 * Check if the line p1_1 to p1_2 cuts line p2_1 to p2_2 in two pieces and vice versa.
	 * This is a form of intersection check where it is allowed that one line ends on the
	 * other line or that the two lines overlap.
	 * @param p1_1 first point of line 1
	 * @param p1_2 second point of line 1
	 * @param p2_1 first point of line 2
	 * @param p2_2 second point of line 2
	 * @return true if both lines intersect somewhere in the middle of each other
	 */
	private boolean linesCutEachOther(Coord p1_1, Coord p1_2, Coord p2_1,
			Coord p2_2) {
		int width1 = p1_2.getLongitude() - p1_1.getLongitude();
		int width2 = p2_2.getLongitude() - p2_1.getLongitude();

		int height1 = p1_2.getLatitude() - p1_1.getLatitude();
		int height2 = p2_2.getLatitude() - p2_1.getLatitude();

		int denominator = ((height2 * width1) - (width2 * height1));
		if (denominator == 0) {
			// the lines are parallel
			// they might overlap but this is ok for this test
			return false;
		}
		
		int x1Mx3 = p1_1.getLongitude() - p2_1.getLongitude();
		int y1My3 = p1_1.getLatitude() - p2_1.getLatitude();

		double isx = (double)((width2 * y1My3) - (height2 * x1Mx3))
				/ denominator;
		if (isx < 0 || isx > 1) {
			return false;
		}
		
		double isy = (double)((width1 * y1My3) - (height1 * x1Mx3))
				/ denominator;

		if (isy < 0 || isy > 1) {
			return false;
		} 

		return true;
	}

	private List<JoinedWay> getWaysFromPolygonList(BitSet selection) {
		if (selection.isEmpty()) {
			return Collections.emptyList();
		}
		List<JoinedWay> wayList = new ArrayList<JoinedWay>(selection
				.cardinality());
		for (int i = selection.nextSetBit(0); i >= 0; i = selection.nextSetBit(i + 1)) {
			wayList.add(polygons.get(i));
		}
		return wayList;
	}

	private void logWayURLs(Level level, String preMsg, Way way) {
		if (log.isLoggable(level)) {
			if (way instanceof JoinedWay) {
				if (((JoinedWay) way).getOriginalWays().isEmpty()) {
					log.warn("Way", way, "does not contain any original ways");
				}
				for (Way segment : ((JoinedWay) way).getOriginalWays()) {
					if (preMsg == null || preMsg.length() == 0) {
						log.log(level, segment.toBrowseURL());
					} else {
						log.log(level, preMsg, segment.toBrowseURL());
					}
				}
			} else {
				if (preMsg == null || preMsg.length() == 0) {
					log.log(level, way.toBrowseURL());
				} else {
					log.log(level, preMsg, way.toBrowseURL());
				}
			}
		}
	}
	
	/**
	 * Logs the details of the original ways of a way with a fake id. This is
	 * primarily necessary for the sea multipolygon because it consists of 
	 * faked ways only. In this case logging messages can be improved by the
	 * start and end points of the faked ways.
	 * @param logLevel the logging level
	 * @param fakeWay a way composed by other ways with faked ids
	 */
	private void logFakeWayDetails(Level logLevel, JoinedWay fakeWay) {
		if (log.isLoggable(logLevel) == false) {
			return;
		}
		
		// only log if this is an artificial multipolygon
		if (FakeIdGenerator.isFakeId(getId()) == false) {
			return;
		}
		
		boolean containsOrgFakeWay = false;
		for (Way orgWay : fakeWay.getOriginalWays()) {
			if (FakeIdGenerator.isFakeId(orgWay.getId())) {
				containsOrgFakeWay = true;
			}
		}
		
		if (containsOrgFakeWay == false) {
			return;
		}
		
		// the fakeWay consists only of other faked ways
		// there should be more information about these ways
		// so that it is possible to retrieve the original
		// OSM ways
		// => log the start and end points
		
		for (Way orgWay : fakeWay.getOriginalWays()) {
			log.log(logLevel, " Way",orgWay.getId(),"is composed of other artificial ways. Details:");
			log.log(logLevel, "  Start:",orgWay.getPoints().get(0).toOSMURL());
			if (orgWay.isClosed()) {
				// the way is closed so start==end - log the point in the middle of the way
				int mid = orgWay.getPoints().size()/2;
				log.log(logLevel, "  Mid:  ",orgWay.getPoints().get(mid).toOSMURL());
			} else {
				log.log(logLevel, "  End:  ",orgWay.getPoints().get(orgWay.getPoints().size()-1).toOSMURL());
			}
		}		
	}

	protected void tagOuterWays() {
		Map<String, String> tags;
		if (hasTags(this)) {
			tags = new HashMap<String, String>();
			for (Entry<String, String> relTag : getEntryIteratable()) {
				tags.put(relTag.getKey(), relTag.getValue());
			}
		} else {
			tags = JoinedWay.getMergedTags(outerWaysForLineTagging);
		}
		
		
		// Go through all original outer ways, create a copy, tag them
		// with the mp tags and mark them only to be used for polyline processing
		// This enables the style file to decide if the polygon information or
		// the simple line information should be used.
		for (Way orgOuterWay : outerWaysForLineTagging) {
			Way lineTagWay =  new Way(FakeIdGenerator.makeFakeId(), orgOuterWay.getPoints());
			lineTagWay.setName(orgOuterWay.getName());
			lineTagWay.addTag(STYLE_FILTER_TAG, STYLE_FILTER_LINE);
			for (Entry<String,String> tag : tags.entrySet()) {
				lineTagWay.addTag(tag.getKey(), tag.getValue());
				
				// remove the tag from the original way if it has the same value
				if (tag.getValue().equals(orgOuterWay.getTag(tag.getKey()))) {
					removeTagsInOrgWays(orgOuterWay, tag.getKey());
				}
			}
			
			if (log.isDebugEnabled())
				log.debug("Add line way", lineTagWay.getId(), lineTagWay.toTagString());
			tileWayMap.put(lineTagWay.getId(), lineTagWay);
		}
	}
	
	
	/**
	 * Marks all tags of the original ways of the given JoinedWay that are also
	 * contained in the given tagElement for removal.
	 * 
	 * @param tagElement
	 *            an element contains the tags to be removed
	 * @param way
	 *            a joined way
	 */
	private void removeTagsInOrgWays(Element tagElement, JoinedWay way) {
		for (Entry<String, String> tag : tagElement.getEntryIteratable()) {
			removeTagInOrgWays(way, tag.getKey(), tag.getValue());
		}
	}

	/**
	 * Mark the given tag of all original ways of the given JoinedWay.
	 * 
	 * @param way
	 *            a joined way
	 * @param tagname
	 *            the tag to be removed (<code>null</code> means remove all
	 *            tags)
	 * @param tagvalue
	 *            the value of the tag to be removed (<code>null</code> means
	 *            don't check the value)
	 */
	private void removeTagInOrgWays(JoinedWay way, String tagname,
			String tagvalue) {
		for (Way w : way.getOriginalWays()) {
			if (w instanceof JoinedWay) {
				// remove the tags recursively
				removeTagInOrgWays((JoinedWay) w, tagname, tagvalue);
				continue;
			}

			boolean remove = false;
			if (tagname == null) {
				// remove all tags
				remove = true;
			} else if (tagvalue == null) {
				// remove the tag without comparing the value
				remove = w.getTag(tagname) != null;
			} else if (tagvalue.equals(w.getTag(tagname))) {
				remove = true;
			}

			if (remove) {
				if (tagname == null) {
					// remove all tags
					if (log.isDebugEnabled())
						log.debug("Will remove all tags from", w.getId(), w
								.toTagString());
					removeTagsInOrgWays(w, tagname);
				} else {
					if (log.isDebugEnabled())
						log.debug("Will remove", tagname + "="
								+ w.getTag(tagname), "from way", w.getId(), w
								.toTagString());
					removeTagsInOrgWays(w, tagname);
				}
			}
		}
	}
	
	protected void removeTagsInOrgWays(Way way, String tag) {
		if (tag == null) {
			way.addTag(ElementSaver.MKGMAP_REMOVE_TAG, ElementSaver.MKGMAP_REMOVE_TAG_ALL_KEY);
			return;
		}
		if (tag.isEmpty()) {
			return;
		}
		String removedTagsTag = way.getTag(ElementSaver.MKGMAP_REMOVE_TAG);
		if (ElementSaver.MKGMAP_REMOVE_TAG_ALL_KEY.equals(removedTagsTag)) {
			// cannot add more tags to remove
			return;
		}

		if (removedTagsTag == null) {
			way.addTag(ElementSaver.MKGMAP_REMOVE_TAG, tag);
		} else if (removedTagsTag.equals(tag) == false) {
			way.addTag(ElementSaver.MKGMAP_REMOVE_TAG, removedTagsTag+";"+tag);
		}
	}

	protected Map<Long, Way> getTileWayMap() {
		return tileWayMap;
	}

	protected Map<Long, Way> getMpPolygons() {
		return mpPolygons;
	}

	protected uk.me.parabola.imgfmt.app.Area getBbox() {
		return bbox;
	}
	
	/**
	 * This is a helper class that stores that gives access to the original
	 * segments of a joined way.
	 */
	public static final class JoinedWay extends Way {
		private final List<Way> originalWays;
		private boolean closedArtificially;

		private int minLat;
		private int maxLat;
		private int minLon;
		private int maxLon;
		private Rectangle bounds;

		public JoinedWay(Way originalWay) {
			super(FakeIdGenerator.makeFakeId(), new ArrayList<Coord>(
					originalWay.getPoints()));
			this.originalWays = new ArrayList<Way>();
			addWay(originalWay);

			// we have to initialize the min/max values
			Coord c0 = originalWay.getPoints().get(0);
			minLat = maxLat = c0.getLatitude();
			minLon = maxLon = c0.getLongitude();

			updateBounds(originalWay.getPoints());
		}

		public void addPoint(int index, Coord point) {
			getPoints().add(index, point);
			updateBounds(point);
		}

		public void addPoint(Coord point) {
			super.addPoint(point);
			updateBounds(point);
		}

		private void updateBounds(List<Coord> pointList) {
			for (Coord c : pointList) {
				updateBounds(c);
			}
		}

		private void updateBounds(Coord point) {
			if (point.getLatitude() < minLat) {
				minLat = point.getLatitude();
				bounds = null;
			} else if (point.getLatitude() > maxLat) {
				maxLat = point.getLatitude();
				bounds = null;
			}

			if (point.getLongitude() < minLon) {
				minLon = point.getLongitude();
				bounds = null;
			} else if (point.getLongitude() > maxLon) {
				maxLon = point.getLongitude();
				bounds = null;
			}

		}
		
		/**
		 * Checks if this way intersects the given bounding box at least with
		 * one point.
		 * 
		 * @param bbox
		 *            the bounding box
		 * @return <code>true</code> if this way intersects or touches the
		 *         bounding box; <code>false</code> else
		 */
		public boolean intersects(uk.me.parabola.imgfmt.app.Area bbox) {
			return (maxLat >= bbox.getMinLat() && 
					minLat <= bbox.getMaxLat() && 
					maxLon >= bbox.getMinLong() && 
					minLon <= bbox.getMaxLong());
		}

		public Rectangle getBounds() {
			if (bounds == null) {
				// note that we increase the rectangle by 1 because intersects
				// checks
				// only the interior
				bounds = new Rectangle(minLon - 1, minLat - 1, maxLon - minLon
						+ 2, maxLat - minLat + 2);
			}

			return bounds;
		}

		public boolean linePossiblyIntersectsWay(Coord p1, Coord p2) {
			return getBounds().intersectsLine(p1.getLongitude(),
					p1.getLatitude(), p2.getLongitude(), p2.getLatitude());
		}

		public void addWay(Way way) {
			if (way instanceof JoinedWay) {
				for (Way w : ((JoinedWay) way).getOriginalWays()) {
					addWay(w);
				}
			} else {
				if (log.isDebugEnabled()) {
					log.debug("Joined", this.getId(), "with", way.getId());
				}
				this.originalWays.add(way);
			}
		}

		public void closeWayArtificially() {
			addPoint(getPoints().get(0));
			closedArtificially = true;
		}

		public boolean isClosedArtificially() {
			return closedArtificially;
		}

		public static Map<String,String> getMergedTags(Collection<Way> ways) {
			Map<String,String> mergedTags = new HashMap<String, String>();
			boolean first = true;
			for (Way way : ways) {
				if (first) {
					// the tags of the first way are copied completely 
					for (Map.Entry<String, String> tag : way.getEntryIteratable()) {
						mergedTags.put(tag.getKey(), tag.getValue());
					}
					first = false;
				} else {
					// for all other ways all non matching tags are removed
					ArrayList<String> tagsToRemove = null;
					for (Map.Entry<String, String> tag : mergedTags.entrySet()) {
						String wayTagValue = way.getTag(tag.getKey());
						if (!tag.getValue().equals(wayTagValue)) {
							// the tags are different
							if (wayTagValue!= null) {
								if (tagsToRemove == null) {
									tagsToRemove=new ArrayList<String>();
								}
								tagsToRemove.add(tag.getKey());
							}
						}
					}
					if (tagsToRemove!=null) {
						for (String tag : tagsToRemove) {
							mergedTags.remove(tag);
						}
					}
				}
			}
			return mergedTags;
		}
		
		/**
		 * Tags this way with a merge of the tags of all original ways.
		 */
		public void mergeTagsFromOrgWays() {
			if (log.isDebugEnabled()) {
				log.debug("Way",getId(),"merge tags from",getOriginalWays().size(),"ways");
			}
			removeAllTags();
			
			Map<String,String> mergedTags = getMergedTags(getOriginalWays());
			for (Entry<String,String> tag : mergedTags.entrySet()) {
				addTag(tag.getKey(),tag.getValue());
			}
		}

		public List<Way> getOriginalWays() {
			return originalWays;
		}

		public String toString() {
			StringBuilder sb = new StringBuilder(200);
			sb.append(getId());
			sb.append("(");
			sb.append(getPoints().size());
			sb.append("P)(");
			boolean first = true;
			for (Way w : getOriginalWays()) {
				if (first) {
					first = false;
				} else {
					sb.append(",");
				}
				sb.append(w.getId());
				sb.append("[");
				sb.append(w.getPoints().size());
				sb.append("P]");
			}
			sb.append(")");
			return sb.toString();
		}
	}

	public static class PolygonStatus {
		public final boolean outer;
		public final int index;
		public final JoinedWay polygon;

		public PolygonStatus(boolean outer, int index, JoinedWay polygon) {
			this.outer = outer;
			this.index = index;
			this.polygon = polygon;
		}
		
		public String toString() {
			return polygon+"_"+outer;
		}
	}

	private static class AreaCutData {
		Area outerArea;
		List<Area> innerAreas;
	}

	private static class CutPoint implements Comparable<CutPoint>{
		int startPoint = Integer.MAX_VALUE;
		int stopPoint = Integer.MIN_VALUE;
		final LinkedList<Area> areas;
		private final Comparator<Area> comparator;
		private final CoordinateAxis axis;

		public CutPoint(CoordinateAxis axis) {
			this.axis = axis;
			this.areas = new LinkedList<Area>();
			this.comparator = (axis == CoordinateAxis.LONGITUDE ? COMP_LONG_STOP : COMP_LAT_STOP);
		}
		
		public CutPoint duplicate() {
			CutPoint newCutPoint = new CutPoint(this.axis);
			newCutPoint.areas.addAll(areas);
			newCutPoint.startPoint = startPoint;
			newCutPoint.stopPoint = stopPoint;
			return newCutPoint;
		}

		private boolean isGoodCutPoint() {
			// It is better if the cutting line is on a multiple of 2048. 
			// Otherwise MapSource and QLandkarteGT paints gaps between the cuts
			return getCutPoint() % 2048 == 0;
		}
		
		public int getCutPoint() {
			int cutPoint = startPoint + (stopPoint - startPoint) / 2;
			
			// try to find a cut point that is a multiple of 2048 to 
			// avoid that gaps are painted by MapSource and QLandkarteGT
			// between the cutting lines
			int cutMod = cutPoint % 2048;
			if (cutMod == 0) {
				return cutPoint;
			}
			
			int cut1 = (cutMod > 0 ? cutPoint-cutMod : cutPoint  - 2048- cutMod);
			if (cut1 >= startPoint && cut1 <= stopPoint) {
				return cut1;
			}
			
			int cut2 = (cutMod > 0 ? cutPoint + 2048 -cutMod : cutPoint - cutMod);
			if (cut2 >= startPoint && cut2 <= stopPoint) {
				return cut2;
			}
			
			return cutPoint;
		}

		public Rectangle getCutRectangleForArea(Area toCut, boolean firstRect) {
			Rectangle areaRect = toCut.getBounds();
			if (axis == CoordinateAxis.LONGITUDE) {
				int newWidth = getCutPoint()-areaRect.x;
				if (firstRect) {
					return new Rectangle(areaRect.x, areaRect.y, newWidth, areaRect.height); 
				} else {
					return new Rectangle(areaRect.x+newWidth, areaRect.y, areaRect.width-newWidth, areaRect.height); 
				}
			} else {
				int newHeight = getCutPoint()-areaRect.y;
				if (firstRect) {
					return new Rectangle(areaRect.x, areaRect.y, areaRect.width, newHeight); 
				} else {
					return new Rectangle(areaRect.x, areaRect.y+newHeight, areaRect.width, areaRect.height-newHeight); 
				}
			}
		}
		
		public Collection<Area> getAreas() {
			return areas;
		}

		public void addArea(Area area) {
			// remove all areas that do not overlap with the new area
			while (!areas.isEmpty() && axis.getStop(areas.getFirst()) < axis.getStart(area)) {
				// remove the first area
				areas.removeFirst();
			}

			areas.add(area);
			Collections.sort(areas, comparator);
			startPoint = axis.getStart(Collections.max(areas,
				(axis == CoordinateAxis.LONGITUDE ? COMP_LONG_START
						: COMP_LAT_START)));
			stopPoint = axis.getStop(areas.getFirst());
		}

		public int getNumberOfAreas() {
			return this.areas.size();
		}

		public int compareTo(CutPoint o) {
			if (this == o) {
				return 0;
			}
			
			if (isGoodCutPoint() != o.isGoodCutPoint()) {
				if (isGoodCutPoint())
					return 1;
				else
					return -1;
			}
			
			int ndiff = getNumberOfAreas()-o.getNumberOfAreas();
			if (ndiff != 0) {
				return ndiff;
			}
			// prefer the larger area that is split
			return (stopPoint-startPoint)-(o.stopPoint-o.startPoint); 
		}

		public String toString() {
			return axis +" "+getNumberOfAreas()+" "+startPoint+" "+stopPoint+" "+getCutPoint();
		}
		
	}

	private static enum CoordinateAxis {
		LATITUDE(false), LONGITUDE(true);

		private CoordinateAxis(boolean useX) {
			this.useX = useX;
		}

		private final boolean useX;

		public int getStart(Area area) {
			return getStart(area.getBounds());
		}

		public int getStart(Rectangle rect) {
			return (useX ? rect.x : rect.y);
		}

		public int getStop(Area area) {
			return getStop(area.getBounds());
		}

		public int getStop(Rectangle rect) {
			return (useX ? rect.x + rect.width : rect.y + rect.height);
		}

	}

	private static final AreaComparator COMP_LONG_START = new AreaComparator(
			true, CoordinateAxis.LONGITUDE);
	private static final AreaComparator COMP_LONG_STOP = new AreaComparator(
			false, CoordinateAxis.LONGITUDE);
	private static final AreaComparator COMP_LAT_START = new AreaComparator(
			true, CoordinateAxis.LATITUDE);
	private static final AreaComparator COMP_LAT_STOP = new AreaComparator(
			false, CoordinateAxis.LATITUDE);

	private static class AreaComparator implements Comparator<Area> {

		private final CoordinateAxis axis;
		private final boolean startPoint;

		public AreaComparator(boolean startPoint, CoordinateAxis axis) {
			this.startPoint = startPoint;
			this.axis = axis;
		}

		public int compare(Area o1, Area o2) {
			if (o1 == o2) {
				return 0;
			}

			if (startPoint) {
				int cmp = axis.getStart(o1) - axis.getStart(o2);
				if (cmp == 0) {
					return axis.getStop(o1) - axis.getStop(o2);
				} else {
					return cmp;
				}
			} else {
				int cmp = axis.getStop(o1) - axis.getStop(o2);
				if (cmp == 0) {
					return axis.getStart(o1) - axis.getStart(o2);
				} else {
					return cmp;
				}
			}
		}

	}
}
