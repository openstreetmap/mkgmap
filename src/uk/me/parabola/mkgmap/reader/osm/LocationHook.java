package uk.me.parabola.mkgmap.reader.osm;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

	private static class LocationInfo {
		private final String key;
		private final String mkgmapTag;
		private final Collection<String> osmTags;

		public LocationInfo(String key, String mkgmapTag, String... osmTags) {
			super();
			this.key = key;
			this.mkgmapTag = mkgmapTag;
			this.osmTags = new ArrayList<String>(Arrays.asList(osmTags));
		}

		public String getMkgmapTag() {
			return mkgmapTag;
		}

		public Collection<String> getOsmTags() {
			return osmTags;
		}

		public String getKey() {
			return key;
		}

	}

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

	private final List<LocationInfo> locationInfos = new ArrayList<LocationInfo>() {
		{
			add(new LocationInfo("admin_level=1", "mkgmap:admin_level1",
					"addr:country", "is_in:country"));
			add(new LocationInfo("admin_level=2", "mkgmap:admin_level2",
					"addr:country", "is_in:country"));
			add(new LocationInfo("admin_level=3", "mkgmap:admin_level3",
					"addr:province"));
			add(new LocationInfo("admin_level=4", "mkgmap:admin_level4",
					"addr:province"));
			add(new LocationInfo("admin_level=5", "mkgmap:admin_level5",
					"addr:district"));
			add(new LocationInfo("admin_level=6", "mkgmap:admin_level6",
					"addr:subdistrict"));
			add(new LocationInfo("admin_level=7", "mkgmap:admin_level7",
					"addr:subdistrict"));
			add(new LocationInfo("admin_level=8", "mkgmap:admin_level8",
					"addr:city", "is_in:city"));
			add(new LocationInfo("admin_level=9", "mkgmap:admin_level9",
					"addr:city", "is_in:city"));
			add(new LocationInfo("admin_level=10", "mkgmap:admin_level10",
					"addr:city", "is_in:city"));
			add(new LocationInfo("admin_level=11", "mkgmap:admin_level11",
					"addr:city", "is_in:city"));
			add(new LocationInfo("postal_code", "mkgmap:postcode",
					"addr:postcode", "postal_code"));
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

		boundaryDir = new File(props.getProperty("boundsdirectory", "boundary"));
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
		for (Boundary b : boundaries) {
			if (b == null) {
				log.error("Null boundary");
			}
		}
		ListIterator<Boundary> bIter = boundaries.listIterator();
		while (bIter.hasNext()) {
			Boundary b = bIter.next();
			String name = getName(b.getTags());
			if (name == null) {
				log.error("Cannot process boundary element because it contains no name tag.");

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
			b.getTags().put("mkgmap:bname", name);
		}

		log.error("Element lists created after "
				+ (System.currentTimeMillis() - t1) + " ms");

		// java.awt.geom.Area tileArea = Java2DConverter
		// .createBoundsArea(saver.getBoundingBox());

		log.info("Creating quadtree for", allElements.size(), "elements");
		ElementQuadTree quadTree = new ElementQuadTree(allElements);
		log.error("Quadtree created after " + (System.currentTimeMillis() - t1)
				+ " ms");

		// // first add the tags to the boundaries itself
		// for (LocationInfo boundaryInfo : locationInfos) {
		// String tag = boundaryInfo.getMkgmapTag();
		// log.info("Preparing", boundaryInfo.getKey(), "with tag", tag);
		// List<Way> levelBoundaries = locationElements
		// .get(boundaryInfo.getKey());
		// log.info(levelBoundaries.size(), "boundaries");
		// for (Way boundary : levelBoundaries) {
		// if (boundaryInfo.getKey().startsWith("admin_level")) {
		// String name = getName(boundary);
		// if (name == null) {
		// continue;
		// }
		// boundary.addTag(tag, name);
		// } else if (boundaryInfo.getKey().equals("postal_code")) {
		// String zip = getZip(boundary);
		// if (zip != null) {
		// boundary.addTag(tag, zip);
		// } else {
		// log.error("No zip information in " + boundary.getId()
		// + " " + boundary.toTagString());
		// }
		// }
		// }
		// }

		bIter = boundaries.listIterator();
		while (bIter.hasNext()) {
//		for (Boundary boundary : boundaries) {
			Boundary boundary = bIter.next();
			String admin_level = boundary.getTags().get("admin_level");
			String mkgmapTag = mkgmapTags.get("admin_level=" + admin_level);
			if (mkgmapTag == null) {
				log.error("Cannot find mkgmap tag for " + boundary.getTags());
				continue;
			}
			String name = boundary.getTags().get("mkgmap:bname");

			Set<Element> elemsInLocation = quadTree.get(boundary.getArea());

			for (Element elem : elemsInLocation) {
				if (elem.getTag(mkgmapTag) == null) {
					elem.addTag(mkgmapTag, name);
					if (log.isDebugEnabled())
						log.debug("Added tag", mkgmapTag, "=", name, "to",
								elem.toTagString());
				}
			}
			bIter.remove();
		}

		// for (LocationInfo locationInfo : locationInfos) {
		// String mkgmapTag = locationInfo.getMkgmapTag();
		// log.info("Processing", locationInfo.getKey(), "with tag", mkgmapTag);
		//
		// List<Way> locationPolys = locationElements.get(locationInfo
		// .getKey());
		// log.info(locationPolys.size(), "location relevant polygons");
		// for (Way locationPoly : locationPolys) {
		// String name = (locationInfo.getKey().equals("postal_code") ?
		// getZip(locationPoly)
		// : getName(locationPoly));
		// if (name == null) {
		// log.error("Cannot process location element because it contains no name tag: "
		// + locationPoly.getId()
		// + " "
		// + locationPoly.toTagString());
		// continue;
		// }
		//
		// if (locationInfo.getKey().equals("admin_level=2")) {
		// log.info("Input country: " + name);
		// name = locator.fixCountryString(name);
		// log.info("Fixed country: " + name);
		// String cCode = locator.getCountryCode(name);
		// if (cCode != null) {
		// name = cCode;
		// }
		// log.info("Coded: " + name);
		// }
		//
		// java.awt.geom.Area boundArea = Java2DConverter
		// .createArea(locationPoly.getPoints());
		//
		// log.info("Checking", locationPoly.toTagString());
		// Set<Element> elemsInLocation = quadTree.get(boundArea);
		//
		// for (Element elem : elemsInLocation) {
		//
		// if (elem.getTag(mkgmapTag) == null) {
		// elem.addTag(mkgmapTag, name);
		// if (log.isDebugEnabled())
		// log.debug("Added tag", mkgmapTag, "=", name, "to",
		// elem.toTagString());
		// }
		// }
		// }
		//
		// log.error("Location " + locationInfo.getKey() + " processed after "
		// + (System.currentTimeMillis() - t1) + " ms");
		//
		// // perform a special handling for country borders
		// if (locationInfo.getKey().equals("admin_level=2")) {
		//
		// // count all elements within the tile bounds and use the most
		// // assigned one
		// // for all unassigned elements
		// Collection<String> osmTags = locationInfo.getOsmTags();
		// Map<String, Integer> tagValueCounter = new HashMap<String,
		// Integer>();
		// for (Element elem : allElements) {
		// String refTagValue = null;
		// for (String osmTag : osmTags) {
		// refTagValue = elem.getTag(osmTag);
		// if (refTagValue != null) {
		// break;
		// }
		// }
		// if (refTagValue != null) {
		// Integer i = tagValueCounter.get(refTagValue);
		// if (i == null) {
		// tagValueCounter.put(refTagValue, 1);
		// } else {
		// tagValueCounter.put(refTagValue, i + 1);
		// }
		// }
		// }
		//
		// log.info("Distribution of country information: "
		// + tagValueCounter);
		//
		// String maxTag = null;
		// int maxCount = 0;
		// for (Entry<String, Integer> tagEntry : tagValueCounter
		// .entrySet()) {
		// if (maxCount < tagEntry.getValue()) {
		// maxCount = tagEntry.getValue();
		// maxTag = tagEntry.getKey();
		// }
		// }
		//
		// log.error("Start assigning  " + locationInfo.getKey()
		// + " after " + (System.currentTimeMillis() - t1) + " ms");
		// if (maxCount > 0) {
		// maxTag = locator.fixCountryString(maxTag);
		// String cCode = locator.getCountryCode(maxTag);
		// if (cCode != null) {
		// maxTag = cCode;
		// }
		// log.info("admin_level2 fixed: " + maxTag);
		//
		// // assign the most used country to all unassigned elements
		// for (Element elem : allElements) {
		// if (elem.getTag(mkgmapTag) == null)
		// elem.addTag(mkgmapTag, maxTag);
		// }
		// }
		//
		// // fix the addr:country and is_in:country tag
		// for (Element elem : allElements) {
		// String addrCountry = elem.getTag("addr:country");
		// if (addrCountry != null) {
		// addrCountry = locator.fixCountryString(addrCountry);
		// String cCode = locator.getCountryCode(addrCountry);
		// if (cCode != null) {
		// elem.addTag("addr:country", cCode);
		// }
		// // log.error("addr:country fixed: "+addrCountry+"/"+cCode);
		// }
		//
		// String isInCountry = elem.getTag("is_in:country");
		// if (isInCountry != null) {
		// isInCountry = locator.fixCountryString(isInCountry);
		// String cCode = locator.getCountryCode(isInCountry);
		// if (cCode != null) {
		// elem.addTag("is_in:country", cCode);
		// }
		// // log.error("is_in:country fixed: "+isInCountry+"/"+cCode);
		// }
		// }
		//
		// log.error("Special " + locationInfo.getKey()
		// + " handling processed after "
		// + (System.currentTimeMillis() - t1) + " ms");
		// }
		// }

		log.error("Location hook finished in "
				+ (System.currentTimeMillis() - t1) + " ms");
	}

	private String getName(Element element) {
		for (String nameTag : nameTags) {
			String nameTagValue = element.getTag(nameTag);
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

	private String getZip(Element element) {
		String zip = element.getTag("postal_code");
		if (zip == null) {
			String name = getName(element);
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
