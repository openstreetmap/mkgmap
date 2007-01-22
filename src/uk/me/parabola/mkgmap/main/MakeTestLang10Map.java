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
import uk.me.parabola.imgfmt.app.Map;
import uk.me.parabola.imgfmt.app.Polyline;
import uk.me.parabola.imgfmt.app.PolylineOverview;
import uk.me.parabola.imgfmt.app.Subdivision;
import uk.me.parabola.log.Logger;

/**
 * A test map for language support.  This is to investigate the 10 bit format
 * about which nothing is known by me.
 *
 * @author Steve Ratcliffe
 */
public class MakeTestLang10Map extends AbstractTestMap {
	private static final Logger log = Logger.getLogger(MakeTestLang10Map.class);

	public static void main(String[] args)  {
		MakeTestLang10Map tm = new MakeTestLang10Map();
		tm.makeMap(args);
	}

	protected void drawTestMap(Map map, Subdivision div, double lat, double lng) {
		drawStreetnames(map, div, lat, lng);
	}

	protected void writeOverviews(Map map) {
		// nothing to do here
	}

	private void drawStreetnames(Map map, Subdivision div, double slat, double slon) {

		double lat = slat + 0.004;
		double lon = slon + 0.002;
		double space = 0.002;
		double size = 0.006;

		div.startLines();

		map.setLabelCharset("10bit");
//		map.setLabelCodePage(1250); // No difference?

		for (int x = 0; x < 4; x++) {
			for (int y = 0; y < 16; y++) {

				int start = x*256 + y * 16;
				StringBuilder sb = new StringBuilder();
	//			sb.append((int) hexChars[(start & 0xf00) >> 8]);
	//			sb.append(',');
	//			sb.append((int) hexChars[(start & 0xf0) >> 4]);
	//			sb.append(',');
	//			sb.append((int) hexChars[(start & 0x0f)]);
	//			sb.append(',');
	//			sb.append((int) ' ');

				for (int i = 0; i < 16; i++) {
					sb.append(start + i+1);
					sb.append(',');
				}
				sb.append(0x3ff);
				sb.append(',');
				sb.append(0);
	//			sb.append(',');

				log.debug(sb);

				Polyline l = div.createLine(sb.toString());
				double baseLat = lat + y * space;
				double baseLong = lon + x * (size+space);
				Coord co = new Coord(baseLat, baseLong);
				l.addCoord(co);
				co = new Coord(baseLat, baseLong + size);
				l.addCoord(co);

				l.setType(6);
				map.addMapObject(l);
			}
		}
		map.addPolylineOverview(new PolylineOverview(6));
	}
}
