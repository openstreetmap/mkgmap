/*
 * Copyright (C) 2013
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * Set the first unset label on the given element. The tags of the element may be
 * used in setting the label.
 *
 * We have a list of possible substitutions.
 */
public class AddLabelAction extends ValueBuildedAction {

	/**
	 * Search for the first matching pattern and set the first unset element label
	 * to it.
	 *
	 * If all four labels are already set, then nothing is done.
	 *
	 * @param el The element on which a label may be set.
	 */
	public void perform(Element el) {
		for (int index = 1; index <=4; index++) {
			// find the first unset label and set it
			if (el.getTag("mkgmap:label:"+index) == null) {
				for (ValueBuilder vb : getValueBuilder()) {
					String s = vb.build(el, el);
					if (s != null) {
						// now check if the new label is different to all other labels
						for (int n = index-1; n>= 1; n--) {
							if (s.equals(el.getTag("mkgmap:label:"+n))) {
								// value is equal to a previous label
								// do not use it
								return;
							}
						}
						
						// set the label
						el.addTag("mkgmap:label:"+index, s);
						return;
					}
				}
				return;
			}
		}
	}

	public String toString() {
		StringBuilder sb = new  StringBuilder();
		sb.append("addlabel ");
		for (ValueBuilder vb : getValueBuilder()) {
			sb.append(vb);
			sb.append(" | ");
		}
		sb.setLength(sb.length() - 1);
		return sb.toString();
	}
}
