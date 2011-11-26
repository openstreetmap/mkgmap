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
	private boolean released;
	protected int nItems;
	private boolean sizeValid;

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
			assert false;
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
	public final int getNumberOfItems() {
		assert sizeValid;
		if (released)
			return nItems;
		else
			return numberOfItems();
	}

	/**
	 * Method to be implemented by subclasses to return the number of items in the section.
	 * This will only be valid after the section is completely finished etc.
	 * @return The number of items in the section.
	 */
	protected abstract int numberOfItems();

	/**
	 * Get the size of an integer that is sufficient to store a record number
	 * from this section.  If the pointer has a flag(s) then this must be
	 * taken into account too.
	 * @return A number between 1 and 4 giving the number of bytes required
	 * to store the largest record number in this section.
	 */
	public int getSizeForRecord() {
		return numberToPointerSize(getNumberOfItems());
	}

	/**
	 * This is called after all the sections are read in but before any section is written.
	 *
	 * This is now pretty much redundant and could be replaced with direct calls for sections
	 * that need it.
	 */
	public void finish() {
	}

	/**
	 * Called before the section is written and before the actual size of the section
	 * is required.
	 *
	 * Calling it more than once is ok.
	 *
	 * The actual work is implemented in the subclass via the {@link #preWriteImpl()} method.
	 */
	public final void preWrite() {
		if (!sizeValid)
			preWriteImpl();
		sizeValid = true;
	}

	/**
	 * Prepare the final list of items to be written.
	 * Used to de-duplicate or remove invalid entries from the raw data that was
	 * saved.
	 * 
	 * In particular after this call the number of items must not change.
	 */
	protected void preWriteImpl() {
	}

	public final void release() {
		nItems = numberOfItems();
		releaseMemory();
		released = true;
	}

	protected void releaseMemory() {
		throw new UnsupportedOperationException();
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
			return sections[1].getSizeForRecord();
		}

		public int getCitySize() {
			return sections[5].getSizeForRecord();
		}

		/**
		 * Get the number of bytes required to represent a city when there is
		 * one bit reserved for a flag.
		 * There is a minimum size of 2.
		 * @return Number of bytes to represent a city record number and a
		 * one bit flag.
		 */
		public int getCitySizeFlagged() {
			return Math.max(2, numberToPointerSize(sections[5].getNumberOfItems() << 1));
		}

		public int getCityFlag() {
			return flagForSize(getCitySizeFlagged());
		}

		public int getStreetSize() {
			return sections[7].getSizeForRecord();
		}

		public int getStreetSizeFlagged() {
			return numberToPointerSize(sections[7].getNumberOfItems() << 1);
		}

		public int getPoiSize() {
			return sections[11].getSizeForRecord();
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

		/**
		 * Size of the pointer required to index a byte offset into mdr15 (strings).
		 * There is a minimum of 3 for this value.
		 * @return Pointer size required for the string offset value.
		 */
		public int getStrOffSize() {
			return Math.max(3, sections[15].getSizeForRecord());
		}

		public int getMdr20Size() {
			return sections[20].getSizeForRecord();
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

		public int getSize(int sect) {
			return sections[sect].getSizeForRecord();
		}
	}
}
