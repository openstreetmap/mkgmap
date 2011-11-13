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
package uk.me.parabola.mkgmap.main;

import java.io.IOException;

import uk.me.parabola.mkgmap.typ.TypTextReader;

/**
 * Standalone program to compile a TYP file from the text format.
 *
 * @author Steve Ratcliffe
 */
public class TypCompiler {

	public static void main(String[] args) {
		try {
			// For the moment, just call the old version in TypTextReader.
			TypTextReader.main(args);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
