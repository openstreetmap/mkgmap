/*
 * Copyright (C) 2015
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Find sharp angles at junctions. The Garmin routing algorithm doesn't
 * like to route on roads building a sharp angle. It adds a time penalty
 * from 30 to 150 seconds and often prefers small detours instead.
 * The penalty depends on the road speed and the vehicle, for pedestrian
 * mode it is zero, for bicycles it is rather small, for cars it is high.
 * The sharp angles typically don't exist in the real world, they are
 * caused by the simplifications done by mappers.
 * 
 * Maps created for cyclists typically "abuse" the car routing for racing 
 * bikes, but in this scenario the time penalties are much too high,
 * and detours are likely.
 * 
 * This method tries to modify the initial heading values of the arcs 
 * which are used to calculate the angles. Where possible, the values are
 * changed so that angles appear larger. 
 * 
 * @author Gerd Petermann
 *
 */
public class AngleChecker {
	private static final Logger log = Logger.getLogger(AngleChecker.class);

	private boolean ignoreSharpAngles;
	private boolean cycleMap;
//	private final Coord test = new Coord(48.074815,16.272771);

	private final int MIN_ANGLE = 0x10;
	private final int MIN_LOW_SPEED_ANGLE = 0x20;

	private int mask;
	private int mrnd;

	// helper class to collect multiple arcs with (nearly) the same initial headings
	private class ArcGroup {
		float initialHeading;
		byte imgHeading;
		int isOneWayTrueCount;
		int isForwardTrueCount;
		int maxRoadSpeed;
		byte orAccessMask;
		HashSet<RoadDef> roadDefs = new HashSet<>();
		
		List<RouteArc> arcs = new ArrayList<>();
		public void addArc(RouteArc arc) {
			arcs.add(arc);
			if (arc.getRoadDef().isOneway())
				isOneWayTrueCount++;
			if (arc.isForward())
				isForwardTrueCount++;
			if (arc.getRoadDef().getRoadSpeed() > maxRoadSpeed)
				maxRoadSpeed = arc.getRoadDef().getRoadSpeed();
			orAccessMask |= arc.getRoadDef().getAccess();
			roadDefs.add(arc.getRoadDef());
		}
		public float getInitialHeading() {
			return initialHeading;
		}
		public boolean isOneway() {
			return isOneWayTrueCount == arcs.size();
		}
		public boolean isForward() {
			return isForwardTrueCount == arcs.size();
		}
		/**
		 * @return
		 */
		public void setInitialHeading(float modIH) {
			while (modIH > 180)
				modIH -= 360; 
			while (modIH < -180)
				modIH += 360; 
			initialHeading = modIH;
			imgHeading = calcEncodedBearing(initialHeading); 
			
			for (RouteArc arc : arcs){
				arc.setInitialHeading(modIH);
			}
		}
		
		public String toString(){
			return arcs.get(0).toString();
		}
	}
	
	
	private byte calcEncodedBearing (float b) {
		return (byte) ((RouteArc.directionFromDegrees(b) + mrnd) & mask);
	}
	
	public void config(EnhancedProperties props) {
		// undocumented option - usually used for debugging only
		ignoreSharpAngles = props.getProperty("ignore-sharp-angles", false);
		cycleMap = props.getProperty("cycle-map", false);
//		float a = 0;
//		for (int i = 0; i <= 1440; i++){
//			int ar = (int) Math.round(a * 256.0 / 360);
//			int am = ar & 0xf0;
//			log.error(a,ar,"0x" + Integer.toHexString(am));
//			a +=0.25;
//			if (a >= 180)
//				a -= 360;
//		}
		return;
	}

	public void check(Map<Integer, RouteNode> nodes) {
		if (!ignoreSharpAngles){
			byte sharpAnglesCheckMask = cycleMap ? (byte) (0xff & ~AccessTagsAndBits.FOOT) : AccessTagsAndBits.BIKE;

			for (RouteNode node : nodes.values()){
				mask = 0xf0; // we assume compacted format
				mrnd = 0x08; // rounding
				fixSharpAngles(node, sharpAnglesCheckMask);				
			}
		}
	}

