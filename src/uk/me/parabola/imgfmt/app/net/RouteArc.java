/*
 * Copyright (C) 2008
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
 * Create date: 07-Jul-2008
 */
package uk.me.parabola.imgfmt.app.net;

import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.log.Logger;

/**
 * An arc joins two nodes within a {@link RouteCenter}.  This may be renamed
 * to a Segment.
 * The arc also references the road that it is a part of.
 *
 * There are also links between nodes in different centers.
 *
 * @author Steve Ratcliffe
 */
public class RouteArc {
	private static final Logger log = Logger.getLogger(RouteArc.class);
	
	// Flags A
	private static final int FLAG_NEWDIR = 0x80;
	private static final int FLAG_FORWARD = 0x40;
	private static final int MASK_DESTCLASS = 0x7;
	public static final int MASK_CURVE_LEN = 0x38;

	// Flags B
	private static final int FLAG_LAST_LINK = 0x80;
	private static final int FLAG_EXTERNAL = 0x40;

	private int offset;

	// heading / bearing: 
	private float initialHeading; // degrees (A-> B in an arc ABCD) 
	private final float finalHeading; // degrees (D-> D in an arc ABCD)
	private final float directHeading; // degrees (A-> D in an arc ABCD)

	private final RoadDef roadDef;

	// The nodes that this arc comes from and goes to
	private final RouteNode source;
	private final RouteNode dest;

	// The index in Table A describing this arc.
	private byte indexA;
	// The index in Table B that this arc goes via, if external.
	private byte indexB;
	
	private byte flagA;
	private byte flagB;

	private final boolean haveCurve;
	private final int length;
	private final byte lengthRatio;
	private final int pointsHash;

	/**
	/**
	 * Create a new arc. An arc can contain multiple points (eg. A->B->C->D->E)
	 * @param roadDef The road that this arc segment is part of.
	 * @param source The source node. (A)
	 * @param dest The destination node (E).
	 * @param initialBearing The initial heading (signed degrees) (A->B)
	 * @param finalBearing The final heading (signed degrees) (D->E)
	 * @param directBearing The direct heading (signed degrees) (A->E)
	 * @param arcLength the length of the arc in meter (A->B->C->D->E)
	 * @param directLength the length of the arc in meter (A-E) 
	 * @param curveEnabled false means don't write curve bytes 
	 * @param pointsHash
	 */
	public RouteArc(RoadDef roadDef,
					RouteNode source, RouteNode dest,
					double initialBearing, double finalBearing, double directBearing,
					double arcLength,
					double directLength,
					boolean curveEnabled,
					int pointsHash) {
		this.roadDef = roadDef;
		this.source = source;
		this.dest = dest;
		this.initialHeading = (float) initialBearing;
		this.finalHeading = (float) finalBearing;
		this.directHeading = (directBearing < 180) ? (float) directBearing : -180.0f;
		int len = convertMeters(arcLength);
		if (len >= (1 << 22)) {
			log.error("Way " + roadDef.getName() + " (id " + roadDef.getId() + ") contains an arc whose length (" + len + " units) is too big to be encoded, using length",((1 << 22) - 1));
			len = (1 << 22) - 1;
		}
		this.length = len;
		if (len > (1 << 14)){
			long dd = 4;
		}
		this.pointsHash = pointsHash;
		int ratio = 0;
		if (arcLength > directLength){
			ratio = (byte) ((int)(directLength * 32 / arcLength) & 0x1f);
			if (ratio > 26 && this.length < (1 << 14))
				ratio = 0;
		}
		lengthRatio = (byte)ratio;
		haveCurve = curveEnabled && (ratio > 0 || length > (1 << 14));
	}

	public float getInitialHeading() {
		return initialHeading;
	}

	public void setInitialHeading(float ih) {
		initialHeading = ih;
	}

	public float getFinalHeading() {
		return finalHeading;
	}

