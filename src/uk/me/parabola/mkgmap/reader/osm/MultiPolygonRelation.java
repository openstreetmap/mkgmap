package uk.me.parabola.mkgmap.reader.osm;

import java.awt.Polygon;
import java.awt.geom.Line2D;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;

/**
 * Representation of an OSM Multipolygon Relation. This will combine the
 * different roles into one area.
 * 
 * @author Rene_A
 */
public class MultiPolygonRelation extends Relation {
	private static final Logger log = Logger
			.getLogger(MultiPolygonRelation.class);

	// private final List<Way> outerSegments = new ArrayList<Way>();
	// private final List<Way> innerSegments = new ArrayList<Way>();
	private final Map<Long, Way> myWayMap;

	private final ArrayList<BitSet> containsMatrix = new ArrayList<BitSet>();

	// this list contains the polygons that should be used to create the garmin
	// map
	public final List<Way> polygonResults = new ArrayList<Way>();

	private ArrayList<JoinedWay> rings;

	private static final List<String> relationTags = Arrays.asList("boundary",
			"natural", "landuse", "building", "waterway");

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
	public MultiPolygonRelation(Relation other, Map<Long, Way> wayMap) {
		myWayMap = wayMap;
		setId(other.getId());
		for (Map.Entry<String, Element> pair : other.getElements()) {
			String role = pair.getKey();
			Element el = pair.getValue();
			addElement(role, el);

			// if (role != null && el instanceof Way) {
			// 	Way way = (Way) el;
			// 	if ("outer".equals(role)) {
			// 		outerSegments.add(way);
			// 	} else if ("inner".equals(role)) {
			// 		innerSegments.add(way);
			// 	}
			// }
		}

		setName(other.getName());
		copyTags(other);
	}

	/**
	 * Combine a list of way segments to a list of maximally joined ways
	 * 
	 * @param segments
	 *            a list of closed or unclosed ways
	 * @return a list of closed ways
	 */
	private ArrayList<JoinedWay> joinWays(List<Map.Entry<String,Way>> segments) {
		// this method implements RA-1 to RA-4
		// TODO check if the closed polygon is valid and implement a
		// backtracking algorithm to get other combinations

		ArrayList<JoinedWay> joinedWays = new ArrayList<JoinedWay>();
		if (segments == null || segments.size() == 0) {
			return joinedWays;
		}

		// go through all segments and categorize them to closed and unclosed
		// list
		ArrayList<JoinedWay> unclosedWays = new ArrayList<JoinedWay>();
		for (Map.Entry<String,Way> orgSegment : segments) {
			String role = orgSegment.getKey();
			Way way = orgSegment.getValue();

			if (way.isClosed()) {
				joinedWays.add(new JoinedWay(role, way));
			} else {
				unclosedWays.add(new JoinedWay(role, way));
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

				// TODO: compare roles too
				// use == or equals as comparator??
				if (joinWay.getPoints().get(0) == tempWay.getPoints().get(0)) {
					for (Coord point : tempWay.getPoints().subList(1,
							tempWay.getPoints().size())) {
						joinWay.addPoint(0, point);
					}
					joined = true;
				} else if (joinWay.getPoints().get(
						joinWay.getPoints().size() - 1) == tempWay.getPoints()
						.get(0)) {
					for (Coord point : tempWay.getPoints().subList(1,
							tempWay.getPoints().size())) {
						joinWay.addPoint(point);
					}
					joined = true;
				} else if (joinWay.getPoints().get(0) == tempWay.getPoints()
						.get(tempWay.getPoints().size() - 1)) {
					int insertIndex = 0;
					for (Coord point : tempWay.getPoints().subList(0,
							tempWay.getPoints().size() - 1)) {
						joinWay.addPoint(insertIndex, point);
						insertIndex++;
					}
					joined = true;
				} else if (joinWay.getPoints().get(
						joinWay.getPoints().size() - 1) == tempWay.getPoints()
						.get(tempWay.getPoints().size() - 1)) {
					int insertIndex = joinWay.getPoints().size();
					for (Coord point : tempWay.getPoints().subList(0,
							tempWay.getPoints().size() - 1)) {
						joinWay.addPoint(insertIndex, point);
					}
					joined = true;
				}

				if (joined) {
					unclosedWays.remove(tempWay);
					joinWay.addWay(tempWay);
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
					log.warn("Unclosed polygons in multipolygon relation "
							+ getId() + ":");
				}
				for (Map.Entry<String,Way> rw : tempWay.getOriginalRoleWays()) {
					String role = rw.getKey();
					Way way = rw.getValue();
					log.warn(" - way:", way.getId(), "role:", role,
							"osm:", way.toBrowseURL());
				}

				it.remove();
				first = false;
			}
		}
	}

