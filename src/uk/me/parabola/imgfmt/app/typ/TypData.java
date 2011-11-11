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

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds all the data for a typ file.
 *
 * @author Steve Ratcliffe
 */
public class TypData {
	private final ShapeStacking stacking = new ShapeStacking();
	private final TypParam param = new TypParam();
	private final List<TypPolygon> polygons = new ArrayList<TypPolygon>();
	private CharsetEncoder encoder;

	public void addPolygonStackOrder(int level, int type, int subtype) {
		stacking.addPolygon(level, type, subtype);
	}

	public void setCodePage(int val) {
		param.setCodePage(val);
		Charset charset = Charset.forName("cp" + val);
		encoder = charset.newEncoder();
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

	public void addPolygon(TypPolygon current) {
		polygons.add(current);
	}

	public CharsetEncoder getEncoder() {
		return encoder;
	}

	public List<TypPolygon> getPolygons() {
		return polygons;
	}
}