	public RouteNode getSource() {
		return source;
	}

	public RouteNode getDest() {
		return dest;
	}

	public int getLength() {
		return length;
	}

	public int getPointsHash() {
		return pointsHash;
	}

	/**
	 * Provide an upper bound for the written size in bytes.
	 */
	public int boundSize() {

		int[] lendat = encodeLength();

		// 1 (flagA) + 1-2 (offset) + 1 (indexA) + 1 (initialHeading)
		int size = 5 + lendat.length;
		if(haveCurve)
			size += encodeCurve().length;
		return size;
	}

	/**
	 * Is this an arc within the RouteCenter?
	 */
	public boolean isInternal() {
		// we might check that setInternal has been called before
		return (flagB & FLAG_EXTERNAL) == 0;
	}

	public void setInternal(boolean internal) {
		if (internal)
			flagB &= ~FLAG_EXTERNAL;
		else
			flagB |= FLAG_EXTERNAL;
	}


	/**
	 * Set this arc's index into Table A.
	 */
	public void setIndexA(byte indexA) {
		this.indexA = indexA;
	}

	/**
	 * Get this arc's index into Table A.
	 *
	 * Required for writing restrictions (Table C).
	 */
	public byte getIndexA() {
		return indexA;
	}

	/**
	 * Set this arc's index into Table B. Applies to external arcs only.
	 */
	public void setIndexB(byte indexB) {
		assert !isInternal() : "Trying to set index on internal arc.";
		this.indexB = indexB;
	}

	/**
	 * Get this arc's index into Table B.
	 *
	 * Required for writing restrictions (Table C).
	 */
	public byte getIndexB() {
		return indexB;
	}
	 

	// units of 16 feet
	final static double LENGTH_FACTOR = 3.2808 / 16;
	private static int convertMeters(double l) {
		return (int) (l * LENGTH_FACTOR);
	}

	public void write(ImgFileWriter writer) {
		offset = writer.position();
		if(log.isDebugEnabled())
			log.debug("writing arc at", offset, ", flagA=", Integer.toHexString(flagA));

		// fetch destination class -- will have been set correctly by now
		setDestinationClass(dest.getNodeClass());

		// determine how to write length and curve bit
		int[] lendat = encodeLength();

		writer.put(flagA);

		if (isInternal()) {
			// space for 14 bit node offset, written in writeSecond.
			writer.put(flagB);
			writer.put((byte) 0);
		} else {
			if(indexB >= 0x3f) {
				writer.put((byte) (flagB | 0x3f));
				writer.put(indexB);
			}
			else
				writer.put((byte) (flagB | indexB));
		}

		writer.put(indexA);

		if(log.isDebugEnabled())
			log.debug("writing length", length);
		for (int aLendat : lendat)
			writer.put((byte) aLendat);

		writer.put((byte)(initialHeading * 256 / 360));

		if (haveCurve) {
			int[] curvedat = encodeCurve();
			for (int aCurvedat : curvedat)
				writer.put((byte) aCurvedat);
		}
	}

	/**
	 * Second pass over the nodes in this RouteCenter.
	 * Node offsets are now all known, so we can write the pointers
	 * for internal arcs.
	 */
	public void writeSecond(ImgFileWriter writer) {
		if (!isInternal())
			return;

		writer.position(offset + 1);
		char val = (char) (flagB << 8);
		int diff = dest.getOffsetNod1() - source.getOffsetNod1();
		assert diff < 0x2000 && diff >= -0x2000
			: "relative pointer too large for 14 bits (source offset = " + source.getOffsetNod1() + ", dest offset = " + dest.getOffsetNod1() + ")";
		val |= diff & 0x3fff;

		// We write this big endian
		if(log.isDebugEnabled())
			log.debug("val is", Integer.toHexString((int)val));
		writer.put((byte) (val >> 8));
		writer.put((byte) val);
	}

