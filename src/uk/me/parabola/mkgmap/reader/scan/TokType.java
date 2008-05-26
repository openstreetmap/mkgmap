/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: May 25, 2008
 */
package uk.me.parabola.mkgmap.reader.scan;

/**
 * The different token types.
 */
public enum TokType {
	/** A symbol, such as '!', '@' etc.  Currently symbols are single
	 * and do not combine.  This may change. */
	SYMBOL,

	/** Text, an alphanumeric string. */
	TEXT,

	/** A sequence of space characters, not including newline.  Several
	 * characters can be combined into one token, but the actual characters
	 * are available as the value. */
	SPACE,

	/** An end of line character.*/
	EOL,

	/** Used for end of file.*/
	EOF,
}
