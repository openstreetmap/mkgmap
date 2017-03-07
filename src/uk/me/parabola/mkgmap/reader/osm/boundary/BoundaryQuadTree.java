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

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Tags;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.EnhancedProperties;
import uk.me.parabola.util.GpxCreator;
import uk.me.parabola.util.Java2DConverter;
import uk.me.parabola.util.ShapeSplitter;

/**
 * A quadtree implementation to handle areas formed by boundaries.
 * Each node of the Quadtree stores a list of NodeElems. A NodeElem 
 * stores one area and the related location relevant tags. 
 * @author GerdP 
 *
 */
public class BoundaryQuadTree {
	private static final Logger log = Logger.getLogger(BoundaryQuadTree.class);
	private static final boolean DEBUG = false;
	// debugging  aid 
	private static final String DEBUG_TREEPATH = "?";
	private static final boolean DO_ALL_TESTS = false;
	
	private static final boolean DO_CLIP = true;
	private static final boolean DO_NOT_CLIP = false;
	
	// maps the "normal" tags of the boundaries that are saved in this tree to
	// the boundaryId
	private final HashMap<String, Tags> boundaryTags = new LinkedHashMap<>();
	// maps the location relevant info to the boundaryId 
	private final HashMap<String, BoundaryLocationInfo> preparedLocationInfo;
	// property controlled preparer
	private final BoundaryLocationPreparer preparer;
	
	private final Node root;
	// the bounding box of the quadtree
	private final Rectangle bbox;
	private final String bbox_key; 
	
	// tags that can be returned in the get method
	public final static String[] mkgmapTagsArray =  {
		"mkgmap:admin_level1",
		"mkgmap:admin_level2",
		"mkgmap:admin_level3",
		"mkgmap:admin_level4",
		"mkgmap:admin_level5",
		"mkgmap:admin_level6",
		"mkgmap:admin_level7",
		"mkgmap:admin_level8",
		"mkgmap:admin_level9",
		"mkgmap:admin_level10",
		"mkgmap:admin_level11",
		"mkgmap:postcode"
	};
	// 11: the position of "mkgmap:postcode" in the above array
	public final static short POSTCODE_ONLY = 1 << 11;   
	
	/**
	 * Create a quadtree with the data in an open stream. 
	 * @param inpStream  the open stream with QUADTREE_DATA_FORMAT 
	 * @param fileBbox	The bounding box for the quadTree 
	 * @param searchBbox	The bounding box for the quadTree, only data within this box is used 
	 * @param props if not null, use it to set location names
	 */
	public BoundaryQuadTree(DataInputStream inpStream,
			uk.me.parabola.imgfmt.app.Area fileBbox,
			uk.me.parabola.imgfmt.app.Area searchBbox, EnhancedProperties props)
			throws IOException {
		preparedLocationInfo = new LinkedHashMap<> ();
		preparer = new BoundaryLocationPreparer(props);
		assert fileBbox != null: "parameter fileBbox must not be null";
		this.bbox = new Rectangle(fileBbox.getMinLong(), fileBbox.getMinLat(),
				fileBbox.getMaxLong() - fileBbox.getMinLong(), fileBbox.getMaxLat()
				- fileBbox.getMinLat());
		this.bbox_key = BoundaryUtil.getKey(this.bbox.y, this.bbox.x);
		root = new Node(this.bbox);
		
		readStreamQuadTreeFormat(inpStream,searchBbox);
	}
	
	
	/**
	 * Create a quadtree for a given bounding box and a list of boundaries.
	 * Involves costly calculations to split the areas.
	 * @param givenBbox	The bounding box for the quadTree, only data within this box is used 
	 * @param boundaries A list of boundaries. For better performance, the list should be sorted so that small areas come first.
	 * @param props if not null, use it to set location names
	 */
	public BoundaryQuadTree(uk.me.parabola.imgfmt.app.Area givenBbox,
			List<Boundary> boundaries, EnhancedProperties props) {
		preparer = new BoundaryLocationPreparer(props);
		assert givenBbox != null: "parameter givenBbox must not be null";
		this.bbox = new Rectangle(givenBbox.getMinLong(), givenBbox.getMinLat(),
				givenBbox.getMaxLong() - givenBbox.getMinLong(), 
				givenBbox.getMaxLat() - givenBbox.getMinLat());
		this.bbox_key = BoundaryUtil.getKey(this.bbox.y, this.bbox.x);
		
		root = new Node(this.bbox);
		// extract the location relevant tags
		preparedLocationInfo = preparer.getPreparedLocationInfo(boundaries);
		if (boundaries == null || boundaries.size() == 0)
			return;
		

		HashMap<String,Boundary> bMap = new HashMap<>();
		for (Boundary b: boundaries){
			bMap.put(b.getId(), b);
			boundaryTags.put(b.getId(), b.getTags());
		}
		sortBoundaryTagsMap();
		// add the boundaries in a specific order
		for (String id: boundaryTags.keySet()){
			root.add (bMap.get(id).getArea(), id, null, DO_NOT_CLIP);
		}
		bMap = null;
		root.split("_");
	}

	/**
	 * Return location relevant Tags for the point defined by Coord 
	 * @param co the point
	 * @return a reference to the internal Tags or null if the point was not found. 
	 * The returned Tags must not be modified by the caller.   
	 */
	public Tags get(Coord co){
		Tags res = root.get(co/*, "_"*/);
		if (res == null && bbox.contains(co.getLongitude(),co.getLatitude())){
			// we did not find the point, probably it lies on a boundary and
			// the clauses regarding insideness of areas make it "invisible"
			// try again a few other nearby points 
			Coord neighbour1 = new Coord(co.getLatitude()-1, co.getLongitude());
			Coord neighbour2 = new Coord(co.getLatitude()  , co.getLongitude()-1);
			Coord neighbour3 = new Coord(co.getLatitude()+1, co.getLongitude());
			Coord neighbour4 = new Coord(co.getLatitude()  , co.getLongitude()+1);
			res = root.get(neighbour1/*, "_"*/);
			if (res == null)
				res = root.get(neighbour2/*, "_"*/);
			if (res == null)
				res = root.get(neighbour3/*, "_"*/);
			if (res == null)
				res = root.get(neighbour4/*, "_"*/);
		}
		return res;
	}

