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

import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.lbl.LBLFile;

import uk.me.parabola.log.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.text.DecimalFormat;
import java.text.ParsePosition;

/*
Add support for extended type attributes.

These are nearly all for marine objects. Attribute values are supplied
as tags with a mkgmap:xt- prefix. These tags are supported:

mkgmap:xt-depth
mkgmap:xt-height
  value is distance with optional units suffix (ft or m)
  applicable to points with types 0x0103xx and 0x0104xx and
  lines with types 0x0103xx and 0x010105-0x010107 and areas
  with types 0x0103xx.

mkgmap:xt-style
  value is 16 bit integer that specifies colour (lower 8 bits)
  and line style (upper 8 bits) - applicable to lines with types
  0x0104xx, 0x0105xx, 0x0106xx and points with type 0x010500 (colour
  only).

mkgmap:xt-colour
  value is one of:
	red
	green
	yellow
	white
	black
	black-yellow
	white-red
	black-red
	white-green
	red-yellow
	red-green
	orange
	black-yellow-black
	yellow-black
	yellow-black-yellow
	red-white
	green-red-green
	red-green-red
	black-red-black
	yellow-red-yellow
	green-red
	black-white
	white-orange
	orange-white
	green-white
  applicable to points with type 0x0102xx (buoys) and it specifies the
  foundation colour of the buoy.

mkgmap:xt-type
  value is one of:
	fixed
	isophase
	flashing
	group flashing
	composite group flashing
	occulting
	group occulting
	composite group occulting
	long flashing
	group long flashing
	morse (followed by a letter)
	quick
	group quick
	group quick and long flashing
	interrupted quick
	very quick
	group very quick
	group very quick and long flashing
	interrupted very quick
	ultra quick
	interrupted ultra quick
	fixed and occulting
	fixed and group occulting
	fixed and isophase
	fixed and flashing
	fixed and group flashing
	fixed and long flashing
	alternating
	alternating occulting
	alternating flashing
	alternating group flashing
  applicable to points with types 0x0101xx (lights) and 0x0102xx (buoys)
  and it specifies the type of light

mkgmap:xt-light
  one or more light definitions (separated by ;: or /) - each light
  definition is of the form colour,range,angle where colour is one
  of:
	unlit
	red
	green
	white
	blue
	yellow
	violet
	amber
  range is an optional number that specifies the visible range in nm -
  angle is the start angle for the light (only makes sense when more than
  one light is defined.

mkgmap:xt-period
  value is one or more period values (in seconds) (separated by commas) -
  applicable to point types 0x0101xx (lights) and 0x0102xx (buoys).

mkgmap:xt-racon
  when set to yes/true/1 specifies object has a racon - applicable to
  point types 0x0101xx (lights).

mkgmap:xt-height-above-foundation
mkgmap:xt-height-above-datum
  value is distance with optional units (m or ft) - applicable to
  point types 0x0101xx (lights).

mkgmap:xt-leading-angle
  value is a number - applicable to point types 0x0101xx (lights).

mkgmap:xt-note
mkgmap:xt-int-desig
mkgmap:xt-local-desig
  values are strings that are encoded as labels - applicable to
  points of type 0x01xx (lights) and 0x0102xx (buoys).

mkgmap:xt-facilities
  value is a bitmask of facilities available:
    0x000001 boat ramp
    0x000002 drinking water
    0x000004 restrooms
    0x000008 picnic area
    0x000010 campground
    0x000020 marina
    0x000040 fuel
    0x000080 marine supply
    0x000100 bait and tackle
    0x000200 groceries
    0x000400 restaurant
    0x000800 water/electric hook-up
    0x001000 boat/motor rental
    0x002000 guide service
    0x004000 lodging
    0x008000 dump station
    0x010000 handicap accessible
  applicable only to points of type 0x010903 (facility)
 */

public class ExtTypeAttributes {

	private static final Logger log = Logger.getLogger(ExtTypeAttributes.class);

	private DecimalFormat decimalFormat = new DecimalFormat();

	private Map<String, String> attributes;
	private String objectName;

	private byte[] extraBytes;

