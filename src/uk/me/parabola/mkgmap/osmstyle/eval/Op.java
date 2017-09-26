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

import static uk.me.parabola.mkgmap.osmstyle.eval.NodeType.AND;
import static uk.me.parabola.mkgmap.osmstyle.eval.NodeType.OR;

/**
 * Interface for an node in the style expression language.
 * Operations need have only one operand, use {@link BinaryOp} for
 * when they definitely have two.
 *
 * In spite of the name this covers both values and operations.
 *
 * @author Steve Ratcliffe
 */
public interface Op {

	/**
	 * Evaluate the expression.
	 * @param el The OSM element to be tested.
	 * @return True if the expression is true for the given element.
	 */
	public boolean eval(Element el);
	
	/**
	 * Evaluate the expression using a cache.
	 * @param cacheId used to recognise an invalid cache
	 * @param el The OSM element to be tested.
	 * @return True if the expression is true for the given element.
	 */
	public boolean eval(int cacheId, Element el);


	/**
	 * Does this operation have a higher priority that the other one?
	 * @param other The other operation.
	 */
	public boolean hasHigherPriority(Op other);

	/**
	 * Get the first operand.
	 */
	public Op getFirst();

	/**
	 * Set the first operand.
	 */
	public <T extends Op> T setFirst(Op first);

	/**
	 * Set the second operand.
	 * Only supported on binary nodes, but declared here to avoid casts all over the place.
	 */
	public default void  setSecond(Op second) {
		throw new UnsupportedOperationException("setSecond only supported on binary nodes");
	}

	/**
	 * Return the second operand for a binary operation. If this is not a binary operation,
	 * then null is returned.
	 * @return The right hand side, or null if there is not one.
	 */
	public Op getSecond();

	/**
	 * Set both first and second in one call.
	 *
	 * Only supported on BinaryOp types.
	 */
	public <T extends Op> T set(Op first, Op second);

	/** Get the operation type */
	public NodeType getType();

	/**
	 * For operations that are value types this is the string value.
	 *
	 * Only applies to nodes of type VALUE and FUNCTION, returns null for all
	 * other kinds of node.
	 *
	 * @return The value, or UnsupportedOperationException if it does not have a value.
	 */
	public String value(Element el);

	/**
	 * For a value-type node, this is a key value associated with value. For a base Value node
	 * this is the same as value(), but if value() is overridden then it may not be.
	 */
	public String getKeyValue();

	/**
	 * Test the node type and return true if it matches the given argument.
	 */
	public boolean isType(NodeType value);

	/**
	 * For an operation this is a number that determines the precedence of this operation.
	 * Used when building the node tree. Higher numbers bind more tightly.
	 */
	public int priority();

	/**
	 * @return a set with the tag keys which are evaluated, maybe empty 
	 */
	public Set<String> getEvaluatedTagKeys();


	/**
	 * A semi-deep copy of the Node.
	 *
	 * Copy all AND/OR nodes, the EQUALS, LT nodes remain shared.  They can be shared because
	 * they are never modified by the arranger code.  If you are using this for something else
	 * you may need a different method.
	 */
	public default Op copy() {
		NodeType t = getType();
		if (t == AND || t == OR) {
			return AbstractOp.createOp(getType())
					.set(getFirst().copy(), getSecond().copy());
		} else
			return this;
	}
}