	/**
	 * Return a map with boundary IDs and the related tags.    
	 * @return the map. It is a LinkedHashMap, the order is created with 
	 * AdminLevelCollator and then reversed.  
	 */
	public Map<String, Tags> getTagsMap() {
		return new LinkedHashMap<>(boundaryTags);
	}
	
	/**
	 * Create a map with boundary Ids and the related area parts that 
	 * are stored in the tree. When the parts are added, they normally 
	 * give the original boundary (clipped by the bounding box of the quadtree). 
	 * Exception: For overlapping boundaries with equal admin_levels only one
	 * boundary will contain the complete area information.   
	 * @return A HashMap mapping BoundaryIds to a List with all area parts  
	 */
	public Map<String, List<Area>> getAreas(){
		Map<String, List<Area>> areas = new HashMap<>();
		root.getAreas(areas, "_", null);
		return areas;
	}
	
	/**
	 * For BoundaryMerger: Add the data of another tree into this tree. 
	 * @param other the other instance of BoundaryQuadTree
	 */
	public void merge(BoundaryQuadTree other){
		if (bbox.equals(other.bbox) == false){
			log.error("Cannot merge tree with different bounding box");
			return;
		}
		for (Entry <String, BoundaryLocationInfo> entry : other.preparedLocationInfo.entrySet()){
			if (this.preparedLocationInfo.containsKey(entry.getKey()) == false){
				this.preparedLocationInfo.put(entry.getKey(),entry.getValue());
			}
		}
		// add the others tags
		for (Entry <String, Tags> entry : other.boundaryTags.entrySet()){
			if (this.boundaryTags.containsKey(entry.getKey()) == false){
				this.boundaryTags.put(entry.getKey(),entry.getValue());
			}
		}
		sortBoundaryTagsMap();
		root.mergeNodes(other.root, "_");
	}
	
	/**
	 * Return the area that is covered by a given admin level
	 * @param admLevel reasonable values are 2 .. 11 (inclusive)
	 * @return a new Area instance (might be empty) 
	 */
	public Area getCoveredArea (Integer admLevel){
		return root.getCoveredArea(admLevel, "_");
	}
	
	/**
	 * Return boundary names relevant for the point defined by Coord 
	 * @param co the point
	 * @return A string with a boundary Id, optionally followed by pairs of admlevel:boundary Id.
	 * Sample: r1184826;6:r62579;4:r62372;2:r51477  
	 */
	public String getBoundaryNames(Coord co){
		return root.getBoundaryNames(co);
	}


	/**
	 * Save the BoundaryQuadTree to an open stream. The format is QUADTREE_DATA_FORMAT.
	 * @param stream
	 * @throws IOException
	 */
	public void save(OutputStream stream)throws IOException{
		// save the tag infos of all boundaries first
		for (Entry<String,Tags> entry : boundaryTags.entrySet()){
			writeBoundaryTags(stream, entry.getValue(), entry.getKey());
		}
		// now write the area info for those boundaries that have positions in the quadtree
		root.save(stream, "_");
	}

	/**
	 * Sort the boundary-Tags-Map so that zip-code-only boundaries appear first, followed by
	 * admin_level-11,10,9,...2
	 */
	private void sortBoundaryTagsMap(){
		// make sure that the merged LinkedHashMap is sorted as mergeBoundaries() needs it
		ArrayList<String> ids = new ArrayList<>(boundaryTags.keySet());
		Collections.sort(ids, new AdminLevelCollator());
		Collections.reverse(ids);
		HashMap<String,Tags> tmp = new LinkedHashMap<>(boundaryTags);
		boundaryTags.clear();
		for (String id: ids){
			boundaryTags.put(id,tmp.get(id));
		}
	}


	/**
	 * Write the TAGS sections of the QUADTREE_DATA_FORMAT. Each
	 * section starts with the "TAGS" eye catcher followed by the
	 * boundary id, the number of tags and the tags as 
	 * key/value pairs.
	 * @param stream an already opened OutputStream
	 * @param tags the boundary tags
	 * @param id the boundaryId
	 * @throws IOException
	 */
	private static void writeBoundaryTags(OutputStream stream, Tags tags, String id) throws IOException{
		DataOutputStream dOutStream = new DataOutputStream(stream);
		dOutStream.writeUTF("TAGS");
		dOutStream.writeUTF(id);
		// write the tags
		int noOfTags = tags.size();
	
		dOutStream.writeInt(noOfTags);
	
		Iterator<Entry<String, String>> tagIter = tags.entryIterator();
		while (tagIter.hasNext()) {
			Entry<String, String> tag = tagIter.next();
			dOutStream.writeUTF(tag.getKey());
			dOutStream.writeUTF(tag.getValue());
			noOfTags--;
		}
	
		assert noOfTags == 0 : "Remaining tags: " + noOfTags + " size: "
				+ tags.size() + " " + tags.toString();
	
		dOutStream.flush();
	}