	/*
	 * length and curve flag are stored in a variety of ways, involving
	 * 1. flagA & 0x38 (3 bits)
	 * 2. 1-3 bytes following the possible Table A index
	 *
	 * There's even more different encodings supposedly.
	 */
	private int[] encodeLength() {
		// clear existing bits in case length or final heading have
		// been changed
		flagA &= ~0x38;
		int[] lendat;
		if(length < 0x200) {
			// 9 bit length optional curve
			if(haveCurve)
				flagA |= 0x20;
			flagA |= (length >> 5) & 0x08; // top bit of length
			lendat = new int[1];		   // one byte of data
			lendat[0] = length;			   // bottom 8 bits of length
		} 
		else if(length >= (1 << 14)) {
			// 22 bit length with curve
			flagA |= 0x38;
			lendat = new int[3];		   // three bytes of data
			lendat[0] = 0xC0 | (length & 0x3f); // 0x80 set, 0x40 set, 6 low bits of length
			lendat[1] = (length >> 6) & 0xff; // 8 more bits of length
			lendat[2] = (length >> 14) & 0xff; // 8 more bits of length
		}

		else if(haveCurve) {
			// 15 bit length with curve
			flagA |= 0x38;				 // all three bits set
			lendat = new int[2];		 // two bytes of data
			lendat[0] = (length & 0x7f); // 0x80 not set, 7 low bits of length
			lendat[1] = (length >> 7) & 0xff; // 8 more bits of length
		}
		else {
			// 14 bit length no curve
			flagA |= 0x38;		 // all three bits set
			lendat = new int[2]; // two bytes of data
			lendat[0] = 0x80 | (length & 0x3f); // 0x80 set, 0x40 not set, 6 low bits of length
			lendat[1] = (length >> 6) & 0xff; // 8 more bits of length
		}

		return lendat;
	}

	/**
	 * Encode the curve data into a sequence of bytes.
	 * Curve data contains a ratio between arc length and direct distance
	 * and the direct bearing. This is typically encode in one byte, 
	 * for extreme ratios two bytes are used.
	 */
	private int[] encodeCurve() {
		assert lengthRatio != 0;
		int[] curveData;
		
			
		int dh = ((int) (directHeading * 256 / 360));
		if (lengthRatio >= 1 && lengthRatio <= 17) {
			// two byte curve data neeeded
			curveData = new int[2];
			curveData[0] = lengthRatio;
			curveData[1] = dh;
			
		} else {
			// use compacted form
			int compactedRatio = lengthRatio / 2 - 8;
			assert compactedRatio > 0 && compactedRatio < 8;
			curveData = new int[1];
			
			curveData[0] = (compactedRatio << 5) | ((dh >> 3) & 0x1f);
			/* check math:
			int dhx = curveData[0] & 0x1f;
			int decodedDirectHeading = (dhx <16) ?  dhx << 3 : -(256 - (dhx<<3));
			if ((dh & 0xfffffff8) != decodedDirectHeading)
				log.error("failed to encode direct heading", directHeading, dh, decodedDirectHeading);
			int ratio = (curveData[0] & 0xe0) >> 5;
			if (ratio != compactedRatio)
				log.error("failed to encode length ratio", lengthRatio, compactedRatio, ratio);
				*/
		}
		return curveData;
	}

	public RoadDef getRoadDef() {
		return roadDef;
	}

	public void setNewDir() {
		flagA |= FLAG_NEWDIR;
	}

	public void setForward() {
		flagA |= FLAG_FORWARD;
	}

	public boolean isForward() {
		return (flagA & FLAG_FORWARD) != 0;
	}

	public void setLast() {
		flagB |= FLAG_LAST_LINK;
	}

	protected void setDestinationClass(int destinationClass) {
		if(log.isDebugEnabled())
			log.debug("setting destination class", destinationClass);
		flagA |= (destinationClass & MASK_DESTCLASS);
	}
}
