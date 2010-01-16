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

	private final Map<Long, Way> myWayMap;
	private final Map<Long, String> roleMap = new HashMap<Long, String>();

	private final uk.me.parabola.imgfmt.app.Area bbox;
	private final java.awt.geom.Area awtBbox;

	private final ArrayList<BitSet> containsMatrix = new ArrayList<BitSet>();

	// this list contains the polygons that should be used to create the garmin
	// map
	public final List<Way> polygonResults = new ArrayList<Way>();

	private ArrayList<JoinedWay> rings;

	private static final List<String> relationTags = Arrays.asList("boundary",
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
		myWayMap = wayMap;
		this.bbox = bbox;

		List<Coord> points = new ArrayList<Coord>(5);
		points.add(new Coord(bbox.getMinLat(), bbox.getMinLong()));
		points.add(new Coord(bbox.getMaxLat(), bbox.getMinLong()));
		points.add(new Coord(bbox.getMaxLat(), bbox.getMaxLong()));
		points.add(new Coord(bbox.getMinLat(), bbox.getMaxLong()));
		points.add(new Coord(bbox.getMinLat(), bbox.getMinLong()));
		awtBbox = createArea(points);

		setId(other.getId());

		for (Map.Entry<String, Element> pair : other.getElements()) {
			String role = pair.getKey();
			Element el = pair.getValue();
			log.debug(" ", role, el.toBrowseURL());
			addElement(role, el);
			roleMap.put(el.getId(), role);
		}

		setName(other.getName());
		copyTags(other);
	}

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
	 * @param checkOnly
	 *            <code>true</code> checks only and does not perform the join
	 *            operation
	 * @return <code>true</code> if w2 way (or could) be joined to w1
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
				// if a role is null then it is used as universal
				// check if the roles of the ways are matching
				if (joinRole == null || joinRole.equals("") || tempRole == null
						|| tempRole.equals("") || joinRole.equals(tempRole)) {
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
				log.warn("Join ways with different roles. " + toBrowseURL());
				log.warn("Way1:", joinWay, "Role:", getRole(joinWay));
				log.warn("Way2:", wrongRoleWay, "Role:", getRole(wrongRoleWay));
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
				way.closeWayArtificial();
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
					log.warn("Unclosed polygons in multipolygon relation "
							+ getId() + ":");
				}
				for (Way orgWay : tempWay.getOriginalWays()) {
					log.warn(" - way:", orgWay.getId(), "role:",
							getRole(orgWay), "osm:", orgWay.toBrowseURL());
				}

				it.remove();
				first = false;
			}
		}
	}

	private BitSet findOutmostRings(BitSet candidates, BitSet roleFilter) {
		BitSet realCandidates = ((BitSet) candidates.clone());
		realCandidates.and(roleFilter);
		log.debug("Checkmatrix",realCandidates);
		BitSet result =  findOutmostRings(realCandidates);
		log.debug("Outmost",result);
		return result;
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
		log.info("processing", toBrowseURL());

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
					|| hasUsefulTags(currentRing.ring);

			if (processRing) {

				List<Way> innerWays = new ArrayList<Way>(holes.size());
				for (RingStatus holeRingStatus : holes) {
					innerWays.add(holeRingStatus.ring);
				}

				List<Way> singularOuterPolygons = processRing(currentRing.ring,
						innerWays);

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
						&& hasUsefulTags(this);
				if (useRelationTags) {
					// the multipolygon contains tags that overwhelm the
					// tags of the outer ring
					for (Way p : singularOuterPolygons) {
						p.copyTags(this);
					}
				}

				polygonResults.addAll(singularOuterPolygons);
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

		// log.info("Result");
		// StringBuilder sb = new StringBuilder();
		// for (Way resultWay : polygonResults) {
		// sb.append("Way: " + resultWay.getId() + " Points: "
		// + resultWay.getPoints().size() + " Closed: "
		// + resultWay.isClosed() + " Tags: ");
		// for (Map.Entry<String, String> entry : resultWay
		// .getEntryIteratable()) {
		// sb.append(entry.getKey() + "=" + entry.getValue() + "; ");
		// }
		// log.info(sb.toString());
		// sb.setLength(0);
		// }

		// the polygonResults contain all polygons that should be used in the
		// map
		for (Way mpWay : polygonResults) {
			// store the ways generated by the multipolygon code
			// to the way map
			myWayMap.put(mpWay.getId(), mpWay);
		}

		cleanup();
	}

	private void cleanup() {
		roleMap.clear();
		containsMatrix.clear();
		rings.clear();
		polygonResults.clear();
	}

	private List<Way> processRing(Way outerWay, List<Way> innerWays) {
		// this list contains all non overlapping and singular areas
		// of the outer way
		List<Area> outerAreas = new ArrayList<Area>();

		// 1st create an Area object of the outerWay and put it to the list
		// this must be clipped by the bounding box
		Area oa = createArea(outerWay.getPoints());
		// the polygons will be later clipped in the style converter
		// so it is not necessary to clip it here
		// oa.intersect(awtBbox);
		outerAreas.add(oa);

		for (Way innerWay : innerWays) {
			Area innerArea = createArea(innerWay.getPoints());

			List<Area> outerAfterThisStep = new ArrayList<Area>();
			for (Area outerArea : outerAreas) {
				// check if this outerArea is probably intersected by the inner
				// area
				// to save computation time in case it is not
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
					// original
					// polygon. This will make two new areas, and each area will
					// be one
					// (or more) polygons.
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

		List<Way> outerWays = new ArrayList<Way>(outerAreas.size());
		for (Area area : outerAreas) {
			Way w = singularAreaToWay(area, FakeIdGenerator.makeFakeId());
			if (w != null) {
				w.copyTags(outerWay);
				outerWays.add(w);
			}
		}

		return outerWays;
	}

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

	private Polygon createPolygon(List<Coord> points) {
		Polygon polygon = new Polygon();
		for (Coord co : points) {
			polygon.addPoint(co.getLongitude(), co.getLatitude());
		}
		return polygon;
	}

	private Area createArea(List<Coord> points) {
		return new Area(createPolygon(points));
	}

	private Way singularAreaToWay(Area area, long wayId) {
		if (area.isSingular() == false) {
			log
					.warn(
							"singularAreaToWay called with non singular area. Multipolygon ",
							toBrowseURL());
		}
		if (area.isEmpty()) {
			log.debug("Empty area.", toBrowseURL());
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

	private boolean hasUsefulTags(JoinedWay way) {
		for (Way segment : way.getOriginalWays()) {
			if (hasUsefulTags(segment)) {
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

	private void createContainsMatrix(List<JoinedWay> wayList) {
		for (int i = 0; i < wayList.size(); i++) {
			containsMatrix.add(new BitSet());
		}

		// mark which ring contains which ring
		for (int i = 0; i < wayList.size(); i++) {
			JoinedWay tempRing = wayList.get(i);
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
		
		log.debug("Containsmatrix");
		for (BitSet b : containsMatrix) {
			log.debug(b);
		}
	}

	/*
	 * this is an alternative createContainsMatrix method seems to speed up only
	 * if lots of inner ways are in the mulitpolygon
	 */
	// private void createContainsMatrix(List<JoinedWay> wayList) {
	// long t1 = System.currentTimeMillis();
	//		
	// for (int i = 0; i < wayList.size(); i++) {
	// containsMatrix.add(new BitSet());
	// }
	//
	// // use this matrix to check which matrix element has been
	// // calculated
	// ArrayList<BitSet> finishedMatrix = new ArrayList<BitSet>(wayList.size());
	//
	// for (int i = 0; i < wayList.size(); i++) {
	// BitSet matrixRow = new BitSet();
	// // an element does not contain itself
	// matrixRow.set(i);
	// finishedMatrix.add(matrixRow);
	// }
	//
	// for (int rowIndex = 0; rowIndex < wayList.size(); rowIndex++) {
	// Way potentialOuterRing = wayList.get(rowIndex);
	// BitSet containsColumns = containsMatrix.get(rowIndex);
	// BitSet finishedCol = finishedMatrix.get(rowIndex);
	//
	// // get all non calculated columns of the matrix
	// for (int colIndex = finishedCol.nextClearBit(0); colIndex >= 0 &&
	// colIndex < wayList.size(); colIndex = finishedCol
	// .nextClearBit(colIndex + 1)) {
	//
	// boolean contains = contains(potentialOuterRing, wayList.get(colIndex));
	//
	// if (contains) {
	// containsColumns.set(colIndex);
	//					
	// // we also know that the inner ring does not contain the outer ring
	// // so we can set the finished bit for this matrix element
	// finishedMatrix.get(colIndex).set(rowIndex);
	//					
	// // additionally we know that the outer ring contains all rings
	// // that are contained by the inner ring
	// containsColumns.or(containsMatrix.get(colIndex));
	// finishedCol.or(containsColumns);
	// }
	//
	// // this matrix element is calculated now
	// finishedCol.set(colIndex);
	// }
	// }
	//		
	// long t2 = System.currentTimeMillis();
	// log.warn("createMatrix",(t2-t1),"ms");
	// }

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

				boolean intersects = Line2D.linesIntersect(p1_1.getLongitude(),
						p1_1.getLatitude(), p1_2.getLongitude(), p1_2
								.getLatitude(), p2_1.getLongitude(), p2_1
								.getLatitude(), p2_2.getLongitude(), p2_2
								.getLatitude());

				if (intersects) {
					if ((ring1.isClosedArtificially() && it1.hasNext() == false)
							|| (ring2.isClosedArtificially() && it2.hasNext() == false)) {
						// don't care about this intersection
						// one of the rings is closed by this mp code and the
						// closing way causes the intersection
						log.warn("Way",ring1,"may contain way",ring2,". Ignoring artificial generated intersection.");
					} else {
						return false;
					}
				}
			}
		}

		// don't have any intersection
		// => ring1 contains ring2
		return true;
	}

	/**
	 * This is a helper class that stores that gives access to the original
	 * segments of a joined way.
	 */
	private static class JoinedWay extends Way {
		private final List<Way> originalWays;
		private boolean closedArtifical = false;

		public JoinedWay(Way originalWay) {
			super(FakeIdGenerator.makeFakeId(), new ArrayList<Coord>(originalWay
					.getPoints()));
			this.originalWays = new ArrayList<Way>();
			addWay(originalWay);
		}

		public void addPoint(int index, Coord point) {
			getPoints().add(index, point);
		}

		public void addWay(Way way) {
			if (way instanceof JoinedWay) {
				for (Way w : ((JoinedWay) way).getOriginalWays()) {
					addWay(w);
				}
			} else {
				this.originalWays.add(way);
				addTagsOf(way);
				if (getName() == null && way.getName() != null) {
					setName(way.getName());
				}
			}
			log.debug("Joined", this.getId(), "with", way.getId());
		}

		public void closeWayArtificial() {
			addPoint(getPoints().get(0));
			closedArtifical = true;
		}

		public boolean isClosedArtificially() {
			return closedArtifical;
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
			super();
			this.outer = outer;
			this.index = index;
			this.ring = ring;
		}
	}
}