	/**
	 * Read a stream in QUADTREE_DATA_FORMAT 
	 * @param inpStream the already opened DataInputStream
	 * @param searchBBox a bounding box. Areas not intersecting the bbox are 
	 * ignored. 
	 * @throws IOException
	 */
	private void readStreamQuadTreeFormat(DataInputStream inpStream,
			uk.me.parabola.imgfmt.app.Area searchBBox) throws IOException{
		boolean isFirstArea = true;
		try {
			while (true) {
				String type = inpStream.readUTF();
				if (type.equals("TAGS")){
					String id = inpStream.readUTF();
					Tags tags = new Tags();
					int noOfTags = inpStream.readInt();
					for (int i = 0; i < noOfTags; i++) {
						String name = inpStream.readUTF();
						String value = inpStream.readUTF();
						tags.put(name, value.intern());
					}
					boundaryTags.put(id, tags);
				}
				else if (type.equals("AREA")){
					if (isFirstArea){
						isFirstArea = false;
						prepareLocationInfo();
					}
					int minLat = inpStream.readInt();
					int minLong = inpStream.readInt();
					int maxLat = inpStream.readInt();
					int maxLong = inpStream.readInt();
					if (log.isDebugEnabled()){
						log.debug("Next boundary. Lat min:",minLat,"max:",maxLat,"Long min:",minLong,"max:",maxLong);
					}
					uk.me.parabola.imgfmt.app.Area rBbox = new uk.me.parabola.imgfmt.app.Area(
							minLat, minLong, maxLat, maxLong);
					int bSize = inpStream.readInt();
					log.debug("Size:",bSize);

					if ( searchBBox == null || searchBBox.intersects(rBbox)) {
						log.debug("Bbox intersects. Load the boundary");
						String treePath = inpStream.readUTF();
						String id = inpStream.readUTF();
						String refs = inpStream.readUTF();
						if (refs.isEmpty()) 
							refs = null;
						Area area = BoundaryUtil.readAreaAsPath(inpStream);
						
						if (area != null && area.isEmpty() == false)
							root.add(area, refs, id, treePath);
						else {
							log.warn(refs,id,treePath,"invalid or empty or too small area");
						}
					} else {
						log.debug("Bbox does not intersect. Skip",bSize);
						inpStream.skipBytes(bSize);
					}
				}
				else{
					log.error("unknown type field " + type );
				}
			}
		} catch (EOFException exp) {
			// it's always thrown at the end of the file
			//				log.error("Got EOF at the end of the file");
		}
	}

	/**
	 * Fill the map preparedLocationInfo with data from the boundary tags.
	 */
	private void prepareLocationInfo() {
		for (Entry<String, Tags> entry : boundaryTags.entrySet()) {
			BoundaryLocationInfo info = preparer.parseTags(entry.getValue());
			preparedLocationInfo.put(entry.getKey(), info);
		}
	}

	

	/**
	 * A node for the BoundaryQuadTree. Many methods use a so-called treePath to identify the position 
	 * of the node in the tree. A treePath _021 means root->childs[0]->childs[2]->childs[1].
	 * This path is also saved in the QUADTREE_DATA_FORMAT. 
	 * @author GerdP
	 *
	 */
	private class Node {
		private Node [] childs;
		private List<NodeElem> nodes;

		// bounding box of this part of the tree
		private final Rectangle bbox;
		private final uk.me.parabola.imgfmt.app.Area bounds;

		private short depth;
		private boolean isLeaf;

		/**
		 * Create an empty node for the given bbox
		 * @param bbox
		 */
		private Node (Rectangle bbox){
			this.bounds = new uk.me.parabola.imgfmt.app.Area (bbox.y, bbox.x, bbox.y+bbox.height, bbox.x+bbox.width);
			this.bbox = new Rectangle(bbox);
			isLeaf = true;
		}

		/**
		 * Constructor that is used by the split method. The parameters give the corners of a bounding box.
		 * @param minLat
		 * @param minLong
		 * @param maxLat
		 * @param maxLong
		 */
		private Node (int minLat, int minLong, int maxLat, int maxLong){
			this.bounds = new uk.me.parabola.imgfmt.app.Area (minLat, minLong, maxLat, maxLong);
			this.bbox = new Rectangle(minLong, minLat, maxLong - minLong, maxLat - minLat);
			this.isLeaf = true;
		}

		/**
		 * Travel through the tree, save all usable areas of all leaves 
		 * @param stream the open OutputStream
		 * @param treePath the path to this tree node
		 * @throws IOException 
		 */
		private void save(OutputStream stream, String treePath )throws IOException{
			if (isLeaf){
				if (nodes != null){
					for (NodeElem nodeElem :nodes){
						if (nodeElem.isValid())
							nodeElem.save(stream, treePath);
					}
				}
			}
			else {
				for (int i = 0; i < 4; i++){
					childs[i].save(stream, treePath + i);
				}
			}
		}

		/**
		 * Return boundary names relevant for the point defined by Coord 
		 * @param co the point
		 * @return A string with a boundary Id, optionally followed by pairs of admlevel:boundary Id.
		 * Sample: r1184826;6:r62579;4:r62372;2:r51477  
		 */
		private String getBoundaryNames(Coord co) {
			if (this.bounds.contains(co) == false)
				return null;
			if (isLeaf){
				if (nodes == null || nodes.size() == 0)
					return null;
				int lon = co.getLongitude();
				int lat = co.getLatitude();
				for (NodeElem nodeElem: nodes){
					if (nodeElem.tagMask > 0){	
						if (nodeElem.getArea().contains(lon,lat)){
							String res = new String (nodeElem.boundaryId);
							if (nodeElem.locationDataSrc != null)
								res += ";" + nodeElem.locationDataSrc;
							return res;
						}
					}
				}
			}
			else {
				for (int i = 0; i < 4; i++){
					String res = childs[i].getBoundaryNames(co);
					if (res != null) 
						return res; 
				}
			}
			return null;
		}

		/**
		 * Return location relevant Tags for the point defined by Coord 
		 * @param co the point
		 * @return a reference to the internal Tags or null if the point was not found. 
		 * The returned Tags must not be modified by the caller.   
		 */
		private Tags get(Coord co/*, String treePath*/){
			if (this.bounds.contains(co) == false)
				return null;
			if (isLeaf){
				if (nodes == null || nodes.size() == 0)
					return null;
				int lon = co.getLongitude();
				int lat = co.getLatitude();
				for (NodeElem nodeElem: nodes){
					if (nodeElem.tagMask > 0){	
						if (nodeElem.getArea().contains(lon,lat)){
							return nodeElem.locTags;
						}
					}
				}
			}
			else {
				for (int i = 0; i < 4; i++){
					Tags res = childs[i].get(co/*, treePath+i*/);
					if (res != null) 
						return res; 
				}
			}
			return null;
		}

