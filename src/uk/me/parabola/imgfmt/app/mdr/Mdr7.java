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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.srt.MultiSortKey;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;

/**
 * The MDR 7 section is a list of all streets.  Only street names are saved
 * and so I believe that the NET section is required to make this work.
 *
 * @author Steve Ratcliffe
 */
public class Mdr7 extends MdrMapSection {
	public static final int MDR7_HAS_STRING = 0x01;
	public static final int MDR7_HAS_NAME_OFFSET = 0x20;
	public static final int MDR7_PARTIAL_SHIFT = 6;
	public static final int MDR7_U1 = 0x2;
	public static final int MDR7_U2 = 0x4;

	private static final int MAX_NAME_OFFSET = 127;

	private final int codepage;
	private final boolean isMulti;
	private final boolean splitName;

	private List<Mdr7Record> allStreets = new ArrayList<>();
	private List<Mdr7Record> streets = new ArrayList<>();

	private final int u2size = 1;

	public Mdr7(MdrConfig config) {
		setConfig(config);
		Sort sort = config.getSort();
		splitName = config.isSplitName();
		codepage = sort.getCodepage();
		isMulti = sort.isMulti();
	}

	public void addStreet(int mapId, String name, int lblOffset, int strOff, Mdr5Record mdrCity) {
		// Find a name prefix, which is either a shield or a word ending 0x1e. We are treating
		// a shield as a prefix of length one.
		int prefix = 0;
		if (name.charAt(0) < 7)
			prefix = 1;
		int sep = name.indexOf(0x1e);
		if (sep > 0)
			prefix = sep + 1;

		// Find a name suffix which begins with 0x1f
		sep = name.indexOf(0x1f);
		int suffix = 0;
		if (sep > 0)
			suffix = sep;

		// Large values can't actually work.
		if (prefix >= MAX_NAME_OFFSET || suffix >= MAX_NAME_OFFSET)
			return;

		Mdr7Record st = new Mdr7Record();
		st.setMapIndex(mapId);
		st.setLabelOffset(lblOffset);
		st.setStringOffset(strOff);
		st.setName(name);
		st.setCity(mdrCity);
		st.setPrefixOffset((byte) prefix);
		st.setSuffixOffset((byte) suffix);
		allStreets.add(st);

		if (!splitName)
			return;

		boolean start = false;
		boolean inWord = false;

		int c;
		int outOffset = 0;

		int end = Math.min((suffix > 0) ? suffix : name.length() - prefix - 1, MAX_NAME_OFFSET);
		for (int nameOffset = 0; nameOffset < end; nameOffset += Character.charCount(c)) {
			c = name.codePointAt(prefix + nameOffset);

			// Don't use any word after a bracket
			if (c == '(')
				break;

			if (!Character.isLetterOrDigit(c)) {
				start = true;
				inWord = false;
			} else if (start && Character.isLetterOrDigit(c)) {
				inWord = true;
			}

			if (start && inWord && outOffset > 0) {
				st = new Mdr7Record();
				st.setMapIndex(mapId);
				st.setLabelOffset(lblOffset);
				st.setStringOffset(strOff);
				st.setName(name);
				st.setCity(mdrCity);
				st.setNameOffset((byte) nameOffset);
				st.setOutNameOffset((byte) outOffset);
				st.setPrefixOffset((byte) prefix);
				st.setSuffixOffset((byte) suffix);
				//System.out.println(st.getName() + ": add partial " + st.getPartialName());
				allStreets.add(st);
				start = false;
			}

			outOffset += outSize(c);
			if (outOffset > MAX_NAME_OFFSET)
				break;
		}
	}

	/**
	 * Return the number of bytes that the given character will consume in the output encoded
	 * format.
	 */
	private int outSize(int c) {
		if (codepage == 65001) {
			// For unicode a simple lookup gives the number of bytes.
			if (c < 0x80) {
				return 1;
			} else if (c <= 0x7FF) {
				return 2;
			} else if (c <= 0xFFFF) {
				return 3;
			} else if (c <= 0x10FFFF) {
				return 4;
			} else {
				throw new MapFailedException(String.format("Invalid code point: 0x%x", c));
			}
		} else if (!isMulti) {
			// The traditional single byte code-pages, always one byte.
			return 1;
		} else {
			// Other multi-byte code-pages (eg ms932); can't currently create index for these anyway.
			return 0;
		}
	}

