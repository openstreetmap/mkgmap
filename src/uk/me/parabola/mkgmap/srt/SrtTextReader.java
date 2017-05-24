/*
 * Copyright (C) 2010, 2011.
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

package uk.me.parabola.mkgmap.srt;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.app.srt.CodePosition;
import uk.me.parabola.imgfmt.app.srt.SRTFile;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.FileImgChannel;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokType;
import uk.me.parabola.mkgmap.scan.Token;
import uk.me.parabola.mkgmap.scan.TokenScanner;

/**
 * Read in a sort file from a text format.
 *
 * The file is in utf-8, regardless of the target codepage.
 *
 * The file should start with a codepage declaration, which determines the
 * target codepage for the sort.  This can be followed by a description which is
 * added into the SRT file.
 *
 * The characters are listed in order arranged in a way that shows the strength of the
 * difference between the characters. These are:
 *
 * Primary difference - different letters (eg a and b)
 * Secondary difference - different accents (eg a and a-acute)
 * Tertiary difference - different case (eg a and A)
 *
 * The sort order section begins with the word 'code'.
 *
 * Primary differences are represented by the less-than separator.
 * Secondary differences are represented by the semi-colon separator.
 * Tertiary differences are represented by the comma separator.
 *
 * Characters are represented in <emphasis>unicode (utf-8)</emphasis> (the whole file must be in utf-8).
 * Or alternatively you can use a four hex-digit number. A few special punctuation characters must
 * be written that way to prevent them being mistaken for separators.
 *
 * Example
 * <pre>
 * # This is a comment
 * codepage 1252
 * description "Example sort"
 * code a, A; â Â
 * < b, B
 * # Last two lines could be written:
 * # code a, A; â, Â < b, B
 * </pre>
 *
 * @author Steve Ratcliffe
 */
public class SrtTextReader {

	// States
	private static final int IN_INITIAL = 0;
	private static final int IN_CHARACTER = 1;
	private static final int IN_EXPAND = 2;
	private static final boolean EXPERIMENTAL = false;

	// Data that is read in, the output of the reading operation
	private final Sort sort = new Sort();

	private CharsetEncoder encoder;

	// Used during parsing.
	private int pos1;
	private int pos2;
	private int pos3;
	private int state;
	private String cflags = "";
	private Map<Integer, Integer> maxSec;
	private Map<Integer, Integer> maxTert;
	private List<CodePosition> expansions;

	public SrtTextReader(Reader r) throws IOException {
		this("stream", r);
	}

	private SrtTextReader(String filename) throws IOException {
		this(filename, new InputStreamReader(new FileInputStream(filename), "utf-8"));
	}

	private SrtTextReader(String filename, Reader r) throws IOException {
		maxSec = new HashMap<>();
		maxTert = new HashMap<>();
		expansions = new ArrayList<>();
		read(filename, r);
		maxSec = null;
		maxTert = null;
		expansions = null;
	}

	/**
	 * Find and read in the default sort description for the given codepage.
	 */
	public static Sort sortForCodepage(int codepage) {
		String name = "sort/cp" + codepage + ".txt";
		InputStream is = Sort.class.getClassLoader().getResourceAsStream(name);
		if (is == null) {
			if (codepage == 1252)
				throw new ExitException("No sort description for code-page 1252 available");

			Sort defaultSort = SrtTextReader.sortForCodepage(1252);
			defaultSort.setCodepage(codepage);
			defaultSort.setDescription("Default sort");
			return defaultSort;
		}

		try {
			InputStreamReader r = new InputStreamReader(is, "utf-8");
			SrtTextReader sr = new SrtTextReader(r);
			return sr.getSort();
		} catch (IOException e) {
			return SrtTextReader.sortForCodepage(codepage);
		}
	}

	/**
	 * Read in a file and save the information in a form that can be used
	 * to compare strings.
	 * @param filename The name of the file, used for display purposes. It need
	 * not refer to a file that actually exists.
	 * @param r The opened file or other readable source.
	 * @throws SyntaxException If the format of the file is incorrect.
	 */
	public void read(String filename, Reader r) {
		TokenScanner scanner = new TokenScanner(filename, r);
		resetPos();
		state = IN_INITIAL;
		while (!scanner.isEndOfFile()) {
			Token tok = scanner.nextToken();

			// We deal with whole line comments here
			if (tok.isValue("#")) {
				scanner.skipLine();
				continue;
			}

			switch (state) {
			case IN_INITIAL:
				initialState(scanner, tok);
				break;
			case IN_CHARACTER:
				characterState(scanner, tok);
				break;
			case IN_EXPAND:
				expandState(scanner, tok);
				break;
			}
		}
		sort.setExpansions(expansions);
		sort.finish();
	}

