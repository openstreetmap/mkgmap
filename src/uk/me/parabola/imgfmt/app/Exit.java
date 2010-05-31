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
 * Create date: Jan 1, 2008
 */
package uk.me.parabola.imgfmt.app;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.lbl.ExitFacility;
import uk.me.parabola.imgfmt.app.lbl.Highway;

/**
 * Represent a motorway exit
 *
 * @author Mark Burton
 */
public class Exit {

	public final static String TAG_ROAD_REF = "exit:road_ref";
	public final static String TAG_TO = "exit:to";
	public final static String TAG_FACILITY = "exit:facility";

	private final Highway highway;
	private Label description;
	private final List<ExitFacility> facilities = new ArrayList<ExitFacility>();

	public Exit(Highway highway) {
		this.highway = highway;
	}

	public void setDescription(Label description) {
		this.description = description;
	}

	public void addFacility(ExitFacility facility) {
		facilities.add(facility);
	}

	public boolean getOvernightParking() {
		return false;	// FIXME
	}

	public Highway getHighway() {
		return highway;
	}

	public List<ExitFacility> getFacilities() {
		return facilities;
	}

	public Label getDescription() {
		return description;
	}
}
