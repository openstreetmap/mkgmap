package uk.me.parabola.mkgmap.reader.osm;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.regex.Pattern;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.build.Locator;
import uk.me.parabola.mkgmap.reader.osm.boundary.Boundary;
import uk.me.parabola.mkgmap.reader.osm.boundary.BoundaryUtil;
import uk.me.parabola.util.ElementQuadTree;
import uk.me.parabola.util.EnhancedProperties;
import uk.me.parabola.util.MultiHashMap;

public class LocationHook extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger.getLogger(LocationHook.class);

	private ElementSaver saver;
	private final List<String> nameTags = new ArrayList<String>();
	private final Locator locator = new Locator();

	private File boundaryDir;

	private static final Pattern COMMA_OR_SPACE_PATTERN = Pattern
			.compile("[,\\s]+");
	private static final Pattern COMMA_OR_SEMICOLON_PATTERN = Pattern
			.compile("[,;]+");

	private final static Hashtable<String, String> mkgmapTags = new Hashtable<String, String>() {
		{
			put("admin_level=1", "mkgmap:admin_level1");
			put("admin_level=2", "mkgmap:admin_level2");
			put("admin_level=3", "mkgmap:admin_level3");
			put("admin_level=4", "mkgmap:admin_level4");
			put("admin_level=5", "mkgmap:admin_level5");
			put("admin_level=6", "mkgmap:admin_level6");
			put("admin_level=7", "mkgmap:admin_level7");
			put("admin_level=8", "mkgmap:admin_level8");
			put("admin_level=9", "mkgmap:admin_level9");
			put("admin_level=10", "mkgmap:admin_level10");
			put("admin_level=11", "mkgmap:admin_level11");
			put("postal_code", "mkgmap:postcode");
		}
	};

	public boolean init(ElementSaver saver, EnhancedProperties props) {
		if (props.containsKey("index") == false) {
			log.info("Disable LocationHook because no index is created.");
			return false;
		}

		this.saver = saver;

		String nameTagProp = props.getProperty("name-tag-list", "name");
		nameTags.addAll(Arrays.asList(COMMA_OR_SPACE_PATTERN.split(nameTagProp)));

		boundaryDir = new File(props.getProperty("boundsdirectory", "bounds"));
		if (boundaryDir.exists() == false) {
			log.error("Disable LocationHook because boundary directory does not exist. Dir: "
					+ boundaryDir);
			return false;
		}
		return true;
	}

	public void end() {
		long t1 = System.currentTimeMillis();
		log.info("Starting with location hook");

		List<Element> allElements = new ArrayList<Element>(saver.getWays()
				.size() + saver.getNodes().size());

		MultiHashMap<String, Way> locationElements = new MultiHashMap<String, Way>();

		// add all nodes that might be converted to a garmin node (tagcount > 0)
		for (Node node : saver.getNodes().values()) {
			if (node.getTagCount() > 0) {
				allElements.add(node);
			}
		}

		// add all ways that might be converted to a garmin way (tagcount > 0)
		// and save all polygons that contains location information
		for (Way way : saver.getWays().values()) {
			if (way.getTagCount() == 0) {
				continue;
			}

			// in any case add it to the element list
			allElements.add(way);

			if (way.isClosed()
					&& "polyline".equals(way.getTag("mkgmap:stylefilter")) == false) {
				// it is a polygon - check if it contains location relevant
				// information

				if ("administrative".equals(way.getTag("boundary"))
						&& way.getTag("admin_level") != null) {
					try {
						int adminlevel = Integer.valueOf(way
								.getTag("admin_level"));
						if (adminlevel >= 1 && adminlevel <= 11) {
							locationElements.add("admin_level=" + adminlevel,
									way);
						}
					} catch (NumberFormatException nfe) {
						log.warn("Wrong admin_level format in way "
								+ way.getId() + " " + way.toTagString());
					}
				} else if ("postal_code".equals(way.getTag("boundary"))) {
					locationElements.add("postal_code", way);
				}
			}
		}

		List<Boundary> boundaries = BoundaryUtil.loadBoundaries(boundaryDir,
				saver.getBoundingBox());

		ListIterator<Boundary> bIter = boundaries.listIterator();
		while (bIter.hasNext()) {
			Boundary b = bIter.next();
			
			String name = getName(b.getTags());
			String zip = getZip(b.getTags());
			if (name == null && zip == null) {
				log.warn("Cannot process boundary element because it contains no name and no zip tag. "+b.getTags());

				bIter.remove();
				continue;
			}

			if ("2".equals(b.getTags().get("admin_level"))) {
				log.info("Input country: " + name);
				name = locator.fixCountryString(name);
				log.info("Fixed country: " + name);
				String cCode = locator.getCountryCode(name);
				if (cCode != null) {
					name = cCode;
				} else {
					log.error("Ccode == null name="+name);
				}
				log.info("Coded: " + name);
			}
			if (name != null)
				b.getTags().put("mkgmap:bname", name);
			
			if (zip != null)
				b.getTags().put("mkgmap:bzip", zip);
		}

		log.error("Element lists created after "
				+ (System.currentTimeMillis() - t1) + " ms");

		log.info("Creating quadtree for", allElements.size(), "elements");
		ElementQuadTree quadTree = new ElementQuadTree(allElements);
		log.error("Quadtree created after " + (System.currentTimeMillis() - t1)
				+ " ms");



		bIter = boundaries.listIterator();
		while (bIter.hasNext()) {
			Boundary boundary = bIter.next();
			String admin_level = boundary.getTags().get("admin_level");
			String admMkgmapTag = mkgmapTags.get("admin_level=" + admin_level);
			String zipMkgmapTag = mkgmapTags.get("postal_code");

			String admName = boundary.getTags().get("mkgmap:bname");
			String zip = boundary.getTags().get("mkgmap:bzip");
			
			if (admMkgmapTag == null && zip == null) {
				log.error("Cannot find any mkgmap tag for " + boundary.getTags());
				continue;
			}

			Set<Element> elemsInLocation = quadTree.get(boundary.getArea());

			for (Element elem : elemsInLocation) {
				if (admName != null && admMkgmapTag != null && elem.getTag(admMkgmapTag) == null) {
					elem.addTag(admMkgmapTag, admName);
					if (log.isDebugEnabled())
						log.debug("Added tag", admMkgmapTag, "=", admName, "to",
								elem.toTagString());
				}
				if (zip != null && zipMkgmapTag != null && elem.getTag(zipMkgmapTag) == null) {
					elem.addTag(zipMkgmapTag, zip);
					if (log.isDebugEnabled())
						log.debug("Added tag", zipMkgmapTag, "=", zip, "to",
								elem.toTagString());
				}
			}
			bIter.remove();
		}


		log.error("Location hook finished in "
				+ (System.currentTimeMillis() - t1) + " ms");
	}


	private String getName(Tags tags) {
		for (String nameTag : nameTags) {
			String nameTagValue = tags.get(nameTag);
			if (nameTagValue == null) {
				continue;
			}

			String[] nameParts = COMMA_OR_SEMICOLON_PATTERN.split(nameTagValue);
			if (nameParts.length == 0) {
				continue;
			}
			return nameParts[0].trim().intern();
		}
		return null;
	}

	private String getZip(Tags tags) {
		String zip = tags.get("postal_code");
		if (zip == null) {
			String name = getName(tags);
			if (name != null) {
				String[] nameParts = name.split(Pattern.quote(" "));
				if (nameParts.length > 0) {
					zip = nameParts[0].trim();
				}
			}
		}
		return zip;
	}

	@Override
	public Set<String> getUsedTags() {
		Set<String> tags = new HashSet<String>();
		tags.add("boundary");
		tags.add("admin_level");
		tags.add("postal_code");
		tags.addAll(nameTags);
		return tags;
	}

}
