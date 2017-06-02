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
package uk.me.parabola.imgfmt.app.mdr;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.Sort.SrtCollator;

/**
 * Holds an index of name prefixes to record numbers.
 *
 * Extends MdrSection, although is sometimes a subsection, not an actual section.
 *
 * @author Steve Ratcliffe
 */
public class PrefixIndex extends MdrSection {
	private final int prefixLength;
	private int maxIndex;

	// We use mdr8record for all similar indexes.
	private final List<Mdr8Record> index = new ArrayList<>();

	/**
	 * Sets the config and the prefix length for this index.
	 *
	 * Prefix length may differ depending on the amount of data, so will have
	 * to deal with that when it happens.
	 *
	 * @param config Configuration for sorting methods.
	 * @param prefixLength The prefix length for this index.
	 */
	public PrefixIndex(MdrConfig config, int prefixLength) {
		setConfig(config);
		this.prefixLength = prefixLength;
	}

	/**
	 * We can create an index for any type that has a name.
	 * @param list A list of items that have a name.
	 */
	public void createFromList(List<? extends NamedRecord> list, boolean grouped) {
		maxIndex = list.size();

		// Prefixes are equal based on the primary unaccented character, so
		// we need to use the collator to test for equality and not equals().
		Sort sort = getConfig().getSort();
		Sort.SrtCollator collator = (SrtCollator) sort.getCollator();
		collator.setStrength(Collator.PRIMARY);

		String lastCountryName = null;
		char[] lastPrefix = "".toCharArray();
		int inRecord = 0;  // record number of the input list
		int outRecord = 0; // record number of the index
		int lastMdr22SortPos = -1; 
		for (NamedRecord r : list) {
			inRecord++;
			String name;
			if (r instanceof Mdr7Record) {
				name = ((Mdr7Record) r).getPartialName();
				if (grouped) {
					int mdr22SortPos = ((Mdr7Record) r).getCity().getMdr22SortPos();
					if (mdr22SortPos != lastMdr22SortPos)
						lastPrefix = "".toCharArray();
					lastMdr22SortPos = mdr22SortPos; 
				}
			}
			else 
				name = r.getName();
			char[] prefix = sort.getPrefix(name, prefixLength);
			int cmp = collator.compareOneStrengthWithLength(prefix, lastPrefix, Collator.PRIMARY, prefixLength);
			if (cmp > 0) {
				outRecord++;
				Mdr8Record ind = new Mdr8Record();
				ind.setPrefix(prefix);
				ind.setRecordNumber(inRecord);
				index.add(ind);

				lastPrefix = prefix;
				
				if (grouped) {
					// Peek into the real type to support the mdr17 feature of indexes sorted on country.
					Mdr5Record city = ((Mdr7Record) r).getCity();
					if (city != null) {
						String countryName = city.getCountryName();
						if (!countryName.equals(lastCountryName)) {
							city.getMdrCountry().getMdr29().setMdr17(outRecord);
							lastCountryName = countryName;
						}
					}
				}
			}
		}
	}
	
	public void createFromList(List<? extends NamedRecord> list) {
		createFromList(list, false);
	}

	/**
	 * Write the section or subsection.
	 */
	public void writeSectData(ImgFileWriter writer) {
		int size = numberToPointerSize(maxIndex);
		for (Mdr8Record s : index) {
			for (int i = 0; i< prefixLength; i++) {
				writer.put((byte) s.getPrefix()[i]);
			}
			putN(writer, size, s.getRecordNumber());
		}
	}

	public int getItemSize() {
		return prefixLength + numberToPointerSize(maxIndex);
	}

	protected int numberOfItems() {
		return index.size();
	}

	public int getPrefixLength() {
		return prefixLength;
	}
}
