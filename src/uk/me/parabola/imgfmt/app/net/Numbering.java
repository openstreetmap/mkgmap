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

/**
 * Describes the house numbering from a node in the road.
 * @author Steve Ratcliffe
 */
public class Numbering {
	// The node in the road where these numbers apply.
	private int nodeNumber;

	// On the left hand side of the road.
	private NumberStyle leftNumberStyle;
	private int leftStart;
	private int leftEnd;

	// On the right hand side of the road.
	private NumberStyle rightNumberStyle;
	private int rightStart;
	private int rightEnd;

	public Numbering() {
	}

	public Numbering(String spec) {
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

	@Override
	public String toString() {
		return String.format("%d,%s,%d,%d,%s,%d,%d",
				nodeNumber,
				leftNumberStyle,
				leftStart,
				leftEnd,
				rightNumberStyle,
				rightStart,
				rightEnd);
	}

	public boolean equals(Object obj) {
		if (!(obj instanceof Numbering))
			return false;

		Numbering other = (Numbering) obj;
		return leftNumberStyle == other.leftNumberStyle
				&& leftStart == other.leftStart
				&& leftEnd == other.leftEnd
				&& rightNumberStyle == other.rightNumberStyle
				&& rightStart == other.rightStart
				&& rightEnd == other.rightEnd;
	}

	public int hashCode() {
		return leftNumberStyle.hashCode()
				+ leftStart
				+ leftEnd
				+ rightNumberStyle.hashCode()
				+ rightStart
				+ rightEnd;
	}
}