		/**
		 * Debugging helper: Print node Tags and maybe create gpx
		 * @param prefix identifies the calling routine
		 * @param treePath 
		 */
		private void printNodes(String prefix, String treePath){
			int n = 0;
			for (NodeElem nodeElem: nodes){
				if (treePath.equals(DEBUG_TREEPATH)){
					nodeElem.saveGPX(prefix,treePath);
				}
				String res = new String();
				for (int i = mkgmapTagsArray.length-1; i >= 0 ; --i){
					String tagVal = nodeElem.locTags.get(mkgmapTagsArray[i] );
					if (tagVal != null){
						res += i+1 + "=" + tagVal + ";";
					}
				}
				System.out.println(prefix + " " + treePath + " " +  n + ":" + nodeElem.boundaryId + " " + nodeElem.tagMask + " " + res );
				++n;
			}
		}

		/**
		 * Test if all areas in one node are distinct areas. This is wanted, but not 
		 * absolutely needed. 
		 * @param treePath Position in the quadtree. Used for GPX.
		 * @return false if any area intersects with another area and the 
		 * intersection has a dimension.
		 */
		private boolean testIfDistinct(String treePath){
			boolean ok = true;
			for (int i=0; i< nodes.size()-1; i++){
				for (int j=i+1; j < nodes.size(); j++){
					Area a = new Area (nodes.get(i).getArea());
					a.intersect(nodes.get(j).getArea());
					
					if (a.isEmpty())
						continue;
					Path2D.Double path = new Path2D.Double(a);
					a = new Area(path);
					if (a.isEmpty())
						continue;
					
					if (a.getBounds2D().getHeight() < 0.1d && a.getBounds2D().getWidth() < 0.1d)
						continue;
					ok = false;
					log.error("boundaries still intersect in tree path "
							+ treePath + " " + nodes.get(i).boundaryId + " "
							+ nodes.get(j).boundaryId + " bbox: " + a.getBounds2D());
					NodeElem tmpNodeElem = new NodeElem(nodes.get(i).boundaryId+"_"+nodes.get(j).boundaryId,
							new Area(a.getBounds2D()), null); 
					tmpNodeElem.saveGPX("intersection_rect",treePath);
				}
			}
			if (DEBUG){
				if (!ok){
					for (NodeElem nodeElem: nodes){
						nodeElem.saveGPX("not_distinct",treePath);
					}				
				}
			}
			return ok;
		}
		/**
		 * Add an area and the related tags to the tree. The position in the tree is known
		 * and passed via the treePath.
		 * @param area the part of the boundary area that should be added to the tree.    
		 * @param refs a string that contains information about other boundaries that share the
		 * same area
		 * @param boundaryId id of the originating boundary
		 * @param treePath empty string: calculate position, else the first character is used as index of the child
		 */
		private void add(Area area, String refs, String boundaryId, String treePath){
			Node node = this;
			String path = treePath;
			while(path.isEmpty() == false){
				int idx = Integer.parseInt(path.substring(0, 1));
				path = path.substring(1);
				if (node.childs == null)
					node.allocChilds();
				node = node.childs[idx];
			}
			
			if (node.nodes == null){
				node.nodes = new ArrayList<>();
			}
			NodeElem nodeElem = new NodeElem(boundaryId, area, refs);
			assert (area.getBounds2D().getWidth() == 0 || area.getBounds2D().getHeight() == 0 || this.bbox.intersects(area.getBounds2D())) : "boundary bbox doesn't fit into quadtree "+ bbox + " " + area.getBounds2D(); 
			node.nodes.add(nodeElem);
		}

		/**
		 * Add a shape and the related tags to the tree. 
		 * @param shape the part of the boundary area that should be added to the tree.    
		 * @param boundaryId id of the originating boundary
		 * @param refs A string containing boundaryIds and admin-level info
		 * of all boundaries with lower admin levels that share the same area. 
		 * @param clipOption true: clip the shape with the bounding box of the nodeElem, 
		 * false: use shape without clipping 
		 */
		private void add(Shape shape, String boundaryId, String refs, boolean clipOption){
			assert isLeaf;
			Path2D.Double path;
			if (clipOption){
				path = ShapeSplitter.clipShape (shape, bbox);
				// only add areas that intersect with this part of the tree
				if (path == null)
					return;
			}
			else
				path = new Path2D.Double(shape);
			if (nodes == null)
				nodes = new ArrayList<>();
			NodeElem nodeElem = new NodeElem(boundaryId, path, refs);
			nodes.add(nodeElem);
		}

		/**
		 * Merge this subtree with another subtree.
		 * @param other A Node of another BoundaryQuadTree
		 * @param treePath position of this node in its tree 
		 */
		private void mergeNodes(Node other, String treePath){
			if (!this.isLeaf && !other.isLeaf){
				for (int i = 0; i < 4; i++){
					childs[i].mergeNodes(other.childs[i], treePath+i);
				}
			}
			else{
				// (sub) tree is different, rebuild it as combination of 
				// both trees.
				HashMap<String,List<Area>> areas = new HashMap<>();
				this.getAreas(areas, treePath,null);
				other.getAreas(areas, treePath,null);
				isLeaf = true;
				nodes = null;
				childs = null;
				
				for (String id: boundaryTags.keySet()){
					List<Area> aList = areas.get(id);
					if (aList == null)
						continue;
					Path2D.Double path = new Path2D.Double();
					for (Area area : aList){
						path.append(area, false);
					}
					add(new Area(path), id, null, DO_NOT_CLIP);
				}
				split(treePath);
			}
		}
		
		/**
		 * Calculate the area that is covered by boundaries of a given adminlevel
		 * @param admLevel the admin_level, a value from 2 to 11 (including)
		 * @param treePath A string that helps to identify the position in the quadtree 
		 * @return a new Area instance (might be empty)
		 */
		private Area getCoveredArea(Integer admLevel, String treePath){
			HashMap<String,List<Area>> areas = new HashMap<>();
			this.getAreas(areas, treePath, admLevel);
			if (areas.isEmpty() == false){
				Path2D.Double path = new Path2D.Double(PathIterator.WIND_NON_ZERO, 1024 * 1024);
				for (Entry <String, List<Area>> entry : areas.entrySet()){
					for (Area area: entry.getValue()){
						path.append(area, false);
					}
				}
				Area combinedArea = new Area(path);
				return combinedArea;
			}
			return new Area();
		}
		
