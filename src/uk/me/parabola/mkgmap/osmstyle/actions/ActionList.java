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

import java.util.List;

/**
 * Wrap an a action list along with information about tags that could be
 * affected by the actions.
 * @author Steve Ratcliffe
 */
public class ActionList {
	private final List<Action> list;

	public ActionList(List<Action> list) {
		this.list = list;
	}

	public List<Action> getList() {
		return list;
	}

	public boolean isEmpty() {
		return list.isEmpty();
	}
}
