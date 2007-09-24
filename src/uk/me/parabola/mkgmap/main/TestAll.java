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

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.ExitException;

/**
 * @author Steve Ratcliffe
 */
public class TestAll {
	private static final Logger log = Logger.getLogger(TestAll.class);

	private static final String[] noargs = new String[0];
	private static final String[] defargs = new String[] {
			"area1.osm"
	};

	public static void main(String[] args) {
		positive();
		negative();
	}

	private static final String[][] chartestArgs = new String[][] {
			new String[] {"test/osm/czech_test.osm"},
			new String[] {"test/osm/german_test.osm"},
			new String[] {"test/osm/sweden_test.osm"},
			new String[] {"test/osm5/cricklewood-5.osm"},
			new String[] {"test/polish/lon.mp"},
			new String[] {"test/polish/non-existing"},
			new String[] {"test/osm/63247525"},
			new String[] {"test/osm/63253506"},
			new String[] {"test-map:all-elements"},
			new String[] {"test-map:test-points"},
			new String[] {"test/osm/63253506"},

			new String[] {"--xcharset=latin1", "test/osm/czech_test.osm"},
			new String[] {"--xcharset=latin1", "test/osm/german_test.osm"},
			new String[] {"--xcharset=latin1", "test/osm/sweden_test.osm"},
			new String[] {"--xcharset=simple8", "test/osm/sweden_test.osm"},
			new String[] {"--xcharset=10bit", "test/osm/sweden_test.osm"},
			new String[] {"-c", "test/args/t1", "test/osm5/cricklewood-5.osm"},
			new String[] {"-c", "test/args/t2", "test/osm5/cricklewood-5.osm"},
			new String[] {"-c", "test/args/t3", "test/osm5/cricklewood-5.osm"},
	};

	private static void positive() {
		MakeMap.main(new String[] {
				"--mapname=12008899",
				"vbig.osm"
		});

		MakeTestLang10Map.main(noargs);
		MakeTestLangMap.main(noargs);

		for (String[] args : chartestArgs) {
			MakeMap.main(args);
		}

		Logger.resetLogging("test/log.properties");
		MakeMap.main(new String[] {"-c",
				"test/args/t1",
				"area1.osm"});
		MakeMap.main(new String[] {"-c",
				"test/args/t1",
				"test/osm5/cricklewood-5.osm"});
		if (log.isInfoEnabled()) {
			log.info("info is enabled");
		}
		if (log.isWarnEnabled()) {
			log.warn("info is enabled");
		}
		if (log.isErrorEnabled()) {
			log.error("error is enabled");
			try {
				throw new ExitException("testing");
			} catch (ExitException e) {
				log.error("error is enabled", e);
			}
		}
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
