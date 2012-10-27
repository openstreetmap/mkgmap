/*
 * Copyright (C) 2010.
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
package uk.me.parabola.mkgmap.reader.osm.bin;

import java.util.List;

import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GeneralRelation;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.OsmHandler;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.EnhancedProperties;

import crosby.binary.BinaryParser;
import crosby.binary.Osmformat;

/**
 * Handler for Scott Crosby's binary format, based on the Google
 * protobuf format.
 *
 * @author Steve Ratcliffe
 */
public class OsmBinHandler extends OsmHandler {
	private static final Logger log = Logger.getLogger(OsmBinHandler.class);

	public OsmBinHandler(EnhancedProperties props) {
	}

	public class BinParser extends BinaryParser {

		protected void parse(Osmformat.HeaderBlock header) {
			double multiplier = .000000001;
			double maxLon = header.getBbox().getRight() * multiplier;
			double minLon = header.getBbox().getLeft() * multiplier;
			double maxLat = header.getBbox().getTop() * multiplier;
			double minLat = header.getBbox().getBottom() * multiplier;

			for (String s : header.getRequiredFeaturesList()) {
				if (s.equals("OsmSchema-V0.6"))
					continue; // We can parse this.

				if (s.equals("DenseNodes"))
					continue; // We can parse this.
				
				throw new MapFailedException("File requires unknown feature: " + s);
			}

			setBBox(minLat, minLon, maxLat, maxLon);
		}

		protected void parseNodes(List<Osmformat.Node> nodes) {
			for (Osmformat.Node binNode : nodes) {
				Coord co = new Coord(parseLat(binNode.getLat()), parseLon(binNode.getLon()));
				long id = binNode.getId();
				saver.addPoint(id, co);

				int tagCount = binNode.getKeysCount();
				if (tagCount > 0) {
					Node node = new Node(id, co);
					for (int tid = 0; tid < tagCount; tid++) {
						String key = getStringById(binNode.getKeys(tid));
						String val = getStringById(binNode.getVals(tid));
						key = keepTag(key, val);
						if (key != null)
							node.addTag(key, val.intern());
					}

					saver.addNode(node);
					hooks.onAddNode(node);
				}
			}
		}

		protected final void parseDense(Osmformat.DenseNodes nodes) {
			long lastId = 0, lastLat = 0, lastLon = 0;

			int kvid = 0; // Index into the key val array.

			for (int nid = 0; nid < nodes.getIdCount(); nid++) {
				long lat = nodes.getLat(nid) + lastLat;
				long lon = nodes.getLon(nid) + lastLon;
				long id = nodes.getId(nid) + lastId;
				lastLat = lat;
				lastLon = lon;
				lastId = id;

				Coord co = new Coord(parseLat(lat), parseLon(lon));
				saver.addPoint(id, co);

				if (nodes.getKeysValsCount() > 0) {
					int ntags = 0;
					Node node = null;
					while (nodes.getKeysVals(kvid) != 0) {
						int keyid = nodes.getKeysVals(kvid++);
						int valid = nodes.getKeysVals(kvid++);
						String key = getStringById(keyid);
						String val = getStringById(valid);
						key = keepTag(key, val);
						if (key != null) {
							if (node == null)
								node = new Node(id, co);
							node.addTag(key, val.intern());
							ntags++;
						}
					}
					kvid++; // Skip over the '0' delimiter.

					if (ntags > 0) {
						// If there are tags, then we save a proper node for it.
						saver.addNode(node);
						hooks.onAddNode(node);
					}
				}
			}
		}

		protected void parseWays(List<Osmformat.Way> ways) {
			for (Osmformat.Way binWay : ways) {
				Way way = new Way(binWay.getId());

				for (int j = 0; j < binWay.getKeysCount(); j++) {

					String key = getStringById(binWay.getKeys(j));
					String val = getStringById(binWay.getVals(j));
					key = keepTag(key, val);
					if (key != null)
						way.addTag(key, val.intern());
				}

				long nid = 0;
				for (long idDelta : binWay.getRefsList()) {
					nid += idDelta;
					Coord co = saver.getCoord(nid);
					if (co != null) {
						hooks.onCoordAddedToWay(way, nid, co);
						co = saver.getCoord(nid);
						way.addPoint(co);

						// nodes (way joins) will have highwayCount > 1
						co.incHighwayCount();
					} else {
						log.error("Way references undefined node" + nid);
					}
				}

				saver.addWay(way);
				hooks.onAddWay(way);
			}
		}

		protected void parseRelations(List<Osmformat.Relation> rels) {

			for (Osmformat.Relation binRel : rels) {
				long id = binRel.getId();
				GeneralRelation rel = new GeneralRelation(id);

				for (int j = 0; j < binRel.getKeysCount(); j++) {
					String key = getStringById(binRel.getKeys(j));
					String val = getStringById(binRel.getVals(j));
					key = keepTag(key, val);
					if (key != null)
						rel.addTag(key, val.intern());
				}


				long lastMid = 0;

				for (int j = 0; j < binRel.getMemidsCount(); j++) {
					long mid = lastMid + binRel.getMemids(j);
					lastMid = mid;
					String role = getStringById(binRel.getRolesSid(j));
					Element el = null;

					if (binRel.getTypes(j) == Osmformat.Relation.MemberType.NODE) {
						el = saver.getNode(mid);
						if(el == null) {
							// we didn't make a node for this point earlier,
							// do it now (if it exists)
							Coord co = saver.getCoord(mid);
							if(co != null) {
								el = new Node(mid, co);
								saver.addNode((Node)el);
							}
						}
					} else if (binRel.getTypes(j) == Osmformat.Relation.MemberType.WAY) {
						el = saver.getWay(mid);
					} else if (binRel.getTypes(j) == Osmformat.Relation.MemberType.RELATION) {
						el = saver.getRelation(mid);
						if (el == null) {
							saver.deferRelation(mid, rel, role);
						}
					} else {
						assert false;
					}

					if (el != null) // ignore non existing ways caused by splitting files
						rel.addElement(role, el);
				}
				saver.addRelation(rel);
			}
		}

		/**
		 * Called when the file is fully read.
		 */
		public void complete() {
		}
	}
}
