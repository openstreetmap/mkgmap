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
import java.util.LinkedList;

/**
 * Read a file in terms of word and symbol tokens.
 *
 * @author Steve Ratcliffe
 */
public class TokenScanner {
	private static final int NO_PUSHBACK = 0;
	private final Reader reader;
	private int pushback = NO_PUSHBACK;
	private boolean isEOF;

	private final String fileName;
	private int linenumber = 1;

	// Extra word characters.
	private String extraWordChars = "";

	private final LinkedList<Token> tokens = new LinkedList<Token>();

	public TokenScanner(String filename, Reader reader) {
		if (reader instanceof BufferedReader)
			this.reader = reader;
		else
			this.reader = new BufferedReader(reader);
		fileName = filename;
	}

	/**
	 * Peek and return the first token.  It is not consumed.
	 */
	public Token peekToken() {
		ensureTok();
		return tokens.peek();
	}

	/**
	 * Get and remove the next token.
	 */
	private Token nextRawToken() {
		ensureTok();

		Token token = tokens.removeFirst();
		if (token.getType() == TokType.EOL)
			linenumber++;
		return token;
	}

	/**
	 * Get the next token tht is not a space or newline.
	 * @return The first valid text or symbol token.
	 */
	public Token nextToken() {
		skipSpace();
		return nextRawToken();
	}

	/**
	 * Get the value of the next token and consume the token.  You'd
	 * probably only call this after having peek'ed the type earlier.
	 */
	public String nextValue() {
		skipSpace();
		return nextRawToken().getValue();
	}

	public boolean isEndOfFile() {
		if (tokens.isEmpty()) {
			return isEOF;
		} else {
			return tokens.peek().getType() == TokType.EOF;
		}
	}

	/**
	 * Skip any white space.  After calling this the next token
	 * will be end of file or something other than SPACE or EOL.
	 */
	public void skipSpace() {
		while (!isEndOfFile()) {
			ensureTok();
			if (tokens.peek().isValue("#")) {
				skipLine();
				continue;
			}
			if (!tokens.peek().isWhiteSpace())
				break;
			nextRawToken();
		}
	}

	/**
	 * Skip everything up to a new line token.  The new line
	 * token will be consumed, so the next token will the the first
	 * on a new line (or at EOF).
	 */
	public void skipLine() {
		while (!isEndOfFile()) {
			Token t = nextRawToken();
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
			// A symbol.  The value has already been set.  Some symbols
			// combine from multiple characters.
			if (c == '!' || c == '<' || c == '>') {
				c = readChar();
				if (c == '=')
					val.append('=');
				else
					pushback = c;
			}
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
			pushback = NO_PUSHBACK;
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
		return Character.isLetterOrDigit(ch)
				|| ch == '_'
				|| extraWordChars.indexOf(ch) >= 0;
	}

	/**
	 * Read the tokens up untill the end of the line and combine then
	 * into one string.
	 * 
	 * @return A single string, not including the newline terminator.  Never
	 * returns null, returns an empty string if there is nothing there.  The
	 * end of line is comsumed.
	 */
	public String readLine() {
		String res = readUntil(TokType.EOL, null);
		nextRawToken();  // use up new line
		return res;
	}

	public String readUntil(TokType type, String value) {
		StringBuffer sb = new StringBuffer();
		while (!isEndOfFile()) {
			Token t = peekToken();
			if (t.getType() == type && (value == null || value.equals(t.getValue())))
				break;
			sb.append(nextRawToken().getValue());
		}
		return sb.toString().trim();
	}

	/**
	 * Convenience routine to get an integer.  Skips space and reads a
	 * token.  This token is converted to an integer if possible.
	 * @return An integer as read from the next non space token.
	 * @throws NumberFormatException When the next symbol isn't
	 * a valid integer.
	 */
	public int nextInt() throws NumberFormatException {
		skipSpace();
		Token t = nextRawToken();
		if (t == null)
			throw new NumberFormatException("no number");

		return Integer.parseInt(t.getValue());
	}

	/**
	 * Read a string that can be quoted.  If it is quoted, then everything
	 * until the closing quotes is part of the string.  Both single
	 * and double quotes can be used.
	 *
	 * If there are no quotes then it behaves like nextToken apart from
	 * skipping space.
	 *
	 * Initial and final space is skipped.
	 */
	public String nextWord() {
		skipSpace();
		Token tok = peekToken();
		char quotec = 0;
		if (tok.getType() == TokType.SYMBOL) {
			String s = tok.getValue();
			if ("'".equals(s) || "\"".equals(s)) {
				quotec = s.charAt(0);
				nextRawToken();
			}
		}

		StringBuffer sb = new StringBuffer();
		while (!isEndOfFile()) {
			tok = nextRawToken();
			if (quotec == 0) {
				sb.append(tok.getValue());
				break;
			} else {
				if (tok.isValue(String.valueOf(quotec)))
					break;
				sb.append(tok.getValue());
			}
		}
		skipSpace();
		return sb.toString();
	}

	public boolean checkToken(String val) {
		skipSpace();
		Token tok = peekToken();
		if (val == null || tok.getValue() == null)
			return false;
		return val.equals(tok.getValue());
	}

	public int getLinenumber() {
		return linenumber;
	}

	public String getFileName() {
		return fileName;
	}

	public void setExtraWordChars(String extraWordChars) {
		this.extraWordChars = extraWordChars;
	}
}
