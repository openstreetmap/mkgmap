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

import java.nio.charset.Charset;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.srt.Sort;

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
	private final List<Mdr8Record> index = new ArrayList<Mdr8Record>();

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
		Collator collator = sort.getCollator();
		collator.setStrength(Collator.PRIMARY);

		String lastCountryName = null;
		String lastPrefix = "";
		int inRecord = 0;  // record number of the input list
		int outRecord = 0; // record number of the index
		for (NamedRecord r : list) {
			inRecord++;

			String prefix = getPrefix(r.getName());
			if (collator.compare(prefix, lastPrefix) != 0) {
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
		Charset charset = getConfig().getSort().getCharset();
		for (Mdr8Record s : index) {
			writer.put(s.getPrefix().getBytes(charset), 0, prefixLength);
			putN(writer, size, s.getRecordNumber());
		}
	}

	public int getItemSize() {
		return prefixLength + numberToPointerSize(maxIndex);
	}

	protected int numberOfItems() {
		return index.size();
	}

	/**
	 * Get the prefix of the name at the given record.
	 * If the name is shorter than the prefix length, then it padded with nul characters.
	 * So it can be longer than the input string.
	 * 
	 * @param in The name to truncate.
	 * @return A string prefixLength characters long, consisting of the initial
	 * prefix of name and padded with nulls if necessary to make up the length.
	 */
	private String getPrefix(String in) {
		String name = Label.stripGarminCodes(in);
		if (prefixLength > name.length()) {
			StringBuilder sb = new StringBuilder(name);
			while (sb.length() < prefixLength)
				sb.append('\0');
			return sb.toString();
		}
		return name.substring(0, prefixLength);
	}

	public int getPrefixLength() {
		return prefixLength;
	}
}
