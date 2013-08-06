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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
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
	//public static final int MDR7_PARTIAL_FIELD = 0x1c0;
	public static final int MDR7_PARTIAL_SHIFT = 6;
	public static final int MDR7_U1 = 0x2;
	public static final int MDR7_U2 = 0x4;

	private List<Mdr7Record> allStreets = new ArrayList<Mdr7Record>();
	private List<Mdr7Record> streets = new ArrayList<Mdr7Record>();

	private final int u2size = 1;

	public Mdr7(MdrConfig config) {
		setConfig(config);
	}

	public void addStreet(int mapId, String name, int lblOffset, int strOff, Mdr5Record mdrCity) {
		Mdr7Record st = new Mdr7Record();
		st.setMapIndex(mapId);
		st.setLabelOffset(lblOffset);
		st.setStringOffset(strOff);
		st.setName(name);
		st.setCity(mdrCity);
		allStreets.add(st);

		// XXX Quick test...
		//int nameOffset = name.indexOf(' ');
		//if (nameOffset > 0) {
		//	st = new Mdr7Record();
		//	st.setMapIndex(mapId);
		//	st.setLabelOffset(lblOffset);
		//	st.setStringOffset(strOff);
		//	st.setName(name);
		//	st.setCity(mdrCity);
		//	st.setNameOffset(nameOffset+1);
		//	allStreets.add(st);
		//}

		boolean start = true;

		for (int nameOffset = 0; nameOffset < name.length(); nameOffset++) {
			char c = name.charAt(nameOffset);
			if (Character.isWhitespace(c)) {
				start = true;
				continue;
			}
			if (!Character.isLetterOrDigit(c)) {
				start = true;
				continue;
			}

			if (start) {
				st = new Mdr7Record();
				st.setMapIndex(mapId);
				st.setLabelOffset(lblOffset);
				st.setStringOffset(strOff);
				st.setName(name);
				st.setCity(mdrCity);
				st.setNameOffset(nameOffset);
				if (st.getPartialName().startsWith("CALLE"))
					continue;
				allStreets.add(st);
				String part = st.getPartialName();
				//System.out.println("adding: " + part);
				start = false;
			}
		}

	}

	/**
	 * Since we change the number of records by removing some after sorting,
	 * we sort and de-duplicate here.
	 */
	protected void preWriteImpl() {
		createPartials();

		Sort sort = getConfig().getSort();
		List<SortKey<Mdr7Record>> sortedStreets = new ArrayList<SortKey<Mdr7Record>>(allStreets.size());
		for (Mdr7Record m : allStreets) {
			SortKey<Mdr7Record> sortKey = sort.createSortKey(m, m.getPartialName(),
					m.getMapIndex());
			sortedStreets.add(sortKey);
		}
		Collections.sort(sortedStreets);

		// De-duplicate the street names so that there is only one entry
		// per map for the same name.
		int recordNumber = 0;
		Mdr7Record last = new Mdr7Record();
		for (SortKey<Mdr7Record> sk : sortedStreets) {
			Mdr7Record r = sk.getObject();
			if (r.getMapIndex() != last.getMapIndex() || !r.getPartialName().equals(last.getPartialName())) {
				recordNumber++;
				last = r;
				r.setIndex(recordNumber);
				//System.out.println("sorted: " + r.getPartialName());
				streets.add(r);
			} else {
				// This has the same name (and map number) as the previous one. Save the pointer to that one
				// which is going into the file.
				r.setIndex(recordNumber);
			}
		}
	}

	static class Int {
		int value = 1;
		void inc() { value += 1; }
	}

	// TODO: work in progress
	private void createPartials() {
		Map<String, Int> firstWords = new HashMap<String, Int>();
		Map<String, Int> lastWords = new HashMap<String, Int>();
		int nWords = 0;

		for (Mdr7Record m : allStreets) {
			String name = m.getName();
			String[] split = name.split("\\s+");

			// If just one word (or none) then nothing needs to be done
			if (split.length < 2)
				continue;

			int first = 0;
			String word;
			do {
				word = split[first];
			} while (!Character.isLetter(word.charAt(0)) && ++first < split.length-1);

			if (split.length - first < 2)
				continue;

			nWords++;

			putWord(firstWords, split[first]);
			putWord(lastWords, split[split.length - 1]);
		}

		System.out.println("=== FIRST");
		int count = 0;
		int t1 = 0;
		int t3 = 0;
		int t2 = 0;
		int t4 = 0;

		for (Map.Entry<String, Int> ent : firstWords.entrySet()) {
			int val = ent.getValue().value;
			if (val > nWords/50) {
				t1++;
				t3 += val;
			} else {
				t2++;
				t4 += val;
			}
		}
		int firstMainAv = t4/t2;
		int firstTopAv = t3/t1;

		t1 = t2 = t3 = t4 = 0;
		for (Map.Entry<String, Int> ent : lastWords.entrySet()) {

			int val = ent.getValue().value;
			if (val > nWords/50) {
				t1++;
				t3 += val;
			}
			else {
				t2++;
				t4 += val;
			}
		}
		int lastMainAv = t2==0? 0: t4/t2;
		int lastTopAv = t1==0? 0: t3/t1;

		System.out.printf("t1=%d, t2=%d\n", t1, t2);
		System.out.printf("first av %d/%d, last %d/%d\n", firstTopAv, firstMainAv,
				lastTopAv, lastMainAv);

		int av = 350 * Math.max(firstMainAv, lastMainAv);

		int factor = 200;
		for (Map.Entry<String, Int> ent : firstWords.entrySet()) {
			int value = ent.getValue().value;
			if (value > nWords/ factor && value > av) {
				System.out.printf("%s : %d\n", ent.getKey(), value);
			}
		}

		System.out.println("=== LAST");
		for (Map.Entry<String, Int> ent : lastWords.entrySet()) {
			int value = ent.getValue().value;
			if (value > nWords/ factor && value > av) {
				System.out.printf("%s : %d\n", ent.getKey(), value);
			}
		}
	}

	private void putWord(Map<String, Int> words, String s) {
		if (s.length() < 2)
			return;

		Int val = words.get(s);
		if (val == null)
			words.put(s, new Int());
		else
			val.inc();
	}

	public void writeSectData(ImgFileWriter writer) {
		String lastName = null;
		boolean hasStrings = hasFlag(MDR7_HAS_STRING);
		boolean hasNameOffset = hasFlag(MDR7_HAS_NAME_OFFSET);

		for (Mdr7Record s : streets) {
			addIndexPointer(s.getMapIndex(), s.getIndex());

			putMapIndex(writer, s.getMapIndex());
			int lab = s.getLabelOffset();
			String name = Label.stripGarminCodes(s.getName());
			int trailingFlags = 0;
			if (!name.equals(lastName)) {
				lab |= 0x800000;
				lastName = name;
				trailingFlags = 1;
			}
			writer.put3(lab);
			if (hasStrings)
				putStringOffset(writer, s.getStringOffset());

			if (hasNameOffset)
				writer.put((byte) s.getNameOffset());

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
		List<Mdr8Record> list = new ArrayList<Mdr8Record>();
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
}
