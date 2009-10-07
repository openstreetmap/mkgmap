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
 * Interface for decoding characters for use in the Label section of a .img
 * file.
 *
 * These things are stateful, you add bytes read from the file to them and
 * they return a status when a complete label has been obtained.  At this point
 * you retrieve the text.
 *
 * @author Steve Ratcliffe
 */
public interface CharacterDecoder {

	/**
	 * Add a byte to this decoder.  This will be saved until a complete
	 * label string has been detected.
	 *
	 * @param b The byte read from the lbl file.
	 * @return True if a label string is finished and is ready to be retrieved
	 * via the {@link #getText} method.
	 */
	public boolean addByte(int b);

	/**
	 * Get the valid text.  This is guaranteed to be encoded as utf-8.
	 *
	 * @return The byte array and length as an EncodedText struct.
	 */
	public DecodedText getText();

	/**
	 * Resets the state.  This should be called for example if the reader is
	 * jumping to a new place in the file and cannot guarantee that the
	 * previous label was fully read.
	 */
	//public void reset();
}