	private Label note;
	private Label intDesig;
	private Label localDesig;

	private Byte morseLetter;

	private final int DISTANCE_FLAG_METRIC_INDEX = 0;
	private final int DISTANCE_FLAG_TENTHS_INDEX = 1;

	private final byte FLAGS0_RACON_BIT       = (1 << 0);
	private final byte FLAGS0_NOTE_BIT        = (1 << 1);
	private final byte FLAGS0_INT_DESIG_BIT   = (1 << 2);
	private final byte FLAGS0_LOCAL_DESIG_BIT = (1 << 3);

	public ExtTypeAttributes(Map<String, String> attributes, String objectName) {
		this.attributes = attributes;
		this.objectName = objectName;
	}

	public void processLabels(LBLFile lbl) {
		if(note == null) {
			String ns = attributes.get("note");
			if(ns != null)
				note = lbl.newLabel(ns);
		}
		if(intDesig == null) {
			String ids = attributes.get("int-desig");
			if(ids != null)
				intDesig = lbl.newLabel(ids);
		}
		if(localDesig == null) {
			String lds = attributes.get("local-desig");
			if(lds != null)
				localDesig = lbl.newLabel(lds);
		}
	}

	protected byte[] getExtTypeExtraBytes(MapObject mapObject) {
		try {
			return encodeExtraBytes(mapObject);
		}
		catch (Exception e) {
			log.error(objectName + " (" + e + ")");
			return null;
		}
	}

