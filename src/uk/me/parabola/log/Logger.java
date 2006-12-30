/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 30-Dec-2006
 */
package uk.me.parabola.log;


/**
 * @author Steve Ratcliffe
 */
public class Logger {
	public static Logger getLogger(Class aClass) {
		return new Logger();
	}

	public void debug(String o) {
		
	}

	public boolean isDebugEnabled() {
		return true;
	}

	public void warn(Object o) {
		
	}

	public void info(Object o) {

	}

	public void error(Object o, Throwable e) {
		
	}

	public void debug(Object o) {
	}
}
