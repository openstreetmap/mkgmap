/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.mdr;

/**
 * @author Steve Ratcliffe
 */
public interface HasHeaderFlags {
	/**
	 * Return the value that is put in the header after the section start, len
	 * and record size fields.
	 * At least in some cases this field controls what fields and/or size
	 * exist in the section.
	 * @return The correct value based on the contents of the section.  Zero
	 * if nothing needs to be done.
	 */
	int getExtraValue();
}
