/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * Create date: Apr 12, 2008
 */
package uk.me.parabola.mkgmap.reader.osm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.NoSuchElementException;

/**
 * Class partially used to replace java.util.Scanner which is not available
 * in GNU classpath, but also to provide a more word orientated interface
 * for parsing the style descriptions.
 *
 * @author Steve Ratcliffe
 */
class WordScanner {
	private final BufferedReader reader;

	WordScanner(Reader r) {
		reader = new LineNumberReader(r);
	}

	public int nextInt() {
		String word = nextWord();
		return Integer.parseInt(word);
	}

	/**
	 * Get the next word that consists of letters and digits.
	 *
	 * @return A alphanumeric word.
	 */
	private String nextWord() {
		StringBuffer sb = new StringBuffer();

		skipSpace();
		char ch;
		while ((ch = nextChar()) != -1) {
			if (Character.isLetterOrDigit(ch)) {
				sb.append(ch);
			} else {
				reset();
				break;
			}
		}
		return sb.toString();
	}

	/**
	 * If the next character is a space, then skip all space. After returning
	 * then the next character to be read will not be a space.  The position
	 * in the file is not changed if it was already pointing at a non-space
	 * character.
	 *
	 * If end of file is reached then an exception is thrown.
	 */
	protected void skipSpace() {
		char ch;
		while ((ch = nextChar()) != -1) {
			if (!Character.isWhitespace(ch)) {
				reset();
				return;
			}
		}
	}

	/**
	 * reset back to the position before the previous {@link #nextChar} call.
	 */
	private void reset() {
		try {
			reader.reset();
		} catch (IOException e) {
			throw new NoSuchElementException();
		}
	}

	/**
	 * Get the next character, but save the current position so that
	 * we can get back to it.
	 * @return The next character in the stream.
	 */
	private char nextChar() {
		try {
			reader.mark(1);
			int ich = reader.read();
			if (ich == -1)
				throw new NoSuchElementException("End of file reached");
			return (char) ich;
		} catch (IOException e) {
			throw new NoSuchElementException();
		}
	}
}
