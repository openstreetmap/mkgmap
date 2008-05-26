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
 * Create date: Apr 27, 2008
 */
package uk.me.parabola.mkgmap.reader.osm;

/**
 * A rule to map an element from osm to a garmin type.  Each rule has a
 * priority and lower priority rules win when more than one matches a
 * given element.
 * 
 * @author Steve Ratcliffe
 */
public interface TypeRule {

	public GType resolveType(Element el);

	public int getPriority();

	public void setPriority(int index);

	public boolean isBetter(TypeRule other);
}
