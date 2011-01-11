package uk.me.parabola.mkgmap.reader.osm;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.reader.osm.xml.Osm5CoastDataSource;
import uk.me.parabola.util.EnhancedProperties;

public final class CoastlineFileLoader {

	private static final Logger log = Logger
			.getLogger(CoastlineFileLoader.class);

	private static final List<Class<? extends LoadableMapDataSource>> coastFileLoaders;

	static {
		String[] sources = {
				"uk.me.parabola.mkgmap.reader.osm.bin.OsmBinCoastDataSource",
				// must be last as it is the default
				"uk.me.parabola.mkgmap.reader.osm.xml.Osm5CoastDataSource", };

		coastFileLoaders = new ArrayList<Class<? extends LoadableMapDataSource>>();

		for (String source : sources) {
			try {
				@SuppressWarnings({ "unchecked" })
				Class<? extends LoadableMapDataSource> c = (Class<? extends LoadableMapDataSource>) Class
						.forName(source);
				coastFileLoaders.add(c);
			} catch (ClassNotFoundException e) {
				// not available, try the rest
			} catch (NoClassDefFoundError e) {
				// not available, try the rest
			}
		}
	}

	private final Set<String> coastlineFiles;
	private final Collection<CoastlineWay> coastlines = new ArrayList<CoastlineWay>();

	private final AtomicBoolean coastlinesLoaded = new AtomicBoolean(false);
	private final AtomicBoolean loadingStarted = new AtomicBoolean(false);

	private final EnhancedProperties coastConfig = new EnhancedProperties();

	private CoastlineFileLoader() {
		this.coastlineFiles = new HashSet<String>();
	}

	private static final CoastlineFileLoader loader = new CoastlineFileLoader();

	public static synchronized CoastlineFileLoader getCoastlineLoader() {
		return loader;
	}

	public synchronized void setCoastlineFiles(String[] coastlineFiles) {
		this.coastlineFiles.addAll(Arrays.asList(coastlineFiles));
	}

	public void loadCoastlines() {
		boolean loadInThisThread = loadingStarted.compareAndSet(false, true);
		if (loadInThisThread) {
			// load it
			loadCoastlinesImpl();
		} else {
			log.info("Coastline loading performed by another thread");
		}
	}

	private OsmMapDataSource loadFromFile(String name)
			throws FileNotFoundException, FormatException {
		OsmMapDataSource src = createMapReader(name);
		src.config(getConfig());
		log.info("Started loading coastlines from", name);
		src.load(name);
		log.info("Finished loading coastlines from", name);
		return src;
	}

	public static OsmMapDataSource createMapReader(String name) {
		for (Class<? extends LoadableMapDataSource> loader : coastFileLoaders) {
			try {
				LoadableMapDataSource src = loader.newInstance();
				if (name != null && src instanceof OsmMapDataSource
						&& src.isFileSupported(name))
					return (OsmMapDataSource) src;
			} catch (InstantiationException e) {
				// try the next one.
			} catch (IllegalAccessException e) {
				// try the next one.
			} catch (NoClassDefFoundError e) {
				// try the next one
			}
		}

		// Give up and assume it is in the XML format. If it isn't we will get
		// an
		// error soon enough anyway.
		return new Osm5CoastDataSource();
	}

	private Collection<Way> loadFile(String filename)
			throws FileNotFoundException {
		OsmMapDataSource src = loadFromFile(filename);
		return src.getElementSaver().getWays().values();
	}

	private EnhancedProperties getConfig() {
		return coastConfig;
	}

	private synchronized void loadCoastlinesImpl() {
		log.info("Load coastlines");
		for (String coastlineFile : coastlineFiles) {
			try {
				int nBefore = coastlines.size();

				Collection<Way> loadedCoastlines = loadFile(coastlineFile);
				log.info(loadedCoastlines.size(), "coastline ways from",
						coastlineFile, "loaded.");

				ArrayList<Way> ways = SeaGenerator.joinWays(loadedCoastlines);
				ListIterator<Way> wayIter = ways.listIterator();
				ways = null;
				while (wayIter.hasNext()) {
					Way way = wayIter.next();
					wayIter.remove();
					coastlines.add(new CoastlineWay(way.getId(), way
							.getPoints()));
				}

				log.info((coastlines.size() - nBefore),
						"coastlines loaded from", coastlineFile);
			} catch (FileNotFoundException exp) {
				log.error("Coastline file " + coastlineFile + " not found.");
			} catch (Exception exp) {
				log.error(exp);
				exp.printStackTrace();
			}
		}
		coastlinesLoaded.set(true);
	}

	public Collection<Way> getCoastlines(Area bbox) {
		if (coastlinesLoaded.get() == false) {
			synchronized (this) {
				loadCoastlines();
			}
		}
		Collection<Way> ways = new ArrayList<Way>();
		for (CoastlineWay w : coastlines) {
			if (w.getBbox().intersects(bbox)) {
				Way x = new Way(FakeIdGenerator.makeFakeId(), w.getPoints());
				x.addTag("natural", "coastline");
				ways.add(x);
			}
		}
		return ways;
	}

	public static class CoastlineWay extends Way {
		private final Area bbox;

		public CoastlineWay(long id, List<Coord> points) {
			super(id, points);
			if (points.isEmpty()) {
				throw new IllegalArgumentException(
						"No support for empty ways. WayId: " + id);
			}

			if (log.isDebugEnabled())
				log.debug("Create coastline way", id, "with", points.size(),
						"points");
			Coord firstPoint = getPoints().get(0);

			int minLat = firstPoint.getLatitude();
			int maxLat = firstPoint.getLatitude();
			int minLong = firstPoint.getLongitude();
			int maxLong = firstPoint.getLongitude();

			for (Coord c : getPoints()) {
				if (c.getLatitude() < minLat) {
					minLat = c.getLatitude();
				} else if (c.getLatitude() > maxLat) {
					maxLat = c.getLatitude();
				}
				if (c.getLongitude() < minLong) {
					minLong = c.getLongitude();
				} else if (c.getLongitude() > maxLong) {
					maxLong = c.getLongitude();
				}
			}
			bbox = new Area(minLat, minLong, maxLat, maxLong);
		}

		@Override
		public String getTag(String key) {
			if ("natural".equals(key)) {
				return "coastline";
			} else {
				return null;
			}
		}

		@Override
		public String toTagString() {
			return "[natural=coastline]";
		}

		@Override
		public Map<String, String> getTagsWithPrefix(String prefix,
				boolean removePrefix) {
			if ("natural".startsWith(prefix)) {
				if (removePrefix) {
					return Collections.singletonMap(
							"natural".substring(prefix.length()), "coastline");
				} else {
					return Collections.singletonMap("natural", "coastline");
				}
			} else {
				return Collections.emptyMap();
			}
		}

		@Override
		protected void removeAllTags() {
		}

		@Override
		public Iterable<Entry<String, String>> getEntryIteratable() {
			return Collections.singletonMap("natural", "coastline").entrySet();
		}

		public Area getBbox() {
			return bbox;
		}
	}

}
