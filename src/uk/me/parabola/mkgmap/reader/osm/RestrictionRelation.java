
package uk.me.parabola.mkgmap.reader.osm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import uk.me.parabola.log.Logger;

import uk.me.parabola.mkgmap.general.RoadNetwork;

import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.Way;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;

/**
 * Representation of an OSM turn restriction
 *
 * @author Mark Burton
 */
public class RestrictionRelation extends Relation {

    private static final Logger log = Logger.getLogger(RestrictionRelation.class);

    private Way fromWay;
    private Way toWay;
    private Coord viaCoord;
    private String restriction;

    private CoordNode fromNode;
    private CoordNode toNode;
    private CoordNode viaNode;
    private List<CoordNode> otherNodes = new ArrayList<CoordNode>();
    private final String messagePrefix;

	/**
	 * Create an instance based on an existing relation.  We need to do
	 * this because the type of the relation is not known until after all
	 * its tags are read in.
	 * @param other The relation to base this one on.
	 */
	public RestrictionRelation(Relation other) {

		setId(other.getId());

		messagePrefix = "Turn restriction " + getId() + " ";

		for (Map.Entry<Element, String> pairs: other.getRoles().entrySet()){
			Element el = pairs.getKey();
			String role = pairs.getValue();
			addElement(role, el);

			if("to".equals(role)) {
				if(toWay != null) {
					log.error(messagePrefix + "has multiple 'to' members - first 'to' member starts at " + toWay.getPoints().get(0).toDegreeString());
				}
				else if(el instanceof Way) {
					toWay = (Way)el;
				}
				else
					log.error(messagePrefix + "'to' member should be a Way but is a " + el);
			}
			else if("from".equals(role)) {
				if(fromWay != null) {
					log.error(messagePrefix + "has multiple 'from' members - first 'from' member starts at " + fromWay.getPoints().get(0).toDegreeString());
				}
				else if(el instanceof Way) {
					fromWay = (Way)el;
				}
				else
					log.error(messagePrefix + "'from' member should be a Way but is a " + el);
			}
			else if("via".equals(role)) {
				if(viaCoord != null) {
					log.warn(messagePrefix + "can't cope (yet) with multiple 'via' members");
				}
				else if(el instanceof Node) {
					viaCoord = ((Node)el).getLocation();
				}
				else {
					String bleat = messagePrefix + "can't cope (yet) with 'via' member not being a node";
					if(el instanceof Way) {
						List<Coord> vp = ((Way)el).getPoints();
						bleat += " ('via' way starts at " + vp.get(0).toDegreeString() + ")";
					}
					log.warn(bleat);
				}
			}
			else if("location_hint".equals(role)) {
				// relax - we don't care about this
			}
			else {
				log.warn(messagePrefix + "unknown member role '" + role + "'");
			}
		}

		setName(other.getName());

		copyTags(other);

		restriction = getTag("restriction");

		String[] unsupportedTags = {
		    "except",
		    "day_on",
		    "day_off",
		    "hour_on",
		    "hour_off" };
		for(int i = 0; i < unsupportedTags.length; ++i) {
			if(getTag(unsupportedTags[i]) != null) {
				log.warn(messagePrefix + "ignoring unsupported '" + unsupportedTags[i] + "' tag");
			}
		}
	}

	public Way getFromWay() {
		return fromWay;
	}

	public Way getToWay() {
		return toWay;
	}

	public Coord getViaCoord() {
		return viaCoord;
	}

	public String getRestriction() {
		return restriction;
	}

	public void setFromNode(CoordNode fromNode) {
		this.fromNode = fromNode;
		log.debug(messagePrefix + restriction + " 'from' node is " + fromNode.toDegreeString());
	}

	public void setToNode(CoordNode toNode) {
		this.toNode = toNode;
		log.debug(messagePrefix + restriction + " 'to' node is " + toNode.toDegreeString());
	}

	public void setViaNode(CoordNode viaNode) {
		if(this.viaNode == null)
			log.debug(messagePrefix + restriction + " 'via' node is " + viaNode.toDegreeString());
		else if(!this.viaNode.equals(viaNode))
			log.error(messagePrefix + restriction + " 'via' node redefined from " +
				  this.viaNode.toDegreeString() + " to " +
				  viaNode.toDegreeString());
		this.viaNode = viaNode;
	}

