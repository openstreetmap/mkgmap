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
package uk.me.parabola.mkgmap.typ;

import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Interface for classes that are used to process sections ot the typ.txt file.
 *
 * @author Steve Ratcliffe
 */
interface ProcessSection {
	/**
	 * Process a single key value pair read from the typ.txt file.
	 *
	 * If the tag has data on the following lines, then it must read it all before returning.
	 *
	 * @param scanner The scanner to get any extra data and for use in error messages.
	 * @param name The key.
	 * @param value The data value of this line.
	 */
	public void processLine(TokenScanner scanner, String name, String value);

	/**
	 * Called at the end of a section. The item will have been fully defined and so can be saved.
	 */
	public void finish();
}
