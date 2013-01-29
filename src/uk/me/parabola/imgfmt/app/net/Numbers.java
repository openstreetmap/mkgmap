/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.net;

import uk.me.parabola.log.Logger;

/**
 * Describes the house numbering from a node in the road.
 * @author Steve Ratcliffe
 */
public class Numbers {
	private static final Logger log = Logger.getLogger(Numbers.class);

	// The node in the road where these numbers apply.  In the polish notation it is the
	// node in the road, whereas in the NET file it is the number of the routing node.
	private int nodeNumber; // node in road index
	private Integer rnodNumber; // routing node index

	// On the left hand side of the road.
	private NumberStyle leftNumberStyle;
	private int leftStart;
	private int leftEnd;

	// On the right hand side of the road.
	private NumberStyle rightNumberStyle;
	private int rightStart;
	private int rightEnd;

	public Numbers() {
	}

	/**
	 * This constructor takes a comma separated list as in the polish format. Also used in testing as
	 * it is an easy way to set all the parameters at once.
	 *
	 * @param spec Node number, followed by left and then right parameters as in the polish format.
	 */
	public Numbers(String spec) {
		String[] strings = spec.split(",");
		nodeNumber = Integer.valueOf(strings[0]);
		leftNumberStyle = NumberStyle.fromChar(strings[1]);
		leftStart = Integer.valueOf(strings[2]);
		leftEnd = Integer.valueOf(strings[3]);
		rightNumberStyle = NumberStyle.fromChar(strings[4]);
		rightStart = Integer.valueOf(strings[5]);
		rightEnd = Integer.valueOf(strings[6]);
	}

	public int getNodeNumber() {
		return nodeNumber;
	}

	public void setNodeNumber(int nodeNumber) {
		this.nodeNumber = nodeNumber;
	}

	public int getRnodNumber() {
		if (rnodNumber == null) {
			log.error("WARNING: rnod not set!!");
			return nodeNumber;
		}
		return rnodNumber;
	}

	public boolean hasRnodNumber() {
		return rnodNumber != null;
	}

	public void setRnodNumber(int rnodNumber) {
		this.rnodNumber = rnodNumber;
	}

	public NumberStyle getLeftNumberStyle() {
		return leftNumberStyle;
	}

	public void setLeftNumberStyle(NumberStyle leftNumberStyle) {
		this.leftNumberStyle = leftNumberStyle;
	}

	public int getLeftStart() {
		return leftStart;
	}

	public void setLeftStart(int leftStart) {
		this.leftStart = leftStart;
	}

	public int getLeftEnd() {
		return leftEnd;
	}

	public void setLeftEnd(int leftEnd) {
		this.leftEnd = leftEnd;
	}

	public NumberStyle getRightNumberStyle() {
		return rightNumberStyle;
	}

	public void setRightNumberStyle(NumberStyle rightNumberStyle) {
		this.rightNumberStyle = rightNumberStyle;
	}

	public int getRightStart() {
		return rightStart;
	}

	public void setRightStart(int rightStart) {
		this.rightStart = rightStart;
	}

	public int getRightEnd() {
		return rightEnd;
	}

	public void setRightEnd(int rightEnd) {
		this.rightEnd = rightEnd;
	}

	public String toString() {
		String nodeStr = "0";
		if (nodeNumber > 0)
			nodeStr = String.valueOf(nodeNumber);
		else if (getRnodNumber() > 0)
			nodeStr = String.format("(n%d)", getRnodNumber());

		return String.format("%s,%s,%d,%d,%s,%d,%d",
				nodeStr,
				leftNumberStyle,
				leftStart,
				leftEnd,
				rightNumberStyle,
				rightStart,
				rightEnd);
	}

	public boolean equals(Object obj) {
		if (!(obj instanceof Numbers))
			return false;

		Numbers other = (Numbers) obj;
		return toString().equals(other.toString());
	}

	public int hashCode() {
		return toString().hashCode();
	}
}
