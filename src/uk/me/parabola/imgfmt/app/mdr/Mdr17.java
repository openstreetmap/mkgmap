/*
 * Copyright (C) 2011.
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

import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Name indexes consisting of a prefix of the string and the record
 * number at which that prefix first occurs. So it is like 8 and 12
 * except that they are all combined together in this one section.
 *
 * @author Steve Ratcliffe
 */
public class Mdr17 extends MdrSection {
	private PrefixIndex streets;
	private PrefixIndex streetsByCountry;
	private PrefixIndex cities;
	private PrefixIndex pois;

	public Mdr17(MdrConfig config) {
		setConfig(config);
		streets = new PrefixIndex(getConfig(), 4);		
		streetsByCountry = new PrefixIndex(getConfig(), 4);
		cities = new PrefixIndex(getConfig(), 2);
		pois = new PrefixIndex(getConfig(), 4);
	}

	public void writeSectData(ImgFileWriter writer) {
		writeSubSect(writer, streets);
		writeSubSect(writer, cities);
		writeSubSect(writer, streetsByCountry);
		writeSubSect(writer, pois);
	}

	/**
	 * Write one of the subsections that makes up the section. They are all similar and
	 * have a header with the length and the record size and prefix length of the
	 * records in the subsection.
	 */
	private void writeSubSect(ImgFileWriter writer, PrefixIndex index) {
		index.preWrite();
		int len = index.getItemSize() * index.getNumberOfItems() + 2;
		if (len == 2)
			return; // nothing to do

		// The length is a variable length integer with the length indicated by a suffix.
		len = (len << 1) + 1;
		int mask = ~0xff;
		int count = 1;
		while ((len & mask) != 0) {
			mask <<= 8;
			len <<= 1;
			count++;
		}
		putN(writer, count, len);

		// Calculate the header. This code is unlikely to survive the finding of another example!
		// Have no idea what the real thinking behind this is.
		int prefixLength = index.getPrefixLength();
		int header = (prefixLength - 1) << 8;
		header += (prefixLength + 1) * (prefixLength + 1);
		header += (index.getItemSize() - prefixLength - 1) * 0xa;

		writer.putChar((char) header);
		index.writeSectData(writer);
	}

	protected void releaseMemory() {
		streets = null;
		cities = null;
		streetsByCountry = null;
		pois = null;
	}

	public void addStreets(List<Mdr7Record> streetList) {
		streets.createFromList(streetList);
	}

	public void addCities(List<Mdr5Record> cityList) {
		cities.createFromList(cityList);
	}

	public void addStreetsByCountry(List<Mdr7Record> streets) {
		streetsByCountry.createFromList(streets, true);
	}

	public void addPois(List<Mdr11Record> poiList) {
		pois.createFromList(poiList);
	}

	public int getItemSize() {
		return 0;
	}

	protected int numberOfItems() {
		return 0;
	}
}