	/**
	 * The initial state, looking for a variable to set or a command to change
	 * the state.
	 * @param scanner The scanner for more tokens.
	 * @param tok The first token to process.
	 */
	private void initialState(TokenScanner scanner, Token tok) {
		String val = tok.getValue();
		TokType type = tok.getType();
		if (type == TokType.TEXT) {
			switch (val) {
			case "codepage":
				int codepage = scanner.nextInt();
				sort.setCodepage(codepage);
				encoder = sort.getCharset().newEncoder();
				break;
			case "description":
				sort.setDescription(scanner.nextWord());
				break;
			case "id1":
				sort.setId1(scanner.nextInt());
				break;
			case "id2":
				sort.setId2(scanner.nextInt());
				break;
			case "multi":
				sort.setMulti(true);
				break;

			case "code":  // The old name; use characters
			case "characters":
				if (encoder == null)
					throw new SyntaxException(scanner, "Missing codepage declaration before code");
				state = IN_CHARACTER;
				scanner.skipSpace();
				break;
			case "expand":
				state = IN_EXPAND;
				scanner.skipSpace();
				break;
			default:
				throw new SyntaxException(scanner, "Unrecognised command " + val);
			}
		}
	}

	/**
	 * Block consisting of characters and relations between them.
	 *
	 * The sort order is derived from this.
	 *
	 * @param scanner The scanner for more tokens.
	 * @param tok The current token to process.
	 */
	private void characterState(TokenScanner scanner, Token tok) {
		String val = tok.getValue();
		TokType type = tok.getType();
		if (type == TokType.TEXT) {
			switch (val) {
			case "flags":
				scanner.validateNext("=");
				cflags = scanner.nextWord();
				// TODO not yet
				break;
			case "pos": // Used to set the actual sort position value, not used any more
				scanner.validateNext("=");
				try {
					int newPos = Integer.decode(scanner.nextWord());
					if (newPos < pos1)
						throw new SyntaxException(scanner, "cannot set primary position backwards, was " + pos1);
					pos1 = newPos;
				} catch (NumberFormatException e) {
					throw new SyntaxException(scanner, "invalid integer for position");
				}
				break;
			case "pos2": // Used to set the actual sort position value, not used any more
				scanner.validateNext("=");
				pos2 = Integer.decode(scanner.nextWord());
				break;
			case "pos3": // Used to set the actual sort position value, not used any more
				scanner.validateNext("=");
				pos3 = Integer.decode(scanner.nextWord());
				break;
			case "code":  // the old name, use 'characters'
			case "characters":
				advancePos();
				break;
			case "expand":
				//scanner.pushToken(tok);
				state = IN_EXPAND;
				break;
			default:
				addCharacter(scanner, val);
				break;
			}
		} else if (type == TokType.SYMBOL) {
			switch (val) {
			case "=":
				break;
			case ",":
				pos3++;
				break;
			case ";":
				pos3 = 1;
				pos2++;
				break;
			case "<":
				advancePos();
				break;
			default:
				addCharacter(scanner, val);
				break;
			}

		}
	}

	/**
	 * Within an 'expand' command. The whole command is read before return, they can not span
	 * lines.
	 *
	 * @param tok The first token after the keyword.
	 */
	private void expandState(TokenScanner scanner, Token tok) {
		String val = tok.getValue();

		Code code = new Code(scanner, val).read();

		String s = scanner.nextValue();
		if (!s.equals("to"))
			throw new SyntaxException(scanner, "Expected the word 'to' in expand command");

		int secondary = 0;
		int tertiary = 0; 
		int num = 0;
		while (!scanner.isEndOfFile()) {
			Token t = scanner.nextRawToken();
			if (t.isEol())
				break;
			if (t.isWhiteSpace())
				continue;

			Code r = new Code(scanner, t.getValue()).read();

			CodePosition cp = new CodePosition();
			int b = r.getBval();
			int primary = sort.getPrimary(b);
			cp.setPrimary((char) primary);

			// We do not want the character to sort fully equal to the expanded characters (or any other
			// character so adjust the ordering at other strengths.  May need further tweaks.
			if (EXPERIMENTAL) {
				secondary = sort.getSecondary(b);
				tertiary = sort.getTertiary(b);
				if (num++ == 0) {
					Integer max = maxSec.get(primary);
					secondary += max == null ? 0 : max;
					if (charFlags(code.getCval()) == 1) {
						max = maxTert.get(primary);
						tertiary += max == null ? 0 : max;
					}
				} else {
					secondary = 1;
				}
				cp.setSecondary((byte) (secondary));
				cp.setTertiary((byte) (tertiary));
			} else {
				num++;
				secondary = sort.getSecondary(b) & 0xff;
				cp.setSecondary((byte) (secondary + 7));

				tertiary = sort.getTertiary(b) & 0xff;
				cp.setTertiary((byte) (tertiary + 2));
			}
			expansions.add(cp);
		}

		int flags = charFlags(code.getCval()) | (num-1) << 4;
		sort.add(code.getBval(), expansions.size() - num + 1, 0, 0, flags);
		state = IN_INITIAL;
	}

