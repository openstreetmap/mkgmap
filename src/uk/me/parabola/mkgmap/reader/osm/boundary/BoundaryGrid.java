/*
 * Copyright (C) 2012.
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
package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Tags;
import uk.me.parabola.util.EnhancedProperties;

/**
 * A simple grid that stores the BoundaryQuadTrees that intersect with the grid.
 * Each element of the grid is related to one *.bnd file.
 * 
 * @author GerdP
 * 
 */
public class BoundaryGrid {
	private static final Logger log = Logger.getLogger(BoundaryGrid.class);

	private final uk.me.parabola.imgfmt.app.Area searchBbox;
	private final BoundaryQuadTree[][] grid;
	private final boolean [][]emptyMessagePrinted;
	private final int minLat;
	private final int minLon;
	private final EnhancedProperties props;

	/**
	 * A simple grid that contains references to BoundaryQuadTrees loaded from
	 * preprocessed boundary files. The grid will fully cover the bounding box
	 * passed in bbox.
	 * 
	 * @param boundaryDirName
	 *            the path to the preprocessed boundary files
	 * @param bbox
	 *            the bounding box of all points that might be searched
	 * @param props
	 *            used to determine the ISO code of level 2 boundaries
	 */
	public BoundaryGrid(String boundaryDirName,
			uk.me.parabola.imgfmt.app.Area bbox, EnhancedProperties props) {
		minLat = BoundaryUtil.getSplitBegin(bbox.getMinLat());
		minLon = BoundaryUtil.getSplitBegin(bbox.getMinLong());
		int gridMaxLat = BoundaryUtil.getSplitBegin(bbox.getMaxLat());
		int gridMaxLon = BoundaryUtil.getSplitBegin(bbox.getMaxLong());
		int dimLat = (gridMaxLat - minLat) / BoundaryUtil.RASTER + 1;
		int dimLon = (gridMaxLon - minLon) / BoundaryUtil.RASTER + 1;
		grid = new BoundaryQuadTree[dimLat][dimLon];
		emptyMessagePrinted = new boolean[dimLat][dimLon];
		this.searchBbox = bbox;

		this.props = props;
		init(boundaryDirName);
	}

	/**
	 * Returns the location relevant tags for a given point 
	 * @param co the coords of the point
	 * @return null if not found, else a reference to the Tags 
	 * object saved in a BoundaryQuadTree
	 */
	public Tags get(Coord co) {
		if (!searchBbox.contains(co))
			return null;
		int gridLat = (co.getLatitude() - minLat) / BoundaryUtil.RASTER;
		int gridLon = (co.getLongitude() - minLon) / BoundaryUtil.RASTER;
		if (grid[gridLat][gridLon] == null){
			if (emptyMessagePrinted[gridLat][gridLon] == false){
				emptyMessagePrinted[gridLat][gridLon] = true;
				int keyLat = BoundaryUtil.getSplitBegin(co.getLatitude());
				int keyLon = BoundaryUtil.getSplitBegin(co.getLongitude());
				log.warn("no precompiled boundary information available for raster tile", BoundaryUtil.getKey(keyLat,keyLon));
			}
			return null;
		}
		else
			return grid[gridLat][gridLon].get(co);
	}

	/**
	 * Fill the grid. Calculate the names of the *.bnd files that 
	 * may be needed. For each file, try to create a BoundaryQuadTree.
	 * Save each tree to its place in the grid. 
	 * 
	 * @param boundaryDir
	 *            Directory or a *.zip file with bnd files
	 */
	private void init(String boundaryDirName){
		List<String> requiredFileNames = BoundaryUtil.getRequiredBoundaryFileNames(searchBbox);
		Map<String,BoundaryQuadTree> trees = BoundaryUtil.loadQuadTrees(boundaryDirName, requiredFileNames, searchBbox, props);
		for (Entry<String,BoundaryQuadTree> entry: trees.entrySet()) {
			uk.me.parabola.imgfmt.app.Area fileBbox = BoundaryUtil.getBbox(entry.getKey());
			int gridLat = (fileBbox.getMinLat() - minLat) / BoundaryUtil.RASTER;
			int gridLon = (fileBbox.getMinLong() - minLon) / BoundaryUtil.RASTER;
			grid[gridLat][gridLon] = entry.getValue();
		}
	}
}
