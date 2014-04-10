/*
 * Copyright (C) 2014
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
 */
package uk.me.parabola.imgfmt.app.net;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.CoordNode;

/**
 * A class to collect the data related to routing restrictions
 * like only-left-turn or no-right-turn
 * @author GerdP
 *
 */
public class GeneralRouteRestriction {
	public enum RestrType {TYPE_ONLY , 
		TYPE_NOT, 
		TYPE_NO_TROUGH // for elements like barriers, gates, etc.
	}

	private final byte exceptionMask;
	private final RestrType type;
	private final String sourceDesc;
	
	private long fromWayId, toWayId;
	private CoordNode fromNode, toNode;
	private List<Long> viaWayIds = new ArrayList<>();
	private List<CoordNode> viaNodes = new ArrayList<>();

	public GeneralRouteRestriction(String type, byte exceptionMask, String sourceDesc) {
		if ("not".equals(type))
			this.type = RestrType.TYPE_NOT;
		else if ("only".equals(type))
			this.type = RestrType.TYPE_ONLY;
		else if ("no_through".equals(type))
			this.type = RestrType.TYPE_NO_TROUGH;
		else 
			throw new IllegalArgumentException("invalid type " + type);
		this.exceptionMask = exceptionMask;
		this.sourceDesc = sourceDesc;
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
	public byte getExceptionMask() {
		return exceptionMask;
	}
	public RestrType getType() {
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

	public List<Long> getViaWayIds() {
		return viaWayIds;
	}

	public void setViaWayIds(List<Long> viaWayIds) {
		this.viaWayIds = new ArrayList<Long>(viaWayIds);
	}
	public List<CoordNode> getViaNodes() {
		return viaNodes;
	}
	public void setViaNodes(List<CoordNode> viaNodes){
		this.viaNodes = new ArrayList<>(viaNodes);
	}
	public String getSourceDesc(){
		return sourceDesc;
	}
}
