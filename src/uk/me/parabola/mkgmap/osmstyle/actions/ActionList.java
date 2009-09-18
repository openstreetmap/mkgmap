/*
 * Copyright (c) 2009.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package uk.me.parabola.mkgmap.osmstyle.actions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wrap an a action list allong with information about tags that could be
 * affected by the actions.
 * @author Steve Ratcliffe
 */
public class ActionList {
	private final List<Action> list;
	private Set<String> changeableTags = new HashSet<String>();

	public ActionList(List<Action> list) {
		this.list = list;
	}

	public List<Action> getList() {
		return list;
	}

	public Set<String> getChangeableTags() {
		return changeableTags;
	}

	public void setChangeableTags(Set<String> changeableTags) {
		this.changeableTags = changeableTags;
	}

	public boolean isEmpty() {
		return list.isEmpty();
	}
}