	private byte[] encodeExtraBytes(MapObject mapObject) {

		// if we get called again, just return same result as before
		if(extraBytes != null)
			return extraBytes;

		// first see if a string of raw hex digits has been supplied
		// if so, use that and ignore everything else
		String eb = attributes.get("extra-bytes");
		if(eb != null) {
			extraBytes = new byte[(eb.length() + 1) / 2];
			for(int i = 0; i < eb.length(); ++i) {
				int d = Integer.parseInt(eb.substring(i, i + 1), 16);
				extraBytes[i / 2] |= (byte)(d << (4 * (1 - (i & 1))));
			}
			return extraBytes;
		}

		int type0to15 = mapObject.getType() & 0xffff;
		int type8to15 = type0to15 & 0xff00;

		if(mapObject instanceof Point) {

			Light lights[] = parseLights(attributes.get("light"));
			int[] periods = parsePeriods(attributes.get("period"));

			if(type8to15 == 0x0100) { // lights
				int nob = 6;
				byte flags0 = 0;
				byte flags1 = 0;
				int lightType = lightType("");
				if(meansYes(attributes.get("racon")))
					flags0 |= FLAGS0_RACON_BIT;
				if(note != null) {
					nob += 3;
					flags0 |= FLAGS0_NOTE_BIT;
				}
				if(intDesig != null) {
					nob += 3;
					flags0 |= FLAGS0_INT_DESIG_BIT;
				}
				if(localDesig != null) {
					nob += 3;
					flags0 |= FLAGS0_LOCAL_DESIG_BIT;
				}
				if(lights.length > 1) {
					for(Light l : lights)
						nob += (l.colour != 0)? 3 : 2;
					flags1 |= 0x08; // multiple lights
				}
				if(periods.length > 1) {
					for(int p : periods) {
						while(p > 0x3f) {
							++nob;
							p -= 0x3f;
						}
						++nob;
					}
					flags1 |= 0x01; // further record present?
				}
				else if(morseLetter != null)
					flags1 |= 0x01; // further record present?
				byte lightsDef = 0x22;
				String hafs = attributes.get("height-above-foundation");
				boolean[] hafsDistFlags = new boolean[2];
				Integer hafi = null;
				if(hafs != null) {
					hafi = parseDistance(hafs, hafsDistFlags);
					if(hafsDistFlags[DISTANCE_FLAG_TENTHS_INDEX])
						hafi /= 10;
					nob += (hafi > 255)? 2 : 1;
					if(hafi > 255)
						lightsDef |= 0x80;
					else
						lightsDef |= 0x40;
					if(!hafsDistFlags[DISTANCE_FLAG_METRIC_INDEX])
						lightsDef &= ~0x20;
				}
				String hads = attributes.get("height-above-datum");
				boolean[] hadsDistFlags = new boolean[2];
				Integer hadi = null;
				if(hads != null) {
					hadi = parseDistance(hads, hadsDistFlags);
					if(hadsDistFlags[DISTANCE_FLAG_TENTHS_INDEX])
						hadi /= 10;
					nob += (hadi > 255)? 2 : 1;
					if(hadi > 255)
						lightsDef |= 0x08;
					else
						lightsDef |= 0x04;
					if(!hadsDistFlags[DISTANCE_FLAG_METRIC_INDEX])
						lightsDef &= ~0x02;
				}
				String las = attributes.get("leading-angle");
			    Integer leadingAngle = null;
				if(las != null) {
					leadingAngle = (int)(Double.parseDouble(las.trim()) * 10);
					nob += 2;
					flags1 |= 0x02; // leading angle present
				}
				extraBytes = new byte[nob + 2];
				int i = 0;
				extraBytes[i++] = (byte)(0xe0 | flags0);
				extraBytes[i++] = (byte)((nob << 1) | 1); // bit0 always set?
				extraBytes[i++] = (byte)(0x80 | lightType);
				extraBytes[i++] = flags1;
				extraBytes[i++] = lightsDef;
				if(hafi != null) {
					extraBytes[i++] = (byte)(int)hafi;
					if(hafi > 255)
						extraBytes[i++] = (byte)(hafi >> 8);
				}
				if(hadi != null) {
					extraBytes[i++] = (byte)(int)hadi;
					if(hadi > 255)
						extraBytes[i++] = (byte)(hadi >> 8);
				}
				int period = 0;
				for(int p : periods)
					period += p;
				extraBytes[i++] = (byte)period;
				if(note != null) {
					int off = note.getOffset();
					extraBytes[i++] = (byte)off;
					extraBytes[i++] = (byte)(off >> 8);
					extraBytes[i++] = (byte)(off >> 16);
				}
				if(localDesig != null) {
					int off = localDesig.getOffset();
					extraBytes[i++] = (byte)off;
					extraBytes[i++] = (byte)(off >> 8);
					extraBytes[i++] = (byte)(off >> 16);
				}
				if(intDesig != null) {
					int off = intDesig.getOffset();
					extraBytes[i++] = (byte)off;
					extraBytes[i++] = (byte)(off >> 8);
					extraBytes[i++] = (byte)(off >> 16);
				}
				if(leadingAngle != null) {
					extraBytes[i++] = (byte)(int)leadingAngle;
					extraBytes[i++] = (byte)(leadingAngle >> 8);
				}
				if(lights.length > 1) {
					for(int l = 0; l < lights.length; ++l) {
						int val = (lights[l].colour << 12) | (int)(lights[l].angle * 10);
						if((l + 1) == lights.length)
							val |= 0x8000;
						extraBytes[i++] = (byte)val;
						extraBytes[i++] = (byte)(val >> 8);
						if(lights[l].colour != 0)
							extraBytes[i++] = (byte)lights[l].range;
					}
				}
				else {
					int lc = 0;
					int lr = 0;
					if(lights.length > 0) {
						lc = lights[0].colour;
						lr = (int)lights[0].range & 0x1f;
					}
					extraBytes[i++] = (byte)((lc << 5) | lr);
				}
				if(periods.length > 1) {
					if(periods.length > 2)
						extraBytes[i++] = (byte)0x82;
					else
						extraBytes[i++] = (byte)0x81;
					for(int p : periods) {
						while(p > 0x3f) {
							extraBytes[i++] = (byte)0x3f;
							p -= 0x3f;
						}
						extraBytes[i++] = (byte)p;
					}
				}
				else if(morseLetter != null)
					extraBytes[i++] = morseLetter;
				else
					extraBytes[i++] = 0x01; // terminator?

				return extraBytes;
			}
			else if(type8to15 == 0x0200) { // buoys
				int nob = 4;
				byte flags0 = 0;
				byte flags1 = 0;
				int lt = lightType("");
				if(meansYes(attributes.get("racon"))) {
					// this doesn't get reported on mapsource
					// maybe racons aren't supported for buoys?
					flags0 |= FLAGS0_RACON_BIT;
				}
				if(note != null) {
					nob += 3;
					flags0 |= FLAGS0_NOTE_BIT;
				}
				if(intDesig != null) {
					nob += 3;
					flags0 |= FLAGS0_INT_DESIG_BIT;
				}
				if(localDesig != null) {
					nob += 3;
					flags0 |= FLAGS0_LOCAL_DESIG_BIT;
				}
				if(periods.length > 0)
					++nob;		// for total period
				if(periods.length > 1) {
					for(int p : periods) {
						while(p > 0x3f) {
							++nob;
							p -= 0x3f;
						}
						++nob;
					}
					flags1 |= 0x02; // further record present?
				}
				else if(morseLetter != null)
					flags1 |= 0x02; // further record present?
				extraBytes = new byte[nob + 2];
				int i = 0;
				extraBytes[i++] = (byte)(0xe0 | flags0);
				extraBytes[i++] = (byte)((nob << 1) | 1); // bit0 always set?
				int lc = 0;
				if(lights.length > 0) {
					lc = lights[0].colour;
				}
				extraBytes[i++] = (byte)((lc << 6) | colour(""));
				flags1 |= (byte)((lc >> 2) & 1); // bit 0 is MSB of light colour
				extraBytes[i++] = flags1;
				if(note != null) {
					int off = note.getOffset();
					extraBytes[i++] = (byte)off;
					extraBytes[i++] = (byte)(off >> 8);
					extraBytes[i++] = (byte)(off >> 16);
				}
				if(localDesig != null) {
					int off = localDesig.getOffset();
					extraBytes[i++] = (byte)off;
					extraBytes[i++] = (byte)(off >> 8);
					extraBytes[i++] = (byte)(off >> 16);
				}
				if(intDesig != null) {
					int off = intDesig.getOffset();
					extraBytes[i++] = (byte)off;
					extraBytes[i++] = (byte)(off >> 8);
					extraBytes[i++] = (byte)(off >> 16);
				}
				byte flags2 = 0;
				if(periods.length > 0)
					flags2 |= (byte)0x80;
				extraBytes[i++] = (byte)(flags2 | lt);
				if(periods.length > 0) {
					int period = 0;
					for(int p : periods)
						period += p;
					extraBytes[i++] = (byte)period;
					if(periods.length > 1) {
						if(periods.length > 2)
							extraBytes[i++] = (byte)0x82;
						else
							extraBytes[i++] = (byte)0x81;
						for(int p : periods) {
							while(p > 0x3f) {
								extraBytes[i++] = (byte)0x3f;
								p -= 0x3f;
							}
							extraBytes[i++] = (byte)p;
						}
					}
					else
						extraBytes[i++] = 0x01; // terminator?
				}
				else if(morseLetter != null)
					extraBytes[i++] = morseLetter;
				else
					extraBytes[i++] = 0x01; // terminator?

				return extraBytes;
			}
			else if(type8to15 == 0x0300 || // things with depth/height
					type8to15 == 0x0400) { // obstructions
				String ds = attributes.get("depth");
				if(ds == null)
					ds = attributes.get("height");
				if(ds != null) {
					boolean[] distFlags = new boolean[2];
					Integer di = parseDistance(ds, distFlags);
					if(di != null) {
						if(di > 255) {
							extraBytes = new byte[3];
							extraBytes[0] = (byte)0xa0;
						}
						else {
							extraBytes = new byte[2];
							extraBytes[0] = (byte)0x80;
						}
						if(distFlags[DISTANCE_FLAG_METRIC_INDEX])
							extraBytes[0] |= 0x10;
						if(distFlags[DISTANCE_FLAG_TENTHS_INDEX])
							extraBytes[0] |= 0x08;
						if(type8to15 == 0x0400) { // obstructions
							extraBytes[0] |= position();
						}
						extraBytes[1] = (byte)(int)di;
						if(di > 255)
							extraBytes[2] = (byte)(di >> 8);

						return extraBytes;
					}
				}
			}
			else if(type8to15 == 0x0500) { // label
				String ss = attributes.get("style");
				if(ss != null) {
					int style = Integer.decode(ss.trim());
					// format is 0xCC (CC = colour)
					extraBytes = new byte[1];
					extraBytes[0] = (byte)(style & 0xf);
					return extraBytes;
				}
			}
			else if(type0to15 == 0x0903) { // facility
				String fs = attributes.get("facilities");
				if(fs != null) {
					int facilities = Integer.decode(fs.trim());
					extraBytes = new byte[3];
					extraBytes[0] = (byte)(0xa0 | (facilities & 0x1f));
					extraBytes[1] = (byte)(facilities >> 5);
					extraBytes[2] = (byte)(((facilities >> 13) & 0x07) |
										   ((facilities >> 12) & 0x18));
					return extraBytes;
				}
			}
		}
		else if(mapObject instanceof Polyline) {

			if(type8to15 == 0x0300 ||	// depth areas
			   (!(mapObject instanceof Polygon) &&
				(type0to15 == 0x0105 || // contour line
				 type0to15 == 0x0106 || // overhead cable
				 type0to15 == 0x0107))) { // bridge
				String ds = attributes.get("depth");
				if(ds == null)
					ds = attributes.get("height");
				if(ds != null) {
					boolean[] distFlags = new boolean[2];
					Integer di = parseDistance(ds, distFlags);
					if(di != null) {
						if(di > 255) {
							extraBytes = new byte[3];
							extraBytes[0] = (byte)0xa0;
						}
						else {
							extraBytes = new byte[2];
							extraBytes[0] = (byte)0x80;
						}
						if(distFlags[DISTANCE_FLAG_METRIC_INDEX])
							extraBytes[0] |= 0x10;
						if(distFlags[DISTANCE_FLAG_TENTHS_INDEX])
							extraBytes[0] |= 0x08;
						if(type8to15 == 0x04) { // obstructions
							extraBytes[0] |= position();
						}
						extraBytes[1] = (byte)(int)di;
						if(di > 255)
							extraBytes[2] = (byte)(di >> 8);

						return extraBytes;
					}
				}
			}
			else if(!(mapObject instanceof Polygon) &&
					(type8to15 == 0x0400 || // various lines
					 type8to15 == 0x0500 ||
					 type8to15 == 0x0600)) {
				String ss = attributes.get("style");
				if(ss != null) {
					int style = Integer.decode(ss.trim());
					if((style & 0xff00) != 0) {
						// format is 0xSSCC (SS = style, CC = colour)
						extraBytes = new byte[2];
						extraBytes[0] = (byte)(0x80 | (style & 0xf));
						extraBytes[1] = (byte)(((style >> 9) & 0x18) | ((style >> 8) & 0x3));
					}
					else {
						// format is 0xCC (CC = colour)
						extraBytes = new byte[1];
						extraBytes[0] = (byte)(style & 0xf);
					}
					return extraBytes;
				}
			}
		}

		return null;
	}

