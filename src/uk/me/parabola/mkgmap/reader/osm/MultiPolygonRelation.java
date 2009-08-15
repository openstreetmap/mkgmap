package uk.me.parabola.mkgmap.reader.osm;

import java.util.ArrayList;
import java.util.Collection;
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
	private final Collection<Way> inners = new ArrayList<Way>();

	/**
	 * Create an instance based on an exsiting relation.  We need to do
	 * this because the type of the relation is not known until after all
	 * its tags are read in.
	 * @param other The relation to base this one on.
	 * @param wayMap Map of all ways.
	 */
	public MultiPolygonRelation(Relation other, Map<Long, Way> wayMap) {
		setId(other.getId());
		for (Map.Entry<Element, String> pairs: other.getRoles().entrySet()){
			addElement(pairs.getValue(), pairs.getKey());
			
	        String value = pairs.getValue();

			if (value != null && pairs.getKey() instanceof Way) {
				Way way = (Way) pairs.getKey();
				if (value.equals("outer")){
					// duplicate outer way and remove tags for cascaded multipolygons
					outer = new Way(-way.getId());
					outer.copyTags(way);
					for(Coord point: way.getPoints())
						outer.addPoint(point);
					wayMap.put(outer.getId(), outer);
					if (way.getTags() != null)
						way.getTags().removeAll();
				}
				else if (value.equals("inner"))
					inners.add(way);
			}
		}

		setName(other.getName());
		copyTags(other);
	}

	/** Process the ways in this relation.
	 * Adds ways with the role "inner" to the way with the role "outer"
	 */
	public void processElements() {
		if (outer != null)
		{   

			for (Way w: inners) {	
				if (w != null) {
					int[] insert = findCpa(outer.getPoints(), w.getPoints());
					if (insert[0] >= 0 && insert[1] >= 0)
						insertPoints(w, insert[0], insert[1]);
					
					// remove tags from inner way that are available in the outer way
					if (outer.getTags() != null){
						for (Map.Entry<String, String> mapTags: outer.getTags().getKeyValues().entrySet()){
							String key = mapTags.getKey();
							String value = mapTags.getValue();
							if (w.getTag(key) != null)
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
	private void insertPoints(Way way, int out, int in){
		List<Coord> outList = outer.getPoints();
		List<Coord> inList = way.getPoints();
		int index = out+1;
		for (int i = in; i < inList.size(); i++)
			outList.add(index++, inList.get(i));
		for (int i = 0; i < in; i++)
			outList.add(index++, inList.get(i));
		
		if (outer.getPoints().size() < 32){
			outList.add(index++, inList.get(in));
			outList.add(index, outList.get(out));
		}
		else{
			// we shift the nodes to avoid duplicate nodes (large areas only)
			int oLat = outList.get(out).getLatitude();
			int oLon = outList.get(out).getLongitude();
			int iLat = inList.get(in).getLatitude();
			int iLon = inList.get(in).getLongitude();
			if ((oLat - iLat) > (oLon - iLon)){
				outList.add(index++, new Coord(iLat-1, iLon));
				outList.add(index, new Coord(oLat-1, oLon));
				}
			else{
				outList.add(index++, new Coord(iLat, iLon-1));
				outList.add(index, new Coord(oLat, oLon-1));
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
	private static int[] findCpa(List<Coord> l1, List <Coord> l2){
		double oldDistance = Double.MAX_VALUE;
		Coord found1 = null;
		Coord found2 = null;

		for (Coord c1: l1){
			for(Coord c2: l2){
				double newDistance = c1.distanceInDegreesSquared(c2);
				if (newDistance < oldDistance)
				{
					oldDistance = newDistance;
					found1 = c1;
					found2 = c2;				
				}				
			}
		}
		// FIXME: what if not found?
		return new int[]{l1.indexOf(found1), l2.indexOf(found2)};
	}
}
