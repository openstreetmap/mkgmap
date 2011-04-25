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
		if (way.isClosed() && BoundaryElementSaver.isBoundary(way)) {
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