	private boolean meansYes(String s) {
		if(s == null)
			return false;
		s = s.toLowerCase();
		return ("yes".startsWith(s) || "true".startsWith(s) || "1".equals(s));
	}

	private Integer parseDistance(String ds, boolean[] flags) {
		ParsePosition pp = new ParsePosition(0);
		Number dn = decimalFormat.parse(ds, pp);
		if(dn != null) {
			double dd = dn.doubleValue();
			int di = dn.intValue();
			flags[DISTANCE_FLAG_METRIC_INDEX] = true;
			flags[DISTANCE_FLAG_TENTHS_INDEX] = false;
			if("ft".equals(ds.substring(pp.getIndex()).trim().toLowerCase()))
				flags[DISTANCE_FLAG_METRIC_INDEX] = false;
			if((double)di != dd) {
				// number has fractional part
				di = (int)(dd * 10);
				flags[DISTANCE_FLAG_TENTHS_INDEX] = true;
			}

			return di;
		}

		return null;
	}

	private int colour(String prefix) {

		String c = attributes.get(prefix + "colour");
		if(c == null)
			c = attributes.get(prefix + "color");
		if(c == null)
			return 0;

		String[] colours = {
			"",
			"red",
			"green",
			"yellow",
			"white",
			"black",
			"black-yellow",
			"white-red",
			"black-red",
			"white-green",
			"red-yellow",
			"red-green",
			"orange",
			"black-yellow-black",
			"yellow-black",
			"yellow-black-yellow",
			"red-white",
			"green-red-green",
			"red-green-red",
			"black-red-black",
			"yellow-red-yellow",
			"green-red",
			"black-white",
			"white-orange",
			"orange-white",
			"green-white"
		};

		c = c.toLowerCase();

		for(int i = 0; i < colours.length; ++i)
			if(colours[i].equals(c))
				return i;

		return 0;
	}

