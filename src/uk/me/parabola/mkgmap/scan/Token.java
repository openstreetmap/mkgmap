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
 * Create date: May 5, 2008
 */
package uk.me.parabola.mkgmap.scan;

/**
 * @author Steve Ratcliffe
 */
public class Token {
	private final TokType type;
	private String value;

	public boolean isWhiteSpace() {
		return type == TokType.SPACE || type == TokType.EOL;
	}

	public Token(TokType type) {
		this.type = type;
	}

	public TokType getType() {
		return type;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public boolean isValue(String val) {
		return val.equals(value);
	}
}