		/**
		 * See BoundaryQuadTree {@link #BoundaryQuadTree.getAreas()}
		 * @param areas
		 * @param treePath
		 * @param admLevel
		 */
		private void getAreas(Map<String, List<Area>> areas, String treePath, Integer admLevel){
			if (!this.isLeaf ){
				for (int i = 0; i < 4; i++){
					childs[i].getAreas(areas, treePath+i, admLevel);
				}
				return;
			}
			if (nodes == null || nodes.size() == 0)
				return;
			
			Short testMask = null;
			if (admLevel != null)
				testMask = (short) (1<<(admLevel-1));
			for (NodeElem nodeElem : nodes){
				String id = nodeElem.boundaryId;
				if (testMask != null && (nodeElem.tagMask & testMask) == 0)
					continue;
				List<Area> aList = areas.get(id);
				Area a = new Area(nodeElem.getArea());
				if (aList == null){
					aList = new ArrayList<>(4);
					areas.put(id, aList);
				}
				aList.add(a);
				if (testMask != null)
					continue;
				
				String refInfo = nodeElem.locationDataSrc;
				if (refInfo != null) {
					String[] relBounds = refInfo.split(Pattern.quote(";"));
					for (String relBound : relBounds) {
						String[] relParts = relBound.split(Pattern.quote(":"));
						if (relParts.length != 2) {
							log.error("Wrong format in locationDataSrc. Value: " + refInfo);
							continue;
						}
						id = relParts[1];
						aList = areas.get(id);
						a = new Area(nodeElem.getArea());
						if (aList == null){
							aList = new ArrayList<>(4);
							areas.put(id, aList);
						}
						aList.add(a);
					}
				}
			}
		}
		
		/***
		 * Merge information from the boundaries saved by BoundarySaver.
		 * This method is used when bnd file is in raw format.
		 * For intersections, create new areas with the merged 
		 * location tags, and subtract the parts from the source 
		 * areas. The result should be a reduced number of distinct areas.
		 * @param treePath Identifies the position in the tree
		 */
		private void makeDistinct(String treePath){
			if (isLeaf == false || nodes == null || nodes.size() <= 1)
				return;
			if (DEBUG){
				printNodes("start", treePath);
			}
			long t1 = System.currentTimeMillis();
			if (DEBUG){
				if (treePath.equals(DEBUG_TREEPATH) || DEBUG_TREEPATH.equals("all")){
					for (NodeElem nodeElem: nodes){
						nodeElem.saveGPX("start",treePath);
					}			
				}
			}
			
			mergeEqualIds();
			mergeLastRectangles();
			if (DEBUG)
				printNodes("prep", treePath);

			List<NodeElem> reworked = new ArrayList<>();

			// detect intersection of areas, merge tag info
			for (int i=0; i < nodes.size(); i++){
				NodeElem toAdd = nodes.get(i);
				if (DEBUG){
					if (treePath.equals(DEBUG_TREEPATH) || DEBUG_TREEPATH.equals("all")){
						for (NodeElem nodeElem: reworked){
							nodeElem.saveGPX("debug"+i,treePath);
						}			
					}
				}
				for (int j=0; j < reworked.size(); j++){
					if (toAdd.isValid() == false)
						break;
					NodeElem currElem = reworked.get(j);
					if (currElem.srcPos == i || currElem.getArea().isEmpty())
						continue;

					Rectangle2D rCurr = currElem.getArea().getBounds2D();

					Rectangle2D rAdd = rCurr.createIntersection(toAdd.getArea().getBounds2D());
					if (rAdd.isEmpty()){
						continue; 
					}
					// the bounding boxes intersect, so we have to find out if the areas also intersect
					Area toAddxCurr = new Area(currElem.getArea());
					toAddxCurr.intersect(toAdd.getArea());
										
					if (!isWritable(toAddxCurr)){
						continue; // empty or only too small fragments 
					}
					
					Area toAddMinusCurr = new Area(toAdd.getArea());
					toAddMinusCurr.subtract(currElem.getArea());

					if (toAddMinusCurr.isEmpty()){
						// toadd is fully covered by curr
						if (toAdd.tagMask == POSTCODE_ONLY){
							// if we get here, toAdd has only zip code that is already known 
							// in larger or equal area of currElem
							toAdd.getArea().reset(); // ignore this
							break;
						}
					}

					// test if toAdd contains usable tag(s)
					String chkMsg = currElem.checkAddTags(toAdd, bounds);
					// warning: intersection of areas with equal levels   
					if (chkMsg != null){
						if (DEBUG){
							// save debug GPX for areas that wiil 
							// appear in warning message below 
							toAdd.saveGPX("warn_toAdd",treePath);
							currElem.saveGPX("warn_curr",treePath);
						}
						log.warn(chkMsg);
					}
					
					Area currMinusToAdd = new Area(currElem.getArea());
					currMinusToAdd.subtract(toAdd.getArea());
					
					// remove intersection part from toAdd
					toAdd.setArea(toAddMinusCurr);
					if (!isWritable(currMinusToAdd)){
					    // curr is fully covered by toAdd 
						if (toAdd.tagMask != POSTCODE_ONLY){
							currElem.addLocInfo(toAdd);
						}
						continue; // no need to create new intersection area
					}

					NodeElem intersect = new NodeElem(currElem, toAddxCurr, i);
					
					if (DEBUG){
						if (chkMsg != null)
							intersect.saveGPX("warn_inter", treePath);
					}

					// remove intersection part also from curr 
					currElem.setArea(currMinusToAdd);
					
					if (toAdd.tagMask != POSTCODE_ONLY){
						// combine tag info in intersection
						intersect.addLocInfo(toAdd);
						reworked.add(intersect);
					}
				}

				if (toAdd.isValid())
					reworked.add(toAdd);
			}
			nodes = reworked;
			// free memory for nodes with empty or too small areas
			removeEmptyAreas(treePath);

			long dt = System.currentTimeMillis()-t1;
			if (dt  > 1000){
				log.info(bbox_key, " : makeDistinct required long time:", dt, "ms");
			}
			if (DEBUG)
				printNodes("end", treePath);

			//double check ?
			if (DO_ALL_TESTS){
				testIfDistinct(treePath);
			}
		}
			
