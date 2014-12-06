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
package uk.me.parabola.imgfmt.app.srt;

import java.text.Collator;

/**
 * Represents the collation positions of a given code point.
 *
 * @author Steve Ratcliffe
 */
class CodePosition {
	private char primary;
	private byte secondary;
	private byte tertiary;

	public char getPrimary() {
		return primary;
	}

	public byte getSecondary() {
		return secondary;
	}

	public byte getTertiary() {
		return tertiary;
	}

	/**
	 * Get the position with the given strength.
	 *
	 * @param type The strength, Collator.PRIMARY, SECONDARY etc.
	 * @return The collation position at the given strength.
	 */
	public int getPosition(int type) {
		switch (type) {
		case Collator.PRIMARY:
			return primary;
		case Collator.SECONDARY:
			return secondary & 0xff;
		case Collator.TERTIARY:
			return tertiary & 0xff;
		default:
			return 0;
		}
	}

	public void setPrimary(char primary) {
		this.primary = primary;
	}

	public void setSecondary(byte secondary) {
		this.secondary = secondary;
	}

	public void setTertiary(byte tertiary) {
		this.tertiary = tertiary;
	}
}
