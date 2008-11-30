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
 * Create date: 09-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

/**
 * Less than or equal.  For population speeds etc.
 * @author Steve Ratcliffe
 */
public class LTEOp extends NumericOp {
	public LTEOp() {
		setType(LTE);
	}

	protected boolean doesCompare(int result) {
		return result <= 0;
	}
}