		/**
		 * Combine the areas with equal boundary IDs. 
		 * We can assume that equal IDs are paired when add is 
		 * called with sorted input.
		 */
		private void mergeEqualIds(){
			int start = nodes.size()-1;
			for (int i = start; i > 0; i--){
				if (nodes.get(i).boundaryId.equals(nodes.get(i-1).boundaryId)){
					nodes.get(i-1).getArea().add(nodes.get(i).getArea());
					nodes.remove(i);
				}
			}
		}
		/**
		 * Optimization:
		 * The last nodes are likely to fully cover the quadtree bbox. 
		 * Merge the tag information for them to avoid some splitting
		 * and later merging.
		 */
		private void mergeLastRectangles(){
			boolean done;
			//step1: merge nodes that fully cover the quadtree area
			do{
				done = true;
				if (nodes.size()<= 1)
					break;
				NodeElem lastNode = nodes.get(nodes.size()-1);
				NodeElem prevNode = nodes.get(nodes.size()-2);
				// don't merge admin_level tags into zip-code only boundary
				if (prevNode.tagMask != POSTCODE_ONLY && lastNode.getArea().isRectangular() && prevNode.getArea().isRectangular()){
					// two areas are rectangles, it is likely that they are equal to the bounding box
					// In this case we add the tags to the existing area instead of creating a new one
					if (prevNode.getArea().equals(lastNode.getArea())){
						prevNode.addLocInfo(lastNode);
						nodes.remove(nodes.size()-1);
						done = false;
					}
				}
			} while (!done);
		}

		/**
		 * The mergeBoundaries() algorithm can create empty
		 * areas (points, lines, or extremely small intersections). 
		 * These are removed here.
		 * @param treePath
		 */
		private void removeEmptyAreas(String treePath){
			for (int j = nodes.size()-1; j >= 0 ; j--){
				boolean removeThis = false;
				NodeElem chkRemove = nodes.get(j);
				if (chkRemove.isValid() == false)
					removeThis = true;
				else if (this.bbox.intersects(chkRemove.getArea().getBounds2D()) == false){
					// we might get here because of errors in java.awt.geom.Area
					// sometimes, Area.subtract() seems to produce an area which 
					// lies outside of original areas
					removeThis = true;
				}else if (!isWritable(chkRemove.getArea())){
					removeThis = true;
				}
				if (removeThis){
					nodes.remove(j);
				}
			}			 		
		}
		
		/**
		 * allocate 4 childs with bounding boxes that have 1/4 of the 
		 * size of the parent.  
		 */
 		private void allocChilds(){
			childs = new Node[4];
			Coord center = bounds.getCenter();

			childs[0] = new Node(bounds.getMinLat(), bounds.getMinLong(),
					center.getLatitude(), center.getLongitude());
			childs[1] = new Node(center.getLatitude(), bounds.getMinLong(),
					bounds.getMaxLat(), center.getLongitude());
			childs[2] = new Node(bounds.getMinLat(), center.getLongitude(),
					center.getLatitude(), bounds.getMaxLong());
			childs[3] = new Node(center.getLatitude(), center.getLongitude(),
					bounds.getMaxLat(), bounds.getMaxLong());
			for (int i = 0; i < 4; i++){
				childs[i].depth = (short) (this.depth + 1);
			}
			isLeaf = false;
		}

		/**
		 * Split the tree into 4 equally sized parts and
		 * distribute the data.
		 */
		private void split(String treePath){
			if (isLeaf == true){
				if  (nodes == null)
					return;
				if (DEBUG){
					String fname = "gpx/" + treePath+ "/bbox"+treePath;
					List<List<Coord>> polys = Java2DConverter.areaToShapes(Java2DConverter.createBoundsArea(bounds));
					GpxCreator.createGpx(fname, polys.get(0));
				}
				// subject to tuning
				if (depth >= 5 || nodes.size() <= 7 || bounds.getHeight() < 10 || bounds.getWidth() < 10  ){
					makeDistinct(treePath);
					return ;
				}

//				mergeLastRectangles();
				allocChilds();
				for (NodeElem nodeElem: nodes){
					Rectangle shapeBBox = nodeElem.shape.getBounds();
					for (int i = 0; i < 4; i++){
						if (childs[i].bbox.intersects(shapeBBox))
							childs[i].add(nodeElem.shape, nodeElem.boundaryId, nodeElem.locationDataSrc, DO_CLIP);
					}
				}
				// return memory to GC
				nodes = null;
			}
			// finally try splitting the sub trees
			for (int i = 0; i < 4; i++){
				childs[i].split(treePath+i);
			}
		}
	}

	private class NodeElem{
		// the intersections of the boundaries with the bounding box of this node
		private Area area;
		
		private Shape shape; // for temp. use when splitting
		// location relevant tags of boundaries that intersect with the bounding box of this node
		private Tags locTags;

		// a bit mask that helps comparing Tags
		private short tagMask;
		// boundary that was initially used 
		private final String boundaryId;
		// data for the intersects_with tag
		private String locationDataSrc;
		private int srcPos;

		/**
		 * Create a node element. 
		 * @param boundaryId The boundary Id
		 * @param area the (part of the) boundary area stored in this node
		 * @param refs A string containing boundaryIds and admin level info
		 * of all boundaries with lower admin levels that share the same area. 
		 */
		NodeElem (String boundaryId, Area area, String refs){
			srcPos = -1;
			this.boundaryId = boundaryId;
			this.area = area;
			this.locationDataSrc = refs;
			calcLocTags();
		}