	public void fixSharpAngles(RouteNode node, byte sharpAnglesCheckMask) {

		// get direct arcs leaving the node
		List<ArcGroup> arcGroups = buildArcGroups(node);

		int n = arcGroups.size();
		if (n <= 1)
			return;
		// sort the arcs by initial heading 
		Collections.sort(arcGroups, new Comparator<ArcGroup>() {
				public int compare(ArcGroup ag1, ArcGroup ag2) {
					if (ag1.initialHeading < ag2.initialHeading)
						return -1;
					if (ag1.initialHeading > ag2.initialHeading)
						return 1;
					return 0;
				}
			});
		
		class AngleAttr {
			int angle;
			int maskedAngle;
			int maskedMinAngle = MIN_ANGLE;
			boolean noAccess;
			
			int maskedDeltaToMin(){
				return maskedAngle - maskedMinAngle;
			}
			void setMaskedMinAngle(int maskedMinAngle){
				this.maskedMinAngle = maskedMinAngle;
			}
			
			public String toString(){
				return angle + "° " + maskedAngle + " " + maskedMinAngle + " " + noAccess;
			}
		}
		
		// step one: calculate the existing angles
		AngleAttr[] angles = new AngleAttr[n];
		for (int i = 0; i < n; i++){
			ArcGroup ag1 = arcGroups.get(i);
			ArcGroup ag2 = arcGroups.get(i+1 < n ? i+1 : 0);
			AngleAttr angleAttr = new AngleAttr(); 
			angles[i] = angleAttr;
			angleAttr.angle = Math.round(ag2.getInitialHeading() - ag1.getInitialHeading());
			angleAttr.maskedAngle = ag2.imgHeading - ag1.imgHeading;
			if (i + 1 >= n){
				angleAttr.angle += 360;
			}
			if (angleAttr.maskedAngle < 0)
				angleAttr.maskedAngle += 256;

			if (ag1.isOneway() && ag1.isForward()){
				// the "incoming" arc is a wrong direction oneway
				angleAttr.noAccess = true;
			} else if (ag2.isOneway() && ag2.isForward() == false){
				// the "outgoing" arc is a wrong direction oneway
				angleAttr.noAccess = true;
			}
			
//			if (node.getCoord().distance(test) < 2){
//				if (angleAttr.angle == 20){
//					angleAttr.maskedMinAngle = 0x30;
//					continue;
//				}
//			}
			int sumSpeeds = ag1.maxRoadSpeed + ag2.maxRoadSpeed;
			if (sumSpeeds <= 1)
				continue;
			byte pathAccessMask = (byte) (ag1.orAccessMask & ag2.orAccessMask);
			if (pathAccessMask == 0){
				// no common vehicle allowed on both arcs
				angleAttr.noAccess = true;
			}
			if (angleAttr.noAccess)
				continue;
			int maskedMinAngle = MIN_LOW_SPEED_ANGLE;
			// the Garmin algorithm sees rounded values, so the thresholds are probably 
			// near 22.5 (0x10), 45(0x20), 67.5 (0x30), 90, 112.5 (0x40)

			// the following code doesn't seem to improve anything, I leave it as comment 
			// for further experiments.
//			if (cycleMap){
//				if (sumSpeeds >= 14)
//					maskedMinAngle = 0x80;
//				if (sumSpeeds >= 12)
//					maskedMinAngle = 0x70;
//				if (sumSpeeds >= 10)
//					maskedMinAngle = 0x60;
//				if (sumSpeeds >= 8)
//					maskedMinAngle = 0x50;
//				else if (sumSpeeds >= 6)
//					maskedMinAngle = 0x40;
//				else if (sumSpeeds >= 4)
//					maskedMinAngle = 0x30;
//			}
			angleAttr.setMaskedMinAngle(maskedMinAngle);
			
			if (angleAttr.maskedDeltaToMin() >= 0)
				continue;

			String ignoredReason = null;
			if (pathAccessMask == AccessTagsAndBits.FOOT)
				ignoredReason = "because it can only be used by pedestrians";
			else if ((pathAccessMask & sharpAnglesCheckMask) == 0)
				ignoredReason = "because it can not be used by bike";
			else if (ag1.isOneway() && ag2.isOneway()){
				// both arcs are one-ways, probably the road splits here 
				// to avoid the sharp angles we are looking for
				ignoredReason = "because it seems to be a flare road";
			}		
			else if (ag1.roadDefs.size() == 1 && ag2.roadDefs.size() == 1 && ag1.roadDefs.containsAll(ag2.roadDefs)){
				ignoredReason = "because both arcs belong to the same road";
			}
			if (ignoredReason != null){
				if (log.isInfoEnabled()){
					String sharpAngle = "sharp angle "  + angleAttr.angle + "° at " + node.getCoord().toDegreeString();
					log.info(sharpAngle, "headings",getCompassBearing(ag1.getInitialHeading()) , getCompassBearing(ag2.getInitialHeading()),"speeds",ag1.maxRoadSpeed, ag2.maxRoadSpeed);
					log.info("ignoring", sharpAngle, ignoredReason);
				}
				angleAttr.setMaskedMinAngle(MIN_ANGLE);
				angleAttr.noAccess = true;
			}
		}
		
		for (int i = 0; i < n; i++){
			AngleAttr aa = angles[i];
			if (aa.maskedAngle >= aa.maskedMinAngle || aa.noAccess)
				continue;
			int oldAngle = aa.angle;
			ArcGroup ag1 = arcGroups.get(i);
			ArcGroup ag2 = arcGroups.get(i+1 < n ? i+1 : 0);
			String sharpAngle = "";
			if (log.isInfoEnabled()){
				sharpAngle = "sharp angle "  + aa.angle + "° at " + node.getCoord().toDegreeString();
				log.info(sharpAngle, "headings",getCompassBearing(ag1.getInitialHeading()) , getCompassBearing(ag2.getInitialHeading()),"speeds",ag1.maxRoadSpeed, ag2.maxRoadSpeed);
			}

			// XXX restrictions ?
			boolean fixed = false;
			int wantedIncrement = Math.abs(aa.maskedDeltaToMin()) ;
			AngleAttr predAA = angles[i == 0 ? n - 1 : i - 1]; 
			AngleAttr nextAA = angles[i >= n - 1 ? 0 : i + 1];

			// we can increase the angle by changing the heading values of one or both arcs
			// find out which one to change first
			byte origImgDir1 = ag1.imgHeading;
			byte origImgDir2 = ag2.imgHeading;
			int origImgAngle = getImgAngle(ag1.imgHeading, ag2.imgHeading);

			int deltaPred = predAA.maskedDeltaToMin();
			int deltaNext = nextAA.maskedDeltaToMin();

			if (deltaNext > 0 && (deltaNext > deltaPred || deltaPred < wantedIncrement)){
				int usedIncrement = Math.min(wantedIncrement, deltaNext);
				float oldIH = ag2.getInitialHeading();
				int modIH = ag2.imgHeading + usedIncrement;
				if (modIH > 128)
					modIH -= 256;
				ag2.setInitialHeading(modIH * 360/256);
				int modAngle = Math.round(ag2.getInitialHeading() - ag1.getInitialHeading());
				if (modAngle < 0)
					modAngle += 360;				
				int modImgAngle = getImgAngle(ag1.imgHeading, ag2.imgHeading);
				if (modImgAngle >= aa.maskedMinAngle)
					fixed = true;
				log.info(sharpAngle, "changing arc with heading", getCompassBearing(oldIH), "->",getCompassBearing(ag2.getInitialHeading()), 
						"angle is now",modAngle+"°, in img format:",origImgDir2,"->",ag2.imgHeading, "img angle (0-255)",origImgAngle, "->", modImgAngle);
				aa.angle = modAngle;
				nextAA.angle -= usedIncrement;
			}
			if (!fixed && deltaPred > 0){
				wantedIncrement = Math.abs(aa.maskedDeltaToMin());
				int usedIncrement = Math.min(wantedIncrement, deltaPred);
				float oldIH = ag1.getInitialHeading();
				int modIH = ag1.imgHeading - usedIncrement;
				if (modIH < -128)
					modIH += 256;
				ag1.setInitialHeading(modIH * 360/256);
				int modAngle = Math.round(ag2.getInitialHeading() - ag1.getInitialHeading()); 
				if (modAngle < 0)
					modAngle += 360;				
				int modImgAngle = getImgAngle(ag1.imgHeading, ag2.imgHeading);
				if (modImgAngle >= aa.maskedMinAngle)
					fixed = true;
				
				log.info(sharpAngle, "changing arc with heading", getCompassBearing(oldIH), "->", getCompassBearing(ag1.getInitialHeading()), 
						"angle is now",modAngle+"°, in img format:",origImgDir1,"->",ag1.imgHeading, "img angle (0-255)",origImgAngle, "->", modImgAngle);
				aa.angle = modAngle;
				predAA.angle -= usedIncrement;
			}
			if (!fixed){
				if (aa.angle == oldAngle)
					log.info(sharpAngle, "don't know how to fix it");
				else 
					log.info(sharpAngle, "don't know how to enlarge it further");
			}
		}
		return;
	}


