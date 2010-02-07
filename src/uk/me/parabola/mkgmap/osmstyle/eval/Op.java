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
 * Interface for an operation in the style expression language.
 * Operations need have only one operand, use {@link BinaryOp} for
 * when they definately have two.
 *
 * @author Steve Ratcliffe
 */
public interface Op {
	// The operation types.  They are represented by a mildly mnemonic character.
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

	public void setFirst(Op first);

	public char getType();

	/**
	 * Get the value in a 'pure' form, without being quoted in any way.
	 * If you want the expression in a form that can be printed and
	 * used in a style file, then use toString().
	 */
	public String value();

	public boolean isType(char value);

	int priority();
}
