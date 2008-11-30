package uk.me.parabola.mkgmap.reader.osm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 
 * Represent a Relation.
 * 
 * @author Rene_A
 */
public abstract class Relation extends Element {
	private final Map<Element, String> roles = new HashMap<Element, String>();
	private final List<Element> elements = new ArrayList<Element>();

	/** 
	 * Add a Way, role pair to this Relation. Only one role can be associated to a way
	 * @param role The role this way performs in this relation
	 * @param el The Way added
	 */
	public void addElement(String role, Element el) {
		roles.put(el, role);
		elements.add(el);
	}

	public abstract void processElements();

	public List<Element> getElements() {
		return elements;
	}

	protected Map<Element, String> getRoles() {
		return roles;
	}
}
