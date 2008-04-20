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

import uk.me.parabola.log.Logger;

/**
 * Class partially used to replace java.util.Scanner which is not available
 * in GNU classpath, but also to provide a more word orientated interface
 * for parsing the style descriptions.
 *
 * @author Steve Ratcliffe
 */
class WordScanner {
	private static final Logger log = Logger.getLogger(WordScanner.class);

	private final BufferedReader reader;

	private boolean eof;

	WordScanner(Reader r) {
		reader = new LineNumberReader(r);
	}

	public int nextInt() {
		String word = nextWord();
		return Integer.parseInt(word);
	}

	/**
	 * Return the very next character in the input stream without
	 * consuming it.
	 */
	public int peekChar() {
		int ch = nextChar();
		reset();
		return ch;
	}

	/**
	 * Get the next word that consists of letters and digits.
	 */
	public String nextWord() {
		skipSpace();

		StringBuffer sb = new StringBuffer();

		while (!isEndOfFile()) {
			char ch = nextChar();
			if (Character.isLetterOrDigit(ch)) {
				sb.append(ch);
			} else {
				reset();
				break;
			}
		}
		return sb.toString();
	}

	public boolean hasNextSymbol() {
		log.debug("check for next symbol");
		skipSpace();
		if (eof)
			return false;
		
		char ch = nextChar();
		int type = Character.getType(ch);
		reset();
		return isSymbol(type);
	}

	/**
	 * Get the rest of the line.  Leading and trailing space is stripped.
	 */
	public String nextLine() {
		StringBuffer sb = new StringBuffer();
		while (!isEndOfFile()) {
			char ch = nextChar();
			if (ch == '\n')
				break;
			else
				sb.append(ch);
		}
		return sb.toString().trim();
	}

	public String nextSymbol() {
		log.debug("get for symbol");
		skipSpace();

		StringBuffer sb = new StringBuffer();

		while (!isEndOfFile()) {
			char ch = nextChar();
			log.debug("symb char got", ch);
			int type = Character.getType(ch);
			if (isSymbol(type)) {
				sb.append(ch);
			} else {
				reset();
				break;
			}
		}
		log.debug("leave symb");
		return sb.toString();
	}

	private boolean isSymbol(int type) {
		log.debug("symbol type " + type);
		return type == Character.OTHER_SYMBOL
				|| type == Character.OTHER_PUNCTUATION
				|| type == Character.MATH_SYMBOL
				;
	}

	/**
	 * If the next character is a space, then skip all space. After returning
	 * then the next character to be read will not be a space.  The position
	 * in the file is not changed if it was already pointing at a non-space
	 * character.
	 *
	 * If end of file is reached then an exception is thrown.
	 */
	private void skipSpace() {
		while (!isEndOfFile()) {
			char ch = nextChar();
			if (!Character.isWhitespace(ch)) {
				if (ch == '#') {
					skipLine();
				} else {
					reset();
				}
				return;
			}
		}
	}

	private void skipLine() {
		char ch;
		do {
			if ((ch = nextChar()) == '\n')
				return;
		} while (ch != -1);
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
		if (eof)
			throw new NoSuchElementException("End of file reached");

		try {
			reader.mark(1);
			int ich = reader.read();
			if (ich == -1) {
				eof = true;
			}
			return (char) ich;
		} catch (IOException e) {
			throw new NoSuchElementException();
		}
	}

	public boolean isEndOfFile() {
		return eof;
	}
}