	public void addOtherNode(CoordNode otherNode) {
		otherNodes.add(otherNode);
		log.debug(messagePrefix + restriction + " adding 'other' node " + otherNode.toDegreeString());
	}

	public boolean isValid() {
		boolean result = true;

		if(restriction == null) {
			log.error(messagePrefix + "lacks 'restriction' tag (e.g. no_left_turn)");
			result = false;
		}

		if(fromWay == null) {
			String bleat = messagePrefix + "lacks 'from' way";
			if(toWay != null) {
				bleat += " ('to' way starts at " + toWay.getPoints().get(0).toDegreeString() + ")";
			}
			log.error(bleat);
		}

		if(toWay == null) {
			String bleat = messagePrefix + "lacks 'to' way";
			if(fromWay != null) {
				List<Coord> fp = fromWay.getPoints();
				bleat += " ('from' way ends at " + fp.get(fp.size() - 1).toDegreeString() + ")";
			}
			log.error(bleat);
		}

		if(fromWay == null || toWay == null)
			return false;

		if(viaCoord == null) {
			List<Coord>fromPoints = fromWay.getPoints();
			List<Coord>toPoints = toWay.getPoints();
			for(Coord fp : fromPoints) {
				for(Coord tp : toPoints) {
					if(fp.equals(tp)) {
						if(viaCoord == null) {
							viaCoord = fp;
						}
						else {
							log.error(messagePrefix + "lacks 'via' node and the 'from' and 'to' ways connect in more than one place - first connection is at " + viaCoord.toDegreeString());
							return false;
						}
					}
				}
			}

			if(viaCoord == null) {
				log.error(messagePrefix + "lacks 'via' node and the 'from' and 'to' ways don't connect");
				return false;
			}

			log.warn(messagePrefix + "lacks 'via' node (guessing it should be at " + viaCoord.toDegreeString() + ", why don't you add it to the OSM data?)");
		}

		Coord e1 = fromWay.getPoints().get(0);
		Coord e2 = fromWay.getPoints().get(fromWay.getPoints().size() - 1);
		if(!viaCoord.equals(e1) && !viaCoord.equals(e2)) {
			log.error(messagePrefix + "'from' way doesn't start or end at 'via' node (" + viaCoord.toDegreeString() + ")");
			result = false;
		}

		e1 = toWay.getPoints().get(0);
		e2 = toWay.getPoints().get(toWay.getPoints().size() - 1);
		if(!viaCoord.equals(e1) && !viaCoord.equals(e2)) {
			log.error(messagePrefix + "'to' way doesn't start or end at 'via' node (" + viaCoord.toDegreeString() + ")");
			result = false;
		}

		return result;
	}

	public void addRestriction(RoadNetwork roadNetwork) {

		if(viaNode == null || fromNode == null || toNode == null) {
			// restriction must have some error (reported earlier)
		    return;
		}

		if("no_left_turn".equals(restriction) ||
		   "no_right_turn".equals(restriction) ||
		   "no_straight_on".equals(restriction) ||
		   "no_u_turn".equals(restriction)) {
			roadNetwork.addRestriction(fromNode, toNode, viaNode);
			log.info(messagePrefix + "(" + restriction + ") added at " + viaNode.toDegreeString() + " (blocked routing to " + toNode.toDegreeString() + ")");
		}
		else if("only_left_turn".equals(restriction) ||
			"only_right_turn".equals(restriction) ||
			"only_straight_on".equals(restriction)) {
			for(CoordNode otherNode : otherNodes) {
				roadNetwork.addRestriction(fromNode, otherNode, viaNode);
				log.info(messagePrefix + "(" + restriction + ") added at " + viaNode.toDegreeString() + " (blocked routing to " + otherNode.toDegreeString() + ")");
			}
		}
		else {
			log.error(messagePrefix + "has unsupported type '" + restriction + "'");
		}
	}

	/** Process the members in this relation.
	 */
	public void processElements() {
		// relax
	}

	public String toString() {
		return "[restriction = " + restriction + ", from = " + fromWay + ", to = " + toWay + ", via = " + viaCoord.toDegreeString() + "]";
	}
}
