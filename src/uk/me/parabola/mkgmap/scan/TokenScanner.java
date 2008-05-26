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
 * Create date: May 10, 2008
 */
package uk.me.parabola.mkgmap.scan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 *
 * @author Steve Ratcliffe
 */
public class TokenScanner {
	private static final int NO_PUSHBACK = 0;
	protected final Reader reader;
	private int pushback = NO_PUSHBACK;
	private boolean isEOF;

	private final LinkedList<Token> tokens = new LinkedList<Token>();

	public TokenScanner(Reader reader) {
		if (reader instanceof BufferedReader)
			this.reader = reader;
		else
			this.reader = new BufferedReader(reader);
	}

	public int peekChar() {
		Token t = peekToken();
		String val = t.getValue();
		if (val == null) {
			return 0;
		} else
			return val.charAt(0);
	}

	public TokType firstTokenType() {
		ensureTok();
		return tokens.peek().getType();
	}

	public Token peekToken() {
		if (tokens.isEmpty())
			fillTok();
		return tokens.peek();
	}

	public Token nextToken() {
		if (tokens.isEmpty())
			return readTok();
		else
			return tokens.removeFirst();
	}

	public String nextValue() {
		return nextToken().getValue();
	}

	public Token findTokenInLine(TokType type, String val, boolean create) {
		Token found = null;
		Iterator<Token> it = new TokIterator();
		while (it.hasNext()) {
			Token t = it.next();
			if (t.getType() == TokType.EOL)
				break;

			if (t.getType() == type) {
				if (val == null) {
					found = t;
					break;
				} else if (t.getValue().equals(val)) {
					found = t;
					break;
				}
			}
		}

		if (create && found == null) {
			found = new Token(type);
			found.setValue(val);
			// we must be at a new line here
			tokens.add(tokens.size()-1, found);
		}
		return found;
	}

	public Token findToken(TokType type, String val) {
		Token found = null;

		Iterator<Token> it = new TokIterator();
		while (it.hasNext()) {
			Token t = it.next();
			if (t.getType() == type) {
				if (val == null) {
					found = t;
					break;
				} else if (t.getValue().equals(val)) {
					found = t;
					break;
				}
			}
		}

		return found;
	}

	public void insertTokenBefore(Token tok, Token before) {
		if (before == null) {
			tokens.addFirst(tok);
			return;
		}

		int ind = 0;
		for (Token t : tokens) {
			//noinspection ObjectEquality
			if (t == before) {
				tokens.add(ind, tok);
				break;
			}
			ind++;
		}
	}

	public boolean isEndOfFile() {
		if (tokens.isEmpty()) {
			return isEOF;
		} else {
			return tokens.peek().getType() == TokType.EOF;
		}
	}

	public void skipSpace() {
		while (!tokens.isEmpty() && tokens.peek().isWhiteSpace())
			tokens.removeFirst();

		// If the list is empty, directly consume white space
		if (tokens.isEmpty()) {
			int c = NO_PUSHBACK;
			while (!isEndOfFile()) {
				c = readChar();
				if (!isSpace(c))
					break;
			}

			pushback = c;
		}
	}

	public void skipLine() {
		while (!isEndOfFile()) {
			Token t = nextToken();
			if (t.getType() == TokType.EOL)
				break;
		}
	}

	private void ensureTok() {
		if (tokens.isEmpty())
			fillTok();
	}

	private void fillTok() {
		Token t = readTok();
		tokens.add(t);
	}

	/**
	 * Read a token from the input stream.  There are only a few
	 * kinds of token that are recognised on input.  Other token
	 * types are recognised or constructed later on.
	 * @return A token.  Never returns null or throws an exception.
	 * Once end of file or an error occurs the routine will always return
	 * EOF.
	 */
	private Token readTok() {
		if (isEOF)
			return new Token(TokType.EOF);

		int c = readChar();

		if (c == -1) {
			isEOF = true;
			return new Token(TokType.EOF);
		}

		StringBuffer val = new StringBuffer();
		val.append((char) c);

		TokType tt;
		if (c == '\n') {
			tt = TokType.EOL;
		} else if (isSpace(c)) {
			while (isSpace(c = readChar()) && c != '\n')
				val.append((char) c);

			pushback = c;
			tt = TokType.SPACE;
		} else if (isWordChar(c)) {
			while (isWordChar(c = readChar()))
				val.append((char) c);
			pushback = c;
			tt = TokType.TEXT;
		} else {
			// A symbol.  The value has already been set.  At present
			// symbols do not combine, so that they are always a single
			// character.
			tt = TokType.SYMBOL;
		}

		Token t = new Token(tt);
		t.setValue(val.toString());
		return t;
	}

	private int readChar() {
		int c;
		if (pushback != NO_PUSHBACK) {
			c = pushback;
			pushback = 0;
			return c;
		}

		try {
			c = reader.read();
		} catch (IOException e) {
			isEOF = true;
			c = -1;
		}

		return c;
	}

	private boolean isSpace(int nextch) {
		return Character.isWhitespace(nextch);
	}

	private boolean isWordChar(int ch) {
		return Character.isLetterOrDigit(ch);
	}

	/**
	 * Iterate over tokens.  Tokens could be already on the <i>tokens</i>
	 * queue or we might have to read them from the input.  If they are
	 * read from the input they will be added to the queue.
	 */
	private class TokIterator implements Iterator<Token> {
		private final Iterator<Token> queueIter = tokens.iterator();
		private boolean qDone = !queueIter.hasNext();
		private boolean finished;

		/**
		 * There is a next token if there is one in the queue, or if there is
		 * another token that can be read.  If we have to read a token then
		 * it gets added to the queue.
		 */
		public boolean hasNext() {
			if (finished)
				return false;

			return true;
		}

		private Token loadTok() {
			Token t = readTok();
			tokens.add(t);

			if (t.getType() == TokType.EOF)
				finished = true;
			return t;
		}

		/**
		 * Return the next token.  Once the queue is finished we start reading
		 * from the input.  These read tokens will be added to the queue, but
		 * we do not go back to reading the queue once we have finished.
		 * @return The next token.
		 */
		public Token next() {
			if (finished)
				throw new NoSuchElementException("finished");

			Token t;
			if (qDone) {
				t = loadTok();
			} else {
				t = queueIter.next();
				if (!queueIter.hasNext())
					qDone = true;
			}
			if (t.getType() == TokType.EOF)
				finished = true;
			return t;
		}

		public void remove() {
			throw new UnsupportedOperationException("removal not supported");
		}
	}
}
