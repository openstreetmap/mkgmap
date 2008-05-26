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
 * Create date: May 26, 2008
 */
package uk.me.parabola.mkgmap;

/**
 * Interface for option processing classes.
 * 
 * @author Steve Ratcliffe
 */
public interface OptionProcessor {
	/**
	 * Process an option.  This is intended for options that change state or
	 * that say how the next filename is to be operated upon.
	 *
	 * @param opt The option.
	 */
	void processOption(Option opt);
}