	/**
	 * Add a character to the sort table.
	 * @param scanner Input scanner, for line number information.
	 * @param val A single character string containing the character to be added. This will
	 * be either a single character which is the unicode representation of the character, or
	 * two or more characters which is the hex representation of the code point in the target codepage.
	 */
	private void addCharacter(TokenScanner scanner, String val) {
		Code code = new Code(scanner, val).read();
		setSortcode(code);
	}

	/**
	 * Set the sort code for the given 8-bit character.
	 * @param ch The same character in unicode.
	 */
	private void setSortcode(Code c) {
		int flags = charFlags(c.getCval());
		if (cflags.contains("0"))
			flags = 0;

		sort.add(c.getBval(), pos1, pos2, pos3, flags);
		this.cflags = "";

		if (EXPERIMENTAL) {
			Integer max = maxSec.get(pos1);
			if (max == null)
				max = pos2;
			else 
				max = Math.max(pos2, max);
			maxSec.put(pos1, max);
			max = maxTert.get(pos1);
			if (max == null)
				max = pos3;
			else 
				max = Math.max(pos3, max);
			maxTert.put(pos3, max);
		}
	}

	/**
	 * The flags that describe the kind of character. Known ones
	 * are letter and digit. There may be others.
	 * @param ch The actual character (unicode).
	 * @return The flags that apply to it.
	 */
	private int charFlags(int ch) {
		int flags = 0;
		if (Character.isLetter(ch) && (Character.getType(ch) & Character.MODIFIER_LETTER) == 0)
			flags = 1;
		if (Character.isDigit(ch))
			flags = 2;
		return flags;
	}

	/**
	 * Reset the position fields to their initial values.
	 */
	private void resetPos() {
		pos1 = 0;
		pos2 = 0;
		pos3 = 0;
	}

	/**
	 * Advance the major position value, resetting the minor position variables.
	 */
	private void advancePos() {
		if (pos1 == 0)
			pos1 = 1;
		else
			pos1 += pos2;
		pos2 = 1;
		pos3 = 1;
	}

	public Sort getSort() {
		return sort;
	}

	/**
	 * Read in a sort description text file and create a SRT from it.
	 * @param args First arg is the text input file, the second is the name of the output file. The defaults are
	 * in.txt and out.srt.
	 */
	public static void main(String[] args) throws IOException {
		String infile = "in.txt";
		if (args.length > 0)
			infile = args[0];

		String outfile = "out.srt";
		if (args.length > 1)
			outfile = args[1];
		try {
			Files.delete(Paths.get(outfile, ""));
		} catch (Exception e) {
		}
		ImgChannel chan = new FileImgChannel(outfile, "rw");
		SRTFile sf = new SRTFile(chan);

		SrtTextReader tr = new SrtTextReader(infile);
		Sort sort1 = tr.getSort();
		sf.setSort(sort1);
		sf.write();
		sf.close();
		chan.close();
	}

	/**
	 * Helper to represent a code read from the file.
	 *
	 * You can write it in unicode, or as a hex number.
	 * We work out what you wrote, and return both the code point in
	 * the codepage and the unicode character form of the letter.
	 */
	private class Code {
		private final TokenScanner scanner;
		private final String val;
		private int cval;
		private int bval;

		public Code(TokenScanner scanner, String val) {
			this.scanner = scanner;
			this.val = val;
		}

		/**
		 * Get the character encoded in the code-page encoding.
		 *
		 * It will be one byte for the format-9 code pages cp1252 etc.
		 * @return A character encoded in the code-page.
		 */
		public int getBval() {
			return bval;
		}

		/**
		 * Get the character in unicode.
		 *
		 * It will in general be a 2 byte value.
		 *
		 * @return The character expressed in unicode.
		 */
		public int getCval() {
			return cval;
		}

		public Code read() {
			try {
				if (val.length() == 1) {
					cval = val.charAt(0);
				} else {
					cval = Integer.parseInt(val, 16);
				}

				if (sort.isMulti()) {
					bval = cval;
				} else {
					CharBuffer cbuf = CharBuffer.wrap(new char[] {(char) cval});
					ByteBuffer out = encoder.encode(cbuf);
					if (out.remaining() > 1)
						throw new SyntaxException(scanner, "more than one character resulted from conversion of " + val);

					bval = out.get() & 0xff;
				}

			} catch (NumberFormatException e) {
				throw new SyntaxException(scanner, "Not a valid hex number " + val);
			} catch (CharacterCodingException e) {
				throw new SyntaxException(scanner, "Character not valid in character set '" + val + "'");
			}
			return this;
		}

		public String toString() {
			return String.format("%x", cval);
		}
	}
}
