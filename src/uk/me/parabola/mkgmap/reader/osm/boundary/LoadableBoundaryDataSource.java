package uk.me.parabola.mkgmap.reader.osm.boundary;

import uk.me.parabola.mkgmap.general.LoadableMapDataSource;

public interface LoadableBoundaryDataSource extends LoadableMapDataSource {

	public void setBoundarySaver(BoundarySaver saver);
}
