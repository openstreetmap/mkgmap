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
import java.util.Deque;
import java.util.LinkedList;

/**
 * Read a file in terms of word and symbol tokens.
 *
 * @author Steve Ratcliffe
 */
public class TokenScanner {
	private static final int NO_PUSHBACK = 0;

	// Reading state
	private Reader reader;
	private int pushback = NO_PUSHBACK;
	private boolean isEOF;

	private String fileName;
	private int linenumber;

	private LinkedList<Token> tokens = new LinkedList<Token>();

	private boolean bol = true;

	// Included file state
	private final Deque<ScanState> states = new LinkedList<ScanState>();

	// Extra word characters.
	private String extraWordChars = "";
	private String commentChar = "#";

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
	 * Get and remove the next token. May return space or newline. This is the
	 * only place that a token is removed from the tokens queue.
	 */
	public Token nextRawToken() {
		ensureTok();

		if (bol) {
			bol = false;
			linenumber++;
		}

		Token token = tokens.removeFirst();
		if (token.getType() == TokType.EOL)
			bol = true;

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
	 * Push a token back to the beginning of the token queue.
	 * @param tok The token to add to the beginning of the queue.
	 */
	public void pushToken(Token tok) {
		tokens.push(tok);
	}

	/**
	 * Get the value of the next non-space token and consume the token.  You'd
	 * probably only call this after having peeked the type earlier.
	 * Any initial space is skipped.
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
			if (tokens.peek().isValue(commentChar)) {
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
			} else if (c == '&' || c == '|') {
				// Allow && and || as single symbols
				int c2 = readChar();
				if (c2 == c)
					val.append((char) c2);
				else
					pushback = c2;
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

		do {
			try {
				c = reader.read();
				if (c == -1)
					isEOF = true;
			} catch (IOException e) {
				if (states.isEmpty()) {
					isEOF = true;
					c = -1;
				} else {
					ScanState state = states.removeFirst();
					reader = state.reader;
					pushback = state.pushback;
					fileName = state.fileName;
					linenumber = state.linenumber;
					bol = state.bol;
					tokens = state.tokens;
					isEOF = false;
					c = -1;
				}
			}
		} while (!isEOF && c == -1);

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
	 * Read the tokens up until the end of the line and combine then
	 * into one string.
	 * 
	 * @return A single string, not including the newline terminator.  Never
	 * returns null, returns an empty string if there is nothing there.  The
	 * end of line is consumed.
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
	 * As {@link #nextWordWithInfo()} but just the string is returned.
	 * @return The next word as a string.  A quoted entity is regarded as a
	 * word for the purposes of this scanner.
	 */
	public String nextWord() {
		WordInfo info = nextWordWithInfo();
		return info.getText();
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
	 *
	 * The word string is returned along with a flag to indicate whether it
	 * was quoted or not.
	 */
	public WordInfo nextWordWithInfo() {
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
		return new WordInfo(sb.toString(), quotec != 0);
	}

	/**
	 * Check the value of the next token without consuming it.
	 *
	 * @param val String value to compare against.
	 * @return True if the next token has the same value as the argument.
	 */
	public boolean checkToken(String val) {
		skipSpace();
		Token tok = peekToken();
		if (val == null || tok.getValue() == null)
			return false;
		return val.equals(tok.getValue());
	}

	/**
	 * Validate the next word is the given value.  Space is skipped before
	 * checking, the checked value is consumed.  Use when you want to
	 * ensure that a required syntax element is present.
	 *
	 * The input will either be positioned after the required word or an
	 * exception will have been thrown.
	 * 
	 * @param val The string value to look for.
	 * @throws SyntaxException If the required string is not found.
	 */
	public void validateNext(String val) {
		skipSpace();
		Token tok = nextToken();
		if (val == null || !val.equals(tok.getValue()))
			throw new SyntaxException(this, "Expecting " + val + ", instead saw " + tok.getValue());
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

	/**
	 * The skip space routine, will skip all characters after a '#' until the end of the
	 * line as part of its skip white space functionality.
	 *
	 * This is a mis-feature if your comment character is not '#' or that character is
	 * sometimes important. Therefore you can turn this off by passing in an empty string here.
	 */
	public void setCommentChar(String commentChar) {
		if (commentChar == null)
			this.commentChar = "";
		else
			this.commentChar = commentChar;
	}

	public void includeFile(String filename, Reader r) {
		ScanState state = new ScanState();
		state.reader = reader;
		state.pushback = pushback;
		state.fileName = fileName;
		state.linenumber = linenumber;
		state.tokens = tokens;
		state.bol = bol;
		states.addFirst(state);

		reader = r;
		pushback = NO_PUSHBACK;
		isEOF = false;
		fileName = filename;
		linenumber = 1;
		tokens = new LinkedList<Token>();
		bol = true;
	}

	private class ScanState {
		private Reader reader;
		private int pushback;

		private String fileName;
		private int linenumber;

		private LinkedList<Token> tokens;

		private boolean bol;
	}
}
