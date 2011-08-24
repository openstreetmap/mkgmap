/*
 * Copyright (C) 2006, 2011.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.build.Locator;
import uk.me.parabola.mkgmap.reader.osm.boundary.Boundary;
import uk.me.parabola.mkgmap.reader.osm.boundary.BoundaryPreparer.BoundaryCollator;
import uk.me.parabola.mkgmap.reader.osm.boundary.BoundaryUtil;
import uk.me.parabola.util.ElementQuadTree;
import uk.me.parabola.util.EnhancedProperties;
import uk.me.parabola.util.GpxCreator;
import uk.me.parabola.util.MultiHashMap;

public class LocationHook extends OsmReadingHooksAdaptor {
	private static final Logger log = Logger.getLogger(LocationHook.class);

	private ElementSaver saver;
	private final List<String> nameTags = new ArrayList<String>();
	private final Locator locator = new Locator();
	private final Set<String> autofillOptions = new HashSet<String>();
	
	private File boundaryDir;

	private static final Pattern COMMA_OR_SPACE_PATTERN = Pattern
			.compile("[,\\s]+");
	private static final Pattern COMMA_OR_SEMICOLON_PATTERN = Pattern
			.compile("[,;]+");
	
	public static final String BOUNDS_OPTION = "bounds";

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
			log.info("Disable LocationHook because index option is not set.");
			return false;
		}

		this.saver = saver;

		autofillOptions.addAll(Locator.parseAutofillOption(props.getProperty("location-autofill", "bounds")));

		if (autofillOptions.isEmpty()) {
			log.info("Disable LocationHook because no location-autofill option set.");
			return false;
		}
		
		String nameTagProp = props.getProperty("name-tag-list", "name");
		nameTags.addAll(Arrays.asList(COMMA_OR_SPACE_PATTERN.split(nameTagProp)));

		if (autofillOptions.contains(BOUNDS_OPTION)) {

			boundaryDir = new File(props.getProperty("bounds", "bounds"));
			if (boundaryDir.exists() == false) {
				log.error("Disable LocationHook because boundary directory does not exist. Dir: "
						+ boundaryDir);
				return false;
			}
			File[] boundaryFiles = boundaryDir.listFiles(new FileFilter() {
				public boolean accept(File pathname) {
					return (pathname.isFile() && pathname.getName().endsWith(
							".bnd"));
				}

			});
			if (boundaryFiles == null || boundaryFiles.length == 0) {
				log.error("Disable LocationHook because boundary directory contains no boundary files. Dir: "
						+ boundaryDir);
				return false;
			}
		}
		return true;
	}
	
	private void assignPreprocBounds() {
		long t1 = System.currentTimeMillis();
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

		// Load the boundaries that intersect with the bounding box of the tile
		List<Boundary> boundaries = BoundaryUtil.loadBoundaries(boundaryDir,
				saver.getBoundingBox());
		// Sort them by the admin level
		Collections.sort(boundaries, new BoundaryCollator());
		// Reverse the sorting because we want to start with the lowest admin level
		Collections.reverse(boundaries);
		
		// go through all boundaries, check if the necessary tags are available
		// and standardize the country name to the 3 letter ISO code
		ListIterator<Boundary> bIter = boundaries.listIterator();
		while (bIter.hasNext()) {
			Boundary b = bIter.next();
			
			String name = getName(b.getTags());
			
			String zip =null;
			if (b.getTags().get("postal_code") != null || "postal_code".equals(b.getTags().get("boundary")))
				zip = getZip(b.getTags());
			
			if (name == null && zip == null) {
				log.warn("Cannot process boundary element because it contains no name and no zip tag. "+b.getTags());

				bIter.remove();
				continue;
			}

			if ("2".equals(b.getTags().get("admin_level"))) {
				log.info("Input country: " + name);
				String lowercaseName = name;
				name = locator.fixCountryString(name);
				log.info("Fixed country: " + name);
				String cCode = locator.getCountryCode(name);
				if (cCode != null) {
					name = cCode;
				} else {
					log.error("Country name "+lowercaseName+" not in locator config. Country may not be assigned correctly.");
				}
				log.info("Coded: " + name);
			}
			if (name != null)
				b.getTags().put("mkgmap:bname", name);
			
			if (zip != null)
				b.getTags().put("mkgmap:bzip", zip);
		}
		
		if (boundaries.isEmpty()) {
			log.info("Do not continue with LocationHook because no valid boundaries are available.");
			return;
		}

		log.info("Element lists created after",
				(System.currentTimeMillis() - t1), "ms");

		log.info("Creating quadtree for", allElements.size(), "elements");
		ElementQuadTree quadTree = new ElementQuadTree(allElements);
		log.info("Quadtree created after", (System.currentTimeMillis() - t1),
				"ms");

		// Map the boundaryid to the boundary for fast access
		Map<String, Boundary> boundaryById = new HashMap<String, Boundary>();
		
		Set<String> availableLevels = new TreeSet<String>();
		for (Boundary b : boundaries) {
			boundaryById.put(b.getTags().get("mkgmap:boundaryid"), b);

			String admin_level = b.getTags().get("admin_level");
			String admMkgmapTag = mkgmapTags.get("admin_level=" + admin_level);
			String zipMkgmapTag = mkgmapTags.get("postal_code");

			String admName = b.getTags().get("mkgmap:bname");
			String zip = b.getTags().get("mkgmap:bzip");
			
			if (admName != null && admMkgmapTag != null) {
				availableLevels.add(admMkgmapTag);
			}
			if (zip != null && zipMkgmapTag!=null)
			{
				availableLevels.add(zipMkgmapTag);
			}
		}
		
		// put all available levels into a list with inverted sort order
		// this contains all levels that are not fully processed
		List<String> remainingLevels = new ArrayList<String>();
		if (availableLevels.contains(mkgmapTags.get("postal_code"))) {
			remainingLevels.add(mkgmapTags.get("postal_code"));
		}
		for (int level = 11; level >= 1; level--) {
			if (availableLevels.contains(mkgmapTags.get("admin_level="+level))) {
				remainingLevels.add(mkgmapTags.get("admin_level="+level));
			}			
		}		

		String currLevel = remainingLevels.remove(0);
		log.debug("First level:",currLevel);
		
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

			// check if the list of remaining levels is still up to date
			while ((admName == null || currLevel.equals(admMkgmapTag) == false) && (zip == null || currLevel.equals(zipMkgmapTag) == false)) {
				if (log.isDebugEnabled()) {
					log.debug("Finish current level:",currLevel);
					log.debug("admname:",admName,"admMkgmapTag:",admMkgmapTag);
					log.debug("zip:",zip,"zipMkgmapTag:",zipMkgmapTag);
					log.debug("Next boundary:",boundary.getTags());
				}
				if (remainingLevels.isEmpty()) {
					log.error("All levels are finished. Remaining boundaries "+boundaries.size()+". Remaining coords: "+quadTree.getCoordSize());
					return;
				} else {
					currLevel = remainingLevels.remove(0);
					log.debug("Next level:",currLevel," Remaining:",remainingLevels);
				}
			}

			// defines which tags can be assigned by this boundary
			Map<String, String> boundarySetTags = new HashMap<String,String>();
			if (admName != null && admMkgmapTag != null) {
				boundarySetTags.put(admMkgmapTag, admName);
			}
			if (zip != null && zipMkgmapTag != null) {
				boundarySetTags.put(zipMkgmapTag, zip);
			}
			
			// check in which other boundaries this boundary lies in
			String liesIn = boundary.getTags().get("mkgmap:lies_in");
			if (liesIn != null) {
				// the common format of mkgmap:lies_in is:
				// mkgmap:lies_in=2:r19884;4:r20039;6:r998818
				String[] relBounds = liesIn.split(Pattern.quote(";"));
				for (String relBound : relBounds) {
					String[] relParts = relBound.split(Pattern.quote(":"));
					if (relParts.length != 2) {
						log.error("Wrong mkgmap:lies_in format. Value: " +liesIn);
						continue;
					}
					Boundary bAdditional = boundaryById.get(relParts[1]);
					if (bAdditional == null) {
						log.error("Referenced boundary not available: "+boundary.getTags()+" refs "+relParts[1]);
						continue;
					}
					String addAdmin_level = bAdditional.getTags().get("admin_level");
					String addAdmMkgmapTag = mkgmapTags.get("admin_level=" + addAdmin_level);
					String addZipMkgmapTag = mkgmapTags.get("postal_code");

					String addAdmName = bAdditional.getTags().get("mkgmap:bname");
					String addZip = bAdditional.getTags().get("mkgmap:bzip");
					
					if (addAdmMkgmapTag != null
							&& addAdmName != null
							&& boundarySetTags.containsKey(addAdmMkgmapTag) == false) {
						boundarySetTags.put(addAdmMkgmapTag, addAdmName);
					}
					if (addZipMkgmapTag != null
							&& addZip != null
							&& boundarySetTags.containsKey(addZipMkgmapTag) == false) {
						boundarySetTags.put(addZipMkgmapTag, addZip);
					}
				}
			}
			
			// search for all elements in the boundary area
			Set<Element> elemsInLocation = quadTree.get(boundary.getArea());

			for (Element elem : elemsInLocation) {
				// tag the element with all tags referenced by the boundary
				for (Entry<String,String> bTag : boundarySetTags.entrySet()) {
					if (elem.getTag(bTag.getKey()) == null) {
						elem.addTag(bTag.getKey(), bTag.getValue());
						if (log.isDebugEnabled()) {
							log.debug("Add tag", admMkgmapTag, "=", admName, "to",
									elem.toTagString());
						}
					}
				}
				
				// check if the element is already tagged with all remaining boundary levels
				// in this case the element can be removed from further processing 
				Set<String> locTags = getUsedLocationTags(elem);
				if (locTags.containsAll(remainingLevels)) {
					if (log.isDebugEnabled()) {
						log.debug("Elem finish: "+elem.kind()+elem.getId()+" "+elem.toTagString());
					}
					quadTree.remove(elem);
				}
			}
			bIter.remove();
			
			if (quadTree.getCoordSize() <= 0) {
				log.info("Finish Location Hook: Remaining boundaries: "+boundaries.size());
				return;
			}
		}
		if (log.isDebugEnabled()) {
			Collection<Element> unassigned =  quadTree.get(new Area(-90.0d, -180.0d, 90.0d, 180.0d));
			Set<Coord> unCoords = new HashSet<Coord>();
			for (Element e : unassigned) {
				log.debug(e.getId()+" "+e.toTagString());
				if (e instanceof Node)
					unCoords.add(((Node) e).getLocation());
				else if ( e instanceof Way) {
					unCoords.addAll(((Way) e).getPoints());
				}
			}
			GpxCreator.createGpx(GpxCreator.getGpxBaseName()+"unassigned", new ArrayList<Coord>(), new ArrayList<Coord>(unCoords));
			log.debug("Finish Location Hook. Unassigned elements: "+unassigned.size());
		}
	}
	
	/**
	 * Retrieves which of the location tags are already set in this element.
	 * @param element the OSM element
	 * @return a set of location tags
	 */
	private Set<String> getUsedLocationTags(Element element) {
		Set<String> usedTags = null;
		for (String locTag : mkgmapTags.values()) {
			if (element.getTag(locTag) != null) {
				if (usedTags == null) {
					usedTags = new HashSet<String>();
				}
				usedTags.add(locTag);
			}
		}
		if (usedTags == null) {
			return Collections.emptySet();
		} else {
			return usedTags;
		}
	}

	public void end() {
		long t1 = System.currentTimeMillis();
		log.info("Starting with location hook");

		if (autofillOptions.contains(BOUNDS_OPTION)) {
			assignPreprocBounds();
		}

		log.info("Location hook finished in",
				(System.currentTimeMillis() - t1), "ms");
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

	public Set<String> getUsedTags() {
		Set<String> tags = new HashSet<String>();
		tags.add("boundary");
		tags.add("admin_level");
		tags.add("postal_code");
		tags.addAll(nameTags);
		return tags;
	}

}