	/**
	 * Combine arcs with nearly the same initial heading.
	 * @param node
	 * @return
	 */
	private List<ArcGroup> buildArcGroups(RouteNode node) {
		List<ArcGroup> arcGroups = new ArrayList<>();
		List<RouteArc> directArcs = new ArrayList<>();
		for (RouteArc arc : node.getArcs()){
			if (arc.isDirect()){
				directArcs.add(arc);
			}
		}
		if (directArcs.size() < 2)
			return arcGroups; // should not happen
		
		// sort the arcs by initial heading 
		Collections.sort(directArcs, new Comparator<RouteArc>() {
				public int compare(RouteArc ra1, RouteArc ra2) {
					if (ra1.getInitialHeading() < ra2.getInitialHeading())
						return -1;
					if (ra1.getInitialHeading() > ra2.getInitialHeading())
						return 1;
					int d = Integer.compare(ra1.getPointsHash(), ra2.getPointsHash());
					if (d != 0)
						return d;
					d = Long.compare(ra1.getRoadDef().getId() , ra2.getRoadDef().getId());
					if (d != 0)
						return d;
					return d;
				}
			});
		
		Iterator<RouteArc> iter = directArcs.listIterator();
		RouteArc arc1 = iter.next();
		boolean addArc1 = false;
		while (iter.hasNext() || addArc1){
			ArcGroup ag = new ArcGroup();
			ag.initialHeading = arc1.getInitialHeading();
			ag.addArc(arc1);
			arcGroups.add(ag);
			addArc1 = false;
			while (iter.hasNext()){
				RouteArc arc2 = iter.next();
				if (Math.abs(arc1.getInitialHeading()- arc2.getInitialHeading()) < 1){
					if (arc1.getDest() != arc2.getDest() && arc1.getRoadDef().getId() != arc2.getRoadDef().getId())
						log.warn("sharp angle < 1° at",node.getCoord().toDegreeString(),",maybe duplicated OSM way with bearing",getCompassBearing(arc1.getInitialHeading()));
					ag.addArc(arc2);
				} else{
					arc1 = arc2;
					if (iter.hasNext() == false)
						addArc1 = true;
					break;
				}
			}
		}
		for (ArcGroup ag : arcGroups){
			ag.imgHeading = calcEncodedBearing(ag.initialHeading);
		}
		return arcGroups;
	}

	/**
	 * for log messages
	 */
	private static String getCompassBearing (float bearing){
		float cb = (bearing + 360) % 360;
		return Math.round(cb) + "°";
	}

	/**
	 * Debugging aid: guess what angle the Garmin algorithm is using.  
	 * @param heading1
	 * @param heading2
	 * @return
	 */
	private static int getImgAngle(byte heading1, byte heading2){
		int angle = heading2 - heading1;
		if (angle < 0)
			angle += 256;
		if (angle > 255)
			angle -= 256;
		return angle;
	}
	
}
