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
import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.lbl.City;
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

	public NETFile(ImgChannel chan, boolean write) {
		setHeader(netHeader);
		if (write) {
			setWriter(new BufferedImgFileWriter(chan));
			position(NETHeader.HEADER_LEN);
		} else {
			setReader(new BufferedImgFileReader(chan));
			netHeader.readHeader(getReader());
		}
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
		List<Sortable<Label, RoadDef>> sortedRoads = new ArrayList<Sortable<Label, RoadDef>>(roads.size());

		for (RoadDef rd : roads) {
			rd.writeRgnOffsets(rgn);
			if(sortRoads) {
				Label[] l = rd.getLabels();
				for(int i = 0; i < l.length && l[i] != null; ++i)
					if(l[i].getLength() != 0)
						sortedRoads.add(new Sortable<Label, RoadDef>(l[i], rd));
			}
		}

		if(sortedRoads.size() > 0) {
			Collections.sort(sortedRoads);
			sortedRoads = simplifySortedRoads(new LinkedList<Sortable<Label, RoadDef>>(sortedRoads));
			ImgFileWriter writer = netHeader.makeSortedRoadWriter(getWriter());
			for(Sortable<Label, RoadDef> srd : sortedRoads)
				srd.getValue().putSortedRoadEntry(writer, srd.getKey());
			Utils.closeFile(writer);
		}

		getHeader().writeHeader(getWriter());
	}

	public void setNetwork(List<RoadDef> roads) {
		this.roads = roads;
	}

	// given a list of roads sorted by name and city, build a new list
	// that only contains one entry for each group of roads that have
	// the same name and city and are directly connected

	private List<Sortable<Label, RoadDef>> simplifySortedRoads(LinkedList<Sortable<Label, RoadDef>> in) {
		List<Sortable<Label, RoadDef>> out = new ArrayList<Sortable<Label, RoadDef>>(in.size());
		while(in.size() > 0) {
			Label name0 = in.get(0).getKey();
			RoadDef road0 = in.get(0).getValue();
			City city0 = road0.getCity();
			// transfer the 0'th entry to the output
			out.add(in.remove(0));
			int n;
			// firstly determine the entries whose name and city match
			// name0 and city0
			for(n = 0; (n < in.size() &&
						name0 == in.get(n).getKey() &&
						city0 == in.get(n).getValue().getCity()); ++n) {
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
						RoadDef roadI = in.get(i).getValue();
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

				/*
				try {
					System.err.println(new String(name0.getCtext()) + " in " + city0 + " is connected to " + connectedRoads.size() + " other segment(s)");
				}
				catch (Exception e) {
				}
				*/
			}
		}

		return out;
	}
}
