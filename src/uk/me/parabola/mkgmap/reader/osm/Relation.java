package uk.me.parabola.mkgmap.reader.osm;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 
 * Represent a Relation.
 * 
 * @author Rene_A
 */
public abstract class Relation extends Element {
	private final List<Map.Entry<String,Element>> elements = new ArrayList<Map.Entry<String,Element>>();

	/**
	 * Add a (role, Element) pair to this Relation.
	 * @param role The role this element performs in this relation
	 * @param el The Element added
	 */
	public void addElement(String role, Element el) {
		elements.add(new AbstractMap.SimpleEntry<String,Element>(role, el));
	}

	/** Invoked after addElement() has been invoked on all Node and Way
	 * members of the relations.  Relation members (subrelations) may be
	 * added later. */
	public abstract void processElements();

	/** Get the ordered list of relation members.
	 * @return list of pairs of (role, Element)
	 */
	public List<Map.Entry<String,Element>> getElements() {
		return elements;
	}

	public String kind() {
		return "relation";
	}
}
