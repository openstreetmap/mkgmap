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

package uk.me.parabola.mkgmap.osmstyle;

import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.trergn.MapObject;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Way;
import static uk.me.parabola.imgfmt.app.net.AccessTagsAndBits.*;

/**
 * Class that is used to connect an OSM way with the attributes of GType
 * after Style processing.
 * 
 * @author GerdP
 *
 */
public class ConvertedWay {
	private static final Logger log = Logger.getLogger(ConvertedWay.class);
	private final int index;
	private final Way way;				// with tags after Style processing
	private final GType gt;

	private byte roadClass;			// 0-4
	private byte roadSpeed;			// 0-7
	private byte mkgmapAccess; 		// bit mask, see ACCESS_TAGS 
	private final byte routeFlags;	// bit mask, see ROUTING_TAGS
	private boolean isRoad;
	private boolean reversed;		// points were reversed
	private boolean overlay;		// this is a non-routable overlay line that for a road 
	

	public ConvertedWay(int index, Way way, GType type) {
		this.index = index;
		this.way = way;
		this.gt = type;
		// note that the gt.getType() may not be a routable type when overlays are used
		if (type.isRoad() && MapObject.hasExtendedType(gt.getType()) == false) {
			this.roadClass = (byte) gt.getRoadClass();
			this.roadSpeed = (byte) gt.getRoadSpeed();
			recalcRoadClass(way);
			recalcRoadSpeed(way);
			mkgmapAccess = evalAccessTags(way);
			routeFlags = evalRouteTags(way);
			isRoad = true;
		} else {
			roadClass = 0;
			roadSpeed = 0;
			mkgmapAccess = 0;
			routeFlags = 0;
			isRoad = false;
		}
	}
	
	public ConvertedWay(ConvertedWay other, Way way){
		this.way = way;
		// copy all other attributes
		this.index = other.index;
		this.gt = other.gt;
		this.roadClass = other.roadClass;
		this.roadSpeed = other.roadSpeed;
		this.mkgmapAccess = other.mkgmapAccess;
		this.routeFlags = other.routeFlags;
	}
	
	public int getIndex(){
		return index;
	}
	
	public GType getType(){
		return gt;
	}

	public Way getWay() {
		return way;
	}
	
	public byte getAccess(){
		return mkgmapAccess;
	}
	
	public byte getRoadClass(){
		return roadClass;
	}

	public byte getRoadSpeed(){
		return roadSpeed;
	}
	
	public byte getRouteFlags(){
		return routeFlags;
	}
	

	/**
	 * Recalculates the road class based on the tags
	 * <ul>
	 * <li>{@code mkgmap:road-class}</li>
	 * <li>{@code mkgmap:road-class-min}</li>
	 * <li>{@code mkgmap:road-class-max}</li>
	 * </ul>
	 * The road class is changed if the tags modify its road class. 
	 * 
	 * @param el an element 
	 * @return {@code true} the road class has been changed, else {@code false} 
	 */
	
	public boolean recalcRoadClass(Element el) {
		// save the original road class value
		byte oldRoadClass = roadClass;
		
		String val = el.getTag("mkgmap:road-class");
		if (val != null) {
			if (val.startsWith("-")) {
				roadClass -= Byte.decode(val.substring(1));
			} else if (val.startsWith("+")) {
				roadClass += Byte.decode(val.substring(1));
			} else {
				roadClass = Byte.decode(val);
			}
			val = el.getTag("mkgmap:road-class-max");
			byte roadClassMax = 4;
			if (val != null)
				roadClassMax = Byte.decode(val);
			val = el.getTag("mkgmap:road-class-min");

			byte roadClassMin = 0;
			if (val != null)
				roadClassMin = Byte.decode(val);
			if (roadClass > roadClassMax)
				roadClass = roadClassMax;
			else if (roadClass < roadClassMin)
				roadClass = roadClassMin;

		}
		return (roadClass != oldRoadClass);
	}
	
	/**
	 * Recalculates the road speed 
	 * <ul>
	 * <li>{@code mkgmap:road-speed-class}</li>
	 * <li>{@code mkgmap:road-speed}</li>
	 * <li>{@code mkgmap:road-speed-min}</li>
	 * <li>{@code mkgmap:road-speed-max}</li>
	 * </ul>
	 * 
	 * @param el an element 
	 * @return {@code true} the road speed has been changed, else {@code false} 
	 */
	public boolean recalcRoadSpeed(Element el) {
		// save the original road speed value
		byte oldRoadSpeed = roadSpeed;
		
		// check if the road speed is modified
		String roadSpeedOverride = el.getTag("mkgmap:road-speed-class");
		if (roadSpeedOverride != null) {
			try {
				byte rs = Byte.decode(roadSpeedOverride);
				if (rs >= 0 && rs <= 7) {
					// override the road speed class
					roadSpeed = rs;
				} else {
					log.error(el.getDebugName()
							+ " road classification mkgmap:road-speed-class="
							+ roadSpeedOverride + " must be in [0;7]");
				}
			} catch (Exception exp) {
				log.error(el.getDebugName()
						+ " road classification mkgmap:road-speed-class="
						+ roadSpeedOverride + " must be in [0;7]");
			}
		}
		
		// check if the road speed should be modified more
		String val = el.getTag("mkgmap:road-speed");
		if(val != null) {
			if(val.startsWith("-")) {
				roadSpeed -= Byte.decode(val.substring(1));
			}
			else if(val.startsWith("+")) {
				roadSpeed += Byte.decode(val.substring(1));
			}
			else {
				roadSpeed = Byte.decode(val);
			}
			val = el.getTag("mkgmap:road-speed-max");
			byte roadSpeedMax = 7;
			if(val != null)
				roadSpeedMax = Byte.decode(val);
			val = el.getTag("mkgmap:road-speed-min");

			byte roadSpeedMin = 0;
			if(val != null)
				roadSpeedMin = Byte.decode(val);
			if(roadSpeed > roadSpeedMax)
				roadSpeed = roadSpeedMax;
			else if(roadSpeed < roadSpeedMin)
				roadSpeed = roadSpeedMin;
		}
		return (oldRoadSpeed != roadSpeed);
	}

	public List<Coord> getPoints(){
		return way.getPoints();
	}

	public boolean isValid() {
		if (way == null)
			return false;
		if (way.getPoints() == null || way.getPoints().size()<2)
			return false;
		return true;
	}
	
	public String toString(){
		return getType() + " " + getWay().getId() + " " + getWay().toTagString();

	}
	
	public boolean isOneway(){
		return (routeFlags & R_ONEWAY) != 0;
	}

	public boolean isRoundabout(){
		return (routeFlags & R_ROUNDABOUT) != 0;
	}
	public boolean isToll(){
		return (routeFlags & R_TOLL) != 0; 
	}

	public boolean isUnpaved(){
		return (routeFlags & R_UNPAVED) != 0; 
	}

	public boolean isFerry(){
		return (routeFlags & R_FERRY) != 0; 
	}

	public boolean isCarpool(){
		return (routeFlags & R_CARPOOL) != 0; 
	}

	public boolean isThroughroute(){
		return (routeFlags & R_THROUGHROUTE) != 0; 
	}
	
	public boolean isRoad(){
		return isRoad;
	}

	public boolean isReversed() {
		return reversed;
	}

	public void setReversed(boolean reversed) {
		this.reversed = reversed;
	}

	public void setOverlay(boolean b) {
		this.overlay = b;
	}

	public boolean isOverlay() {
		return overlay;
	}
}
