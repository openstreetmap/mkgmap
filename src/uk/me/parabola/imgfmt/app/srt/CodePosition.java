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
	private byte primary;
	private byte secondary;
	private byte tertiary;

	public byte getPrimary() {
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
	public byte getPosition(int type) {
		switch (type) {
		case Collator.PRIMARY:
			return primary;
		case Collator.SECONDARY:
			return secondary;
		case Collator.TERTIARY:
			return tertiary;
		default:
			return 0;
		}
	}

	public void setPrimary(byte primary) {
		this.primary = primary;
	}

	public void setSecondary(byte secondary) {
		this.secondary = secondary;
	}

	public void setTertiary(byte tertiary) {
		this.tertiary = tertiary;
	}
}
