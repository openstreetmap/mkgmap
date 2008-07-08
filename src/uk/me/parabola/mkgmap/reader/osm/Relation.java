package uk.me.parabola.mkgmap.reader.osm;

import java.util.HashMap;
import java.util.Map;

/** 
 * Represent a Relation.
 * 
 * @author Rene_A
 */
public class Relation extends Element {
	private final Map<Way, String> roles = new HashMap<Way, String>();

	/** 
	 * Add a Way, role pair to this Relation. Only one role can be associated to a way
	 * @param role The role this way performs in this relation
	 * @param way The Way added 
	 */
	public void addWay(String role, Way way) {
		roles.put(way, role);
	}
	
	/**
	 * @return Is this a multiPolygon relation
	 */
	private boolean isMultiPolygon() {
		String type = getTag("type");
		return (type != null) && (type.equals("multipolygon"));
	}
	
	public void processWays() {
		//NOTE: this will get ugly if more types of relation are added.  
		if (isMultiPolygon())
			processMultiPolygons();		
	}
	
	private void processMultiPolygons() {
		MultiPolygonRelation r = new MultiPolygonRelation(roles);
		r.processWays();
	}
}
