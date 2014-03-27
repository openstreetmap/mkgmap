package uk.me.parabola.imgfmt.app.net;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;

/**
 * A class to collect the data related to routing restrictions
 * like only-left-turn or no-right-turn
 * @author GerdP
 *
 */
public class GeneralRouteRestriction {
	public static final byte TYPE_ONLY = 1; 
	public static final byte TYPE_NOT = 2;
	public static final byte TYPE_NO_TROUGH = 3; // for elements like barriers, gates, etc.

	private final byte exceptionMask;
	private final byte type;
	private final String sourceDesc;
	
	private Coord fromPoint,toPoint;
	private long fromWayId,toWayId,viaWayId;
	private CoordNode fromNode,toNode,via1Node,via2Node;
	LongArrayList onlyWays;

	public GeneralRouteRestriction(String type, byte exceptionMask, String sourceDesc) {
		if ("not".equals(type))
			this.type = TYPE_NOT;
		else if ("only".equals(type))
			this.type = TYPE_ONLY;
		else if ("no_through".equals(type))
			this.type = TYPE_NO_TROUGH;
		else 
			throw new IllegalArgumentException("invalid type " + type);
		this.exceptionMask = exceptionMask;
		this.sourceDesc = sourceDesc;
	}
	
	public Coord getFromPoint() {
		return fromPoint;
	}
	public void setFromPoint(Coord fromPoint) {
		this.fromPoint = fromPoint;
	}
	public Coord getToPoint() {
		return toPoint;
	}
	public void setToPoint(Coord toPoint) {
		this.toPoint = toPoint;
	}
	public long getFromWayId() {
		return fromWayId;
	}
	public void setFromWayId(long fromWayId) {
		this.fromWayId = fromWayId;
	}
	public long getToWayId() {
		return toWayId;
	}
	public void setToWayId(long toWayId) {
		this.toWayId = toWayId;
	}
	public long getViaWayId() {
		return viaWayId;
	}
	public void setViaWayId(long viaWayId) {
		this.viaWayId = viaWayId;
	}
	public byte getExceptionMask() {
		return exceptionMask;
	}
	public byte getType() {
		return type;
	}
	public CoordNode getFromNode() {
		return fromNode;
	}
	public void setFromNode(CoordNode fromNode) {
		this.fromNode = fromNode;
	}
	public CoordNode getToNode() {
		return toNode;
	}
	public void setToNode(CoordNode toNode) {
		this.toNode = toNode;
	}
	public CoordNode getVia1Node() {
		return via1Node;
	}
	public void setVia1Node(CoordNode via1Node) {
		this.via1Node = via1Node;
	}
	public CoordNode getVia2Node() {
		return via2Node;
	}
	public void setVia2Node(CoordNode via2Node) {
		this.via2Node = via2Node;
	}
	public boolean isValid() {
		// TODO Auto-generated method stub
		return false;
	}
	public void addOnlyWayId(long id){
		if (onlyWays == null)
			onlyWays = new LongArrayList();
		onlyWays.add(id);
	}
	public String getSourceDesc(){
		return sourceDesc;
	}
}
