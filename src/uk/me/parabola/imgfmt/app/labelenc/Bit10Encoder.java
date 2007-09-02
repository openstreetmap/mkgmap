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
 * Very experimental.  In fact no evidence that the encoding length
 * of 10 actually means 10 bit format.  At least on my Ledgend Cx it is
 * actually just the same as 8 bit.
 *
 * This will probably be removed.
 *
 * @author Steve Ratcliffe
 */
public class Bit10Encoder extends BaseEncoder implements CharacterEncoder {

	public EncodedText encodeText(String text) {
		//if (text == null)
		//	return NO_TEXT;
		//
		//BitWriter bw = new BitWriter();
		//
		//String[] strings = text.split(",");
		//for (String s : strings) {
		//	try {
		//		int c = Integer.decode(s);
		//		bw.putn(c, 10);
		//	} catch (NumberFormatException e) {
		//		bw.putn(0, 10);
		//	}
		//}
		//
		//return new EncodedText(bw.getBytes(), bw.getLength());
		throw new RuntimeException("not implemented yet");
	}
}
