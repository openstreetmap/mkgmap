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
 * Create date: 10-Jan-2009
 */
package func.lib;

/**
 * Useful constants that are used for arguments etc. in the functional
 * tests.
 *
 * @author Steve Ratcliffe
 */
public interface Args {
	public static final String TEST_RESOURCE_OSM = "test/resources/in/osm/";
	public static final String TEST_RESOURCE_MP = "test/resources/in/mp/";
	public static final String TEST_RESOURCE_IMG = "test/resources/in/img/";

	public static final String TEST_STYLE_ARG = "--style-file=test/resources/teststyles/main";

	public static final String DEF_MAP_ID = "63240001";
	public static final String DEF_MAP_ID2 = "63240002";
	public static final String DEF_MAP_FILENAME = "63240001.img";
	public static final String DEF_MAP_FILENAME2 = "63240002.img";
	public static final String DEF_GMAPSUPP_FILENAME = "gmapsupp.img";
	public static final String DEF_TDB_FILENAME = "63240000.tdb";
}
