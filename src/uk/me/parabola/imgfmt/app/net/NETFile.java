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
import java.util.LinkedList;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.lbl.City;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.util.Sortable;

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

	public void writePost(ImgFileWriter rgn, boolean sortRoads) {
		List<SortKey<Sortable<Label, RoadDef>>> sortedRoads = new ArrayList<SortKey<Sortable<Label, RoadDef>>>(roads.size());

		for (RoadDef rd : roads) {
			rd.writeRgnOffsets(rgn);
			if(sortRoads) {
				Label[] l = rd.getLabels();
				for(int i = 0; i < l.length && l[i] != null; ++i) {
					if(l[i].getLength() != 0) {
						String cleanName = l[i].getTextSansGarminCodes();
						assert sort != null;
						SortKey<Sortable<Label, RoadDef>> sortKey = sort.createSortKey(new Sortable<Label, RoadDef>(l[i], rd), cleanName, 0);
						sortedRoads.add(sortKey);
					}
				}
			}
		}

		if(!sortedRoads.isEmpty()) {
			long start = System.currentTimeMillis();
			Collections.sort(sortedRoads);
			sortedRoads = simplifySortedRoads(new LinkedList<SortKey<Sortable<Label, RoadDef>>>(sortedRoads));
			ImgFileWriter writer = netHeader.makeSortedRoadWriter(getWriter());
			for(SortKey<Sortable<Label, RoadDef>> srd : sortedRoads) {
				//System.err.println("Road " + srd.getKey() + " is " + srd.getValue() + " " + srd.getValue().getCity());
				srd.getObject().getValue().putSortedRoadEntry(writer, srd.getObject().getKey());
			}
			System.out.println("sort " + (System.currentTimeMillis() - start) + "ms");
			Utils.closeFile(writer);
		}

		getHeader().writeHeader(getWriter());
	}

	public void setNetwork(List<RoadDef> roads) {
		this.roads = roads;
	}

	/**
	 * Given a list of roads sorted by name and city, build a new list
	 * that only contains one entry for each group of roads that have
	 * the same name and city and are directly connected
	 */
	private List<SortKey<Sortable<Label, RoadDef>>> simplifySortedRoads(LinkedList<SortKey<Sortable<Label, RoadDef>>> in) {
		List<SortKey<Sortable<Label, RoadDef>>> out = new ArrayList<SortKey<Sortable<Label, RoadDef>>>(in.size());
		while(!in.isEmpty()) {
			String name0 = in.get(0).getObject().getKey().getTextSansGarminCodes();
			RoadDef road0 = in.get(0).getObject().getValue();
			City city0 = road0.getCity();
			// transfer the 0'th entry to the output
			out.add(in.remove(0));
			int n;
			// firstly determine the entries whose name and city match
			// name0 and city0
			for(n = 0; (n < in.size() &&
						name0.equalsIgnoreCase(in.get(n).getObject().getKey().getTextSansGarminCodes()) &&
						city0 == in.get(n).getObject().getValue().getCity()); ++n) {
				// relax
			}
			if(n > 0) {
				// now determine which of those roads are connected to
				// road0 and throw them away
				List<RoadDef> connectedRoads = new ArrayList<RoadDef>();
				connectedRoads.add(road0);
				// we have to keep doing this until no more
				// connections are discovered
				boolean lookAgain = true;
				while(lookAgain) {
					// assume a connected road won't be found
					lookAgain = false;
					// loop over the roads with the same
					// name and city
					for(int i = 0; i < n; ++i) {
						RoadDef roadI = in.get(i).getObject().getValue();
						// see if this road is connected to any of the
						// roads connected to road0
						for(int j = 0; !lookAgain && j < connectedRoads.size(); ++j) {
							if(roadI.connectedTo(connectedRoads.get(j), 0)) {
								// yes, it's connected to one of the
								// roads so put it in connectedRoads,
								// remove from the input and go around
								// again
								connectedRoads.add(roadI);
								in.remove(i);
								--n;
								lookAgain = true;
							}
						}
					}
				}
			}
		}

		return out;
	}

	public void setSort(Sort sort) {
		this.sort = sort;
	}
}
