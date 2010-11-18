package uk.me.parabola.mkgmap.reader.osm;

import java.io.InputStream;
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

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.xml.Osm5XmlHandler;
import uk.me.parabola.util.EnhancedProperties;

public class CoastlineFileLoader {

	private static final Logger log = Logger
			.getLogger(CoastlineFileLoader.class);

	private final Set<String> coastlineFiles;
	private Collection<CoastlineWay> coastlines;

	private AtomicBoolean coastlinesLoaded = new AtomicBoolean(false);
	private AtomicBoolean loadingStarted = new AtomicBoolean(false);

	private CoastlineFileLoader() {
		this.coastlineFiles = new HashSet<String>();
	}

	private static CoastlineFileLoader loader = new CoastlineFileLoader();

	public static synchronized CoastlineFileLoader getCoastlineLoader() {
		return loader;
	}

	public synchronized void setCoastlineFiles(String[] coastlineFiles) {
		this.coastlineFiles.addAll(Arrays.asList(coastlineFiles));
	}

	public void loadCoastlines() {
		boolean nowStarted = loadingStarted.compareAndSet(false, true);
		if (nowStarted) {
			// load it
			loadCoastlinesImpl();
		} else {
			log.info("Coastlines already started");
		}
	}

	private synchronized void loadCoastlinesImpl() {
		log.info("Load coastlines");
		EnhancedProperties coastProps = new EnhancedProperties();
		Set<String> coastlineTags = new HashSet<String>();
		coastlineTags.add("natural");
		for (String coastlineFile : coastlineFiles) {
			try {
				InputStream is = Utils.openFile(coastlineFile);
				SAXParserFactory parserFactory = SAXParserFactory.newInstance();
				parserFactory.setXIncludeAware(true);
				parserFactory.setNamespaceAware(true);
				SAXParser parser = parserFactory.newSAXParser();

				Osm5XmlHandler handler = new Osm5XmlHandler(coastProps);
				Osm5XmlHandler.SaxHandler saxHandler = handler.new SaxHandler();

				CoastlineElementSaver elementSaver = new CoastlineElementSaver(
						coastProps);
				handler.setElementSaver(elementSaver);
				handler.setHooks(new OsmReadingHooksAdaptor());

				handler.setUsedTags(coastlineTags);

				// parse the xml file
				parser.parse(is, saxHandler);

				elementSaver.loadFinished();
				log.info("Coastline ways from",coastlineFile,"loaded.");

				// GC can now garbage the complete load mechanism
				handler = null;
				is = null;
				parser = null;
				parserFactory = null;
				saxHandler = null;
				
				
				ArrayList<Way> ways = new ArrayList<Way>(elementSaver.getWays().values());
				elementSaver = null;
				ways = SeaGenerator.joinWays(ways);
				coastlines = new ArrayList<CoastlineWay>(ways.size());
				ListIterator<Way> wayIter = ways.listIterator();
				ways = null;
				while (wayIter.hasNext()) {
					Way way = wayIter.next();
					wayIter.remove();
					coastlines.add(new CoastlineWay(way.getId(), way.getPoints()));
				}

				log.info(coastlines.size(), "coastlines loaded from",coastlineFile);

			} catch (Exception exp) {
				log.error(exp.toString(), exp);
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

	private static class CoastlineElementSaver extends ElementSaver {

		public CoastlineElementSaver(EnhancedProperties args) {
			super(args);
		}

		@Override
		public void addNode(Node node) {
			return;
		}

		@Override
		public void addRelation(Relation rel) {
			return;
		}
	}

}
