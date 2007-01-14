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
 * Create date: 13-Jan-2007
 */
package uk.me.parabola.mkgmap.main;

/**
 * @author Steve Ratcliffe
 */
public class TestAll {
	private static final String[] noargs = new String[0];
	private static final String[] defargs = new String[] {
			"area1.osm"
	};

	public static void main(String[] args) {
		positive();
		negative();
	}

	private static void positive() {
		MakeMap.main(new String[] {
				"--mapname=12008899",
				"big.osm"
		});

		MakeTestMap.main(defargs);

		MakeTestPointMap.main(noargs);
		MakeTestPolygonMap.main(noargs);

		MakeMap.main(new String[] {
				"--xcharset=latin1",
				"area1.osm"
		});

		MakeMap.main(new String[] {
				"--xcharset=simple8",
				"area1.osm"
		});

		MakeMap.main(new String[] {
				"--xcharset=10bit",
				"area1.osm"
		});
	}

	private static void negative() {
		MakeMap.main(new String[] {
				"nonexistingfile.osm"
		});

		MakeMap.main(new String[] {
				"README"
		});
	}
}
