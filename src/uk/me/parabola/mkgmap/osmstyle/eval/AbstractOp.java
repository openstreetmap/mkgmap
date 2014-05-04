/*
 * Copyright (C) 2008-2012 Steve Ratcliffe
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

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.scan.SyntaxException;

/**
 * A base class that can be used as the superclass of an operation.
 *
 * @author Steve Ratcliffe
 */
public abstract class AbstractOp implements Op {
	
	protected Op first;
	private NodeType type;
	protected boolean lastRes;
	protected int lastCachedId = -1;

	public static Op createOp(String value) {
		char c = value.charAt(0);
		Op op;
		switch (c) {
		case '=': op = new EqualsOp(); break;
		case '&':
			if (value.length() > 1)
				throw new SyntaxException(String.format("Use '&' instead of '%s'", value));
			op = new AndOp();
			break;
		case '|':
			if (value.length() > 1)
				throw new SyntaxException(String.format("Use '|' instead of '%s'", value));
			op = new OrOp();
			break;
		case '~': op = new RegexOp(); break;
		case '(': op = new OpenOp(); break;
		case ')': op = new CloseOp(); break;
		case '>':
			if (value.equals(">="))
				op = new GTEOp();
			else
				op = new GTOp();
			break;
		case '<':
			if (value.equals("<="))
				op = new LTEOp();
			else
				op = new LTOp();
			break;
		case '!':
			if (value.equals("!="))
				op = new NotEqualOp();
			else
				op = new NotOp();
			break;
		default:
			throw new SyntaxException("Unrecognised operation " + c);
		}
		return op;
	}

	public boolean eval(int cacheId, Element el){
		if (lastCachedId != cacheId){
			if (lastCachedId > cacheId){
				throw new ExitException("fatal error: cache id invalid");
			}
			lastRes = eval(el);
			lastCachedId = cacheId;
		}
		return lastRes;
			
	}
	
	
	/**
	 * Does this operation have a higher priority that the other one?
	 * @param other The other operation.
	 */
	public boolean hasHigherPriority(Op other) {
		return priority() > other.priority();
	}

	public Op getFirst() {
		return first;
	}

	public void setFirst(Op first) {
		this.first = first;
		lastCachedId = -1;
	}

	/**
	 * Only supported on Binary operations, but useful to return null to make code simpler, rather than
	 * defaulting to UnsupportedOperation.
	 */
	public Op getSecond() {
		return null;
	}

	public NodeType getType() {
		return type;
	}

	protected void setType(NodeType type) {
		this.type = type;
	}

	/**
	 * Only supported on value nodes.
	 */
	public String value(Element el) {
		throw new UnsupportedOperationException();
	}

	/**
	 * This is only supported on value nodes.
	 */
	public String getKeyValue() {
		throw new UnsupportedOperationException();
	}

	public boolean isType(NodeType value) {
		return type == value;
	}
	
	public void resetCache(){
		lastCachedId = -1;
	}

}
