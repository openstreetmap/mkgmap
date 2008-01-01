/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: Jan 1, 2008
 */
package uk.me.parabola.imgfmt.app.labelenc;

/**
 * @author Steve Ratcliffe
 */
public class CodeFunctions {
	private CharacterEncoder encoder;
	private int encodingType;

	public CharacterEncoder getEncoder() {
		return encoder;
	}

	public void setEncoder(CharacterEncoder encoder) {
		this.encoder = encoder;
	}

	public int getEncodingType() {
		return encodingType;
	}

	public void setEncodingType(int encodingType) {
		this.encodingType = encodingType;
	}
}
