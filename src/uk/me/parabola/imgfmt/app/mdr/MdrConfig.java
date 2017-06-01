/*
 * Copyright (C) 2009.
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
package uk.me.parabola.imgfmt.app.mdr;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.mkgmap.CommandArgs;
import uk.me.parabola.mkgmap.reader.osm.FeatureKind;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.scan.SyntaxException;

/**
 * Configuration for the MDR file.
 * Mostly used when creating a file as there are a number of different options
 * in the way that it is done.
 *
 * @author Steve Ratcliffe
 */
public class MdrConfig {
	//private static final int DEFAULT_HEADER_LEN = 286;
	private static final int DEFAULT_HEADER_LEN = 568;

	private boolean writable;
	private boolean forDevice;
	private int headerLen = DEFAULT_HEADER_LEN;
	private Sort sort;
	private File outputDir;
	private boolean splitName;
	private Set<String> mdr7Excl = Collections.emptySet();
	private Set<String> mdr7Del = Collections.emptySet();
	private Set<Integer> poiExclTypes = Collections.emptySet();
	
	public MdrConfig() {
		
	}
	
	/**
	 * Constructor which copies the values which configure the index details. 
	 * @param base 
	 */
	public MdrConfig(MdrConfig base) {
		splitName = base.isSplitName();
		mdr7Del = base.getMdr7Del();
		mdr7Excl = base.getMdr7Excl();
		poiExclTypes = base.getPoiExclTypes();
	}

	/**
	 * True if we are creating the file, rather than reading it.
	 */
	public boolean isWritable() {
		return writable;
	}

	public void setWritable(boolean writable) {
		this.writable = writable;
	}

	/**
	 * The format that is used by the GPS devices is different to that used
	 * by Map Source. This parameter says which to do.
	 * @return True if we are creating the the more compact format required
	 * for a device.
	 */
	public boolean isForDevice() {
		return forDevice;
	}

	public void setForDevice(boolean forDevice) {
		this.forDevice = forDevice;
	}

	/**
	 * There are a number of different header lengths in existence.  This
	 * controls what sections can exist (and perhaps what must exist).
	 * @return The header length.
	 */
	public int getHeaderLen() {
		return headerLen;
	}

	public void setHeaderLen(int headerLen) {
		this.headerLen = headerLen;
	}

	public Sort getSort() {
		return sort;
	}

	public void setSort(Sort sort) {
		this.sort = sort;
	}

	public File getOutputDir() {
		return outputDir;
	}

	public void setOutputDir(String outputDir) {
		if (outputDir != null)
			this.outputDir = new File(outputDir);
	}

	public void setSplitName(boolean splitName) {
		this.splitName = splitName;
	}

	public boolean isSplitName() {
		return splitName;
	}

	public Set<String> getMdr7Excl() {
		return Collections.unmodifiableSet(mdr7Excl);
	}

	public void setMdr7Excl(String exclList) {
		mdr7Excl = StringToSet(exclList);
	}

	public Set<String> getMdr7Del() {
		return Collections.unmodifiableSet(mdr7Del);
	}

	public void setMdr7Del(String delList) {
		mdr7Del = StringToSet(delList);
	}
	
	private Set<String> StringToSet (String opt) {
		Set<String> set;

		if (opt == null)
			set = Collections.emptySet();
		else {
			if (opt.startsWith("'") || opt.startsWith("\""))
				opt = opt.substring(1);
			if (opt.endsWith("'") || opt.endsWith("\""))
				opt = opt.substring(0, opt.length() - 1);
			List<String> list = Arrays.asList(opt.split(","));
			set = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
			for (String s : list) {
				set.add(s.trim());
			}
		}
		return set;
	}

	/**
	 * Parse option --poi-excl-index .
	 * @param opt the option string given. Expected is a comma separated list,eg
	 * "0x2800" or "0x2800, 0x6400-0x661f" 
	 */
	public void setPoiExcl (String opt) {
		if (opt == null)
			return;
		poiExclTypes = new TreeSet<>();
		
		String[] opts = opt.split(",");
		for (String range : opts) {
			if (range.contains("-")) {
				String[] ranges = range.split("-");
				if (ranges.length != 2)
					throw new IllegalArgumentException("invalid range in option " + range);
				genTypesForRange(poiExclTypes, ranges[0], ranges[1]);
			} else {
				genTypesForRange(poiExclTypes, range,range);
			}
		}
	}

	/**
	 * Create all POI types for a given range.
	 * @param set set of integers. Generated types are added to it.
	 * @param start first type
	 * @param stop last type (included)
	 */
	private void genTypesForRange(Set<Integer> set, String start, String stop) {
		GType[] types = new GType[2];
		String[] ranges = {start, stop};
		boolean ok = true;
		for (int i = 0; i < 2; i++) {
			try {
				types[i] = new  GType(FeatureKind.POINT, ranges[i]);
			} catch (ExitException e) {
				ok = false;
			}
			if (!ok || !GType.checkType(types[i].getFeatureKind(), types[i].getType())){
				throw new SyntaxException("invalid type " + ranges[i] + " for " + FeatureKind.POINT + " in option " + ranges);
			}
		}
		
		if (types[0].getType() > types[1].getType()) {
			GType gt = types[0];
			types[0] = types[1];
			types[1] = gt;
		}
		for (int i = types[0].getType(); i <= types[1].getType(); i++) {
			if ((i & 0xff) > 0x1f)
				i = ((i >> 8) + 1) << 8;
			set.add(i);
		}
		
	}

	public Set<Integer> getPoiExclTypes() {
		return Collections.unmodifiableSet(poiExclTypes);
	}

	public void setIndexOptions(CommandArgs args) {
		setSplitName(args.get("split-name-index", false));
		setMdr7Excl(args.get("mdr7-excl", null));
		setMdr7Del(args.get("mdr7-del", null));
		setPoiExcl(args.get("poi-excl-index", null));
	}
}
