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

import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * Super class of all sections.
 * @author Steve Ratcliffe
 */
public abstract class MdrSection extends ConfigBase {
	private PointerSizes sizes;

	/**
	 * Write out the contents of this section.
	 * @param writer Where to write it.
	 */
	public abstract void writeSectData(ImgFileWriter writer);

	/**
	 * The size of a record in the section.  This is not a constant and
	 * might vary on various factors, such as the file version, if we are
	 * preparing for a device, the number of maps etc.
	 *
	 * @return The size of a record in this section.
	 */
	public abstract int getItemSize();

	protected PointerSizes getSizes() {
		return sizes;
	}

	public void setSizes(PointerSizes sizes) {
		this.sizes = sizes;
	}

	protected void putMapIndex(ImgFileWriter writer, int mapIndex) {
		putN(writer, sizes.getMapSize(), mapIndex);
	}

	protected void putStringOffset(ImgFileWriter writer, int strOff) {
		putN(writer, sizes.getStrOffSize(), strOff);
	}

	protected void putN(ImgFileWriter writer, int n, int value) {
		switch (n) {
		case 1:
			writer.put((byte) value);
			break;
		case 2:
			writer.putChar((char) value);
			break;
		case 3:
			writer.put3(value);
			break;
		case 4:
			writer.putInt(value);
			break;
		default: // Don't write anything.
			break;
		}
	}

	protected static int numberToPointerSize(int n) {
		if (n > 0xffffff)
			return 4;
		else if (n > 0xffff)
			return 3;
		else if (n > 0xff)
			return 2;
		else
			return 1;
	}

	/**
	 * The number of records in this section.
	 * @return The number of items in the section.
	 */
	public abstract int getNumberOfItems();

	/**
	 * Get the size of an integer that is sufficient to store a record number
	 * from this section.  If the pointer has a flag(s) then this must be
	 * taken into account too.
	 * @return A number between 1 and 4 giving the number of bytes required
	 * to store the largest record number in this section.
	 */
	public int getPointerSize() {
		return numberToPointerSize(getNumberOfItems());
	}

	/**
	 * Provides the pointer sizes required to hold record of offset values
	 * in the various sections.
	 */
	static class PointerSizes {

		private final MdrSection[] sections;

		public PointerSizes(MdrSection[] sections) {
			this.sections = sections;
		}

		public int getMapSize() {
			return sections[1].getPointerSize();
		}

		public int getCitySize() {
			return sections[5].getPointerSize();
		}

		/**
		 * Get the number of bytes required to represent a city when it is
		 * flagged.  That is if there is one bit reserved for a flag.
		 * @return Number of bytes to represent a city record number and a
		 * one bit flag.
		 */
		public int getCitySizeFlagged() {
			return numberToPointerSize(sections[5].getNumberOfItems() << 1);
		}

		public int getCityFlag() {
			return flagForSize(getCitySizeFlagged());
		}

		public int getStreetSize() {
			return sections[7].getPointerSize();
		}

		public int getPoiSize() {
			return sections[11].getPointerSize();
		}

		/**
		 * The number of bytes required to represent a POI (mdr11) record number
		 * and a flag bit.
		 */
		public int getPoiSizeFlagged() {
			return numberToPointerSize(sections[11].getNumberOfItems() << 1);
		}

		public int getPoiFlag() {
			return flagForSize(getPoiSizeFlagged());
		}

		public int getStrOffSize() {
			return sections[15].getPointerSize();
		}

		private int flagForSize(int size) {
			int flag;
			if (size == 1)
				flag = 0x80;
			else if (size == 2)
				flag = 0x8000;
			else if (size == 3)
				flag = 0x800000;
			else if (size == 4)
				flag = 0x80000000;
			else
				flag = 0;
			return flag;
		}
	}
}
