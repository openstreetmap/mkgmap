/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 15-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.util.List;

import uk.me.parabola.mkgmap.osmstyle.actions.Action;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.TypeResult;

/**
 * An action rule modifies the tags on the incoming element.
 *
 * It can also have an expression, and does not need to have a Type.  If
 * there is no type then the resolve method does not send any results.
 * The tags on the element may have been modified however.
 *
 * @author Steve Ratcliffe
 */
public class ActionRule implements Rule {
	private final Op expression;
	private final List<Action> actions;
	private final GType type;

	public ActionRule(Op expression, List<Action> actions, GType type) {
		assert actions != null;
		this.expression = expression;
		this.actions = actions;
		this.type = type;
	}

	public ActionRule(Op expression, List<Action> actions) {
		assert actions != null;
		this.expression = expression;
		this.actions = actions;
		this.type = null;
	}
	public void resolveType(Element el, TypeResult result) {
		Element element = el;
		if (expression != null) {
			if (!expression.eval(element))
				return;
			// If this is a continue and we are not to propagate the effects
			// of the action on the element to further rules, then make
			// a copy of the element so that the original is unsullied.
			//
			// There is another reason we need to copy: since there will be
			if (type != null && !type.isPropogateActions() && !(element instanceof Relation)) {
				element = element.copy();
			}
		}

		for (Action a : actions)
			a.perform(element);

		result.add(element, type);
	}

	public String toString() {
		StringBuilder fmt = new StringBuilder();
		if (expression != null)
			fmt.append(expression);

		fmt.append(" {");
		for (Action a : actions)
			fmt.append(a);
		fmt.append("}");

		if (type != null) {
			fmt.append(' ');
			fmt.append(type);
		}

		return fmt.toString();
	}
}
