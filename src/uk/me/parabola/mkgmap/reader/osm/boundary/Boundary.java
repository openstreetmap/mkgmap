package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.geom.Area;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import uk.me.parabola.mkgmap.reader.osm.Tags;
import uk.me.parabola.util.Java2DConverter;

public class Boundary  {

	private final Tags tags;
	private transient Area area;
	private transient uk.me.parabola.imgfmt.app.Area bbox;
	private transient List<BoundaryElement> bList;

	public Boundary(List<BoundaryElement> mulitpolygon, Iterable<Entry<String, String>> tags) {
		bList = mulitpolygon;
		this.tags = new Tags();
		for (Entry<String, String> tag : tags) {
			this.tags.put(tag.getKey(), tag.getValue());
		}
	}

	public Boundary(List<BoundaryElement> mulitpolygon, Tags tags) {
		this.tags = tags;
		this.bList = mulitpolygon;
	}

	
	public Boundary(Area area, Map<String, String> tags) {
		this(area, tags.entrySet());
	}

	public Boundary(Area area, Tags tags) {
		this.area = new Area(area);
		this.tags = new Tags();
		Iterator<Entry<String, String>> tagsIter = tags.entryIterator();
		while (tagsIter.hasNext()) {
			Entry<String, String> tag = tagsIter.next();
			this.tags.put(tag.getKey(), tag.getValue());
		}
	}

	public Boundary(Area area, Iterable<Entry<String, String>> tags) {
		this.area = new Area(area);
		this.tags = new Tags();
		for (Entry<String, String> tag : tags) {
			this.tags.put(tag.getKey(), tag.getValue());
		}
	}

	public Tags getTags() {
		return tags;
	}

	public Area getArea() {
		if (area == null) {
			area =new Area();
			for (BoundaryElement bElem : bList) {
				if (bElem.isOuter()) {
					area.add(bElem.getArea());
				} else {
					area.subtract(bElem.getArea());
				}
			}
			bList = null;
		}
		return area;
	}
	
	public uk.me.parabola.imgfmt.app.Area getBbox() {
		if (bbox == null) {
			bbox = Java2DConverter.createBbox(getArea());
		}
		return bbox;
	}
	
	public List<BoundaryElement> getBoundaryElements() {
		if (bList == null) {
			bList = BoundaryUtil.splitToElements(area);
		}
		return bList;
	}
}
