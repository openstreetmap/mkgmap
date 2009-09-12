/**
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Author: steve
 * Date: 24-Dec-2006
 */

package uk.me.parabola.mkgmap.general;

import uk.me.parabola.imgfmt.app.Exit;

/**
 * A point on the map that represents a motorway exit.
 *
 * @author Mark Burton
 */
public class MapExitPoint extends MapPoint {
	private final String motorwayRef;
	private final String to;
	private String facilityDescription;
	private String OSMId;
	private Exit exit;

	public MapExitPoint(String motorwayRef, String to) {
		this.motorwayRef = motorwayRef;
		this.to = to;
	}

	public MapExitPoint(MapExitPoint p) {
		super(p);
		this.motorwayRef = p.motorwayRef;
		this.to = p.to;
	}

	public MapExitPoint copy() {
		return new MapExitPoint(this);
	}

	public boolean isExit() {
		return true;
	}

	public String getMotorwayRef() {
		return motorwayRef;
	}

	public String getTo() {
		return to;
	}

	public void setFacilityDescription(String facilityDescription) {
		this.facilityDescription = facilityDescription;
	}

	public String getFacilityDescription() {
		return facilityDescription;
	}

	public void setOSMId(String OSMId) {
		this.OSMId = OSMId;
	}

	public String getOSMId() {
		return OSMId;
	}

	public void setExit(Exit exit) {
		this.exit = exit;
	}

	public Exit getExit() {
		return exit;
	}
}