	/**
	 * Since we change the number of records by removing some after sorting,
	 * we sort and de-duplicate here.
	 */
	protected void preWriteImpl() {
		Sort sort = getConfig().getSort();
		List<SortKey<Mdr7Record>> sortedStreets = new ArrayList<>(allStreets.size());
		for (Mdr7Record m : allStreets) {
			String partialName = m.getPartialName();
			String name = m.getName();
			SortKey<Mdr7Record> nameKey = sort.createSortKey(m, m.getName(), m.getMapIndex());
			SortKey<Mdr7Record> partialKey = name.equals(partialName) ? nameKey : sort.createSortKey(m, partialName);
			MultiSortKey<Mdr7Record> sortKey = new MultiSortKey<>(partialKey, nameKey, null);
			sortedStreets.add(sortKey);
		}
		Collections.sort(sortedStreets);

		// De-duplicate the street names so that there is only one entry
		// per map for the same name.
		int recordNumber = 0;
		
		Mdr7Record last = new Mdr7Record();
		for (int i = 0; i < sortedStreets.size(); i++){ 
			SortKey<Mdr7Record> sk = sortedStreets.get(i);
			Mdr7Record r = sk.getObject();
			if (r.getMapIndex() == last.getMapIndex()
					&& r.getName().equals(last.getName())  // currently think equals is correct, not collator.compare()
					&& r.getPartialName().equals(last.getPartialName()))
			{
				// This has the same name (and map number) as the previous one. Save the pointer to that one
				// which is going into the file.
				r.setIndex(recordNumber);
			} else {
				recordNumber++;
				last = r;
				r.setIndex(recordNumber);
				streets.add(r);
			}
			sortedStreets.set(i, null);
		}
		return;
	}

	public void writeSectData(ImgFileWriter writer) {
		String lastName = null;
		String lastPartial = null;
		boolean hasStrings = hasFlag(MDR7_HAS_STRING);
		boolean hasNameOffset = hasFlag(MDR7_HAS_NAME_OFFSET);

		for (Mdr7Record s : streets) {
			addIndexPointer(s.getMapIndex(), s.getIndex());

			putMapIndex(writer, s.getMapIndex());
			int lab = s.getLabelOffset();
			String name = s.getName();
			if (!name.equals(lastName)) {
				lab |= 0x800000;
				lastName = name;
			}

			String partialName = s.getPartialName();
			int trailingFlags = 0;
			if (!partialName.equals(lastPartial)) {
				trailingFlags |= 1;
				lab |= 0x800000;  // If it is not a partial repeat, then it is not a complete repeat either
			}
			lastPartial = partialName;

			writer.put3(lab);
			if (hasStrings)
				putStringOffset(writer, s.getStringOffset());

			if (hasNameOffset)
				writer.put(s.getOutNameOffset());

			putN(writer, u2size, trailingFlags);
		}
	}

	/**
	 * For the map number, label, string (opt), and trailing flags (opt).
	 * The trailing flags are variable size. We are just using 1 now.
	 */
	public int getItemSize() {
		PointerSizes sizes = getSizes();
		int size = sizes.getMapSize() + 3 + u2size;
		if (!isForDevice())
			size += sizes.getStrOffSize();
		if ((getExtraValue() & MDR7_HAS_NAME_OFFSET) != 0)
			size += 1;
		return size;
	}

	protected int numberOfItems() {
		return streets.size();
	}

	/**
	 * Value of 3 possibly the existence of the lbl field.
	 */
	public int getExtraValue() {
		int magic = MDR7_U1 | MDR7_HAS_NAME_OFFSET | (u2size << MDR7_PARTIAL_SHIFT);

		if (isForDevice()) {
			magic |= MDR7_U2;
		} else {
			magic |= MDR7_HAS_STRING;
		}

		return magic;
	}

	protected void releaseMemory() {
		allStreets = null;
		streets = null;
	}

	/**
	 * Must be called after the section data is written so that the streets
	 * array is already sorted.
	 * @return List of index records.
	 */
	public List<Mdr8Record> getIndex() {
		List<Mdr8Record> list = new ArrayList<>();
		for (int number = 1; number <= streets.size(); number += 10240) {
			String prefix = getPrefixForRecord(number);

			// need to step back to find the first...
			int rec = number;
			while (rec > 1) {
				String p = getPrefixForRecord(rec);
				if (!p.equals(prefix)) {
					rec++;
					break;
				}
				rec--;
			}

			Mdr8Record indexRecord = new Mdr8Record();
			indexRecord.setPrefix(prefix);
			indexRecord.setRecordNumber(rec);
			list.add(indexRecord);
		}
		return list;
	}

	/**
	 * Get the prefix of the name at the given record.
	 * @param number The record number.
	 * @return The first 4 (or whatever value is set) characters of the street
	 * name.
	 */
	private String getPrefixForRecord(int number) {
		Mdr7Record record = streets.get(number-1);
		int endIndex = MdrUtils.STREET_INDEX_PREFIX_LEN;
		String name = record.getName();
		if (endIndex > name.length()) {
			StringBuilder sb = new StringBuilder(name);
			while (sb.length() < endIndex)
				sb.append('\0');
			name = sb.toString();
		}
		return name.substring(0, endIndex);
	}

	public List<Mdr7Record> getStreets() {
		return Collections.unmodifiableList(allStreets);
	}
	
	public List<Mdr7Record> getSortedStreets() {
		return Collections.unmodifiableList(streets);
	}

	public void relabelMaps(Mdr1 maps) {
		relabel(maps, allStreets);
	}
}
