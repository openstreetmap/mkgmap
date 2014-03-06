/*
 * Copyright (C) 2012.
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

package uk.me.parabola.mkgmap.sea.optional;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.reader.osm.SeaGenerator;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.splitter.BinaryMapWriter;
import uk.me.parabola.splitter.Node;
import uk.me.parabola.splitter.OSMWriter;
import uk.me.parabola.splitter.OSMXMLWriter;

class PrecompSeaSaver implements Runnable {
	private final AtomicBoolean finished = new AtomicBoolean(false);
	private final CountDownLatch finishWait;

	private final Map<String, String> index;

	private final Map<Integer, String> idMapping;
	private int nextId = 0;
	private final boolean usePbf;

	private final File outputDir;
	
	private final BlockingQueue<Entry<String, List<Way>>> saveQueue = new LinkedBlockingQueue<Entry<String, List<Way>>>();

	public PrecompSeaSaver(File outputDir, boolean usePbf) {
		this.outputDir = outputDir;
		finishWait = new CountDownLatch(1);
		this.usePbf = usePbf;
		idMapping = new HashMap<Integer, String>();
		index = new TreeMap<String, String>();
		this.outputDir.mkdirs();
	}
	
	public BlockingQueue<Entry<String, List<Way>>> getQueue() {
		return saveQueue;
	}

	private OSMWriter createWriter(int id, String key) {
		String[] parts = key.split(Pattern.quote("_"));
		int lat = Integer.valueOf(parts[0]);
		int lon = Integer.valueOf(parts[1]);
		uk.me.parabola.splitter.Area bounds = new uk.me.parabola.splitter.Area(
				lat, lon, lat + SeaGenerator.PRECOMP_RASTER, lon + SeaGenerator.PRECOMP_RASTER);
		OSMWriter writer = (usePbf ? new BinaryMapWriter(bounds, outputDir, nextId, 0)
				: new OSMXMLWriter(bounds, outputDir, nextId, 0));
		idMapping.put(id, key);
		writer.initForWrite();
		return writer;
	}
	
	public void waitForFinish() throws InterruptedException {
		this.finished.set(true);
		this.finishWait.await();
	}

	public void run() {
		while (saveQueue.isEmpty() == false || finished.get() == false) {
			Entry<String, List<Way>> tileData = null;
			try {
				tileData = saveQueue.poll(1, TimeUnit.MINUTES);
			} catch (InterruptedException exp) {
				exp.printStackTrace();
			}
			if (tileData != null) {
				int id = ++nextId;

				if (tileData.getValue().size() == 1) {
					// do not write the tile because it consists of one
					// natural type only
					// write it only to the index
					Way singleWay = tileData.getValue().get(0);
					String naturalTag = singleWay.getTag("natural");
					index.put(tileData.getKey(), naturalTag);
				} else {
					String ext = (usePbf ? "pbf" : "gz");
					index.put(tileData.getKey(), "sea_" + tileData.getKey()
							+ ".osm." + ext);

					OSMWriter writer = createWriter(id, tileData.getKey());

					Long2ObjectOpenHashMap<Long> coordIds = new Long2ObjectOpenHashMap<>();
					Map<Long,uk.me.parabola.splitter.Way> pbfWays = new TreeMap<Long, uk.me.parabola.splitter.Way>();
					long maxNodeId = 1;
					for (Way w : tileData.getValue()) {
						uk.me.parabola.splitter.Way pbfWay = new uk.me.parabola.splitter.Way();
						pbfWay.set(w.getId());
						for (Entry<String, String> tag : w
								.getEntryIteratable()) {
							pbfWay.addTag(tag.getKey(), tag.getValue());
						}
						for (Coord c : w.getPoints()) {
							Node n = new Node();
							long key = Utils.coord2Long(c);
							Long nodeId = coordIds.get(key);
							if (nodeId == null) {
								nodeId = new Long(maxNodeId++);
								coordIds.put(key, nodeId);
								n.set(nodeId,
										c.getLatDegrees(),
										c.getLonDegrees());
								try {
									writer.write(n);
								} catch (IOException exp) {
									exp.printStackTrace();
								}
							}
							pbfWay.addRef(nodeId);
						}
						pbfWays.put(pbfWay.getId(),pbfWay);
					}
					for (uk.me.parabola.splitter.Way pbfWay : pbfWays.values()) {
						try {
							writer.write(pbfWay);
						} catch (IOException exp) {
							exp.printStackTrace();
						}
					}
					writer.finishWrite();

					File tileFile = new File(outputDir, String.format(
							"%08d.osm." + ext, id));
					File precompFile = new File(outputDir, "sea_"
							+ tileData.getKey() + ".osm." + ext);
					if (precompFile.exists()) {
						precompFile.delete();
					}
					
					tileFile.renameTo(precompFile);
				}
			}
		}
		
		writeIndex();

		finishWait.countDown();
	}
	
	private void writeIndex() {
		try {
			PrintWriter indexWriter = new PrintWriter(
					new GZIPOutputStream(new FileOutputStream(
					new File(outputDir, "index.txt.gz"))));
			
			for (Entry<String, String> ind : index.entrySet()) {
				indexWriter.format("%s;%s\n", ind.getKey(), ind.getValue());
			}

			indexWriter.close();
		} catch (IOException exp1) {
			exp1.printStackTrace();
		}
	}
}