		/**
		 * Create a node element. 
		 * @param boundaryId The boundary Id
		 * @param shape the (part of the) boundary area stored in this node
		 * @param refs A string containing boundaryIds and admin level info
		 * of all boundaries with lower admin levels that share the same area. 
		 */
		NodeElem (String boundaryId, Shape shape, String refs){
			srcPos = -1;
			this.boundaryId = boundaryId;
			this.shape = shape;
			this.locationDataSrc = refs;
			calcLocTags();
		}

		/**
		 * Create a new node element as a partly copy of an existing 
		 * NodeElem and a new area. 
		 * @param other the existing NodeElem instance
		 * @param area the new area 
		 * @param srcPos identifies the position of other in the 
		 * nodes list of the Node.
		 */
		NodeElem (NodeElem other, Area area, int srcPos){
			this.area = area;
			this.srcPos = srcPos;
			this.boundaryId = other.boundaryId;
			this.tagMask = other.tagMask;
			this.locationDataSrc = other.locationDataSrc;
			this.locTags = other.locTags.copy();
		}

		/**
		 * check if a NodeElem contains usable info.
		 * @return false if either the area is not usable or 
		 * the tags should be ignored.
		 */
		private boolean isValid(){
			if (tagMask == 0)
				return false;
			Area checkArea = getArea();
			if (checkArea == null || checkArea.isEmpty()
					|| checkArea.getBounds2D().getWidth() <= BoundaryUtil.MIN_DIMENSION && checkArea.getBounds2D().getHeight() <= BoundaryUtil.MIN_DIMENSION)
				return false;
			return true;
		}
		/**
		 * Add the location relevant data of another NodeElem
		 * @param toAdd the other NodeElem
		 */
		private void addLocInfo(NodeElem toAdd){
			addLocationDataString(toAdd);
			addMissingTags(toAdd.locTags); 
			tagMask |= toAdd.tagMask;
		}
		
		/**
		 * Calculate the tags that are location relevant. 
		 * Problem: If the tree is created by  BoundaryPreparer, we do not know how to calculate 
		 * the name because we don't know which tag to use for this, so be aware that this 
		 * may return different results compared to the LocationHook.   
		 * @param boundary
		 */
		private void calcLocTags(){
			locTags = new Tags();
			tagMask = 0;
			BoundaryLocationInfo bInfo  = preparedLocationInfo.get(boundaryId);
			if (bInfo == null){
				log.error("unknown boundaryId " + boundaryId);
				return;
			}
			if (bInfo.getZip() != null){
				locTags.put("mkgmap:postcode",bInfo.getZip());
			}
			
			if (bInfo.getAdmLevel() != BoundaryLocationPreparer.UNSET_ADMIN_LEVEL){
				locTags.put(BoundaryQuadTree.mkgmapTagsArray[bInfo.getAdmLevel()-1], bInfo.getName());
			}
			if (locationDataSrc != null && locationDataSrc.isEmpty() == false){
				// the common format of refInfo is 
				// 2:r19884;4:r20039;6:r998818
				String[] relBounds = locationDataSrc.split(Pattern.quote(";"));
				for (String relBound : relBounds) {
					String[] relParts = relBound.split(Pattern.quote(":"));
					if (relParts.length != 2) {
						log.error("Wrong format. Value: " + locationDataSrc);
						continue;
					}
					BoundaryLocationInfo addInfo = preparedLocationInfo.get(relParts[1]);
					if (addInfo == null) {
						log.warn("Referenced boundary not known:", relParts[1]);
						continue;
					}

					int addAdmLevel = addInfo.getAdmLevel();
					String addAdmName = null;
					if (addAdmLevel != BoundaryLocationPreparer.UNSET_ADMIN_LEVEL){
						addAdmName = addInfo.getName();
					}
					String addZip = addInfo.getZip();

					if (addAdmName != null){
						if (locTags.get(BoundaryQuadTree.mkgmapTagsArray[addAdmLevel-1]) == null)
							locTags.put(BoundaryQuadTree.mkgmapTagsArray[addAdmLevel-1], addAdmName);
					}
					if (addZip != null){
						if (locTags.get("mkgmap:postcode") == null)
							locTags.put("mkgmap:postcode", addZip);
					}
				}
			}
			tagMask = calcLocationTagsMask();
		}
		
		/**
		 * Merge the locationDataSrc of two NodeElems.
		 * The caller has to make sure that the merge makes sense.
		 * @param toAdd The other NodeElem
		 */
		private void addLocationDataString (NodeElem toAdd){
			BoundaryLocationInfo info = preparedLocationInfo.get(toAdd.boundaryId);
			assert info.getAdmLevel() > 0 : "cannot use admLevel";
			
			String admLevel = info.getAdmLevel() + ":" + toAdd.boundaryId;
			if (this.locationDataSrc == null)
				this.locationDataSrc =  admLevel;
			else 
				this.locationDataSrc +=  ";" + admLevel;
			if (toAdd.locationDataSrc != null){
				this.locationDataSrc += ";" + toAdd.locationDataSrc;
			}
		}
		/**
		 * Write a nodeElem an AREA segment of the QUADTREE_DATA_FORMAT. 
		 * @param stream the already opened OutputStream
		 * @param treePath identifies the position within the tree
		 * @throws IOException
		 */
		private void save(OutputStream stream, String treePath) throws IOException{
			ByteArrayOutputStream oneItemStream = new ByteArrayOutputStream();
			try(DataOutputStream dos = new DataOutputStream(oneItemStream)){
				String id = this.boundaryId;
				dos.writeUTF(treePath.substring(1));
				dos.writeUTF(id);
				if (this.locationDataSrc == null)
					dos.writeUTF("");
				else 
					dos.writeUTF(this.locationDataSrc);
				BoundarySaver.writeArea(dos, this.getArea());
			}
			// now start to write into the real stream

			// first write the bounding box so that is possible to skip the
			// complete entry
			uk.me.parabola.imgfmt.app.Area outBBox = Java2DConverter.createBbox(this.getArea());
			DataOutputStream dOutStream = new DataOutputStream(stream);
			dOutStream.writeUTF("AREA");
			dOutStream.writeInt(outBBox.getMinLat());
			dOutStream.writeInt(outBBox.getMinLong());
			dOutStream.writeInt(outBBox.getMaxLat());
			dOutStream.writeInt(outBBox.getMaxLong());

			// write the size of the boundary block so that it is possible to
			// skip it
			byte[] data = oneItemStream.toByteArray();
			assert data.length > 0 : "bSize is not > 0 : " + data.length;
			dOutStream.writeInt(data.length);

			// write the boundary block
			dOutStream.write(data);
			dOutStream.flush();
		}


