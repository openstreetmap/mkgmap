package uk.me.parabola.mkgmap.osmstyle;

import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.RoadNetwork;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Way;

/**
 * A handler that performs the access, maxspeed and ref handling. This was
 * hardcoded in the {@link StyledConverter} but was moved to the style file.
 * This handler is used if the {@code old-style} option is set to {@code true}
 * and gives all style devs time to adapt their styles.
 * 
 * @author WanMil
 * 
 */
public class CompatibilityHandler {
	private static final Logger log = Logger.getLogger(CompatibilityHandler.class);

	private static final Pattern ENDS_IN_MPH_PATTERN = Pattern.compile(".*mph");
	private static final Pattern REMOVE_MPH_PATTERN = Pattern.compile("[ \t]*mph");
	private static final Pattern REMOVE_KPH_PATTERN = Pattern.compile("[ \t]*kmh");

	private final static class AccessMapping {
		private final String type;
		private final int index;
		AccessMapping(String type, int index) {
			this.type = type;
			this.index = index;
		}
	}

	private final AccessMapping[] accessMap = {
		new AccessMapping("access",     RoadNetwork.NO_MAX), // must be first in list
		new AccessMapping("bicycle",    RoadNetwork.NO_BIKE),
		new AccessMapping("carpool",    RoadNetwork.NO_CARPOOL),
		new AccessMapping("foot",       RoadNetwork.NO_FOOT),
		new AccessMapping("hgv",        RoadNetwork.NO_TRUCK),
		new AccessMapping("motorcar",   RoadNetwork.NO_CAR),
		new AccessMapping("motorcycle", RoadNetwork.NO_CAR),
		new AccessMapping("psv",        RoadNetwork.NO_BUS),
		new AccessMapping("taxi",       RoadNetwork.NO_TAXI),
		new AccessMapping("emergency",  RoadNetwork.NO_EMERGENCY),
		new AccessMapping("delivery",   RoadNetwork.NO_DELIVERY),
		new AccessMapping("goods",      RoadNetwork.NO_DELIVERY),
	};

	
	public CompatibilityHandler() {
	}

	/**
	 * Performs calculation of maxspeed, access handling and 
	 * combination of ref tags and sets the calculated values as 
	 * tags so that the {@link StyledConverter} can use them as if
	 * these tags are set by the style file.
	 * 
	 * @param el an OSM element
	 * @param gt its GType
	 */
	public void performCompatHandling(Element el, GType gt) {
		if (gt.isRoad() == false)
			return;
		
		Way way = (Way)el;
		
		calcMaxspeed(way, gt);
		String refs = combineRefs(el);
		if (refs == null)
			way.deleteTag("mkgmap:ref");
		else
			way.addTag("mkgmap:ref", refs);
		
		String toll = way.getTag("toll");
		if (toll == null) {
			way.deleteTag("mkgmap:toll");
		} else {
			way.addTag("mkgmap:toll",toll);
		}
		
		String highwayType = way.getTag("highway");
		if(highwayType == null) {
			// it's a routable way but not a highway (e.g. a ferry)
			// use the value of the route tag as the highwayType for
			// the purpose of testing for access restrictions
			highwayType = way.getTag("route");
		}
		
		way.deleteTag("mkgmap:throughroute");
		
		boolean[] noAccess = new boolean[RoadNetwork.NO_MAX];
		for (AccessMapping anAccessMap : accessMap) {
			int index = anAccessMap.index;
			String type = anAccessMap.type;
			String accessTagValue = way.getTag(type);
			if (accessTagValue == null)
				continue;
			if (accessExplicitlyDenied(accessTagValue)) {
				if (index == RoadNetwork.NO_MAX) {
					// everything is denied access
					for (int j = 1; j < accessMap.length; ++j)
						noAccess[accessMap[j].index] = true;
				} else {
					// just the specific vehicle class is denied
					// access
					noAccess[index] = true;
				}
				log.debug(type, "is not allowed in", highwayType, getDebugName(way));
			} else if (accessExplicitlyAllowed(accessTagValue)) {
				if (index == RoadNetwork.NO_MAX) {
					// everything is allowed access
					for (int j = 1; j < accessMap.length; ++j)
						noAccess[accessMap[j].index] = false;
				} else {
					// just the specific vehicle class is allowed
					// access
					noAccess[index] = false;
				}
				log.debug(type, "is allowed in", highwayType, getDebugName(way));
			}
			else if (accessTagValue.equalsIgnoreCase("destination")) {
				if (type.equals("motorcar") ||
				    type.equals("motorcycle")) {
					way.addTag("mkgmap:throughroute", "no");
				} else if (type.equals("access")) {
					log.info("access=destination only affects routing for cars in", highwayType, getDebugName(way));
					way.addTag("mkgmap:throughroute", "no");
				} else {
					log.info(type + "=destination ignored in " + highwayType + " " + getDebugName(way));
				}
			} else if (accessTagValue.equalsIgnoreCase("unknown")) {
				// implicitly allow access
			} else {
				log.info("Ignoring unsupported access tag value", type + "=" + accessTagValue, "in", highwayType, getDebugName(way));
			}
		}
		
		way.addTag("mkgmap:emergency", noAccess[RoadNetwork.NO_EMERGENCY] ? "no" : "yes");
		way.addTag("mkgmap:delivery", noAccess[RoadNetwork.NO_DELIVERY] ? "no" : "yes");
		way.addTag("mkgmap:car", noAccess[RoadNetwork.NO_CAR] ? "no" : "yes");
		way.addTag("mkgmap:bus", noAccess[RoadNetwork.NO_BUS] ? "no" : "yes");
		way.addTag("mkgmap:taxi", noAccess[RoadNetwork.NO_TAXI] ? "no" : "yes");
		way.addTag("mkgmap:foot", noAccess[RoadNetwork.NO_FOOT] ? "no" : "yes");
		way.addTag("mkgmap:bicycle", noAccess[RoadNetwork.NO_BIKE] ? "no" : "yes");
		way.addTag("mkgmap:truck", noAccess[RoadNetwork.NO_TRUCK] ? "no" : "yes");
		way.addTag("mkgmap:carpool", noAccess[RoadNetwork.NO_CARPOOL] ? "no" : "yes");
	}
	
