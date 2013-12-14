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
 * Create date: 13-Jan-2007
 */
package uk.me.parabola.mkgmap.main;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.map.Map;
import uk.me.parabola.imgfmt.app.trergn.Polyline;
import uk.me.parabola.imgfmt.app.trergn.PolylineOverview;
import uk.me.parabola.imgfmt.app.trergn.Subdivision;

/**
 * A test map for language support.  It has a series of roads all with names
 * that use the whole of the character set.
 *
 * Each 'road name' will contain a number of characters above 0x80.  The name
 * will start with a number that is the first non-ascii character in the name
 * then the characters will be interspaced with the letters a, b, c, d, etc.
 *
 * Its probably easier just to look at it.
 *
 * @author Steve Ratcliffe
 */
public class MakeTestLangMap extends AbstractTestMap {

	public static void main(String[] args)  {
		MakeTestLangMap tm = new MakeTestLangMap();
		tm.makeMap(args);
	}

	protected void drawTestMap(Map map, Subdivision div, double lat, double lng) {
		drawStreetnames(map, div, lat, lng);
	}

	private void drawStreetnames(Map map, Subdivision div, double slat, double slon) {

		char[] hexChars = "0123456789ABCDEF".toCharArray();

		double lat = slat + 0.004;
		double lon = slon + 0.002;

		div.startLines();

		map.setLabelCharset("simple8", true);

		double space = 0.002;
		double size = 0.006;for (int y = 0; y < 16; y++) {

			int start = 128 + 8*y;

			char[] out = new char[19];
			out[0] = hexChars[(start & 0xf0) >> 4];
			out[1] = hexChars[(start & 0x0f)];
			out[2] = ' ';

			for (int i = 0; i < 8; i++) {
				out[3 + 2 * i] = (char) ('A' + i);
				out[3 + 2 * i + 1] = (char) (start + i);
			}

			String name = new String(out);
			Polyline l = div.createLine(new String[]{name, null, null, null});
			double baseLat = lat + y * space;
			Coord co = new Coord(baseLat, lon);
			l.addCoord(co);
			co = new Coord(baseLat, lon + size);
			l.addCoord(co);

			l.setType(6);
			map.addMapObject(l);
		}
		map.addPolylineOverview(new PolylineOverview(0x600, 10));
	}
}
