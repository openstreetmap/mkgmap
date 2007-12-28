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
 * Create date: 21-Jan-2007
 */
package uk.me.parabola.imgfmt.app.trergn;

import uk.me.parabola.imgfmt.app.lbl.LBLFile;

/**
 * Interface to pass around the internal files in the map without
 * passing the whole thing.
 *
 * This is a bad part of the design, particularly how it is used in Subdivision.
 *
 * @author Steve Ratcliffe
 */
public interface InternalFiles {
	public RGNFile getRgnFile();

	public LBLFile getLblFile();
	
	public TREFile getTreFile();
}