	protected boolean accessExplicitlyAllowed(String val) {
		if (val == null)
			return false;

		return (val.equalsIgnoreCase("yes") ||
			val.equalsIgnoreCase("designated") ||
			val.equalsIgnoreCase("permissive") ||
			val.equalsIgnoreCase("official"));
	}

	protected boolean accessExplicitlyDenied(String val) {
		if (val == null)
			return false;

		return (val.equalsIgnoreCase("no") ||
			val.equalsIgnoreCase("private"));
	}
	
	private void calcMaxspeed(Way way, GType gt) {
		// road speed (can be overridden by maxspeed (OSM) tag or
		// mkgmap:road-speed tag)
		int roadSpeed = gt.getRoadSpeed();
			// maxspeed attribute overrides default for road type
			String maxSpeed = way.getTag("maxspeed");
			if(maxSpeed != null) {
				int rs = getSpeedIdx(maxSpeed);
				if(rs >= 0)
					roadSpeed = rs;
				log.debug(getDebugName(way), "maxspeed=" + maxSpeed + ", speedIndex=" + roadSpeed);
			}
			
			String val = way.getTag("mkgmap:road-speed");
			if(val != null) {
				if(val.startsWith("-")) {
					roadSpeed -= Integer.decode(val.substring(1));
				}
				else if(val.startsWith("+")) {
					roadSpeed += Integer.decode(val.substring(1));
				}
				else {
					roadSpeed = Integer.decode(val);
				}
				val = way.getTag("mkgmap:road-speed-max");
				int roadSpeedMax = 7;
				if(val != null)
					roadSpeedMax = Integer.decode(val);
				val = way.getTag("mkgmap:road-speed-min");

				int roadSpeedMin = 0;
				if(val != null)
					roadSpeedMin = Integer.decode(val);
				if(roadSpeed > roadSpeedMax)
					roadSpeed = roadSpeedMax;
				else if(roadSpeed < roadSpeedMin)
					roadSpeed = roadSpeedMin;
				log.info("POI changing road speed of " + way.getName() + " (" + way.getId() + ") to " + roadSpeed + " at " + way.getPoints().get(0));
			}
			
			way.deleteTag("mkgmap:road-speed-max");
			way.deleteTag("mkgmap:road-speed-min");
			way.addTag("mkgmap:road-speed", String.valueOf(roadSpeed));
	}

	private int getSpeedIdx(String tag) {
		double factor = 1.0;
		
		String speedTag = tag.toLowerCase().trim();
		
		if (ENDS_IN_MPH_PATTERN.matcher(speedTag).matches()) {
			// Check if it is a limit in mph
			speedTag = REMOVE_MPH_PATTERN.matcher(speedTag).replaceFirst("");
			factor = 1.61;
		} else
			speedTag = REMOVE_KPH_PATTERN.matcher(speedTag).replaceFirst("");  // get rid of kmh just in case

		double kmh;
		try {
			kmh = Integer.parseInt(speedTag) * factor;
		} catch (Exception e) {
			return -1;
		}
		
		if(kmh > 110)
			return 7;
		if(kmh > 90)
			return 6;
		if(kmh > 80)
			return 5;
		if(kmh > 60)
			return 4;
		if(kmh > 40)
			return 3;
		if(kmh > 20)
			return 2;
		if(kmh > 10)
			return 1;
		else
			return 0;
	}


	private String combineRefs(Element element) {
		String ref = Label.squashSpaces(element.getTag("ref"));
		String int_ref = Label.squashSpaces(element.getTag("int_ref"));
		if(int_ref != null) {
			if(ref == null)
				ref = int_ref;
			else
				ref += ";" + int_ref;
		}
		String nat_ref = Label.squashSpaces(element.getTag("nat_ref"));
		if(nat_ref != null) {
			if(ref == null)
				ref = nat_ref;
			else
				ref += ";" + nat_ref;
		}
		String reg_ref = Label.squashSpaces(element.getTag("reg_ref"));
		if(reg_ref != null) {
			if(ref == null)
				ref = reg_ref;
			else
				ref += ";" + reg_ref;
		}

		return ref;
	}

	
	
	private static String getDebugName(Way way) {
		String name = way.getName();
		if(name == null)
			name = way.getTag("ref");
		if(name == null)
			name = "";
		else
			name += " ";
		return name + "(OSM id " + way.getId() + ")";
	}
	
}
