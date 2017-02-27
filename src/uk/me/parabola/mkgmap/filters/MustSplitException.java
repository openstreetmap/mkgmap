/*
 * Copyright (C) 2017
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
 */
package uk.me.parabola.mkgmap.filters;

/**
 * Used to exit the program.  So that System.exit need only be called
 * in the one place, or indeed not at all.
 *
 * @author Gerd Petermann
 */
public class MustSplitException extends RuntimeException {


	/**
	 * 
	 */
	private static final long serialVersionUID = 3534824303375821690L;

	/**
	 * Constructs a new runtime exception to signal that the object needs a split.
	 */
	public MustSplitException() {
		super();
	}

}