	/**
	 * Finds all rings that are not contained by any other rings. All rings with
	 * index given by <var>candidates</var> are used.
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
					// candidateIndex is not an outmost ring because it is
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
		ArrayList<Map.Entry<String,Way>> allWays =
				new ArrayList<Map.Entry<String,Way>>();

		for (Map.Entry<String,Element> r_el : getElements()) {
			String role = r_el.getKey();
			Element element = r_el.getValue();

			if (element instanceof Way) {
				allWays.add(new AbstractMap.SimpleEntry<String,Way>
						(role, (Way) element));
			} else {
				log.warn("Non way element", element.getId(), "in multipolygon",
						getId());
			}
		}

		rings = joinWays(allWays);
		removeUnclosedWays(rings);
		// now we have closed ways == rings only

		/*
		 * ===== start considering outer and inner ways ====
		 * ArrayList<JoinedWay> joinedOuterWays = joinWays(outerWays);
		 * ArrayList<JoinedWay> joinedInnerWays = joinWays(innerWays);
		 * 
		 * removeUnclosedWays(joinedOuterWays);
		 * removeUnclosedWays(joinedInnerWays);
		 * 
		 * // at this point we don't care about outer and inner // in the end we
		 * will compare if outer and inner tags are matching // what we detect
		 * here and issue some warnings and errors ArrayList<JoinedWay>
		 * completeJoinedWays = new ArrayList<JoinedWay>();
		 * completeJoinedWays.addAll(joinedOuterWays);
		 * completeJoinedWays.addAll(joinedInnerWays); ===== end considering
		 * outer and inner ways ====
		 */

		// check if we have at least one ring left
		if (rings.isEmpty()) {
			// do nothing
			log.warn("Multipolygon " + toBrowseURL()
					+ " does not contain a closed polygon.");
			return;
		}

		createContainsMatrix(rings);

		BitSet unfinishedRings = new BitSet(rings.size());
		unfinishedRings.set(0, rings.size());

		Queue<RingStatus> ringWorkingQueue = new LinkedBlockingQueue<RingStatus>();

		BitSet outmostRings = findOutmostRings(unfinishedRings);

		ringWorkingQueue.addAll(getRingStatus(outmostRings, true));

