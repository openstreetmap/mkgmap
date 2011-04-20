package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.geom.Area;
import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import uk.me.parabola.mkgmap.reader.osm.Tags;

public class Boundary implements Serializable {

	private final Tags tags;
	private transient Area area;

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
		return area;
	}

	private void writeObject(java.io.ObjectOutputStream stream)
			throws IOException {
		stream.defaultWriteObject();
		List<BoundaryElement> bList = BoundaryUtil.splitToElements(area);
		stream.writeObject(bList);
	}

	private void readObject(java.io.ObjectInputStream stream)
			throws IOException, ClassNotFoundException {
		stream.defaultReadObject();
		List<BoundaryElement> bList = (List<BoundaryElement>) stream
				.readObject();
		this.area = BoundaryUtil.convertToArea(bList);
	}

}
