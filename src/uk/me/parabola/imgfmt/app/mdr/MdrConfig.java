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

/**
 * @author Steve Ratcliffe
 */
public class MdrConfig {
	private static final int DEFAULT_HEADER_LEN = 286;

	private boolean writable;
	private boolean forDevice;
	private int headerLen = DEFAULT_HEADER_LEN;

	public boolean isWritable() {
		return writable;
	}

	public void setWritable(boolean writable) {
		this.writable = writable;
	}

	public boolean isForDevice() {
		return forDevice;
	}

	public void setForDevice(boolean forDevice) {
		this.forDevice = forDevice;
	}

	public int getHeaderLen() {
		return headerLen;
	}

	public void setHeaderLen(int headerLen) {
		this.headerLen = headerLen;
	}
}