	private int lightType(String prefix) {
		String lt = attributes.get(prefix + "type");
		if(lt == null)
			return 0;

		String[] types = {
			"",
			"fixed",
			"isophase",
			"flashing",
			"group flashing",
			"composite group flashing",
			"occulting",
			"group occulting",
			"composite group occulting",
			"long flashing",
			"group long flashing",
			"morse",
			"quick",
			"group quick",
			"group quick and long flashing",
			"interrupted quick",
			"very quick",
			"group very quick",
			"group very quick and long flashing",
			"interrupted very quick",
			"ultra quick",
			"interrupted ultra quick",
			"fixed and occulting",
			"fixed and group occulting",
			"fixed and isophase",
			"fixed and flashing",
			"fixed and group flashing",
			"fixed and long flashing",
			"alternating",
			"alternating occulting",
			"alternating flashing",
			"alternating group flashing"
		};

		if(lt.toLowerCase().startsWith("morse")) {
			String ml = lt.substring(5).trim();
			if(ml.length() > 0) {
				morseLetter = (byte)ml.charAt(0);
				lt = "morse";
			}
		}

		lt = lt.toLowerCase();

		for(int i = 0; i < types.length; ++i)
			if(types[i].equals(lt))
				return i;

		return 0;
	}

	private int position() {
		String ps = attributes.get("position");
		if(ps == null)
			return 0;

		String[] positions = {
			"unknown",
			"",
			"doubtful",
			"existence doubtful",
			"approximate",
			"reported"
		};

		ps = ps.toLowerCase();

		for(int i = 0; i < positions.length; ++i)
			if(positions[i].equals(ps))
				return i;

		return 0;
	}

