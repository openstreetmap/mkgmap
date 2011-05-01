package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

public class BoundaryPreparer extends Thread {
	private static final Logger log = Logger.getLogger(BoundaryPreparer.class);

	private static final List<Class<? extends LoadableBoundaryDataSource>> loaders;

	static {
		String[] sources = {
				"uk.me.parabola.mkgmap.reader.osm.boundary.OsmBinBoundaryDataSource",
				// must be last as it is the default
				"uk.me.parabola.mkgmap.reader.osm.boundary.Osm5BoundaryDataSource", };

		loaders = new ArrayList<Class<? extends LoadableBoundaryDataSource>>();

		for (String source : sources) {
			try {
				@SuppressWarnings({ "unchecked" })
				Class<? extends LoadableBoundaryDataSource> c = (Class<? extends LoadableBoundaryDataSource>) Class
						.forName(source);
				loaders.add(c);
			} catch (ClassNotFoundException e) {
				// not available, try the rest
			} catch (NoClassDefFoundError e) {
				// not available, try the rest
			}
		}
	}

	/**
	 * Return a suitable boundary map reader. The name of the resource to be
	 * read is passed in. This is usually a file name, but could be something
	 * else.
	 * 
	 * @param name The resource name to be read.
	 * @return A LoadableBoundaryDataSource that is capable of reading the resource.
	 */
	private static LoadableBoundaryDataSource createMapReader(String name) {
		for (Class<? extends LoadableBoundaryDataSource> loader : loaders) {
			try {
				LoadableBoundaryDataSource src = loader.newInstance();
				if (name != null && src.isFileSupported(name))
					return src;
			} catch (InstantiationException e) {
				// try the next one.
			} catch (IllegalAccessException e) {
				// try the next one.
			} catch (NoClassDefFoundError e) {
				// try the next one
			}
		}

		// Give up and assume it is in the XML format. If it isn't we will get
		// an error soon enough anyway.
		return new Osm5BoundaryDataSource();
	}

	private final String boundaryFilename;
	private final String boundsDir;

	public BoundaryPreparer(EnhancedProperties properties) {
		this.boundaryFilename = properties
				.getProperty("createboundsfile", null);
		this.boundsDir = properties.getProperty("boundsdirectory", "bounds");
	}

	public void run() {
		if (boundaryFilename == null) {
			return;
		}
		try {
			BoundarySaver saver = new BoundarySaver(new File(boundsDir));
			LoadableBoundaryDataSource dataSource = createMapReader(boundaryFilename);
			dataSource.setBoundarySaver(saver);
			log.info("Started loading", boundaryFilename);
			dataSource.load(boundaryFilename);
			log.info("Finished loading", boundaryFilename);
			saver.end();
		} catch (FileNotFoundException exp) {
			log.error("Boundary file " + boundaryFilename + " not found.");
		}
	}
}
