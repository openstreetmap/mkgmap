package uk.me.parabola.mkgmap.reader.osm;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;

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

	private final Map<Long, Way> tileWayMap;
	private final Map<Long, String> roleMap = new HashMap<Long, String>();

	private ArrayList<BitSet> containsMatrix;
	private ArrayList<JoinedWay> rings;

	private final uk.me.parabola.imgfmt.app.Area bbox;

	
	/**
	 * if one of these tags are contained in the multipolygon then the outer
	 * ways use the mp tags instead of their own tags.
	 */
	private static final List<String> polygonTags = Arrays.asList("boundary",
			"natural", "landuse", "land_area", "building", "waterway");

	/**
	 * Create an instance based on an existing relation. We need to do this
	 * because the type of the relation is not known until after all its tags
	 * are read in.
	 * 
	 * @param other
	 *            The relation to base this one on.
	 * @param wayMap
	 *            Map of all ways.
	 */
	public MultiPolygonRelation(Relation other, Map<Long, Way> wayMap, 
			uk.me.parabola.imgfmt.app.Area bbox) {
		this.tileWayMap = wayMap;
		this.bbox = bbox;

		setId(other.getId());

		if (log.isDebugEnabled()) {
			log.debug("Construct multipolygon", toBrowseURL());
		}

		for (Map.Entry<String, Element> pair : other.getElements()) {
			String role = pair.getKey();
			Element el = pair.getValue();
			if (log.isDebugEnabled()) {
				log.debug(" ", role, el.toBrowseURL());
			}
			addElement(role, el);
			roleMap.put(el.getId(), role);
		}

		setName(other.getName());
		copyTags(other);
	}

	/**
	 * Retrieves the mp role of the given element.
	 * 
	 * @param element
	 *            the element
	 * @return the role of the element
	 */
	private String getRole(Element element) {
		String role = roleMap.get(element.getId());
		if (role != null) {
			return role;
		}

		for (Map.Entry<String, Element> r_e : getElements()) {
			if (r_e.getValue() == element) {
				return r_e.getKey();
			}
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
			if (checkOnly == false) {
				for (Coord point : tempWay.getPoints().subList(1,
						tempWay.getPoints().size())) {
					joinWay.addPoint(0, point);
				}
				joinWay.addWay(tempWay);
			}
			return true;
		} else if (joinWay.getPoints().get(joinWay.getPoints().size() - 1) == tempWay
				.getPoints().get(0)) {
			if (checkOnly == false) {
				for (Coord point : tempWay.getPoints().subList(1,
						tempWay.getPoints().size())) {
					joinWay.addPoint(point);
				}
				joinWay.addWay(tempWay);
			}
			return true;
		} else if (joinWay.getPoints().get(0) == tempWay.getPoints().get(
				tempWay.getPoints().size() - 1)) {
			if (checkOnly == false) {
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
			if (checkOnly == false) {
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
	private ArrayList<JoinedWay> joinWays(List<Way> segments) {
		// TODO check if the closed polygon is valid and implement a
		// backtracking algorithm to get other combinations

		ArrayList<JoinedWay> joinedWays = new ArrayList<JoinedWay>();
		if (segments == null || segments.size() == 0) {
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

		while (unclosedWays.isEmpty() == false) {
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
				if (("outer".equals(joinRole) == false && "inner"
						.equals(joinRole) == false)
						|| ("outer".equals(tempRole) == false && "inner"
								.equals(tempRole) == false)
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

			if (joined == false && wrongRoleWay != null) {

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
	private void closeWays(ArrayList<JoinedWay> wayList) {
		// this is a VERY simple algorithm to close the ways
		// need to be improved

		for (JoinedWay way : wayList) {
			if (way.isClosed() || way.getPoints().size() <= 3) {
				continue;
			}
			Coord p1 = way.getPoints().get(0);
			Coord p2 = way.getPoints().get(way.getPoints().size() - 1);
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

			if (intersects == false) {
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

	/**
	 * Removes all ways non closed ways from the given list (
	 * <code>{@link Way#isClosed()} == false</code>)
	 * 
	 * @param wayList
	 *            list of ways
	 */
	private void removeUnclosedWays(ArrayList<JoinedWay> wayList) {
		Iterator<JoinedWay> it = wayList.iterator();
		boolean first = true;
		while (it.hasNext()) {
			JoinedWay tempWay = it.next();
			if (tempWay.isClosed() == false) {
				if (first) {
					log.warn(
						"Cannot join the following ways to closed polygons. MP-Relation",
						toBrowseURL());
				}
				logWayURLs(Level.WARNING, "- way:", tempWay);

				it.remove();
				first = false;
			}
		}
	}

	/**
	 * Removes all ways that are completely outside the bounding box. 
	 * This reduces error messages from problems on the tile bounds.
	 * @param wayList list of ways
	 */
	private void removeWaysOutsideBbox(ArrayList<JoinedWay> wayList) {
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
				if (log.isDebugEnabled()) {
					log.debug("Remove way", w.getId(),
						"because it is completely outside the bounding box.");
				}
				wayIter.remove();
			}
		}
	}
	
	/**
	 * Find all rings that are not contained by any other ring.
	 * 
	 * @param candidates
	 *            all rings that should be checked
	 * @param roleFilter
	 *            an additional filter
	 * @return all ring indexes that are not contained by any other ring
	 */
	private BitSet findOutmostRings(BitSet candidates, BitSet roleFilter) {
		BitSet realCandidates = ((BitSet) candidates.clone());
		realCandidates.and(roleFilter);
		return findOutmostRings(realCandidates);
	}

	/**
	 * Finds all rings that are not contained by any other rings and that match
	 * to the given role. All rings with index given by <var>candidates</var>
	 * are used.
	 * 
	 * @param candidates
	 *            indexes of the rings that should be used
	 * @return the bits of all outmost rings are set to true
	 */
	private BitSet findOutmostRings(BitSet candidates) {
		BitSet outmostRings = new BitSet();

		// go through all candidates and check if they are contained by any
		// other candidate
		for (int candidateIndex = candidates.nextSetBit(0); candidateIndex >= 0; candidateIndex = candidates
				.nextSetBit(candidateIndex + 1)) {
			// check if the candidateIndex ring is not contained by any
			// other candidate ring
			boolean isOutmost = true;
			for (int otherCandidateIndex = candidates.nextSetBit(0); otherCandidateIndex >= 0; otherCandidateIndex = candidates
					.nextSetBit(otherCandidateIndex + 1)) {
				if (contains(otherCandidateIndex, candidateIndex)) {
					// candidateIndex is not an outermost ring because it is
					// contained by
					// the otherCandidateIndex ring
					isOutmost = false;
					break;
				}
			}
			if (isOutmost) {
				// this is an outmost ring
				// put it to the bitset
				outmostRings.set(candidateIndex);
			}
		}

		return outmostRings;
	}

	private ArrayList<RingStatus> getRingStatus(BitSet outmostRings,
			boolean outer) {
		ArrayList<RingStatus> ringStatusList = new ArrayList<RingStatus>();
		for (int ringIndex = outmostRings.nextSetBit(0); ringIndex >= 0; ringIndex = outmostRings
				.nextSetBit(ringIndex + 1)) {
			// ringIndex is the ring that is not contained by any other
			// ring
			JoinedWay ring = rings.get(ringIndex);
			ringStatusList.add(new RingStatus(outer, ringIndex, ring));
		}
		return ringStatusList;
	}

	/**
	 * Process the ways in this relation. Joins way with the role "outer" Adds
	 * ways with the role "inner" to the way with the role "outer"
	 */
	public void processElements() {
		log.info("Processing multipolygon", toBrowseURL());

		// don't care about outer and inner declaration
		// because this is a first try
		ArrayList<Way> allWays = new ArrayList<Way>();

		for (Map.Entry<String, Element> r_e : getElements()) {
			if (r_e.getValue() instanceof Way) {
				allWays.add((Way) r_e.getValue());
			} else {
				log.warn("Non way element", r_e.getValue().getId(),
						"in multipolygon", getId());
			}
		}

		rings = joinWays(allWays);
		closeWays(rings);
		removeUnclosedWays(rings);
		// now we have closed ways == rings only

		// check if we have at least one ring left
		if (rings.isEmpty()) {
			// do nothing
			log.warn("Multipolygon " + toBrowseURL()
					+ " does not contain a closed polygon.");
			return;
		}

		removeWaysOutsideBbox(rings);

		if (rings.isEmpty()) {
			// do nothing
			log.info("Multipolygon " + toBrowseURL()
					+ " is completely outside the bounding box. It is not processed.");
			return;
		}
		
		createContainsMatrix(rings);

		BitSet unfinishedRings = new BitSet(rings.size());
		unfinishedRings.set(0, rings.size());

		// create bitsets which rings belong to the outer and to the inner role
		BitSet innerRings = new BitSet();
		BitSet outerRings = new BitSet();
		int wi = 0;
		for (Way w : rings) {
			String role = getRole(w);
			if ("inner".equals(role)) {
				innerRings.set(wi);
			} else if ("outer".equals(role)) {
				outerRings.set(wi);
			} else {
				// unknown role => it could be both
				innerRings.set(wi);
				outerRings.set(wi);
			}
			wi++;
		}

		Queue<RingStatus> ringWorkingQueue = new LinkedBlockingQueue<RingStatus>();

		BitSet outmostRings = findOutmostRings(unfinishedRings, outerRings);
		if (outmostRings.isEmpty()) {
			// there's no outmost outer ring
			// maybe this is a tile problem
			// try to continue with the inner ring
			outmostRings = findOutmostRings(unfinishedRings, innerRings);
			ringWorkingQueue.addAll(getRingStatus(outmostRings, false));
		} else {
			ringWorkingQueue.addAll(getRingStatus(outmostRings, true));
		}

		while (ringWorkingQueue.isEmpty() == false) {

			// the ring is not contained by any other unfinished ring
			RingStatus currentRing = ringWorkingQueue.poll();

			// QA: check that all ways carry the role "outer/inner" and
			// issue warnings
			checkRoles(currentRing.ring.getOriginalWays(),
					(currentRing.outer ? "outer" : "inner"));

			// this ring is now processed and should not be used by any
			// further step
			unfinishedRings.clear(currentRing.index);

			BitSet ringContains = new BitSet();
			ringContains.or(containsMatrix.get(currentRing.index));
			// use only rings that are contained by the ring
			ringContains.and(unfinishedRings);
			// ringContains is the intersection of the unfinished and
			// the contained rings

			// get the holes
			// these are all rings that are in the main ring
			// and that are not contained by any other ring
			BitSet holeIndexes = findOutmostRings(ringContains,
					(currentRing.outer ? innerRings : outerRings));

			ArrayList<RingStatus> holes = getRingStatus(holeIndexes,
					!currentRing.outer);

			// these rings must all be checked for inner rings
			ringWorkingQueue.addAll(holes);

			// check if the ring has tags and therefore should be processed
			boolean processRing = currentRing.outer
					|| hasPolygonTags(currentRing.ring);

			if (processRing) {

				List<Way> innerWays = new ArrayList<Way>(holes.size());
				for (RingStatus holeRingStatus : holes) {
					innerWays.add(holeRingStatus.ring);
				}

				List<Way> singularOuterPolygons = cutOutInnerRings(
						currentRing.ring, innerWays);

				if (currentRing.ring.getOriginalWays().size() == 1) {
					// the original way was a closed polygon which
					// has been replaced by the new cutted polygon
					// the original way should not appear
					// so we remove all tags
					currentRing.ring.removeAllTagsDeep();
				} else {
					// the ring has been composed by several ways
					// they may contain line tags
					// however all polygon tags are not processed
					// because they are only lines and not polygons
					// so we don't have to remove any tag
				}

				boolean useRelationTags = currentRing.outer
						&& hasPolygonTags(this);
				if (useRelationTags) {
					// the multipolygon contains tags that overwhelm the
					// tags of the outer ring
					for (Way p : singularOuterPolygons) {
						p.copyTags(this);
					}
				}

				for (Way mpWay : singularOuterPolygons) {
					// put the cut out polygons to the
					// final way map
					tileWayMap.put(mpWay.getId(), mpWay);
				}
			}
		}

		if (unfinishedRings.isEmpty() == false) {
			// we have at least one ring that could not be processed
			// Probably we have intersecting or overlapping polygons
			// one possible reason is if the relation overlaps the tile bounds
			// => issue a warning
			log.warn("Multipolygon " + toBrowseURL()
					+ " contains intersected or overlapping ways");
			ArrayList<RingStatus> ringList = getRingStatus(unfinishedRings,
					true);
			for (RingStatus ring : ringList) {
				logWayURLs(Level.WARNING, "-", ring.ring);
			}
		}

		cleanup();
	}

	private void cleanup() {
		roleMap.clear();
		containsMatrix = null;
		rings = null;
	}

	/**
	 * Cut out all inner rings from the outer ring. This will divide the outer
	 * ring in several polygons.
	 * 
	 * @param outerRing
	 *            the outer ring
	 * @param innerRings
	 *            a list of inner rings
	 * @return a list of polygons that make the outer ring cut by the inner
	 *         rings
	 */
	private List<Way> cutOutInnerRings(Way outerRing, List<Way> innerRings) {
		// we use the java.awt.geom.Area class because it's a quick
		// implementation of what we need

		// this list contains all non overlapping and singular areas
		// of the outerRing
		List<Area> outerAreas = new ArrayList<Area>();

		// 1st create an Area object of the outerRing and put it to the list
		Area oa = createArea(outerRing.getPoints());

		// the polygons will be later clipped in the style converter
		// so it is not necessary to clip it here
		outerAreas.add(oa);

		// go through all innerRings (holes) and cut them from the outerRing
		for (Way innerRing : innerRings) {
			Area innerArea = createArea(innerRing.getPoints());

			List<Area> outerAfterThisStep = new ArrayList<Area>();
			for (Area outerArea : outerAreas) {
				// check if this outerArea is probably intersected by the inner
				// area to save computation time in case it is not
				if (outerArea.getBounds().createIntersection(
						innerArea.getBounds()).isEmpty()) {
					outerAfterThisStep.add(outerArea);
					continue;
				}

				// cut the hole
				outerArea.subtract(innerArea);
				if (outerArea.isEmpty()) {
					// this outer area space can be abandoned
				} else if (outerArea.isSingular()) {
					// the area is singular
					// => no further splits necessary
					outerAfterThisStep.add(outerArea);
				} else {
					// 1st cut in two halfs in the middle of the inner area

					// Cut the bounding box into two rectangles
					Rectangle r1;
					Rectangle r2;

					// Get the bounds of this polygon
					Rectangle innerBounds = innerArea.getBounds();
					Rectangle outerBounds = outerArea.getBounds();
					if (outerBounds.width > outerBounds.height) {
						int cutWidth = (innerBounds.x - outerBounds.x)
								+ innerBounds.width / 2;
						r1 = new Rectangle(outerBounds.x, outerBounds.y,
								cutWidth, outerBounds.height);
						r2 = new Rectangle(outerBounds.x + cutWidth,
								outerBounds.y, outerBounds.width - cutWidth,
								outerBounds.height);
					} else {
						int cutHeight = (innerBounds.y - outerBounds.y)
								+ innerBounds.height / 2;
						r1 = new Rectangle(outerBounds.x, outerBounds.y,
								outerBounds.width, cutHeight);
						r2 = new Rectangle(outerBounds.x, outerBounds.y
								+ cutHeight, outerBounds.width,
								outerBounds.height - cutHeight);
					}

					// Now find the intersection of these two boxes with the
					// original polygon. This will make two new areas, and each
					// area will be one (or more) polygons.
					Area a1 = outerArea;
					Area a2 = (Area) a1.clone();
					a1.intersect(new Area(r1));
					a2.intersect(new Area(r2));

					outerAfterThisStep.addAll(areaToSingularAreas(a1));
					outerAfterThisStep.addAll(areaToSingularAreas(a2));
				}
			}
			outerAreas = outerAfterThisStep;
		}

		// convert the java.awt.geom.Area back to the mkgmap way
		List<Way> cutOuterRing = new ArrayList<Way>(outerAreas.size());
		for (Area area : outerAreas) {
			Way w = singularAreaToWay(area, FakeIdGenerator.makeFakeId());
			if (w != null) {
				w.copyTags(outerRing);
				cutOuterRing.add(w);
			}
		}

		return cutOuterRing;
	}

	/**
	 * Convert an area that may contains multiple areas to a list of singular
	 * areas
	 * 
	 * @param area
	 *            an area
	 * @return list of singular areas
	 */
	private List<Area> areaToSingularAreas(Area area) {
		if (area.isEmpty()) {
			return Collections.emptyList();
		} else if (area.isSingular()) {
			return Collections.singletonList(area);
		} else {
			List<Area> singularAreas = new ArrayList<Area>();

			// all ways in the area MUST define outer areas
			// it is not possible that one of the areas define an inner segment

			float[] res = new float[6];
			PathIterator pit = area.getPathIterator(null);
			float[] prevPoint = new float[6];

			Polygon p = null;
			while (!pit.isDone()) {
				int type = pit.currentSegment(res);

				switch (type) {
				case PathIterator.SEG_LINETO:
					if (Arrays.equals(res, prevPoint) == false) {
						p.addPoint(Math.round(res[0]), Math.round(res[1]));
					}
					break;
				case PathIterator.SEG_CLOSE:
					p.addPoint(p.xpoints[0], p.ypoints[0]);
					Area a = new Area(p);
					if (a.isEmpty() == false) {
						singularAreas.add(a);
					}
					p = null;
					break;
				case PathIterator.SEG_MOVETO:
					if (p != null) {
						Area a2 = new Area(p);
						if (a2.isEmpty() == false) {
							singularAreas.add(a2);
						}
					}
					p = new Polygon();
					p.addPoint(Math.round(res[0]), Math.round(res[1]));
					break;
				default:
					log.warn(toBrowseURL(), "Unsupported path iterator type"
							+ type, ". This is an mkgmap error.");
				}

				System.arraycopy(res, 0, prevPoint, 0, 6);
				pit.next();
			}
			return singularAreas;
		}
	}

	/**
	 * Create a polygon from a list of points.
	 * 
	 * @param points
	 *            list of points
	 * @return the polygon
	 */
	private Polygon createPolygon(List<Coord> points) {
		Polygon polygon = new Polygon();
		for (Coord co : points) {
			polygon.addPoint(co.getLongitude(), co.getLatitude());
		}
		return polygon;
	}

	/**
	 * Create an area from a list of points.
	 * 
	 * @param points
	 *            list of points
	 * @return the area
	 */
	private Area createArea(List<Coord> points) {
		return new Area(createPolygon(points));
	}

	/**
	 * Convert an area to an mkgmap way
	 * 
	 * @param area
	 *            the area
	 * @param wayId
	 *            the wayid for the new way
	 * @return a new mkgmap way
	 */
	private Way singularAreaToWay(Area area, long wayId) {
		if (area.isSingular() == false) {
			log.warn(
				"singularAreaToWay called with non singular area. Multipolygon ",
				toBrowseURL());
		}
		if (area.isEmpty()) {
			if (log.isDebugEnabled()) {
				log.debug("Empty area.", toBrowseURL());
			}
			return null;
		}

		Way w = null;

		float[] res = new float[6];
		PathIterator pit = area.getPathIterator(null);

		while (!pit.isDone()) {
			int type = pit.currentSegment(res);

			switch (type) {
			case PathIterator.SEG_MOVETO:
				w = new Way(wayId);
				w.addPoint(new Coord(Math.round(res[1]), Math.round(res[0])));
				break;
			case PathIterator.SEG_LINETO:
				w.addPoint(new Coord(Math.round(res[1]), Math.round(res[0])));
				break;
			case PathIterator.SEG_CLOSE:
				w.addPoint(w.getPoints().get(0));
				return w;
			default:
				log.warn(toBrowseURL(),
						"Unsupported path iterator type" + type,
						". This is an mkgmap error.");
			}
			pit.next();
		}
		return w;
	}

	private boolean hasPolygonTags(JoinedWay way) {
		for (Way segment : way.getOriginalWays()) {
			if (hasPolygonTags(segment)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasPolygonTags(Element element) {
		for (Map.Entry<String, String> tagEntry : element.getEntryIteratable()) {
			if (polygonTags.contains(tagEntry.getKey())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * This is a QA method. All ways of the given wayList are checked if they
	 * they carry the checkRole. If not a warning is logged.
	 * 
	 * @param wayList
	 * @param checkRole
	 */
	private void checkRoles(List<Way> wayList, String checkRole) {
		// QA: check that all ways carry the role "inner" and issue warnings
		for (Way tempWay : wayList) {
			String realRole = getRole(tempWay);
			if (checkRole.equals(realRole) == false) {
				log.warn("Way", tempWay.getId(), "carries role", realRole,
						"but should carry role", checkRole);
			}
		}
	}

	/**
	 * Creates a matrix which polygon contains which polygon. A polygon does not
	 * contain itself.
	 * 
	 * @param polygonList
	 *            a list of polygons
	 */
	private void createContainsMatrix(List<JoinedWay> polygonList) {
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
			JoinedWay potentialOuterRing = polygonList.get(rowIndex);
			BitSet containsColumns = containsMatrix.get(rowIndex);
			BitSet finishedCol = finishedMatrix.get(rowIndex);

			if (log.isDebugEnabled())
				log.debug("check polygon", rowIndex);

			// get all non calculated columns of the matrix
			for (int colIndex = finishedCol.nextClearBit(0); colIndex >= 0
					&& colIndex < polygonList.size(); colIndex = finishedCol
					.nextClearBit(colIndex + 1)) {

				JoinedWay innerPolygon = polygonList.get(colIndex);

				if (potentialOuterRing.getBounds().intersects(
						innerPolygon.getBounds()) == false) {
					// both polygons do not intersect
					// we can flag both matrix elements as finished
					finishedMatrix.get(colIndex).set(rowIndex);
					finishedMatrix.get(rowIndex).set(colIndex);
				} else {
					boolean contains = contains(potentialOuterRing,
							innerPolygon);

					if (contains) {
						containsColumns.set(colIndex);

						// we also know that the inner ring does not contain the
						// outer ring
						// so we can set the finished bit for this matrix
						// element
						finishedMatrix.get(colIndex).set(rowIndex);

						// additionally we know that the outer ring contains all
						// rings
						// that are contained by the inner ring
						containsColumns.or(containsMatrix.get(colIndex));
						finishedCol.or(containsColumns);
					}
				}
				// this matrix element is calculated now
				finishedCol.set(colIndex);
			}
		}

		if (log.isDebugEnabled()) {
			long t2 = System.currentTimeMillis();
			log.debug("createMatrix for", polygonList.size(), "polygons took",
					(t2 - t1), "ms");
			
			log.debug("Containsmatrix");
			for (BitSet b : containsMatrix) {
				log.debug(b);
			}
		}
	}

	/**
	 * Checks if the ring with ringIndex1 contains the ring with ringIndex2.
	 * 
	 * @return true if ring(ringIndex1) contains ring(ringIndex2)
	 */
	private boolean contains(int ringIndex1, int ringIndex2) {
		return containsMatrix.get(ringIndex1).get(ringIndex2);
	}

	/**
	 * Checks if ring1 contains ring2.
	 * 
	 * @param ring1
	 *            a closed way
	 * @param ring2 a 2nd closed way
	 * @return true if ring1 contains ring2
	 */
	private boolean contains(JoinedWay ring1, JoinedWay ring2) {
		// TODO this is a simple algorithm
		// might be improved

		if (ring1.isClosed() == false) {
			return false;
		}

		Polygon p = createPolygon(ring1.getPoints());

		Coord p0 = ring2.getPoints().get(0);
		if (p.contains(p0.getLongitude(), p0.getLatitude()) == false) {
			// we have one point that is not in way1 => way1 does not contain
			// way2
			return false;
		}
		// TODO check if p0 is on any of the lines of ring2

		Iterator<Coord> it1 = ring1.getPoints().iterator();
		Coord p1_1 = it1.next();
		Coord p1_2 = null;

		while (it1.hasNext()) {
			p1_2 = p1_1;
			p1_1 = it1.next();

			if (ring2.linePossiblyIntersectsWay(p1_1, p1_2) == false) {
				// don't check it - this segment of the outer polygon
				// definitely does not intersect the way
				continue;
			}

			int lonMin = Math.min(p1_1.getLongitude(), p1_2.getLongitude());
			int lonMax = Math.max(p1_1.getLongitude(), p1_2.getLongitude());
			int latMin = Math.min(p1_1.getLatitude(), p1_2.getLatitude());
			int latMax = Math.max(p1_1.getLatitude(), p1_2.getLatitude());

			// check all lines of way1 and way2 for intersections
			Iterator<Coord> it2 = ring2.getPoints().iterator();
			Coord p2_1 = it2.next();
			Coord p2_2 = null;

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
				p2_2 = p2_1;
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
						&& Line2D.linesIntersect(p1_1.getLongitude(), p1_1
								.getLatitude(), p1_2.getLongitude(), p1_2
								.getLatitude(), p2_1.getLongitude(), p2_1
								.getLatitude(), p2_2.getLongitude(), p2_2
								.getLatitude());

				// TODO check if one of the points only touches the other ring

				if (intersects) {
					if ((ring1.isClosedArtificially() && it1.hasNext() == false)
							|| (ring2.isClosedArtificially() && it2.hasNext() == false)) {
						// don't care about this intersection
						// one of the rings is closed by this mp code and the
						// closing way causes the intersection
						log
								.warn("Way", ring1, "may contain way", ring2,
										". Ignoring artificial generated intersection.");
					} else {
						return false;
					}
				}

				prevLonField = lonField;
				prevLatField = latField;
			}
		}

		// don't have any intersection
		// => ring1 contains ring2
		return true;
	}

	private void logWayURLs(Level level, String preMsg, Way way) {
		if (log.isLoggable(level)) {
			if (way instanceof JoinedWay) {
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
	 * This is a helper class that stores that gives access to the original
	 * segments of a joined way.
	 */
	private static class JoinedWay extends Way {
		private final List<Way> originalWays;
		private boolean closedArtificially = false;

		public int minLat;
		public int maxLat;
		public int minLon;
		public int maxLon;
		private Rectangle bounds = null;

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

		public Rectangle getBounds() {
			if (bounds == null) {
				// note that we increase the rectangle by 1 because intersects
				// checks
				// only the interior
				bounds = new Rectangle(minLat - 1, minLon - 1, maxLat - minLat
						+ 2, maxLon - minLon + 2);
			}

			return bounds;
		}

		public boolean linePossiblyIntersectsWay(Coord p1, Coord p2) {
			return getBounds().intersectsLine(p1.getLatitude(),
					p1.getLongitude(), p2.getLatitude(), p2.getLongitude());
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
				addTagsOf(way);
				if (getName() == null && way.getName() != null) {
					setName(way.getName());
				}
			}
		}

		public void closeWayArtificially() {
			addPoint(getPoints().get(0));
			closedArtificially = true;
		}

		public boolean isClosedArtificially() {
			return closedArtificially;
		}

		private void addTagsOf(Way way) {
			for (Map.Entry<String, String> tag : way.getEntryIteratable()) {
				if (getTag(tag.getKey()) == null) {
					addTag(tag.getKey(), tag.getValue());
				}
			}
		}

		public List<Way> getOriginalWays() {
			return originalWays;
		}

		public void removeAllTagsDeep() {
			removeOriginalTags();
			removeAllTags();
		}

		public void removeOriginalTags() {
			for (Way w : getOriginalWays()) {
				if (w instanceof JoinedWay) {
					((JoinedWay) w).removeAllTagsDeep();
				} else {
					w.removeAllTags();
				}
			}
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(200);
			sb.append(getId());
			sb.append("[");
			sb.append(getPoints().size());
			sb.append("P : (");
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

	private static class RingStatus {
		boolean outer;
		int index;
		JoinedWay ring;

		public RingStatus(boolean outer, int index, JoinedWay ring) {
			this.outer = outer;
			this.index = index;
			this.ring = ring;
		}
	}
}
