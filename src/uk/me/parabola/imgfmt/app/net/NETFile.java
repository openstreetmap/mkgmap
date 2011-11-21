/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: Jan 5, 2008
 */
package uk.me.parabola.imgfmt.app.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.lbl.City;
import uk.me.parabola.imgfmt.app.srt.IntegerSortKey;
import uk.me.parabola.imgfmt.app.srt.MultiSortKey;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;
import uk.me.parabola.imgfmt.fs.ImgChannel;

/**
 * The NET file.  This consists of information about roads.  It is not clear
 * what this file brings on its own (without NOD) but may allow some better
 * searching, street addresses etc.
 *
 * @author Steve Ratcliffe
 */
public class NETFile extends ImgFile {
	private final NETHeader netHeader = new NETHeader();
	private List<RoadDef> roads;
	private Sort sort;

	public NETFile(ImgChannel chan) {
		setHeader(netHeader);
		setWriter(new BufferedImgFileWriter(chan));
		position(NETHeader.HEADER_LEN);
	}

	/**
	 * Write out NET1.
	 * @param numCities The number of cities in the map. Needed for the size of the written fields.
	 * @param numZips The number of zips in the map. Needed for the size of the written fields.
	 */
	public void write(int numCities, int numZips) {
		// Write out the actual file body.
		ImgFileWriter writer = netHeader.makeRoadWriter(getWriter());
		try {
			for (RoadDef rd : roads)
				rd.writeNet1(writer, numCities, numZips);

		} finally {
			Utils.closeFile(writer);
		}
	}

	/**
	 * Final writing out of net sections.
	 *
	 * We patch the NET offsets into the RGN file and create the sorted roads section.
	 *
	 * @param rgn The region file, this has to be patched with the calculated net offsets.
	 */
	public void writePost(ImgFileWriter rgn) {
		for (RoadDef rd : roads)
			rd.writeRgnOffsets(rgn);

		ImgFileWriter writer = netHeader.makeSortedRoadWriter(getWriter());
		try {
			List<LabeledRoadDef> labeledRoadDefs = sortRoads();
			for (LabeledRoadDef labeledRoadDef : labeledRoadDefs)
				labeledRoadDef.roadDef.putSortedRoadEntry(writer, labeledRoadDef.label);
		} finally {
			Utils.closeFile(writer);
		}

		getHeader().writeHeader(getWriter());
	}

	/**
	 * Sort the roads by name and remove duplicates.
	 *
	 * We want a list of roads such that every entry in the list is a different road. Since in osm
	 * roads are frequently chopped into small pieces we have to remove the duplicates.
	 * This doesn't have to be perfect, it needs to be useful when searching for roads.
	 *
	 * So we have a separate entry if the road is in a different city. This would probably be enough
	 * except that associating streets with cities is not always very good in OSM. So I also create an
	 * extra entry for each subdivision. Finally there a search for disconnected roads within the subdivision
	 * with the same name.
	 *
	 * Performance note: The previous implementation was very, very slow when there were a large number
	 * of roads with the same name. Although this was an unusual situation, when it happened it appears
	 * that mkgmap has hung. This implementation takes a fraction of a second even for large numbers of
	 * same named roads.
	 *
	 * @return A sorted list of road labels that identify all the different roads.
	 */
	private List<LabeledRoadDef> sortRoads() {
		List<SortKey<LabeledRoadDef>> sortKeys = new ArrayList<SortKey<LabeledRoadDef>>(roads.size());
		Map<String, byte[]> cache = new HashMap<String, byte[]>();

		for (RoadDef rd : roads) {
			Label[] labels = rd.getLabels();
			for (int i = 0; i < labels.length && labels[i] != null; ++i) {
				Label label = labels[i];
				if (label.getLength() == 0)
					continue;

				// Sort by name, city, region/country and subdivision number.
				LabeledRoadDef lrd = new LabeledRoadDef(label, rd);
				SortKey<LabeledRoadDef> nameKey = sort.createSortKey(lrd, label.getText(), 0, cache);

				// If there is a city add it to the sort.
				City city = rd.getCity();
				SortKey<LabeledRoadDef> cityKey;
				if (city != null) {
					int region = city.getRegionNumber();
					int country = city.getCountryNumber();
					cityKey = sort.createSortKey(null, city.getName(), (region & 0xffff) << 16 | (country & 0xffff), cache);
				} else {
					cityKey = sort.createSortKey(null, "", 0, cache);
				}

				SortKey<LabeledRoadDef> sortKey = new MultiSortKey<LabeledRoadDef>(nameKey, cityKey,
						new IntegerSortKey<LabeledRoadDef>(null, rd.getStartSubdivNumber(), 0));
				sortKeys.add(sortKey);
			}
		}

		Collections.sort(sortKeys);

		List<LabeledRoadDef> out = new ArrayList<LabeledRoadDef>(sortKeys.size());

		String lastName = null;
		City lastCity = null;
		List<LabeledRoadDef> dupes = new ArrayList<LabeledRoadDef>();

		// Since they are sorted we can easily remove the duplicates.
		// The duplicates are saved to the dupes list.
		for (SortKey<LabeledRoadDef> key : sortKeys) {
			LabeledRoadDef lrd = key.getObject();

			String name = lrd.label.getText();
			RoadDef road = lrd.roadDef;
			City city = road.getCity();

			if (!name.equals(lastName) || city != lastCity) {

				// process any previously collected duplicate road names and reset.
				addDisconnected(dupes, out);
				dupes = new ArrayList<LabeledRoadDef>();

				lastName = name;
				lastCity = city;
			}
			dupes.add(lrd);
		}

		// Finish off the final set of duplicates.
		addDisconnected(dupes, out);

		return out;
	}

