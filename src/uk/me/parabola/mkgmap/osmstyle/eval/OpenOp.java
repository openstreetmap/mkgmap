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
 * Create date: 08-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle.eval;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.scan.SyntaxException;

/**
 * An open parenthesis.  This is treated specially.
 * @author Steve Ratcliffe
 */
public class OpenOp extends AbstractOp {

	public OpenOp() {
		setType(NodeType.OPEN_PAREN);
	}

	public boolean eval(Element el) {
		throw new SyntaxException("Programming error, trying to eval an open parenthesis");
	}

	/**
	 * This is used when placing this on the top of the stack, for that
	 * purpose it has a very high priority and will not cause anything
	 * below to execute.
	 */
	public int priority() {
		return 100;
	}

	/**
	 * This is called when it is on the top of the stack.  In this case all
	 * other operations have a higher priority.
	 * @param other The other operation.
	 * @return Always returns false.
	 */
	public boolean hasHigherPriority(Op other) {
		return false;
	}
}
