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
 * Create date: 02-Sep-2007
 */
package uk.me.parabola.imgfmt;

import java.io.IOException;

/**
 * If a file cannot be created, or written to, then this exception is thrown.
 * @author Steve Ratcliffe
 */
public class FileNotWritableException extends IOException {
	public FileNotWritableException(String s, Exception e) {
		super(s, e);
	}
}