		while (ringWorkingQueue.isEmpty() == false) {

			// the ring is not contained by any other unfinished ring
			RingStatus currentRing = ringWorkingQueue.poll();

			// QA: check that all ways carry the role "outer/inner" and
			// issue
			// warnings
			checkRoles(currentRing.ring.getOriginalRoleWays(),
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
			BitSet holeIndexes = findOutmostRings(ringContains);

			ArrayList<RingStatus> holes = getRingStatus(holeIndexes,
					!currentRing.outer);

			// these rings must all be checked for inner rings
			ringWorkingQueue.addAll(holes);

			// check if the ring has tags and therefore should be processed
			boolean processRing = currentRing.outer
					|| hasUsefulTags(currentRing.ring);

			if (processRing) {
				// now construct the ring polygon with its holes
				for (RingStatus holeRingStatus : holes) {
					int[] insert = findCpa(currentRing.ring.getPoints(),
							holeRingStatus.ring.getPoints());
					if (insert[0] >= 0 && insert[1] >= 0) {
						insertPoints(currentRing.ring, holeRingStatus.ring,
								insert[0], insert[1]);
					} else {
						// this must not happen
						log.error("Cannot find cpa in multipolygon "
								+ toBrowseURL());
					}
				}

				boolean useRelationTags = currentRing.outer
						&& hasUsefulTags(this);

				if (useRelationTags) {
					// the multipolygon contains tags that overwhelm the
					// tags
					// out the outer ring
					currentRing.ring.copyTags(this);
				} else {
					// the multipolygon does not contain any relevant tag
					// use the segments of the ring and merge the tags
					for (Map.Entry<String,Way> roleway : currentRing.ring.getOriginalRoleWays()) {
						String role = roleway.getKey();
						Way ringSegment = roleway.getValue();
						// TODO uuh, this is bad => the last of the
						// ring segments win
						// => any better idea?
						for (Map.Entry<String, String> outSegmentTag : ringSegment
								.getEntryIteratable()) {
							currentRing.ring.addTag(outSegmentTag.getKey(),
									outSegmentTag.getValue());
						}
					}
				}

				polygonResults.add(currentRing.ring);
			}
		}

		if (unfinishedRings.isEmpty() == false) {
			// we have at least one ring that could not be processed
			// Probably we have intersecting polygons
			// => issue a warning
			log.error("Multipolygon " + toBrowseURL()
					+ " contains intersected ways");
			ArrayList<RingStatus> ringList = getRingStatus(unfinishedRings,
					true);
			for (RingStatus ring : ringList) {
				log.error("- " + ring.ring.toBrowseURL());
			}
		}

		// the polygonResults contain all polygons that should be used in the
		// map
		// TODO remove the input stuff? inner ways and outer segments?
		for (Way resultWay : polygonResults) {
			myWayMap.put(resultWay.getId(), resultWay);
		}

	}

	private boolean hasUsefulTags(JoinedWay way) {
		for (Map.Entry<String,Way> segment : way.getOriginalRoleWays()) {
			if (hasUsefulTags(segment.getValue())) {
				return true;
			}
		}
		return false;
	}

