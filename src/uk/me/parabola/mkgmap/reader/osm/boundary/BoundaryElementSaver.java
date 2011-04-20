package uk.me.parabola.mkgmap.reader.osm.boundary;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.ElementSaver;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.EnhancedProperties;

/**
 * This saver only keeps ways with <code>natural=coastline</code> tags. This is
 * used for loading of extra coastline files.
 * 
 * @author WanMil
 */
public class BoundaryElementSaver extends ElementSaver {
	
	public BoundaryElementSaver(EnhancedProperties args) {
		super(args);
	}

	public static boolean isBoundary(Element element) {
		if (element instanceof Relation) {
			String type = element.getTag("type");
			if ("boundary".equals(type)) {
				return true;
			}
			if ("multipolygon".equals(type)
					&& "administrative".equals(element.getTag("boundary"))) {
				return true;
			}
			return false;
		} else if (element instanceof Way) {
			return "administrative".equals(element.getTag("boundary"));
		} else {
			return false;
		}
	}

	@Override
	public void addRelation(Relation rel) {
		if (isBoundary(rel)) {
			super.addRelation(rel);
		}
	}

	@Override
	public Relation createMultiPolyRelation(Relation rel) {
		return new BoundaryRelation(rel, wayMap, getBoundingBox());
	}
	
	

}