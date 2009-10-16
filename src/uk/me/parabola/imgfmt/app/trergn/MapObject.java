/*
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
 * 
 * Author: Steve Ratcliffe
 * Create date: 12-Dec-2006
 */
package uk.me.parabola.imgfmt.app.trergn;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.mkgmap.general.MapElement;

/**
 * An object that appears in a map.  One of point, polyline, polygon or indexed
 * point.
 *
 * All objects appear in a subdivision and are relative to it.  You cannot
 * know where the object is or its size without knowing the subdivision it
 * is in.
 *
 * @author Steve Ratcliffe
 */
public abstract class MapObject {

	// All lines are in a division and many aspects of it are with respect to
	// the division.
	private Subdivision subdiv;

	// The label(s) for this object
	private Label label;
	private List<Label> refLabels;

	// The type of road etc.
	private int type;

	// These long and lat values are relative to the subdivision center.
	// Must be shifted depending on the zoom level.
	private int deltaLong;
	private int deltaLat;

	private ExtTypeAttributes extTypeAttributes;

	// The number of this point within its subdivision.  Set on read only.
	private int number;

	/**
	 * Write this object to the given file.
	 *
	 * @param file The file to write to. It is usually the RGN file.
	 */
	public abstract void write(ImgFileWriter file);

	public abstract void write(OutputStream stream) throws IOException;

	int getDeltaLat() {
		return deltaLat;
	}

	protected int getDeltaLong() {
		return deltaLong;
	}

	public void setLabel(Label label) {
		this.label = label;
	}

	public void addRefLabel(Label refLabel) {
		if(refLabels == null)
			refLabels = new ArrayList<Label>();
		refLabels.add(refLabel);
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public boolean hasExtendedType() {
		return MapElement.hasExtendedType(type);
	}

	/** 
	 * Set an ordinary unshifted latitude.  It will be calculated
	 * relative to the subdivision. 
	 * 
	 * @param lat The original latitude.
	 */
	public void setLatitude(int lat) {
		Subdivision div = getSubdiv();

		int diff = div.roundLatToLocalShifted(lat);

		setDeltaLat(diff);
	}

	/** 
	 * Set an ordinary unshifted longitude.  It will be calculated
	 * relative to the subdivision. 
	 * 
	 * @param lon The original longitude.
	 */
	public void setLongitude(int lon) {
		Subdivision div = getSubdiv();

		int diff = div.roundLonToLocalShifted(lon);

		setDeltaLong(diff);
	}
	
	// directly setting shouldn't be done, unless reading from a file
	protected void setDeltaLat(int deltaLat) {
		this.deltaLat = deltaLat;
	}

	// directly setting shouldn't be done, unless reading from a file
	protected void setDeltaLong(int deltaLong) {
		this.deltaLong = deltaLong;
	}

	public Subdivision getSubdiv() {
		return subdiv;
	}

	protected void setSubdiv(Subdivision subdiv) {
		this.subdiv = subdiv;
	}

	public Label getLabel() {
		return label;
	}

	public List<Label> getRefLabels() {
		return refLabels;
	}

	protected byte[] getExtTypeExtraBytes() {
		return (extTypeAttributes != null)? extTypeAttributes.getExtTypeExtraBytes(this) : null;
	}

	public void setExtTypeAttributes(ExtTypeAttributes eta) {
		extTypeAttributes = eta;
	}

	public String toString() {
		return "Type=" + type + ", l=" + label;
	}

	public int getNumber() {
		return number;
	}

	public void setNumber(int number) {
		this.number = number;
	}
}
