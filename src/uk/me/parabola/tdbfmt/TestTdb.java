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
 * Create date: 23-Sep-2007
 */
package uk.me.parabola.tdbfmt;

import uk.me.parabola.log.Logger;

import java.io.IOException;

/**
 * @author Steve Ratcliffe
 */
public class TestTdb {
	private static final Logger log = Logger.getLogger(TestTdb.class);

	public static void main(String[] args) throws IOException {

		Logger.resetLogging("test/log/log.all");
		TdbFile tdb = TdbFile.read(args[0]);
		log.debug(tdb);

		tdb.write("test.tdb");
	}
}
