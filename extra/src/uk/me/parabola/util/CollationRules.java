/*
 * Copyright (C) 2014.
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
package uk.me.parabola.util;


import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

import com.ibm.icu.text.CollationElementIterator;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;

//import java.text.CollationElementIterator;
//import java.text.Collator;
//import java.text.RuleBasedCollator;

/**
 * Create a set of rules for a given code page.
 *
 * Should be usable, perhaps with a few tweaks.
 * Works with unicode too, need to choose which blocks to take for unicode.
 *
 * @author Steve Ratcliffe
 */
public class CollationRules {

	private CharsetDecoder decoder;
	private final NavigableSet<CharPosition> charmap = new TreeSet<>();
	private final NavigableSet<CharPosition> basemap = new TreeSet<>();
	private boolean isUnicode;
	private Charset charset;

	public static void main(String[] args) {
		String charsetName = args[0];
		CollationRules main = new CollationRules();
		main.go(charsetName);
	}

	private void go(String charsetName) {
		RuleBasedCollator col = (RuleBasedCollator) Collator.getInstance();

		charset = Charset.forName(charsetName);
		if (charsetName.equalsIgnoreCase("utf-8"))
			isUnicode = true;
		decoder = charset.newDecoder();

		List<Integer> blocks = Arrays.asList(0);
		if (isUnicode)
			blocks = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7);
		for (int block : blocks)
			addBlock(col, block);

