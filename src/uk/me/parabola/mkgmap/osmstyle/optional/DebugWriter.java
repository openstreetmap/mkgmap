/*
 * Copyright (C) 2018.
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
package uk.me.parabola.mkgmap.osmstyle.optional;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.osmstyle.ConvertedWay;
import uk.me.parabola.mkgmap.reader.osm.CoordPOI;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.splitter.O5mMapWriter;

public class DebugWriter {
	
	/**
	 * Convert list of ways to osm file for debugging purposes.
	 * @param bbox the bounding box for the osm file
	 * @param outPath the directory 
	 * @param name the file name
	 * @param convertedWays the list of ways
	 */
	public static void writeOSM(Area bbox, String outPath, String name, List<ConvertedWay> convertedWays){
		if (outPath == null)
			return;
		File outDir = new File(outPath + "/.");
		if (outDir.getParentFile() != null) {
			outDir.getParentFile().mkdirs();
		} 		
		Map<String,byte[]> dummyMap = new HashMap<>();
		for (int pass = 1; pass <= 2; pass ++){
			IdentityHashMap<Coord, Integer> allPoints = new IdentityHashMap<>();
			uk.me.parabola.splitter.Area bounds = new uk.me.parabola.splitter.Area(
					bbox.getMinLat(),bbox.getMinLong(),bbox.getMaxLat(),bbox.getMaxLong());

			
			O5mMapWriter writer = new O5mMapWriter(bounds, outDir, 0, 0, dummyMap, dummyMap);
			writer.initForWrite();
			Integer nodeId;
			try {

				for (ConvertedWay cw: convertedWays){
					if (cw == null)
						continue;
					for (Coord p: cw.getPoints()){
						nodeId = allPoints.get(p);
						if (nodeId == null){
							nodeId = allPoints.size();
							allPoints.put(p, nodeId);
							uk.me.parabola.splitter.Node nodeOut = new  uk.me.parabola.splitter.Node();				
							if (pass == 1)
								nodeOut.set(nodeId+1000000000L, p.getLatDegrees(), p.getLonDegrees()); // high prec
							else 
								nodeOut.set(nodeId+1000000000L, Utils.toDegrees(p.getLatitude()), Utils.toDegrees(p.getLongitude()));
							if (p instanceof CoordPOI){
								for (Map.Entry<String, String> tagEntry : ((CoordPOI) p).getNode().getTagEntryIterator()) {
									nodeOut.addTag(tagEntry.getKey(), tagEntry.getValue());
								}
							}
							writer.write(nodeOut);
						}
					}
				}
				for (int w = 0; w < convertedWays.size(); w++){
					ConvertedWay cw = convertedWays.get(w);
					if (cw == null)
						continue;
					Way way = cw.getWay();
					uk.me.parabola.splitter.Way wayOut = new uk.me.parabola.splitter.Way();
					for (Coord p: way.getPoints()){
						nodeId = allPoints.get(p);
						assert nodeId != null;
						wayOut.addRef(nodeId+1000000000L);
					}
					for (Map.Entry<String, String> tagEntry : way.getTagEntryIterator()) {
						wayOut.addTag(tagEntry.getKey(), tagEntry.getValue());
					}
					
					wayOut.setId(way.getId());
					
					writer.write(wayOut);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			writer.finishWrite();
			File f = new File(outDir.getAbsoluteFile() , "00000000.o5m");
			File ren = new File(outDir.getAbsoluteFile() , name+((pass==1) ? "_hp":"_mu") + ".o5m");
			if (ren.exists())
				ren.delete();
			f.renameTo(ren);
		}
	}
}
