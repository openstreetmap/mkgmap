package uk.me.parabola.mkgmap.reader.osm;

import java.awt.*;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;

/**
 * Representation of an OSM Multipolygon Relation.
 * This will combine the different roles into one area.
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
		for (Map.Entry<Element, String> pairs : other.getRoles().entrySet()) {
			addElement(pairs.getValue(), pairs.getKey());

			// String value = pairs.getValue();
			//
			// if (value != null && pairs.getKey() instanceof Way) {
			// Way way = (Way) pairs.getKey();
			// if (value.equals("outer")) {
			// outerSegments.add(way);
			// } else if (value.equals("inner")) {
			// innerSegments.add(way);
			// }
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
	private ArrayList<JoinedWay> joinWays(List<Way> segments) {
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
		for (Way orgSegment : segments) {
			if (orgSegment.isClosed()) {
				joinedWays.add(new JoinedWay(orgSegment));
			} else {
				unclosedWays.add(new JoinedWay(orgSegment));
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
							tempWay.getPoints().size() - 1)) {
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
				for (Way orgWay : tempWay.getOriginalWays()) {
					log.warn(" - way:", orgWay.getId(), "role:", getRoles()
							.get(orgWay), "osm:", orgWay.toBrowseURL());
				}

				it.remove();
				first = false;
			}
		}
	}

	/**
	 * Process the ways in this relation. Joins way with the role "outer" Adds
	 * ways with the role "inner" to the way with the role "outer"
	 */
	public void processElements() {
		// don't care about outer and inner declaration
		// because this is a first 
		ArrayList<Way> allWays = new ArrayList<Way>();
		for (Element element : getElements()) {
			if (element instanceof Way) {
				allWays.add((Way) element);
			} else {
				log.warn("Non way element", element.getId(), "in multipolygon",
						getId());
			}
		}

		ArrayList<JoinedWay> rings = joinWays(allWays);
		removeUnclosedWays(rings);
		// now we have closed ways == rings only

		/* 
		 ===== start considering outer and inner ways ====
		 ArrayList<JoinedWay> joinedOuterWays = joinWays(outerWays);
		 ArrayList<JoinedWay> joinedInnerWays = joinWays(innerWays);
		
		 removeUnclosedWays(joinedOuterWays);
		 removeUnclosedWays(joinedInnerWays);

		// at this point we don't care about outer and inner
		// in the end we will compare if outer and inner tags are matching
		// what we detect here and issue some warnings and errors
		 ArrayList<JoinedWay> completeJoinedWays = new
		 ArrayList<JoinedWay>();
		 completeJoinedWays.addAll(joinedOuterWays);
		 completeJoinedWays.addAll(joinedInnerWays);
		 ===== end considering outer and inner ways ====
		 */

		// check if we have at least one ring left
		if (rings.isEmpty() == false) {

			createContainsMatrix(rings);
			

			BitSet unfinishedRings = new BitSet(rings.size());
			unfinishedRings.set(0, rings.size());

			while (unfinishedRings.isEmpty() == false) {
				// there are still unfinished rings

				// find the next outer ring
				int outerRingIndex = -1;
				for (int checkOuterIndex = unfinishedRings.nextSetBit(0); checkOuterIndex >= 0; checkOuterIndex = unfinishedRings
						.nextSetBit(checkOuterIndex + 1)) {
					// check if the checkOuterIndex ring is not contained by any
					// other unfinished ring
					boolean isNotContained = true;
					for (int possibleOuterIndex = unfinishedRings.nextSetBit(0); possibleOuterIndex >= 0; possibleOuterIndex = unfinishedRings
							.nextSetBit(possibleOuterIndex + 1)) {
						if (contains(possibleOuterIndex,checkOuterIndex)) {
							isNotContained = false;
							break;
						}
					}

					if (isNotContained) {
						// the checkOuterIndex ring is not contained by any other
						// unfinished ring
						outerRingIndex = checkOuterIndex;
						break;
					}
				}

				if (outerRingIndex < 0) {
					// we have an error in this multipolygon
					// => cannot continue
					log.error("Multipolygon " + toBrowseURL()
							+ " contains intersected ways");
					return;
				}

				// outerRingIndex is the ring that is not contained by any other
				// ring
				JoinedWay outerRing = rings.get(outerRingIndex);
				// QA: check that all ways carry the role "outer" and issue
				// warnings
				checkRoles(outerRing.getOriginalWays(), "outer");

				// this ring is now processed and should not be used by any
				// further step
				unfinishedRings.clear(outerRingIndex);

				// create a list of inner rings
				ArrayList<JoinedWay> innerRings = new ArrayList<JoinedWay>();
				BitSet innerIndexes = new BitSet();

				BitSet outerRingContains = containsMatrix.get(outerRingIndex);
				// use only rings that are contained by the outer ring
				outerRingContains.and(unfinishedRings);
				// outerRingContains now contains all rings that are contained
				// by the outer ring and that are not finished yet

				// get the holes (inner rings)
				// go through all inner rings of the outer ring
				// and leave only the ones that are not contained by any other
				// ring
				for (int innerRingIndex = outerRingContains.nextSetBit(0); innerRingIndex >= 0; innerRingIndex = outerRingContains
						.nextSetBit(innerRingIndex + 1)) {
					// check if the inner ring is not contained by any
					// other unfinished ring
					boolean realInnerRing = true;
					for (int unfinishedIndex = unfinishedRings.nextSetBit(0); unfinishedIndex >= 0; unfinishedIndex = unfinishedRings
							.nextSetBit(unfinishedIndex + 1)) {
						if (contains(unfinishedIndex, innerRingIndex)) {
							realInnerRing = false;
							break;
						}
					}

					if (realInnerRing) {
						// this is a real inner ring of the outer ring
						JoinedWay innerRing = rings.get(innerRingIndex);
						innerRings.add(innerRing);
						innerIndexes.set(innerRingIndex);

						// QA: check that all ways carry the role "inner"
						// and issue warnings
						checkRoles(innerRing.getOriginalWays(), "inner");

						// TODO in case the innerRing is tagged itself it
						// must also be treated
						// as special outer ring with its own inner polygons
					}
				}

				// all inner rings are used now, so they are finished
				unfinishedRings.andNot(innerIndexes);

				// now construct the outer polygon with its holes
				for (Way innerRing : innerRings) {
					int[] insert = findCpa(outerRing.getPoints(), innerRing
							.getPoints());
					if (insert[0] >= 0 && insert[1] >= 0) {
						insertPoints(outerRing, innerRing, insert[0], insert[1]);
					}
				}

				// set the tags of the outer ring
				// does the multipolygon itself have any tags?
				boolean validTagFound = false;
				for (Map.Entry<String, String> tagEntry : getEntryIteratable()) {
					if (relationTags.contains(tagEntry.getKey())) {
						validTagFound = true;
						break;
					}
				}

				if (validTagFound) {
					// the multipolygon contains tags that overwhelm the tags
					// out the outer ring
					outerRing.copyTags(this);
				} else {
					// the multipolygon does not contain any relevant tag
					// use the segments of the outer ring and merge the tags
					for (Way outerRingSegment : outerRing.getOriginalWays()) {
						// TODO uuh, this is bad => the last of the outer ring
						// segments win
						// => any better idea?
						for (Map.Entry<String, String> outSegmentTag : outerRingSegment
								.getEntryIteratable()) {
							outerRing.addTag(outSegmentTag.getKey(),
									outSegmentTag.getValue());
						}
					}
				}

				polygonResults.add(outerRing);
			}
		}

		// the polygonResults contain all polygons that should be used in the
		// map
		// TODO remove the input stuff? inner ways and outer segments?
		for (Way resultWay : polygonResults) {
			myWayMap.put(resultWay.getId(), resultWay);
		}

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
			String realRole = getRoles().get(tempWay);
			if (checkRole.equals(realRole) == false) {
				log.warn("Way", tempWay.getId(), "carries role", realRole,
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

	/**
	 * find the Closest Point of Approach between two coordinate-lists This will
	 * probably be moved to a Utils class
	 * 
	 * @param l1
	 *            First list of points.
	 * @param l2
	 *            Second list of points.
	 * @return The first element is the index in l1, the second in l2 which are
	 *         the closest together.
	 */
	private static int[] findCpa(List<Coord> l1, List<Coord> l2) {
		double oldDistance = Double.MAX_VALUE;
		Coord found1 = null;
		Coord found2 = null;

		for (Coord c1 : l1) {
			for (Coord c2 : l2) {
				double newDistance = c1.distanceInDegreesSquared(c2);
				if (newDistance < oldDistance) {
					oldDistance = newDistance;
					found1 = c1;
					found2 = c2;
				}
			}
		}

		return new int[] { l1.indexOf(found1), l2.indexOf(found2) };
	}

	/**
	 * This is a helper class that stores that gives access to the original segments
	 * of a joined way.
	 */
	private static class JoinedWay extends Way {
		private final List<Way> originalWays;

		public JoinedWay(Way originalWay) {
			super(-originalWay.getId(), new ArrayList<Coord>(originalWay
					.getPoints()));
			this.originalWays = new ArrayList<Way>();
			this.originalWays.add(originalWay);
		}

		public void addPoint(int index, Coord point) {
			getPoints().add(index, point);
		}

		public void addWay(Way way) {
			if (way instanceof JoinedWay) {
				this.originalWays
						.addAll(((JoinedWay) way).getOriginalWays());
			} else {
				this.originalWays.add(way);
			}
		}

		public List<Way> getOriginalWays() {
			return originalWays;
		}

	}

}