	private int[] parsePeriods(String ps) {
		if(ps == null)
			return new int[0];
		String [] psa = ps.split(",");
		int [] periods = new int[psa.length];
		for(int i = 0; i < psa.length; ++i)
			periods[i] = (int)(Double.parseDouble(psa[i].trim()) * 10);
		return periods;
	}

	private Light[] parseLights(String ls) {
		if(ls == null)
			return new Light[0];
		String[] defs = ls.split("[:;/]");
		Light[] lights = new Light[defs.length];
		for(int i = 0; i < defs.length; ++i) {
			String def = defs[i].trim();
			if(def.length() > 0)
				lights[i] = new Light(def);
		}
		return lights;
	}

	class Light {
		private int    colour;
		private double range;
		private double angle;

		String[] colours = {
			"unlit",
			"red",
			"green",
			"white",
			"blue",
			"yellow",
			"violet",
			"amber"
		};

		public Light(String desc) {
			String[] parts = desc.split(",");
			if(parts.length > 0) {
				String lc = parts[0].toLowerCase();
				for(int i = 0; i < colours.length; ++i) {
					if(colours[i].equals(lc)) {
						colour = i;
						break;
					}
				}
			}
			if(parts.length > 1 && colour != 0)
				range = Double.parseDouble(parts[1]);
			if(parts.length > 2)
				angle = Double.parseDouble(parts[2]);

			//System.err.println("light = " + this);
		}

		public String toString() {
			return "(" + colours[colour] + "," + range + "," + angle + ")";
		}
	}
}
