/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 03-Dec-2006
 */
package uk.me.parabola.imgfmt.app.map;

import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.lbl.LBLFile;
import uk.me.parabola.imgfmt.app.net.NETFile;
import uk.me.parabola.imgfmt.app.net.NODFile;
import uk.me.parabola.imgfmt.app.trergn.InternalFiles;
import uk.me.parabola.imgfmt.app.trergn.MapObject;
import uk.me.parabola.imgfmt.app.trergn.PointOverview;
import uk.me.parabola.imgfmt.app.trergn.PolygonOverview;
import uk.me.parabola.imgfmt.app.trergn.PolylineOverview;
import uk.me.parabola.imgfmt.app.trergn.RGNFile;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;
import uk.me.parabola.imgfmt.app.trergn.TREFile;
import uk.me.parabola.imgfmt.app.trergn.Zoom;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.log.Logger;
import uk.me.parabola.util.Configurable;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Holder for a complete map.  A map is made up of several files which
 * include at least the TRE, LBL and RGN files.
 *
 * It is the interface for all information about the whole map, such as the
 * point overviews etc.  Subdivision will hold the map elements.
 *
 * <p>Needless to say, it has nothing to do with java.util.Map.
 *
 * @author Steve Ratcliffe
 */
public class Map implements InternalFiles, Configurable {
	private static final Logger log = Logger.getLogger(Map.class);

	private String filename;
	private String mapName;
	private FileSystem fileSystem;

	private TREFile treFile;
	private RGNFile rgnFile;
	private LBLFile lblFile;
	private NETFile netFile;
	private NODFile nodFile;

	// Use createMap() or loadMap() instead of creating a map directly.
	private Map() {
	}

	/**
	 * Create a complete map.  This consists of (at least) three
	 * files that all have the same basename and different extensions.
	 *
	 * @param mapname The name of the map.  This is an 8 digit number as a
	 * string.
	 * @param params Parameters that describe the file system that the map
	 * will be created in.
	 * @return A map object that holds together all the files that make it up.
	 * @throws FileExistsException If the file already exists and we do not
	 * want to overwrite it.
	 * @throws FileNotWritableException If the file cannot
	 * be opened for write.
	 */
	public static Map createMap(String mapname, FileSystemParam params)
			throws FileExistsException, FileNotWritableException
	{
		Map m = new Map();
		m.mapName = mapname;
		String outFilename = mapname + ".img";

		FileSystem fs = ImgFS.createFs(outFilename, params);
		m.filename = outFilename;
		m.fileSystem = fs;

		m.rgnFile = new RGNFile(m.fileSystem.create(mapname + ".RGN"));
		m.treFile = new TREFile(m.fileSystem.create(mapname + ".TRE"), true);
		m.lblFile = new LBLFile(m.fileSystem.create(mapname + ".LBL"));

		int mapid;
		try {
			mapid = Integer.parseInt(mapname);
		} catch (NumberFormatException e) {
			mapid = 0;
		}
		m.treFile.setMapId(mapid);
		m.fileSystem = fs;

		return m;
	}

	public void config(EnhancedProperties props) {
		try {
			if (props.containsKey("route")) {
				addNet();
				addNod();
			} else if (props.containsKey("net")) {
				addNet();
			}
		} catch (FileExistsException e) {
			log.warn("Could not add NET and/or NOD sections");
		}

		treFile.config(props);
	}

	protected void addNet() throws FileExistsException {
		netFile = new NETFile(fileSystem.create(mapName + ".NET"), true);
	}

	protected void addNod() throws FileExistsException {
		nodFile = new NODFile(fileSystem.create(mapName + ".NOD"), true);
	}

	/**
	 * Set the area that the map covers.
	 * @param area The outer bounds of the map.
	 */
	public void setBounds(Area area) {
		treFile.setBounds(area);
	}

	/**
	 * Add a copyright message to the map.
	 * @param str the copyright message. The second (last?) one set
	 * gets shown when the device starts (sometimes?).
	 */
	public void addCopyright(String str) {
		Label cpy = lblFile.newLabel(str);
		treFile.addCopyright(cpy);
	}

	/**
	 * There is an area after the TRE header and before its data
	 * starts that can be used to save any old junk it seems.
	 *
	 * @param info Any string.
	 */
	public void addInfo(String info) {
		treFile.addInfo(info);
	}