		printCharMap();
		printExpansions();
	}

	private void addBlock(RuleBasedCollator col, int block) {
		for (int i = 0; i < 0x100; i++) {
			int ch = (block << 8) + i;
			String testString = getString(ch);
			char conv = testString.charAt(0);
			if (Character.getType(conv) == Character.UNASSIGNED || conv == 65533)
				continue;
			CollationElementIterator it = col.getCollationElementIterator(testString);

			System.out.printf("# %s ", fmtChar(testString.charAt(0)));
			int next;
			int index = 0;
			CharPosition cp = new CharPosition(0);
			while ((next = it.next()) != CollationElementIterator.NULLORDER) {
				if (index == 0) {
					cp = new CharPosition(ch);
					cp.setOrder(next);
				} else {
					if (next > 0xffff) {
						cp.addChar(new CharPosition(ch));
						cp.setOrder(next);
					} else {
						cp.addOrder(next, index);
					}
				}

				index++;
			}
			System.out.printf(" %s %d", cp, Character.getType(cp.getUnicode()));
			System.out.println();

			tweak(cp);
			if (ch > 0)
				charmap.add(cp);
			if (cp.nextChar == null)
				basemap.add(cp);
		}
	}

	private void tweak(CharPosition cp) {
		if (cp.val < 8)
			cp.third = cp.val + 7;
	}

	private String getString(int i) {
		if (isUnicode)
			return new String(new char[]{(char) i});
		else {
			byte[] b = {(byte) i};
			return new String(b, 0, 1, charset);
		}
	}

	private void printCharMap() {

		Formatter chars = new Formatter();
		Formatter comment = new Formatter();
		CharPosition last = new CharPosition(0);
		last.first = 0;
		for (CharPosition cp : charmap) {
			if (cp.isExpansion())
				continue;

			if (cp.first != last.first) {
				chars.format("\n < ");
				comment = new Formatter();
			} else if (cp.second != last.second) {
				chars.format(" ; ");
				comment.format(" ; ");
			} else if (cp.third != last.third) {
				chars.format(",");
				comment.format(",");
			} else {
				chars.format("=");
				comment.format("=");
			}
			last = cp;
			int uni = toUnicode(cp.val);
			chars.format("%s", fmtChar(uni));
			comment.format("U+%04x", uni);
		}

		System.out.println(chars);
	}

	private void printExpansions() {
		for (CharPosition cp : charmap) {
			if (!cp.isExpansion())
				continue;

			//noinspection MalformedFormatString
			System.out.printf("expand %c to ", cp.getUnicode());

			for (CharPosition cp2 = cp; cp2 != null; cp2 = cp2.nextChar) {
				cp2.second &= 0xff0000;
				cp2.third = cp2.third > 0x8f0000 ? 0x8f0000 - 1 : 0x000000;
				CharPosition floor = basemap.ceiling(cp2);
				if (floor == null) {
					System.out.printf(" NF");
					continue;
				}
				System.out.printf(" %s", fmtChar(floor.getUnicode()));
			}
			System.out.println();

			for (CharPosition cp2 = cp; cp2 != null; cp2 = cp2.nextChar) {
				cp2.second &= 0xff0000;
				cp2.third = cp2.third>0x8f0000? 0x8f0000-1: 0x000000;
				CharPosition floor = basemap.ceiling(cp2);
				if (floor == null) {
					System.out.println("#FIX: NF ref=" + cp2);
				} else {
					//System.out.println("floor is " + fmtChar(toUnicode(floor.val)) + ", " +
					//		"" + floor + ", ref is " + cp2);
				}
			}
		}
	}

	private String fmtChar(int val) {
		boolean asChar = true;
		switch (val) {
		case '<':
		case ';':
		case ',':
		case '=':
		case '#':
			asChar = false;
			break;
		default:

			switch (Character.getType(val)) {
			case Character.UNASSIGNED:
			case Character.NON_SPACING_MARK:
			case Character.FORMAT:
			case Character.CONTROL:
			case Character.SPACE_SEPARATOR:
			case Character.LINE_SEPARATOR:
			case Character.PARAGRAPH_SEPARATOR:
				asChar = false;
			}
		}

		if (asChar) {
			//noinspection MalformedFormatString
			return String.format("%c", val);
		} else {
			return String.format("%04x", val);
		}
	}

	private int toUnicode(int c) {
		if (isUnicode)
			return c;
		ByteBuffer b = ByteBuffer.allocate(1);
		b.put((byte) c);
		b.flip();
		try {
			CharBuffer chars = decoder.decode(b);
			return chars.charAt(0);
		} catch (CharacterCodingException e) {
			return '?';
		}
	}


	class CharPosition implements Comparable<CharPosition> {
		private final int val;
		private int first;
		private int second;
		private int third;
		private CharPosition nextChar;

		public CharPosition(int charValue) {
			this.val = charValue;
		}

		public int compareTo(CharPosition other) {
			if (other.first == first)
				return compareSecond(other);
			else if (first < other.first)
				return -1;
			else
				return 1;
		}

		private int compareSecond(CharPosition c2) {
			if (c2.second == second)
				return compareThird(c2);
			else if (second < c2.second)
				return -1;
			else
				return 1;
		}

		private int compareThird(CharPosition c2) {
			if (third == c2.third)
				return new Integer(val).compareTo(c2.val);
			else if (third < c2.third)
				return -1;
			else
				return 1;
		}

		public String toString() {
			Formatter fmt = new Formatter();
			toString(fmt);

			return fmt.toString();
		}

		private void toString(Formatter fmt) {
			fmt.format("[%04x %02x %02x]", first, second, third);
			if (nextChar != null)
				nextChar.toString(fmt);
		}

		public void setOrder(int next) {
			if (nextChar != null) {
				nextChar.setOrder(next);
				return;
			}
			first = (next >> 16) & 0xffff;
			second = (next << 8) & 0xff0000;
			third = (next << 16) & 0xff0000;
		}

		public void addOrder(int next, int count) {
			assert ((next >> 16) & 0xffff) == 0;
			if (this.nextChar != null) {
				this.nextChar.addOrder(next, count);
				return;
			}
			second += ((next >> 8) & 0xff) << (2-count)*8;
			third += ((next) & 0xff) << (2-count)*8;
		}

		public boolean isExpansion() {
			return nextChar != null;
		}

		public void addChar(CharPosition pos) {
			if (nextChar != null) {
				nextChar.addChar(pos);
				return;
			}
			nextChar = pos;
		}

		public int getUnicode() {
			return toUnicode(val);
		}
	}
}