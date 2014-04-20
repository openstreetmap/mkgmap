/*
 * Copyright (C) 2014.
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
package uk.me.parabola.imgfmt.app.net;

import java.util.HashMap;
import java.util.Map.Entry;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * mkgmap internal representation of (vehicle) access.
 * @author GerdP
 *
 */
public class AccessTagsAndBits {
	
	public static final byte FOOT 	   = 0x01;
	public static final byte BIKE      = 0x02;
	public static final byte CAR       = 0x04;
	public static final byte DELIVERY  = 0x08;

	public static final byte TRUCK     = 0x10;
	public static final byte BUS       = 0x20;
	public static final byte TAXI      = 0x40;
	public static final byte EMERGENCY = (byte) 0x80;
	
	public final static HashMap<String, Byte> ACCESS_TAGS = new HashMap<String, Byte>(){{
		put("mkgmap:foot", FOOT);
		put("mkgmap:bicycle", BIKE);
		put("mkgmap:car", CAR);
		put("mkgmap:delivery", DELIVERY);
		put("mkgmap:truck", TRUCK);
		put("mkgmap:bus", BUS);
		put("mkgmap:taxi", TAXI);
		put("mkgmap:emergency", EMERGENCY);
	}};
	
	public static byte evalAccessTags(Element el){
		byte noAccess = 0;
		for (Entry<String, Byte> entry : ACCESS_TAGS.entrySet()){
			String access = el.getTag(entry.getKey());
			if (access == null)
				continue;
			if ("no".equals(access))
				noAccess |= entry.getValue();
		}
		return  (byte) ~noAccess;
	}
}