	/**
	 * Create a new zoom level. The level 0 is the most detailed and
	 * level 15 is the most general.  Most maps would just have 4
	 * different levels or less.  We are just having two to start with
	 * but will probably advance to at least 3.
	 *
	 * @param level The zoom level, and integer between 0 and 15. Its
	 * like a logical zoom level.
	 * @param bits  The number of bits per coordinate, a measure of
	 * the actual amount of detail that will be in the level.  So this
	 * is like a physical zoom level.
	 * @return The zoom object.
	 */
	public Zoom createZoom(int level, int bits) {
		return treFile.createZoom(level, bits);
	}

	/**
	 * Create the top level division. It must be empty afaik and cover
	 * the whole area of the map.
	 *
	 * @param area The whole map area.
	 * @param zoom The zoom level that you want the top level to be
	 * at.  Its going to be at least level 1.
	 * @return The top level division.
	 */
	public Subdivision topLevelSubdivision(Area area, Zoom zoom) {
		zoom.setInherited(true); // May not always be necessary/desired

		InternalFiles ifiles = this;
		Subdivision sub = Subdivision.topLevelSubdivision(ifiles, area, zoom);
		rgnFile.startDivision(sub);
		return sub;
	}

	/**
	 * Create a subdivision that is beneath the top level.  We have to
	 * pass the parent division.
	 * <p>
	 * Note that you cannot create these all up front.  You must
	 * create it, fill it will its map elements and then create the
	 * next one.  You must also start at the top level and work down.
	 *
	 * @param parent The parent subdivision.
	 * @param area The area of the new child subdiv.
	 * @param zoom The zoom level of the child.
	 * @return The new division.
	 */
	public Subdivision createSubdivision(Subdivision parent, Area area, Zoom zoom)
	{
		log.debug("creating division");
		return parent.createSubdivision(this, area, zoom);
	}

	public void addPointOverview(PointOverview ov) {
		treFile.addPointOverview(ov);
	}

	public void addPolylineOverview(PolylineOverview ov) {
		treFile.addPolylineOverview(ov);
	}

	public void addPolygonOverview(PolygonOverview ov) {
		treFile.addPolygonOverview(ov);
	}

	public void addMapObject(MapObject item) {
		rgnFile.addMapObject(item);
	}

	public void setLabelCodePage(int cp) {
		lblFile.setCodePage(cp);
	}

	public void setLabelCharset(String desc, boolean forceUpper) {
		lblFile.setCharacterType(desc, forceUpper);
	}
	
	/**
	 * Close this map by closing all the constituent files.
	 *
	 * Some history: 
	 */
	public void close() {
		ImgFile[] files = {
				rgnFile, treFile, lblFile,
				netFile, nodFile
		};

		int headerSlotsRequired = 0;

		FileSystemParam param = fileSystem.fsparam();
		int blockSize = param.getBlockSize();

		for (ImgFile f : files) {
			if (f == null)
				continue;

			long len = f.getSize();
			System.out.println("len=" + len);

			// Blocks required for this file
			int nBlocks = (int) ((len + blockSize - 1) / blockSize);

			// Now we calculate how many directory blocks we need, you have
			// to round up as files do not share directory blocks.
			headerSlotsRequired += (nBlocks + DirectoryEntry.SLOTS_PER_ENTRY - 1)/DirectoryEntry.SLOTS_PER_ENTRY;
		}

		System.out.println("blocks " + headerSlotsRequired);

		// A header slot is always 512 bytes, so we need to calculate the
		// number of blocks if the blocksize is different.
		// There are 2 slots for the header itself.
		int blocksRequired = 2 + headerSlotsRequired * 512 / blockSize;

		param.setReservedDirectoryBlocks(blocksRequired);
		fileSystem.fsparam(param);

		for (ImgFile f : files)
			Utils.closeFile(f);

		fileSystem.close();
	}

	public String getFilename() {
		return filename;
	}

	public RGNFile getRgnFile() {
		return rgnFile;
	}

	public LBLFile getLblFile() {
		return lblFile;
	}

	public TREFile getTreFile() {
		return treFile;
	}

	public NETFile getNetFile() {
		return netFile;
	}

	public NODFile getNodFile() {
		return nodFile;
	}
}
