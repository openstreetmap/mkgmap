/*
 * Copyright (C) 2006, 2011.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.mkgmap.reader.osm.boundary;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.Java2DConverter;

public class BoundaryConverter implements OsmConverter {

	private final BoundarySaver saver;
	public BoundaryConverter(BoundarySaver saver) {
		this.saver= saver;
	}
	
	@Override
	public void convertWay(Way way) {
		if (BoundaryElementSaver.isBoundary(way)) {
			java.awt.geom.Area boundArea = new java.awt.geom.Area(Java2DConverter.createArea(way.getPoints()));
			Boundary boundary = new Boundary(boundArea, way.getEntryIteratable());
			boundary.getTags().put("mkgmap:boundaryid", "w"+way.getId());
			saver.addBoundary(boundary);
		}
	}

	@Override
	public void convertNode(Node node) {
	}

	@Override
	public void convertRelation(Relation relation) {
		if (relation instanceof BoundaryRelation) {
			Boundary boundary = ((BoundaryRelation)relation).getBoundary();
			if (boundary!=null)
				saver.addBoundary(boundary);
		}
	}

	@Override
	public void setBoundingBox(Area bbox) {

	}

	@Override
	public void end() {
	}

}