	private boolean hasUsefulTags(Element element) {
		for (Map.Entry<String, String> tagEntry : element.getEntryIteratable()) {
			if (relationTags.contains(tagEntry.getKey())) {
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
	private void checkRoles(List<Map.Entry<String,Way>> wayList,
							String checkRole) {
		// QA: check that all ways carry the role "inner" and issue warnings
		for (Map.Entry<String,Way> rw : wayList) {
			String role = rw.getKey();
			Way way = rw.getValue();
			if (!checkRole.equals(role) == false) {
				log.warn("Way", way.getId(), "carries role", role,
						"but should carry role", checkRole);
			}
		}
	}

	private void createContainsMatrix(List<JoinedWay> wayList) {
		for (int i = 0; i < wayList.size(); i++) {
			containsMatrix.add(new BitSet());
		}

		// mark which ring contains which ring
		for (int i = 0; i < wayList.size(); i++) {
			Way tempRing = wayList.get(i);
			BitSet bitSet = containsMatrix.get(i);

			for (int j = 0; j < wayList.size(); j++) {
				boolean contains = false;
				if (i == j) {
					// this is special: a way does not contain itself for
					// our usage here
					contains = false;
				} else {
					contains = contains(tempRing, wayList.get(j));
				}

				if (contains) {
					bitSet.set(j);
				}
			}
		}

	}

	/**
	 * Checks if the ring with ringIndex1 contains the ring with ringIndex2.
	 * 
	 * @param ringIndex1
	 * @param ringIndex2
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
	 * @param ring2
	 * @return true if ring1 contains ring2
	 */
	private boolean contains(Way ring1, Way ring2) {
		// TODO this is a simple algorithm
		// might be improved

		if (ring1.isClosed() == false) {
			return false;
		}
		Polygon p = new Polygon();
		for (Coord c : ring1.getPoints()) {
			p.addPoint(c.getLatitude(), c.getLongitude());
		}

		Coord p0 = ring2.getPoints().get(0);
		if (p.contains(p0.getLatitude(), p0.getLongitude()) == false) {
			// we have one point that is not in way1 => way1 does not contain
			// way2
			return false;
		}

		// check all lines of way1 and way2 for intersections
		Iterator<Coord> it2 = ring2.getPoints().iterator();
		Coord p2_1 = it2.next();
		Coord p2_2 = null;
		while (it2.hasNext()) {
			p2_2 = p2_1;
			p2_1 = it2.next();

			Iterator<Coord> it1 = ring1.getPoints().iterator();
			Coord p1_1 = it1.next();
			Coord p1_2 = null;
			while (it1.hasNext()) {
				p1_2 = p1_1;
				p1_1 = it1.next();

				boolean intersects = Line2D.linesIntersect(p1_1.getLatitude(),
						p1_1.getLongitude(), p1_2.getLatitude(), p1_2
								.getLongitude(), p2_1.getLatitude(), p2_1
								.getLongitude(), p2_2.getLatitude(), p2_2
								.getLongitude());

				if (intersects) {
					return false;
				}
			}
		}

		// don't have any intersection
		// => ring1 contains ring2
		return true;
	}

	/**
	 * Insert Coordinates into the outer way.
	 * 
	 * @param outer
	 *            the outer way
	 * @param inner
	 *            Way to be inserted
	 * @param out
	 *            Coordinates will be inserted after this point in the outer
	 *            way.
	 * @param in
	 *            Points will be inserted starting at this index, then from
	 *            element 0 to (including) this element;
	 */
	private void insertPoints(Way outer, Way inner, int out, int in) {
		// TODO this algorithm may generate a self intersecting polygon
		// because it does not consider the direction of both ways
		// don't know if that's a problem

		List<Coord> outList = outer.getPoints();
		List<Coord> inList = inner.getPoints();
		int index = out + 1;
		for (int i = in; i < inList.size(); i++) {
			outList.add(index++, inList.get(i));
		}
		for (int i = 0; i < in; i++) {
			outList.add(index++, inList.get(i));
		}

		// Investigate and see if we can do the first alternative here by
		// changing the polygon splitter. If not then always do the alternative
		// and remove unused code.
		if (outer.getPoints().size() < 0 /* Always use alternative method for now */) {
			outList.add(index++, inList.get(in));
			outList.add(index, outList.get(out));
		} else {
			// we shift the nodes to avoid duplicate nodes (large areas only)
			int oLat = outList.get(out).getLatitude();
			int oLon = outList.get(out).getLongitude();
			int iLat = inList.get(in).getLatitude();
			int iLon = inList.get(in).getLongitude();
			if (Math.abs(oLat - iLat) > Math.abs(oLon - iLon)) {
				int delta = (oLon > iLon) ? -1 : 1;
				outList.add(index++, new Coord(iLat + delta, iLon));
				outList.add(index, new Coord(oLat + delta, oLon));
			} else {
				int delta = (oLat > iLat) ? 1 : -1;
				outList.add(index++, new Coord(iLat, iLon + delta));
				outList.add(index, new Coord(oLat, oLon + delta));
			}
		}
	}

	private static final class DistIndex implements Comparable<DistIndex> {
		int index1;
		int index2;
		double distance;

		public DistIndex(int index1, int index2, double distance) {
			super();
			this.index1 = index1;
			this.index2 = index2;
			this.distance = distance;
		}

		@Override
		public int compareTo(DistIndex o) {
			if (distance < o.distance)
				return -1;
			else if (distance > o.distance) {
				return 1;
			} else {
				return 0;
			}
		}
	}

	/**
	 * find the Closest Point of Approach between two coordinate-lists This will
	 * probably be moved to a Utils class. Note: works only if l2 lies into l1.
	 * 
	 * @param l1
	 *            First list of points.
	 * @param l2
	 *            Second list of points.
	 * @return The first element is the index in l1, the second in l2 which are
	 *         the closest together.
	 */
	private static int[] findCpa(List<Coord> l1, List<Coord> l2) {
		// calculate and sort all distances first before
		// to avoid the very costly calls of intersect
		// Limit the size of this list to 500000 entries to
		// avoid extreme memory consumption
		int maxEntries = 500000;
		ArrayList<DistIndex> distList = new ArrayList<DistIndex>(Math.min(l1
				.size()
				* l2.size(), maxEntries));

		DistIndex minDistance = null;

		int index1 = 0;
		for (Coord c1 : l1) {
			int index2 = 0;
			for (Coord c2 : l2) {
				double distance = c1.distanceInDegreesSquared(c2);
				distList.add(new DistIndex(index1, index2, distance));
				index2++;

				if (distList.size() == maxEntries) {
					Collections.sort(distList);
					for (DistIndex newDistance : distList) {
						if (minDistance == null
								|| minDistance.distance > newDistance.distance) {
							// this is a new minimum
							// test if a line between c1 and c2 intersects
							// the outer polygon l1.
							if (intersects(l1, l1.get(newDistance.index1), l2
									.get(newDistance.index2)) == false) {
								minDistance = newDistance;
								break;
							}
						} else {
							break;
						}
					}
					distList.clear();
				}
			}
			index1++;
		}

		Collections.sort(distList);
		for (DistIndex newDistance : distList) {
			if (minDistance == null
					|| minDistance.distance > newDistance.distance) {
				// this is a new minimum
				// test if a line between c1 and c2 intersects
				// the outer polygon l1.
				if (intersects(l1, l1.get(newDistance.index1), l2
						.get(newDistance.index2)) == false) {
					minDistance = newDistance;
					break;
				}
			} else {
				break;
			}
		}

		if (minDistance == null) {
			// this should not happen
			return new int[] { -1, -1 };
		} else {
			return new int[] { minDistance.index1, minDistance.index2 };
		}
	}

	private static boolean intersects(List<Coord> lc, Coord lp1, Coord lp2) {
		Coord c11 = null;
		Coord c12 = null;
		for (Coord c : lc) {
			c12 = c11;
			c11 = c;
			if (c12 == null) {
				continue;
			}

			// in case the line intersects in a well known point this is not an
			// inline intersection
			if (c11.equals(lp1) || c11.equals(lp2) || c12.equals(lp1)
					|| c12.equals(lp2)) {
				continue;
			}

			if (Line2D.linesIntersect(c11.getLatitude(), c11.getLongitude(),
					c12.getLatitude(), c12.getLongitude(), lp1.getLatitude(),
					lp1.getLongitude(), lp2.getLatitude(), lp2.getLongitude())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * This is a helper class that stores that gives access to the original
	 * segments of a joined way.
	 */
	private static class JoinedWay extends Way {
		private final List<Map.Entry<String,Way>> originalRoleWays;

		public JoinedWay(String role, Way originalWay) {
			super(-originalWay.getId(), new ArrayList<Coord>(originalWay
					.getPoints()));
			this.originalRoleWays = new ArrayList<Map.Entry<String,Way>>();
			this.originalRoleWays.add(new AbstractMap.SimpleEntry<String,Way>
									  (role, originalWay));
		}

		public void addPoint(int index, Coord point) {
			getPoints().add(index, point);
		}

		public void addWay(JoinedWay way) {
			originalRoleWays.addAll(way.originalRoleWays);
			log.debug("Joined", this.getId(), "with", way.getId());
		}

		public List<Map.Entry<String, Way>> getOriginalRoleWays() {
			return originalRoleWays;
		}

	}

	private static class RingStatus {
		boolean outer;
		int index;
		JoinedWay ring;

		public RingStatus(boolean outer, int index, JoinedWay ring) {
			super();
			this.outer = outer;
			this.index = index;
			this.ring = ring;
		}
	}
}
