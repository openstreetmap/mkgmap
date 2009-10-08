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
 * Super class of all sections
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

	public PointerSizes getSizes() {
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

	protected int numberToPointerSize(int n) {
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
	 * A class to save the sizes of various field that depend on the contents
	 * of the file.
	 */
	static class PointerSizes {
		private int mapSize;
		private int citySize;
		private int cityFlag;
		private int poiSize;
		private int poiFlag;
		private int strOffSize;

		public void setMapSize(int mapSize) {
			this.mapSize = mapSize;
		}

		public void setCitySize(int citySize) {
			int size = Math.max(citySize, 2);
			this.citySize = size;
			cityFlag = flagForSize(size);
		}

		public void setStrOffSize(int strOffSize) {
			this.strOffSize = Math.max(strOffSize, 3);
		}

		public void setPoiSize(int poiSize) {
			this.poiSize = poiSize;
			poiFlag = flagForSize(poiSize);
		}

		public int getCityFlag() {
			return cityFlag;
		}

		public int getMapSize() {
			return mapSize;
		}

		public int getCitySize() {
			return citySize;
		}

		public int getPoiSize() {
			return poiSize;
		}

		public int getPoiFlag() {
			return poiFlag;
		}

		public int getStrOffSize() {
			return strOffSize;
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
