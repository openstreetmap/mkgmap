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

import uk.me.parabola.mkgmap.reader.osm.Element;

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
	// The operation types.  They are represented by a mildly mnemonic character.
	// TODO use an emum
	public char EQUALS = '=';
	public char GT = 'g';
	public char GTE = 'G';
	public char LT = 'l';
	public char LTE = 'L';
	public char NOT_EQUALS = 'N';
	public char EXISTS = 'E';
	public char NOT_EXISTS = 'n';
	public char AND = '&';
	public char OR = '|';
	public char VALUE = 'V';
	public char FUNCTION = 'f';
	public char OPEN_PAREN = '(';
	public char CLOSE_PAREN = ')';
	public char NOT = '!';
	public char REGEX = '~';

	/**
	 * Evaluate the expression.
	 * @param el The OSM element to be tested.
	 * @return True if the expression is true for the given element.
	 */
	public boolean eval(Element el);

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
	public void setFirst(Op first);

	/**
	 * Return the second operand for a binary operation. If this is not a binary operation,
	 * then null is returned.
	 * @return The right hand side, or null if there is not one.
	 */
	public Op getSecond();

	/** Get the operation type */
	public char getType();

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
	public boolean isType(char value);

	/**
	 * For an operation this is a number that determines the precedence of this operation.
	 * Used when building the node tree. Higher numbers bind more tightly.
	 */
	public int priority();

	/**
	 * Get the type as a human readable string.
	 *
	 * Will not be needed when an enum is used.
	 */
	public String getTypeString();
}
