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
 * Create date: 03-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

/**
 * @author Steve Ratcliffe
 */
public class SyntaxException extends RuntimeException {

	private int linenumber;

	public SyntaxException(int linenumber, String message) {
		super(message);
		this.linenumber = linenumber;
	}

	public SyntaxException(String message) {
		super(message);
	}

	public String getMessage() {
		return "Error line " + linenumber + ": " + super.getMessage();
	}
}
