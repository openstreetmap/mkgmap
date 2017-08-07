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

import java.util.Set;

import uk.me.parabola.mkgmap.reader.osm.Element;

/**
 * This is used to implement OR expressions as if each term was a separate
 * indexable expression.
 *
 * <p>So if you have:
 * <pre>
 * A=1 | B=1 | C=1 {set name='${name} ${A}' | '${B}'; }
 * </pre>
 * This is represented by:
 * <pre>
 * A=1 {set name='${name} ${A}' | '${B}'; }
 * B=1 {set name='${name} ${A}' | '${B}'; }
 * C=1 {set name='${name} ${A}' | '${B}'; }
 * </pre>
 * So that each term can be index separately.  However we need to be able to
 * prevent the second and/or third terms running if the first (or second) matches.
 * That is what this class does.  It acts in most respects like the operation
 * it is wrapping, but when a successful evaluation is performed on an
 * element, the test is suppressed for the subsequent terms in the chain.
 *
 * @author Steve Ratcliffe
 */
public class LinkedOp implements Op {
	protected final Op wrapped;
	private final boolean first;
	private LinkedOp link;
	private Element current;

	protected LinkedOp(Op wrapped, boolean first) {
		this.wrapped = wrapped;
		this.first = first;
	}

	public boolean eval(Element el) {
		if (el == current)
			return false;

		boolean b = wrapped.eval(el);
		if (link != null && b)
			link.setMatched(el);
		return b;
	}

	public boolean eval(int cacheId, Element el){
		if (el == current)
			return false;

		boolean b = wrapped.eval(cacheId, el);
		if (link != null && b)
			link.setMatched(el);
		return b;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (!first)
			sb.append("# Part of the previous OR expression: ");

		sb.append(wrapped);
		LinkedOp l = link;
		while (l != null) {
			sb.append(" | ");
			sb.append(l.wrapped);
			l = l.link;
		}

		return sb.toString();
	}

	public int priority() {
		return wrapped.priority();
	}

	public boolean hasHigherPriority(Op other) {
		return this.wrapped.hasHigherPriority(other);
	}

	public Op getFirst() {
		return wrapped.getFirst();
	}

	public <T extends Op> T setFirst(Op first) {
		return wrapped.setFirst(first);
	}

	public Op getSecond() {
		return null;
	}

	/**
	 * Not supported, as setSecond is not.
	 */
	public <T extends Op> T set(Op first, Op second) {
		throw new UnsupportedOperationException();
	}

	public NodeType getType() {
		return wrapped.getType();
	}

	public String value(Element el) {
		return wrapped.value(el);
	}

	public String getKeyValue() {
		throw new UnsupportedOperationException();
	}

	public boolean isType(NodeType value) {
		return wrapped.isType(value);
	}

	/**
	 * Set the fact that the given element has been matched already.  If we
	 * are called to evaluate and find that the element we are given
	 * has already been matched by an earlier term in the OR then we just
	 * return without doing anything.
	 * @param el The element to mark as being matched.
	 */
	private void setMatched(Element el) {
		this.current = el;
		if (link != null)
			link.setMatched(el);
	}

	public void setLink(LinkedOp link) {
		if (this.link == null)
			this.link = link;
		else
			this.link.setLink(link);
	}

	/**
	 * Create either a LinkedOp or a LinkedBinaryOp as appropriate
	 * for the type of operation that is passed in.
	 */
	public static LinkedOp create(Op op, boolean first) {
		if (op instanceof BinaryOp) {
			return new LinkedBinaryOp((BinaryOp) op, first);
		} else {
			return new LinkedOp(op, first);
		}
	}
	
	public boolean isFirstPart(){
		return first;
	}

	@Override
	public Set<String> getEvaluatedTagKeys() {
		return wrapped.getEvaluatedTagKeys();
	}
}
