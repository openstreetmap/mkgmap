/*
 * Copyright (C) 2012-2014.
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

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.reader.osm.FakeIdGenerator;
import uk.me.parabola.mkgmap.reader.osm.GeneralRelation;
import uk.me.parabola.mkgmap.reader.osm.MultiPolygonRelation;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.Java2DConverter;

/**
 * Merges the polygons for one precompiled sea tile.
 * @author WanMil
  */
class PrecompSeaMerger implements Runnable {
	private final MergeData mergeData;
	private final CountDownLatch signal;
	private final BlockingQueue<Entry<String, List<Way>>> saveQueue;
	private ExecutorService service;

	static class MergeData {
		public final Rectangle2D bounds;
		public final BlockingQueue<Area> toMerge;
		public final AtomicBoolean ready = new AtomicBoolean(false);
		public Path2D.Double tmpLandPath = new Path2D.Double();
		public Area landArea = new Area();
		private final String key;

		public MergeData(Rectangle2D bounds, String key) {
			this.key = key;
			this.bounds = bounds;
			toMerge = new LinkedBlockingQueue<Area>();
		}

		public String getKey() {
			return key;
		}

	}

	public PrecompSeaMerger(Rectangle2D bounds, String key,
			CountDownLatch signal,
			BlockingQueue<Entry<String, List<Way>>> saveQueue) {
		this.mergeData = new MergeData(bounds, key);
		this.signal = signal;
		this.saveQueue = saveQueue;
	}

	public MergeData getMergeData() {
		return mergeData;
	}

	public BlockingQueue<Area> getQueue() {
		return mergeData.toMerge;
	}

	public void signalInputComplete() {
		mergeData.ready.set(true);
	}

	public void setExecutorService(ExecutorService service) {
		this.service = service;
	}

	public Rectangle2D getTileBounds() {
		return mergeData.bounds;
	}

	private List<Way> convertToWays(Area a, String naturalTag) {
		List<List<Coord>> pointLists = Java2DConverter.areaToShapes(a);
		List<Way> ways = new ArrayList<Way>(pointLists.size());
		for (List<Coord> points : pointLists) {
			Way w = new Way(FakeIdGenerator.makeFakeId(), points);
			w.addTag("natural", naturalTag);
			ways.add(w);
		}
		return ways;
	}

	public void run() {
		Area merge = null;
		try {
			merge = mergeData.toMerge.poll(5, TimeUnit.MILLISECONDS);
		} catch (InterruptedException exp) {
			exp.printStackTrace();
		}
		int merges = 0;
		while (merge != null) {
			Area landClipped = new Area(mergeData.bounds);
			landClipped.intersect(merge);
			mergeData.tmpLandPath.append(landClipped, false);
			merges++;
			
			if (merges % 500 == 0) {
				// store each 500 polygons into a temporary area
				// and merge them after that. That seems to be quicker
				// than adding lots of very small areas to a highly 
				// scattered area 
				Area tmpLandArea = new Area(mergeData.tmpLandPath);
				mergeData.landArea.add(tmpLandArea);
				mergeData.tmpLandPath.reset();
			}

			if (merges % 500 == 0) {
				break;
			}
			
			merge = mergeData.toMerge.poll();
		}

		if (mergeData.ready.get() == false
				|| mergeData.toMerge.isEmpty() == false) {
			// repost the merge thread
			service.execute(this);
			return;
		}
		if (mergeData.landArea.isEmpty())
			mergeData.landArea = new Area(mergeData.tmpLandPath);
		else
			mergeData.landArea.add(new Area(mergeData.tmpLandPath));
		mergeData.tmpLandPath = null;

		// post processing //
		
		// convert the land area to a list of ways
		List<Way> ways = convertToWays(mergeData.landArea, "land");

		if (ways.isEmpty()) {
			// no land in this tile => create a sea way only
			ways.addAll(convertToWays(new Area(mergeData.bounds), "sea"));
		} else {
			Map<Long, Way> landWays = new HashMap<Long, Way>();
			List<List<Coord>> landParts = Java2DConverter
					.areaToShapes(mergeData.landArea);
			for (List<Coord> landPoints : landParts) {
				Way landWay = new Way(FakeIdGenerator.makeFakeId(), landPoints);
				landWays.put(landWay.getId(), landWay);
			}

			Way seaWay = new Way(FakeIdGenerator.makeFakeId());
			seaWay.addPoint(new Coord(-90.0d, -180.0d));
			seaWay.addPoint(new Coord(90.0d, -180.0d));
			seaWay.addPoint(new Coord(90.0d, 180.0d));
			seaWay.addPoint(new Coord(-90.0d, 180.0d));
			seaWay.addPoint(new Coord(-90.0d, -180.0d));
			landWays.put(seaWay.getId(), seaWay);

			Relation rel = new GeneralRelation(FakeIdGenerator.makeFakeId());
			for (Way w : landWays.values()) {
				rel.addElement((w == seaWay ? "outer" : "inner"), w);
			}

			// process the tile as sea multipolygon to create simple polygons only
			MultiPolygonRelation mpr = new MultiPolygonRelation(rel, landWays,
					Java2DConverter.createBbox(new Area(mergeData.bounds))) 
			{
				// do not calculate the area size => it is not required and adds
				// a superfluous tag 
				protected boolean isAreaSizeCalculated() {
					return false;
				}
			};
			mpr.addTag("type", "multipolygon");
			mpr.addTag("natural", "sea");
			mpr.processElements();

			for (Way w : landWays.values()) {
				// process the polygon ways only
				// the mp processing also creates line ways which must 
				// be ignored here
				if (MultiPolygonRelation.STYLE_FILTER_POLYGON.equals(w
						.getTag(MultiPolygonRelation.STYLE_FILTER_TAG))) {
					
					String tag = w.getTag("natural");
					if ("sea".equals(tag) == false) {
						// ignore the land polygons - we already have them in our list
						continue;
					}
					w.deleteTag(MultiPolygonRelation.STYLE_FILTER_TAG);
					w.deleteTag(MultiPolygonRelation.MP_CREATED_TAG);
					ways.add(w);
				}
			}
		}

		try {
			// forward the ways to the queue of the saver thread
			saveQueue.put(new SimpleEntry<String, List<Way>>(
					mergeData.getKey(), ways));
		} catch (InterruptedException exp) {
			exp.printStackTrace();
		}

		// signal that this tile is finished
		signal.countDown();
	}
}