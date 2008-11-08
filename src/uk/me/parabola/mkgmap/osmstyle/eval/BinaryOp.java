/*
 * Copyright (C) 2008 Steve Ratcliffe
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
 * Create date: 03-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

/**
 * @author Steve Ratcliffe
 */
public abstract class BinaryOp extends Op {

	private Op second;

	public void setChildren(Op first, Op second) {
		this.first = first;
		this.second = second;
	}

	public Op getSecond() {
		return second;
	}

	public void setSecond(Op second) {
		this.second = second;
	}

	public String toString() {
		return "(" + first + getType() + second + ')';
	}
}
