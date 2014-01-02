/*
 * Copyright (C) 2011.
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
package uk.me.parabola.imgfmt.app.typ;

import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.srt.Sort;

/**
 * Holds all the data for a typ file.
 *
 * @author Steve Ratcliffe
 */
public class TypData {
	private final ShapeStacking stacking = new ShapeStacking();
	private final TypParam param = new TypParam();
	private final List<TypPolygon> polygons = new ArrayList<TypPolygon>();
	private final List<TypLine> lines = new ArrayList<TypLine>();
	private final List<TypPoint> points = new ArrayList<TypPoint>();
	private final List<TypIconSet> icons = new ArrayList<TypIconSet>();

	private Sort sort;
	private CharsetEncoder encoder;

	public void addPolygonStackOrder(int level, int type, int subtype) {
		stacking.addPolygon(level, type, subtype);
	}

	public Sort getSort() {
		return sort;
	}

	public void setSort(Sort sort) {
		if (sort == null)
			return;

		if (this.sort != null) {
			int origCodepage = this.sort.getCodepage();
			if (origCodepage != 0) {
				if (origCodepage != sort.getCodepage()) {
					// This is just a warning, not a definite problem
					System.out.println("WARNING: SortCode in TYP txt file different from" +
							" command line setting");
				}
			}
		}
		this.sort = sort;
		encoder = sort.getCharset().newEncoder();
		param.setCodePage(sort.getCodepage());
	}

	public void setFamilyId(int val) {
		param.setFamilyId(val);
	}

	public void setProductId(int val) {
		param.setProductId(val);
	}

	public ShapeStacking getStacking() {
		return stacking;
	}

	public TypParam getParam() {
		return param;
	}

	public void addPolygon(TypPolygon polygon) {
		polygons.add(polygon);
	}

	public CharsetEncoder getEncoder() {
		return encoder;
	}

	public List<TypPolygon> getPolygons() {
		return polygons;
	}

	public void addLine(TypLine line) {
		lines.add(line);
	}

	public List<TypLine> getLines() {
		return lines;
	}

	public void addPoint(TypPoint point) {
		points.add(point);
	}

	public List<TypPoint> getPoints() {
		return points;
	}

	public void addIcon(TypIconSet current) {
		icons.add(current);
	}

	public List<TypIconSet> getIcons() {
		return icons;
	}
}