		private Area getArea(){
			if (shape != null){
				area = new Area(shape);
				shape = null;
			}
			return area;
		}

		private void setArea(Area area) {
			this.area = area;
			this.shape = null;
		}


		/**
		 * calculate a handy short value that represents the available location tags
		 * @return a bit mask, a bit with value 1 means the corresponding entry in {@link locationTagNames } 
		 * is available
		 */
		private short calcLocationTagsMask(){
			short res = 0;
			for (int i = 0; i < mkgmapTagsArray.length; i++){
				if (locTags.get(mkgmapTagsArray[i] ) != null)
					res |= (1 << i);
			}
			return res;
		}
		
		/**
		 * For debugging: Save the area in gpx format
		 * @param desc used as directory name  
		 * @param treePath
		 */
		private void saveGPX(String desc, String treePath){
			if (DEBUG){
				if (area == null)
					return;
				List<List<Coord>> singlePolys = Java2DConverter.areaToShapes(area);
				Collections.reverse(singlePolys);

				int cntPoly = 0;
				for (List<Coord> polyPart : singlePolys) {
					String attr = Way.clockwise(polyPart) ? "o" : "i";
					String fname = "gpx/" + treePath+ "/" +  desc + "_" + area.getBounds().x + "_" + area.getBounds().y + "_" + boundaryId+ "_" + cntPoly + "_"+attr;
					GpxCreator.createGpx(fname, polyPart);
					++cntPoly;
				}
				
			}
		}


		/**
		 * Handle errors in OSM data. Two boundaries with equal levels should not intersect.
		 * Special case: zip-code-only boundaries with same zip code 
		 * @param other the other NodeElem
		 * @param bounds a bounding box for the intersection of the two areas. Used 
		 * to create the error message.
		 * @return null if no error, else a String with an error message
		 */
		private String checkAddTags(NodeElem other, uk.me.parabola.imgfmt.app.Area bounds){
			String errMsg = null;
			int errAdmLevel = 0;
			// case c) toAdd area is fully covered by currElem area
			for (int k = 0; k < mkgmapTagsArray.length; k++){
				int testMask = 1 << k;
				if ((testMask & other.tagMask) != 0 && (this.tagMask & testMask) != 0){
					if (testMask == POSTCODE_ONLY){
						String zipKey = mkgmapTagsArray[k];
						if (other.locTags.get(zipKey).equals(this.locTags.get(zipKey)) == false){
							errMsg = "different " + zipKey;
							break;
						}
					}
					else{
						errAdmLevel = k+1;
						errMsg = new String ("same admin_level (" + errAdmLevel + ")");
						break;
					}
				}
			}
			if (errMsg != null){
				String url = bounds.getCenter().toOSMURL() + "&";
				url += (other.boundaryId.startsWith("w")) ? "way" : "relation";
				url += "=" + other.boundaryId.substring(1);
				//http://www.openstreetmap.org/?lat=49.394988&lon=6.551425&zoom=18&layers=M&relation=122907
				errMsg= "incorrect data: " + url + " intersection of boundaries with " + errMsg + " " + other.boundaryId + " " + this.boundaryId + " " ;
				if (errAdmLevel != 0 && this.locationDataSrc != null)
					errMsg += this.locationDataSrc;
			}
			
			return errMsg;
		}

		/**
		 * Add tags from src to locTags if they are missing 
		 * @param src the Tags to be added 
		 */
		private void addMissingTags(Tags src){
			Iterator<Entry<String,String>> tagIter = src.entryIterator();
			while (tagIter.hasNext()) {
				Entry<String,String> tag = tagIter.next();
				if (locTags.get(tag.getKey()) == null){
					locTags.put(tag.getKey(),tag.getValue());
				}
			}
		}
	}

	
	/***
	 * Used to sort BoundaryLocationInfo. Input are boundaryIds.
	 * @author gerd
	 *
	 */
	public class AdminLevelCollator implements Comparator<String> {

		public int compare(String o1, String o2) {
			if (o1.equals(o2)) {
				return 0;
			}

			BoundaryLocationInfo i1 = preparedLocationInfo.get(o1);
			BoundaryLocationInfo i2 = preparedLocationInfo.get(o2);
			
			int adminLevel1 = i1.getAdmLevel();
			int adminLevel2 = i2.getAdmLevel();

			if (i1.getName() == null || "?".equals(i1.getName())) {
				// admin_level tag is set but no valid name available
				adminLevel1 = BoundaryLocationPreparer.UNSET_ADMIN_LEVEL;
			}
			if (i2.getName() == null || "?".equals(i2.getName())) {
				// admin_level tag is set but no valid name available
				adminLevel2 = BoundaryLocationPreparer.UNSET_ADMIN_LEVEL;
			}
			
			if (adminLevel1 > adminLevel2)
				return 1;
			if (adminLevel1 < adminLevel2)
				return -1;
			
			boolean post1set = i1.getZip() != null;
			boolean post2set = i2.getZip() != null;
			if (post1set && !post2set)
				return 1;
			if (!post1set && post2set)
				return -1;
			// if all is equal, prefer the lower boundaryId
			return o1.compareTo(o2);
		}
	}
	
	/**
	 * test if the conversion to a Path2D and back gives an empty area. If the
	 * area is not already empty this routine simulates the writing and reading
	 * and tests if the result is empty. Returns true is the area is not empty.
	 */
	public static boolean isWritable(Area area){
		if (area.isEmpty())
			return false;
		Path2D.Double path = new Path2D.Double(area);
		Area testArea = new Area(path);
		if (testArea.isEmpty()){
			return false;  
		}
		return true;
	}
}
