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

import uk.me.parabola.imgfmt.Utils;

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

		StringBuilder val = new StringBuilder();
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

	/**
	 * Read a single character.
	 * @return The next character, or -1 if at EOF. The isEOF field will also be set to true at end of file.
	 */
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
			} catch (IOException e) {
				c = -1;
			}

			// Finished a file, return to the including file if there was one.
			if (c == -1)
				popState();
		} while (!isEOF && c == -1);

		return c;
	}

	/**
	 * Finish the currently included file and return the state to start reading from the parent
	 * file.
	 *
	 * This is called when at the end of the current input file.
	 * If there are no more parent files then the end of file flag is set.
	 */
	private void popState() {
		// Close the current reader that is finished.
		Utils.closeFile(reader);

		if (states.isEmpty()) {
			isEOF = true;
			return;
		}

		ScanState state = states.removeFirst();
		state.copyTo(this);
	}

	private boolean isSpace(int nextch) {
		return Character.isWhitespace(nextch) || nextch == '\uFEFF';
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

	/**
	 * Read tokens until one of the given type and value is found and return the result as a single string.
	 * The searched token is not consumed from the input.
	 *
	 * @param type The token type to search for.
	 * @param value The string value of the token to search for.
	 * @return A single string of all the tokens preceding the searched token.
	 */
	public String readUntil(TokType type, String value) {
		StringBuilder sb = new StringBuilder();
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

		StringBuilder sb = new StringBuilder();
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

	/**
	 * Extra word characters are characters that should be considered as part of a word in addition
	 * to alphanumerics and underscore.
	 * @param extraWordChars A string containing all the characters to be considered part of a word.
	 */
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

	/**
	 * Include a new file in the token stream.
	 *
	 * Stop reading from the current file and save all the details about the file. Sets up to read from
	 * the included file.
	 *
	 * @param filename The name of the file that is being read. This is only used for messages and so doesn't
	 * have to be a name that can be directly opened for example.
	 * @param r The input reader for the file.
	 */
	public void includeFile(String filename, Reader r) {
		ScanState state = new ScanState(this);
		states.addFirst(state);

		reader = r;
		pushback = NO_PUSHBACK;
		isEOF = false;
		fileName = filename;
		linenumber = 1;
		tokens = new LinkedList<Token>();
		bol = true;
	}

	/**
	 * Saved state of scanning and individual file. Used when including files.
	 */
	private class ScanState {
		private final Reader reader;
		private final int pushback;

		private final String fileName;
		private final int linenumber;

		private final LinkedList<Token> tokens;

		private final boolean bol;

		/**
		 * Create this state with the state of the token scanner.
		 */
		public ScanState(TokenScanner ts) {
			reader = ts.reader;
			pushback = ts.pushback;
			fileName = ts.fileName;
			linenumber = ts.linenumber;
			tokens = ts.tokens;
			bol = ts.bol;
		}

		/**
		 * Copy this state to the given token scanner.
		 */
		public void copyTo(TokenScanner ts) {
			ts.reader = reader;
			ts.pushback = pushback;
			ts.fileName = fileName;
			ts.linenumber = linenumber;
			ts.tokens = tokens;
			ts.bol = bol;
		}
	}
}