	/**
	 * Take a set of roads with the same name/city etc and find sets of roads that do not
	 * connect with each other. One of the members of each set is added to the road list.
	 *
	 * @param in A list of duplicate roads.
	 * @param out The list of sorted roads. Any new road is added to this.
	 */
	private void addDisconnected(List<LabeledRoadDef> in, List<LabeledRoadDef> out) {
		// switch out to different routines depending on the input size. A normal number of
		// roads with the same name in the same city is a few tens.
		if (in.size() > 200) {
			addDisconnectedLarge(in, out);
		} else {
			addDisconnectedSmall(in, out);
		}
	}

	/**
	 * Split the input set of roads into disconnected groups and output one member from each group.
	 *
	 * This is done in an accurate manner which is slow for large numbers (eg thousands) of items in the
	 * input.
	 *
	 * @param in Input set of roads with the same name.
	 * @param out List to add the discovered groups.
	 */
	private void addDisconnectedSmall(List<LabeledRoadDef> in, List<LabeledRoadDef> out) {
		// Each road starts out with a different group number
		int[] groups = new int[in.size()];
		for (int i = 0; i < groups.length; i++)
			groups[i] = i;

		// Go through pairs of roads, any that are connected we mark with the same (lowest) group number.
		boolean done;
		do {
			done = true;
			for (int current = 0; current < groups.length; current++) {
				RoadDef first = in.get(current).roadDef;

				for (int i = current; i < groups.length; i++) {
					// If the groups are already the same, then no need to test
					if (groups[current] == groups[i])
						continue;

					if (first.connectedTo(in.get(i).roadDef)) {
						groups[current] = groups[i] = Math.min(groups[current], groups[i]);
						done = false;
					}
				}
			}
		} while (!done);

		// Output the first road in each group
		int last = -1;
		for (int i = 0; i < groups.length; i++) {
			if (groups[i] > last) {
				LabeledRoadDef lrd = in.get(i);
				out.add(lrd);
				last = groups[i];
			}
		}
	}

	/**
	 * Split the input set of roads into disconnected groups and output one member from each group.
	 *
	 * This is an modified algorithm for large numbers in the input set (eg thousands).
	 * First sort into groups by subdivision and then call {@link #addDisconnectedSmall} on each
	 * one. Since roads in the same subdivision are near each other this finds most connected roads, but
	 * since there is a maximum number of roads in a subdivision, the test can be done very quickly.
	 * You will get a few extra duplicate entries in the index.
	 *
	 * In normal cases this routine gives almost the same results as {@link #addDisconnectedSmall}.
	 *
	 * @param in Input set of roads with the same name.
	 * @param out List to add the discovered groups.
	 */
	private void addDisconnectedLarge(List<LabeledRoadDef> in, List<LabeledRoadDef> out) {
		Collections.sort(in, new Comparator<LabeledRoadDef>() {
			public int compare(LabeledRoadDef o1, LabeledRoadDef o2) {
				Integer i1 = o1.roadDef.getStartSubdivNumber();
				Integer i2 = o2.roadDef.getStartSubdivNumber();
				return i1.compareTo(i2);
			}
		});

		int lastDiv = 0;
		List<LabeledRoadDef> dupes = new ArrayList<LabeledRoadDef>();
		for (LabeledRoadDef lrd : in) {
			int sd = lrd.roadDef.getStartSubdivNumber();
			if (sd != lastDiv) {
				addDisconnectedSmall(dupes, out);
				dupes = new ArrayList<LabeledRoadDef>();
				lastDiv = sd;
			}
			dupes.add(lrd);
		}

		addDisconnectedSmall(dupes, out);
	}

	public void setNetwork(List<RoadDef> roads) {
		this.roads = roads;
	}

	public void setSort(Sort sort) {
		this.sort = sort;
	}

	/**
	 * A road can have several names. Keep an association between a road def
	 * and one of its names.
	 */
	class LabeledRoadDef {
		private final Label label;
		private final RoadDef roadDef;

		LabeledRoadDef(Label label, RoadDef roadDef) {
			this.label = label;
			this.roadDef = roadDef;
		}
	}
}
