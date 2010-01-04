package uk.me.parabola.mkgmap.reader.osm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.Coord;

/**
 * Representation of an OSM Multipolygon Relation.
 * This will combine the different roles into one area.
 * 
 * @author Rene_A
 */
public class MultiPolygonRelation extends Relation {
	private Way outer;
	private final List<Way> outers = new ArrayList<Way>();
	private final List<Way> inners = new ArrayList<Way>();
	private final Map<Long, Way> myWayMap;

	/**
	 * Create an instance based on an existing relation.  We need to do
	 * this because the type of the relation is not known until after all
	 * its tags are read in.
	 * @param other The relation to base this one on.
	 * @param wayMap Map of all ways.
	 */
	public MultiPolygonRelation(Relation other, Map<Long, Way> wayMap) {
		myWayMap = wayMap;
		setId(other.getId());
		for (Map.Entry<Element, String> pairs: other.getRoles().entrySet()){
			addElement(pairs.getValue(), pairs.getKey());
			
	        String value = pairs.getValue();

			if (value != null && pairs.getKey() instanceof Way) {
				Way way = (Way) pairs.getKey();
				if (value.equals("outer")){
					outers.add(way);
				} else if (value.equals("inner")){
					inners.add(way);
				}
			}
		}

		setName(other.getName());
		copyTags(other);
	}

	/** Process the ways in this relation.
	 * Joins way with the role "outer"
	 * Adds ways with the role "inner" to the way with the role "outer"
	 */
	public void processElements() {

		if (outers != null) {
			// copy first outer way
			Iterator<Way> it = outers.iterator();
			if (it.hasNext()) {
				// duplicate outer way and remove tags for cascaded multipolygons
				Way tempWay = it.next();
				outer = new Way(-tempWay.getId());
				outer.copyTags(tempWay);
				for(Coord point: tempWay.getPoints()) {
					outer.addPoint(point);
				}
				myWayMap.put(outer.getId(), outer);
				tempWay.removeAllTags();
				it.remove();
			}
			
			// if we have more than one outer way, we join them if they are parts of a long way
			it = outers.iterator();
			while (it.hasNext()) {
				Way tempWay = it.next();
				if (tempWay.getPoints().get(0) == outer.getPoints().get(outer.getPoints().size()-1)){
					for(Coord point: tempWay.getPoints()){
						outer.addPoint(point);
					}
					tempWay.removeAllTags();
					it.remove();
					it = outers.iterator();
				}
			}
		
			for (Way w: inners) {	
				if (w != null && outer!= null) {
					int[] insert = findCpa(outer.getPoints(), w.getPoints());
					if (insert[0] >= 0 && insert[1] >= 0)
						insertPoints(w, insert[0], insert[1]);

					// remove tags from inner way that are available in the outer way
					for (Map.Entry<String, String> mapTags: outer.getEntryIteratable()){
						String key = mapTags.getKey();
						String value = mapTags.getValue();
						if (w.getTag(key) != null){
							if (w.getTag(key).equals(value))
								w.deleteTag(key);
						}
					}
				}
			}
		}
	}
	
	/**
	 * Insert Coordinates into the outer way.
	 * @param way Way to be inserted
	 * @param out Coordinates will be inserted after this point in the outer way.
	 * @param in Points will be inserted starting at this index, 
	 *    then from element 0 to (including) this element;
	 */
	private void insertPoints(Way way, int out, int in) {
		List<Coord> outList = outer.getPoints();
		List<Coord> inList = way.getPoints();
		int index = out+1;
		for (int i = in; i < inList.size(); i++) {
			outList.add(index++, inList.get(i));
		}
		for (int i = 0; i < in; i++){
			outList.add(index++, inList.get(i));
		}

		// Investigate and see if we can do the first alternative here by
		// changing the polygon splitter.  If not then always do the alternative
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
				int delta = (oLon > iLon)? -1 : 1;
				outList.add(index++, new Coord(iLat + delta, iLon));
				outList.add(index, new Coord(oLat + delta, oLon));
			} else{
				int delta = (oLat > iLat)? 1 : -1;
				outList.add(index++, new Coord(iLat, iLon + delta));
				outList.add(index, new Coord(oLat, oLon + delta));
			}
		}
	}
	
	/**
	 * find the Closest Point of Approach between two coordinate-lists	 
	 * This will probably be moved to a Utils class
	 * @param l1 First list of points.
	 * @param l2 Second list of points.
	 * @return The first element is the index in l1, the second in l2 which are the closest together.
	 */
	private static int[] findCpa(List<Coord> l1, List <Coord> l2) {
		double oldDistance = Double.MAX_VALUE;
		Coord found1 = null;
		Coord found2 = null;

		for (Coord c1: l1) {
			for(Coord c2: l2) {
				double newDistance = c1.distanceInDegreesSquared(c2);
				if (newDistance < oldDistance) {
					oldDistance = newDistance;
					found1 = c1;
					found2 = c2;				
				}				
			}
		}

		return new int[]{l1.indexOf(found1), l2.indexOf(found2)};
	}
}
