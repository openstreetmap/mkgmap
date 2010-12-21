/*
 * Copyright (C) 2010.
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

import java.util.Set;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Allows common code to be called during the reading of OSM files in both
 * their XML and binary formats.
 *
 * You should only use this when you need access to other ways or nodes
 * or the IDs of the individual points that go into a way, which are no
 * longer available during conversion.
 *
 * This is the stage before conversion from the node/way/tag format to the
 * general intermediate format. Most operations should be done during that
 * conversion process, which is accessible from the style file language.
 *
 * We also want access to the other ways/nodes to generate sea polygons,
 * cycle lanes and so on.
 *
 * @author Steve Ratcliffe
 */
public interface OsmReadingHooks {

	/**
	 * Passes in the element saver and the command line options. The hook code
	 * can use the options to set itself up if needed.
	 *
	 * The saver gives access to the previously saved nodes/ways/relations and
	 * also allows you to add extra ways.
	 *
	 * @param saver This is where all the elements are being collected.  You can access previously added
	 * elements from here by their id. You can also add generated elements.  You must not add the
	 * element that is being passed in as it will be added automatically.
	 *
	 * @param props The command line options.
	 *
	 * @return If you return false then this set of hooks will not be used. So if they
	 * are not needed based on the options supplied you can disable it.
	 */
	public boolean init(ElementSaver saver, EnhancedProperties props);

	/**
	 * Retrieves the tags that are used by this hook. Tags that are used only if they are referenced
	 * in the style file should not be added to this list.
	 * 
	 * @return the tag names used by this hook
	 */
	public Set<String> getUsedTags();
	
	/**
	 * Called on adding a node to the saver and just before it is added. You can modify
	 * it, create new nodes etc.
	 *
	 * @param node The node to be added.
	 */
	void onAddNode(Node node);

	/**
	 * Add the given way. The way must be complete, call after the end tag
	 * is seen for the XML format.
	 *
	 * @param way The osm way.
	 */
	public void onAddWay(Way way);

	/**
	 * This is called whenever a node is added to a way.  A node is something with tags, not just a Coord.
	 *
	 * The way will not have been added via addWay() yet.  The node is the node that
	 *
	 * @param way The incomplete way.
	 * @param coordId The coordinate id of the node that is being added.
	 * @param co The coordinate.
	 */
	public void onCoordAddedToWay(Way way, long coordId, Coord co);

	/**
	 * Called after the file has been read.  Can be used to add more elements to the saver
	 * based on information stored up.
	 */
	public void end();
}
