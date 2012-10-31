/*
 * Copyright (C) 2012.
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
 * The different node types that an {@link Op} can have.
 *
 * @author Steve Ratcliffe
 */
public enum NodeType {
	EQUALS("="),
	GT(">"),
	GTE(">="),
	LT("<"),
	LTE("<="),
	NOT_EQUALS("!="),
	EXISTS("=*"),
	NOT_EXISTS("!=*"),
	AND("&"),
	OR("|"),
	VALUE(null),
	FUNCTION(null),
	OPEN_PAREN("("),
	CLOSE_PAREN(")"),
	NOT("!"),
	REGEX("~");

	private final String symbol;

	private NodeType(String symbol) {
		this.symbol = symbol;
	}

	public String toSymbol() {
		return symbol;
	}
}
