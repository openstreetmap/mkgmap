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
 * Create date: 14-Jan-2007
 */
package uk.me.parabola.imgfmt.app.labelenc;

/**
 * An encoder that just takes the lower 8 bits of the char and uses that
 * without any character set conversion.  Useful for testing mainly (only?).
 *
 * @author Steve Ratcliffe
 */
public class Simple8Encoder extends BaseEncoder implements CharacterEncoder {

	public EncodedText encodeText(String text) {
		return simpleEncode(text);
	}
}
