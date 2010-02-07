/*
 * Copyright (C) 2010.
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
package uk.me.parabola.mkgmap.osmstyle.eval;

/**
 * The binary version of {@link LinkedOp}, see that class for
 * a description.
 *
 * @author Steve Ratcliffe
 */
public class LinkedBinaryOp extends LinkedOp implements BinaryOp {

	public LinkedBinaryOp(BinaryOp other, boolean first) {
		super(other, first);
	}

	public Op getSecond() {
		return ((BinaryOp) wrapped).getSecond();
	}

	public void setSecond(Op second) {
		((BinaryOp) wrapped).setSecond(second);
	}
}
