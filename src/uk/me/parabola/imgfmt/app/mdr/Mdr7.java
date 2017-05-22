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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
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
	public static final int MDR7_HAS_MDR17 = 0x4;

	private static final int MAX_NAME_OFFSET = 127;

	private final int codepage;
	private final boolean isMulti;
	private final boolean splitName;

	private Set<Mdr7Record> roadsPerMap= new HashSet<>();
	private ArrayList<Mdr7Record> allStreets = new ArrayList<>();
	private ArrayList<Mdr7Record> streets = new ArrayList<>();
	private int lastMaxIndex = -1;

	private int partialInfoSize;
	private Set<String> exclNames;
	private final Sort sort;
	private int maxPrefixCount;
	private int maxSuffixCount;
	private boolean magicIsValid;
	private int magic;

	public Mdr7(MdrConfig config) {
		setConfig(config);
		sort = config.getSort();
		splitName = config.isSplitName();
		exclNames = config.getMdr7Excl();
		codepage = sort.getCodepage();
		isMulti = sort.isMulti();
	}

	public void addStreet(int mapId, String name, int lblOffset, int strOff, Mdr5Record mdrCity) {
		if (name.isEmpty())
			return;
			
		// Find a name prefix, which is either a shield or a word ending 0x1e or 0x1c. We are treating
		// a shield as a prefix of length one.
		int prefix = 0;
		if (name.charAt(0) < 7)
			prefix = 1;
		int sep = name.indexOf(0x1e);
		if (sep < 0)
			sep = name.indexOf(0x1b);
		if (sep > 0) {
			prefix = sep + 1;
		}

		// Find a name suffix which begins with 0x1 or 0x1c
		sep = name.indexOf(0x1f);
		if (sep < 0)
			sep = name.indexOf(0x1c);
		int suffix = 0;
		if (sep > 0) {
			suffix = sep;
		}

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
		storeMdr7(st);

		if (!splitName)
			return;

		boolean start = false;
		boolean inWord = false;

		int c;
		int outOffset = 0;

		int end = Math.min(((suffix > 0) ? suffix : name.length()) - prefix - 1, MAX_NAME_OFFSET);
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
				if (!exclNames.contains(st.getPartialName()))
					storeMdr7(st);

				start = false;
			}

			outOffset += outSize(c);
			if (outOffset > MAX_NAME_OFFSET)
				break;
		}
	}

	/**
	 * Store in array if not already done
	 * @param st the mdr7 record
	 */
	private void storeMdr7(Mdr7Record st) {
		if (lastMaxIndex != st.getMapIndex()) {
			// we process all roads of one map tile sequentially, so we can clear the set with each new map tile
			lastMaxIndex = st.getMapIndex();
			roadsPerMap.clear(); 
		}
		
		if (roadsPerMap.add(st)) {
			allStreets.add(st);
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
	 * This is a performance critical part of the index creation process
	 * as it requires a lot of heap to store the sort keys. 	  	 
	 */
	protected void preWriteImpl() {
		
		LargeListSorter<Mdr7Record> partialSorter = new LargeListSorter<Mdr7Record>(sort) {
			@Override
			protected SortKey<Mdr7Record> makeKey(Mdr7Record r, Sort sort, Map<String, byte[]> cache) {
				return sort.createSortKey(r, r.getPartialName(), 0, cache); // first sort by partial name only
			}
		};
		
		ArrayList<Mdr7Record> sorted = new ArrayList<>(allStreets);
		allStreets.clear();
		partialSorter.sort(sorted);
		// list is now sorted by partial name only, we have to group by name and map index now
		String lastPartial = null;
		List<Mdr7Record> samePartial = new ArrayList<>();
		Collator collator = sort.getCollator();
		collator.setStrength(Collator.SECONDARY);
		for (int i = 0; i < sorted.size(); i++) {
			Mdr7Record r = sorted.get(i);
			String partial = r.getPartialName();
			if (lastPartial == null || collator.compare(partial, lastPartial) != 0) {
				groupByNameAndMap(samePartial);
				samePartial.clear();
			}
			samePartial.add(r);
			lastPartial = partial;
		}
		groupByNameAndMap(samePartial);
		
		allStreets.trimToSize();
		streets.trimToSize();
		return;
	}

	/**
	 * Group a list of roads with the same partial name.
	 * @param samePartial
	 * @param minorSorter
	 */
	private void groupByNameAndMap(List<Mdr7Record> samePartial) {
		if (samePartial.isEmpty())
			return;
		
		// Basecamp needs the records grouped by partial name, full name, and map index.
		// This sometimes presents search results in the wrong order. The partial sort fields allow to
		// tell the right order.
		
//		LargeListSorter<Mdr7Record> initalPartSorter = new LargeListSorter<Mdr7Record>(sort) {
//			@Override
//			protected SortKey<Mdr7Record> makeKey(Mdr7Record r, Sort sort, Map<String, byte[]> cache) {
//				return sort.createSortKey(r, r.getInitialPart(), r.getMapIndex(), cache);
//			}
//		};
//		LargeListSorter<Mdr7Record> suffixSorter = new LargeListSorter<Mdr7Record>(sort) {
//			@Override
//			protected SortKey<Mdr7Record> makeKey(Mdr7Record r, Sort sort, Map<String, byte[]> cache) {
//				return sort.createSortKey(r, r.getSuffix(), r.getMapIndex(), cache);
//			}
//		};
		LargeListSorter<Mdr7Record> fullNameSorter = new LargeListSorter<Mdr7Record>(sort) {
			@Override
			protected SortKey<Mdr7Record> makeKey(Mdr7Record r, Sort sort, Map<String, byte[]> cache) {
				return sort.createSortKey(r, r.getName(), r.getMapIndex(), cache);
			}
		};
		
		
//		List<Mdr7Record> sortedByInitial = new ArrayList<>(samePartial);
//		List<Mdr7Record> sortedBySuffix = new ArrayList<>(samePartial);
//		initalPartSorter.sort(sortedByInitial);
//		suffixSorter.sort(sortedBySuffix);
		
//		Mdr7Record last = null;
//		for (int i = 0; i < samePartial.size(); i++) {
//			Mdr7Record r = samePartial.get(i);
//			if (i > 0) {
//				int repeat = r.checkRepeat(last, collator);
//				int b = 0;
//				if (repeat == 0) {
//				} else if (repeat == 3) 
//					b = last.getB();
//				else if (repeat == 1) {
//					b = last.getB() + 1;
//				}
//				if (b != 0) {
//					if (b > maxPrefixCount)
//						maxPrefixCount = b;
//					r.setB(b);
//				}
//			}
//			last = r;
//		}
//		suffixSorter.sort(samePartial);
//		last = null;
//		int s = 0;
//		for (int i = 0; i < samePartial.size(); i++) {
//			Mdr7Record r = samePartial.get(i);
//			if (i > 0) {
//				int cmp = collator.compare(last.getSuffix(), r.getSuffix());
//				if (cmp == 0)
//					s = last.getS();
//				else 
//					s = last.getS() + 1;
//				if (s != 0) {
//					if (s > maxSuffixCount)
//						maxSuffixCount = s;
//					r.setB(s);
//				}
//			}
//			last = r;
//		}
//
//		
		fullNameSorter.sort(samePartial);
		Mdr7Record last = null;
		int recordNumber = streets.size();
		
		// list is now sorted by partial name, name, and map index
//		// De-duplicate the street names so that there is only one entry
//		// per map for the same name.
		for (int i = 0; i < samePartial.size(); i++) {
			Mdr7Record r = samePartial.get(i);
			if (last != null && r.getMapIndex() == last.getMapIndex() && r.getName().equals(last.getName())) {
				// This has the same name (and map number) as the previous one.
				// Save the pointer to that one
				// which is going into the file.
				r.setIndex(recordNumber);
			} else {
				recordNumber++;
				r.setIndex(recordNumber);
				streets.add(r);
			}
			if (r.getCity() != null)
				allStreets.add(r);
			last = r;
		}
	}

	public void writeSectData(ImgFileWriter writer) {
		boolean hasStrings = hasFlag(MDR7_HAS_STRING);
		boolean hasNameOffset = hasFlag(MDR7_HAS_NAME_OFFSET);
		Collator collator = sort.getCollator();
//		int partialBShift = ((getExtraValue() >> 9) & 0xf);
		collator.setStrength(Collator.SECONDARY); 
		Mdr7Record last = null;
		for (Mdr7Record s : streets) {
			addIndexPointer(s.getMapIndex(), s.getIndex());

			putMapIndex(writer, s.getMapIndex());
			int lab = s.getLabelOffset();
			
			int rr = s.checkRepeat(last, collator);
			if (rr != 3)
				lab |= 0x800000;
			
			writer.put3(lab);
			if (hasStrings)
				putStringOffset(writer, s.getStringOffset());

			if (hasNameOffset)
				writer.put(s.getOutNameOffset());
			if (partialInfoSize > 0) {
				int trailingFlags = ((rr & 1) == 0) ? 1 : 0;
				// trailingFlags |= s.getB() << 1;
				// trailingFlags |= s.getS() << (1 + partialBShift);
				putN(writer, partialInfoSize, trailingFlags);
			}
			last = s;
		}
	}

	/**
	 * For the map number, label, string (opt), and trailing flags (opt).
	 * The trailing flags are variable size. We are just using 1 now.
	 */
	public int getItemSize() {
		PointerSizes sizes = getSizes();
		int size = sizes.getMapSize() + 3 + partialInfoSize;
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
		if (!magicIsValid) {
			int bitsPrefix = (maxPrefixCount == 0) ? 0 : Integer.numberOfTrailingZeros(Integer.highestOneBit(maxPrefixCount)) + 1;
			int bitsSuffix = (maxSuffixCount == 0) ? 0 : Integer.numberOfTrailingZeros(Integer.highestOneBit(maxSuffixCount)) + 1;
			int bits = bitsPrefix + bitsSuffix + 1;
			partialInfoSize = 1 + bits / 8;
			assert bitsSuffix <= 15;
			magic = MDR7_U1 | MDR7_HAS_NAME_OFFSET | (partialInfoSize << MDR7_PARTIAL_SHIFT) | (bitsPrefix << 9);

			if (isForDevice()) {
				if (!getConfig().getSort().isMulti())
					magic |= MDR7_HAS_MDR17; // mdr17 sub section present (not with unicode)
			} else {
				magic |= MDR7_HAS_STRING;
			}
			int unk2size = (magic >> 6) & 0x7;
			int unk2split = ((magic >> 9) & 0xf);
			assert unk2size == partialInfoSize;
			assert unk2split == bitsPrefix;
			magicIsValid = true;
		}
		return magic;
	}

	protected void releaseMemory() {
		allStreets = null;
		streets = null;
	}


	public List<Mdr7Record> getStreets() {
		return Collections.unmodifiableList((ArrayList<Mdr7Record>) allStreets);
	}
	
	public List<Mdr7Record> getSortedStreets() {
		return Collections.unmodifiableList(streets);
	}

	
	/**
	 * Free as much memory as possible.
	 */
	public void trim() {
		allStreets.trimToSize();
		roadsPerMap = new HashSet<>();
	}
}
