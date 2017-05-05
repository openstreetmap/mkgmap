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

package uk.me.parabola.imgfmt.app.srt;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.app.Label;

/**
 * Represents the sorting positions for all the characters in a codepage.
 *
 * A map contains a file that determines how the characters are to be sorted. So we
 * have to have to be able to create such a file and sort with exactly the same rules
 * as is contained in it.
 *
 * What about the java {@link java.text.RuleBasedCollator}? It turns out that it is possible to
 * make it work in the way we need it to, although it doesn't help with creating the srt file.
 * Also it is significantly slower than this implementation, so this one is staying. I also
 * found that sorting with the sort keys and the collator gave different results in some
 * cases. This implementation does not.
 *
 * Be careful when benchmarking. With small lists (< 10000 entries) repeated runs cause some
 * pretty aggressive optimisation to kick in. This tends to favour this implementation which has
 * much tighter loops that the java7 or ICU implementations, but this may not be realised with
 * real workloads.
 *
 * @author Steve Ratcliffe
 */
public class Sort {
	private static final byte[] ZERO_KEY = new byte[4];
	private static final Integer NO_ORDER = 0;

	private int codepage;
	private int id1; // Unknown - identifies the sort
	private int id2; // Unknown - identifies the sort

	private String description;
	private Charset charset;

	private final Page[] pages = new Page[256];

	private final List<CodePosition> expansions = new ArrayList<>();
	private int maxExpSize = 1;

	private CharsetEncoder encoder;
	private boolean multi;
	private int maxPage;

	public Sort() {
		pages[0] = new Page();
	}

	public void add(int ch, int primary, int secondary, int tertiary, int flags) {
		ensurePage(ch >>> 8);
		if (getPrimary(ch) != 0)
			throw new ExitException(String.format("Repeated primary index 0x%x", ch & 0xff));
		setPrimary (ch, primary);
		setSecondary(ch, secondary);
		setTertiary( ch, tertiary);

		setFlags(ch, flags);
	}

	/**
	 * Run after all sorting order points have been added.
	 *
	 * Make sure that all tertiary values of secondary ignorable are greater
	 * than any normal tertiary value.
	 *
	 * And the same for secondaries on primary ignorable.
	 */
	public void finish() {
		int maxSecondary = 0;
		int maxTertiary = 0;
		for (Page p : pages) {
			if (p == null)
				continue;

			for (int i = 0; i < 256; i++) {
				if (((p.flags[i] >>> 4) & 0x3) == 0) {
					if (p.getPrimary(i) != 0) {
						byte second = p.getSecondary(i);
						maxSecondary = Math.max(maxSecondary, second);
						if (second != 0) {
							maxTertiary = Math.max(maxTertiary, p.getTertiary(i));
						}
					}
				}
			}
		}

		for (Page p : pages) {
			if (p == null)
				continue;

			for (int i = 0; i < 256; i++) {
				if (((p.flags[i] >>> 4) & 0x3) != 0) continue;

				if (p.getPrimary(i) == 0) {
					if (p.getSecondary(i) == 0) {
						if (p.getTertiary(i) != 0) {
							p.setTertiary(i, p.getTertiary(i) + maxTertiary);
						}
					} else {
						p.setSecondary(i, p.getSecondary(i) + maxSecondary);
					}
				}
			}
		}
	}

	/**
	 * Return a table indexed by a character value in the target codepage, that gives the complete sort
	 * position of the character.
	 *
	 * This is only used for testing.
	 *
	 * @return A table of sort positions.
	 */
	public char[] getSortPositions() {
		char[] tab = new char[256];

		for (int i = 1; i < 256; i++) {
			tab[i] = (char) (((getPrimary(i) << 8) & 0xff00) | ((getSecondary(i) << 4) & 0xf0) | (getTertiary(i) & 0xf));
		}

		return tab;
	}

	/**
	 * Create a sort key for a given unicode string.  The sort key can be compared instead of the original strings
	 * and will compare based on the sorting represented by this Sort class.
	 *
	 * Using a sort key is more efficient if many comparisons are being done (for example if you are sorting a
	 * list of strings).
	 *
	 * @param object This is saved in the sort key for later retrieval and plays no part in the sorting.
	 * @param s The string for which the sort key is to be created.
	 * @param second Secondary sort key.
	 * @param cache A cache for the created keys. This is for saving memory so it is essential that this
	 * is managed by the caller.
	 * @return A sort key.
	 */
	public <T> SortKey<T> createSortKey(T object, String s, int second, Map<String, byte[]> cache) {
		if (s.length() == 0)
			return new SrtSortKey<>(object, ZERO_KEY, second);
		
		// If there is a cache then look up and return the key.
		// This is primarily for memory management, not for speed.
		byte[] key;
		if (cache != null) {
			key = cache.get(s);
			if (key != null)
				return new SrtSortKey<>(object, key, second);
		}

		try {
			char[] chars;
			if (isMulti()) {
				chars = s.toCharArray();
			} else {
				ByteBuffer out = encoder.encode(CharBuffer.wrap(s));
				byte[] bval = out.array();
				chars = new char[bval.length];
				for (int i = 0; i < bval.length; i++)
					chars[i] = (char) (bval[i] & 0xff);
			}
			key = makeKey(chars);
			if (cache != null)
				cache.put(s, key);

			return new SrtSortKey<>(object, key, second);
		} catch (CharacterCodingException e) {
			return new SrtSortKey<>(object, ZERO_KEY);
		}
	}

	/**
	 * Create a sort key based on a Label.
	 *
	 * The label will contain the actual characters (after transliteration for example)
	 * @param object This is saved in the sort key for later retrieval and plays no part in the sorting.
	 * @param label The label, the actual written bytes/chars will be used as input to the sort.
	 * @param second Secondary sort key.
	 * @param cache A cache for the created keys. This is for saving memory so it is essential that this
	 * is managed by the caller.
	 * @return A sort key.
	 */
	public <T> SortKey<T> createSortKey(T object, Label label, int second, Map<Label, byte[]> cache) {
		byte[] key;
		if (cache != null) {
			key = cache.get(label);
			if (key != null)
				return new SrtSortKey<>(object, key, second);
		}

		char[] encText = label.getEncText();
		int sep = -1;
		for (int i = 0; i < encText.length; i++) {
			char c = encText[i];
			if (c == 0x1e) {
				sep = i;
				break;
			}
		}
		if (sep > 0) {
//			System.out.println("sep = " + sep);
			char[] newEncText = new char[encText.length * 2];
			System.arraycopy(encText, sep + 1, newEncText, 0, encText.length - (sep + 1));
			System.arraycopy(encText, 0, newEncText, encText.length - (sep + 1), encText.length);

			encText = newEncText;
//			System.out.println(new String(encText));
		}
		 
		key = makeKey(encText);
		if (cache != null)
			cache.put(label, key);

		return new SrtSortKey<>(object, key, second);
	}

	/**
	 * Convenient version of create sort key method.
	 * @see #createSortKey(Object, String, int, Map)
	 */
	public <T> SortKey<T> createSortKey(T object, String s, int second) {
		return createSortKey(object, s, second, null);
	}

	/**
	 * Convenient version of create sort key method.
	 *
	 * @see #createSortKey(Object, String, int, Map)
	 */
	public <T> SortKey<T> createSortKey(T object, String s) {
		return createSortKey(object, s, 0, null);
	}

	public <T> SortKey<T> createSortKey(T object, Label label) {
		return createSortKey(object, label, 0, null);
	}

	public <T> SortKey<T> createSortKey(T object, Label label, int second) {
		return createSortKey(object, label, second, null);
	}

	/**
	 * Create the key and trim it to the needed length if that saves memory.
	 * @param chars character array
	 * @return byte array 
	 */
	private byte[] makeKey(char[] chars) {
		// In theory you could have a string where every character expands into maxExpSize separate characters
		// in the key.  However if we allocate enough space to deal with the worst case, then we waste a
		// vast amount of memory. So allocate a minimal amount of space, try it and if it fails reallocate the
		// maximum amount.
		//
		// We need +1 for the null bytes, we also +2 for a couple of expanded characters. For a complete
		// german map this was always enough in tests.
		byte[] key = new byte[(chars.length + 1 + 2) * 4];
		int needed = 0;
		try {
			needed = fillCompleteKey(chars, key);
		} catch (ArrayIndexOutOfBoundsException e) {
			// Ok try again with the max possible key size allocated.
			key = new byte[(chars.length+1) * 4 * maxExpSize];
			needed = fillCompleteKey(chars, key);
		}
		// check if we can save bytes by copying
		int neededBytes = needed;
		int padding2 = 8 - (needed & 7);
		if (padding2 != 8)
			neededBytes += padding2;
		if (neededBytes < key.length)
			key = Arrays.copyOf(key, needed);
		return key;
	}
 	
	/**
	 * Fill in the key from the given byte string.
	 *
	 * @param bVal The string for which we are creating the sort key.
	 * @param key The sort key. This will be filled in.
	 * @return the needed number of bytes in case the buffer was large enough
	 */
	private int fillCompleteKey(char[] bVal, byte[] key) {
		int start = fillKey(Collator.PRIMARY, bVal, key, 0);
		start = fillKey(Collator.SECONDARY, bVal, key, start);
		return fillKey(Collator.TERTIARY, bVal, key, start);
	}

	/**
	 * Fill in the output key for a given strength.
	 *
	 * @param input The input string in a particular 8 bit codepage.
	 * @param outKey The output sort key.
	 * @param start The index into the output key to start at.
	 * @return The next position in the output key.
	 */
	private int fillKey(int type, char[] input, byte[] outKey, int start) {
		int index = start;
		for (char c : input) {

			if (!hasPage(c >>> 8))
				continue;

			int exp = (getFlags(c) >> 4) & 0x3;
			if (exp == 0) {
				index = writePos(type, c, outKey, index);
			} else {
				// now have to redirect to a list of input chars, get the list via the primary value always.
				int idx = getPrimary(c);
				for (int i = idx - 1; i < idx + exp; i++) {
					int pos = expansions.get(i).getPosition(type);
					if (pos != 0) {
						if (type == Collator.PRIMARY)
							outKey[index++] = (byte) ((pos >>> 8) & 0xff);
						outKey[index++] = (byte) pos;
					}
				}
			}
		}

		if (type == Collator.PRIMARY)
			outKey[index++] = '\0';
		outKey[index++] = '\0';
		return index;
	}

	public int getPrimary(int ch) {
		return this.pages[ch >>> 8].getPrimary(ch);
	}

	public int getSecondary(int ch) {
		return this.pages[ch >>> 8].getSecondary(ch);
	}

	public int getTertiary(int ch) {
		return this.pages[ch >>> 8].getTertiary(ch);
	}

	public byte getFlags(int ch) {
		assert ch >= 0;
		return this.pages[ch >>> 8].flags[ch & 0xff];
	}

	public int getCodepage() {
		return codepage;
	}

	public Charset getCharset() {
		return charset;
	}

	public int getId1() {
		return id1;
	}

	public void setId1(int id1) {
		this.id1 = id1;
	}

	public int getId2() {
		return id2;
	}

	public void setId2(int id2) {
		this.id2 = id2 & 0x7fff;
	}

	/**
	 * Get the sort order as a single integer.
	 * A combination of id1 and id2. I think that they are arbitrary so may as well treat them as one.
	 *
	 * @return id1 and id2 as if they were a little endian 2 byte integer.
	 */
	public int getSortOrderId() {
		return (this.id2 << 16) + (this.id1 & 0xffff);
	}

	/**
	 * Set the sort order as a single integer.
	 * @param id The sort order id.
	 */
	public void setSortOrderId(int id) {
		id1 = id & 0xffff;
		id2 = (id >>> 16) & 0x7fff;
	}

	public void setCodepage(int codepage) {
		this.codepage = codepage;
		charset = charsetFromCodepage(codepage);

		encoder = charset.newEncoder();
		encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Add an expansion to the sort.
	 * An expansion is a letter that sorts as if it were two separate letters.
	 *
	 * The case were two letters sort as if the were just one (and more complex cases) are
	 * not supported or are unknown to us.
	 *
	 * @param ch The code point of this letter in the code page.
	 * @param inFlags The initial flags, eg if it is a letter or not.
	 * @param expansionList The letters that this letter sorts as, as code points in the codepage.
	 */
	public void addExpansion(int ch, int inFlags, List<Integer> expansionList) {
		ensurePage(ch >>> 8);
		setFlags(ch, (byte) ((inFlags & 0xf) | (((expansionList.size()-1) << 4) & 0xf0)));

		// Check for repeated definitions
		if (getPrimary(ch) != 0)
			throw new ExitException(String.format("repeated code point %x", ch));

		setPrimary(ch, (expansions.size() + 1));
		setSecondary(ch,  0);
		setTertiary(ch, 0);
		maxExpSize = Math.max(maxExpSize, expansionList.size());

		for (Integer b : expansionList) {
			CodePosition cp = new CodePosition();
			cp.setPrimary((char) getPrimary(b & 0xff));

			// We do not want the character to sort fully equal to the expanded characters (or any other
			// character so adjust the ordering at other strengths.  May need further tweaks.
			int secondary = getSecondary(b & 0xff);
			cp.setSecondary((byte) (secondary + 7));

			int tertiary = getTertiary(b & 0xff);
			cp.setTertiary((byte) (tertiary + 2));

			expansions.add(cp);
		}
	}

	/**
	 * Get the expansion with the given index, one based.
	 * @param val The one-based index number of the extension.
	 */
	public CodePosition getExpansion(int val) {
		return expansions.get(val - 1);
	}

	public Collator getCollator() {
		return new SrtCollator(codepage);
	}

	public int getExpansionSize() {
		return expansions.size();
	}

	public String toString() {
		return String.format("sort cp=%d order=%08x", codepage, getSortOrderId());
	}

	private void setPrimary(int ch, int val) {
		this.pages[ch >>> 8].setPrimary(ch, val);
	}

	private void setSecondary(int ch, int val) {
		this.pages[ch >>> 8].setSecondary(ch, val);
	}

	private void setTertiary(int ch, int val) {
		this.pages[ch >>> 8].setTertiary(ch, val);
	}

	private void setFlags(int ch, int val) {
		this.pages[ch >>> 8].flags[ch & 0xff] = (byte) val;
	}

	public static Charset charsetFromCodepage(int codepage) {
		Charset charset;
		switch (codepage) {
		case 0:
			charset = Charset.forName("ascii");
			break;
		case 65001:
			charset = Charset.forName("UTF-8");
			break;
		case 932:
			// Java uses "ms932" for code page 932
			// (Windows-31J, Shift-JIS + MS extensions)
			charset = Charset.forName("ms932");
			break;
		default:
			charset = Charset.forName("cp" + codepage);
			break;
		}
		return charset;
	}

	public void setMulti(boolean multi) {
		this.multi = multi;
	}

	public boolean isMulti() {
		return multi;
	}

	public int getPos(int type, int ch) {
		return pages[ch >>> 8].getPos(type, ch);
	}

	public int writePos(int type, int ch, byte[] outkey, int start) {
		return pages[ch >>> 8].writePos(type, ch, outkey, start);
	}

	/**
	 * Ensure that the given page exists in the page array.
	 *
	 * @param n The page index.
	 */
	private void ensurePage(int n) {
		assert n == 0 || isMulti();
		if (this.pages[n] == null) {
			this.pages[n] = new Page();
			if (n > maxPage)
				maxPage = n;
		}
	}

	/**
	 * The max page, top 8+ bits of the character that we have information on.
	 */
	public int getMaxPage() {
		return maxPage;
	}

	/**
	 * @return True if there is at least one character with the given page/block number.
	 */
	public boolean hasPage(int p) {
		return pages[p] != null;
	}

	/**
	 * Holds the sort positions of a 256 character block.
	 */
	private static class Page {
		private final char[] primary = new char[256];
		private final byte[] secondary = new byte[256];
		private final byte[] tertiary = new byte[256];
		private final byte[] flags = new byte[256];

		char getPrimary(int ch) {
			return primary[ch & 0xff];
		}

		void setPrimary(int ch, int val) {
			primary[ch & 0xff] = (char) val;
		}

		byte getSecondary(int ch) {
			return secondary[ch & 0xff];
		}

		void setSecondary(int ch, int val) {
			secondary[ch & 0xff] = (byte) val;
		}

		byte getTertiary(int ch) {
			return tertiary[ch & 0xff];
		}

		void setTertiary(int ch, int val) {
			tertiary[ch & 0xff] = (byte) val;
		}

		/**
		 * Get the sort position data for a given strength for a character.
		 * @param type The collation strength PRIMARY, SECONDARY etc.
		 * @param ch The character.
		 * @return The sorting weight for the given character.
		 */
		public int getPos(int type, int ch) {
			switch (type) {
			case Collator.PRIMARY:
				return getPrimary(ch) & 0xffff;
			case Collator.SECONDARY:
				return getSecondary(ch) & 0xff;
			case Collator.TERTIARY:
				return getTertiary(ch) & 0xff;
			default:
				assert false : "bad collation type passed";
				return 0;
			}
		}

		/**
		 * Write a sort position for a given character to a sort key.
		 * @param strength The sort strength type.
		 * @param ch The character.
		 * @param outKey The output key.
		 * @param start The offset into outKey, the new position is written here.
		 * @return The new start offset, after the key information has been written.
		 */
		public int writePos(int strength, int ch, byte[] outKey, int start) {
			int pos = getPos(strength, ch);
			if (pos != 0) {
				if (strength == Collator.PRIMARY)
					outKey[start++] = (byte) ((pos >> 8) & 0xff); // for 2 byte charsets
				outKey[start++] = (byte) (pos & 0xff);
			}
			return start;
		}
	}

	/**
	 * A collator that works with this sort. This should be used if you just need to compare two
	 * strings against each other once.
	 *
	 * The sort key is better when the comparison must be done several times as in a sort operation.
	 *
	 * This implementation has the same effect when used for sorting as the sort keys.
	 */
	private class SrtCollator extends Collator {
		private final int codepage;

		private SrtCollator(int codepage) {
			this.codepage = codepage;
		}

		public int compare(String source, String target) {
			if (source == target)
				return 0;
			
			char[] chars1;
			char[] chars2;
			if (isMulti()) {
				chars1 = source.toCharArray();
				chars2 = target.toCharArray();
			} else {
				CharBuffer in1 = CharBuffer.wrap(source);
				CharBuffer in2 = CharBuffer.wrap(target);
				try {
					byte[] bytes1 = encoder.encode(in1).array();
					byte[] bytes2 = encoder.encode(in2).array();
					chars1 = new char[bytes1.length];
					for (int i = 0; i < bytes1.length; i++)
						chars1[i] = (char) (bytes1[i] & 0xff);
					chars2 = new char[bytes2.length];
					for (int i = 0; i < bytes2.length; i++)
						chars2[i] = (char) (bytes2[i] & 0xff);
				} catch (CharacterCodingException e) {
					throw new ExitException("character encoding failed unexpectedly", e);
				}
			}

			int strength = getStrength();
			int res = compareOneStrength(chars1, chars2, Collator.PRIMARY);

			if (res == 0 && strength != PRIMARY) {
				res = compareOneStrength(chars1, chars2, Collator.SECONDARY);
				if (res == 0 && strength != SECONDARY) {
					res = compareOneStrength(chars1, chars2, Collator.TERTIARY);
				}
			}

			return res;
		}

		/**
		 * Compare the bytes against primary, secondary or tertiary arrays.
		 * @param char1 Bytes for the first string in the codepage encoding.
		 * @param char2 Bytes for the second string in the codepage encoding.
		 * @return Comparison result -1, 0 or 1.
		 */
		private int compareOneStrength(char[] char1, char[] char2, int type) {
			int res = 0;

			PositionIterator it1 = new PositionIterator(char1, type);
			PositionIterator it2 = new PositionIterator(char2, type);

			while (it1.hasNext() || it2.hasNext()) {
				int p1 = it1.next();
				int p2 = it2.next();

				if (p1 < p2) {
					res = -1;
					break;
				} else if (p1 > p2) {
					res = 1;
					break;
				}
			}

			return res;
		}

		public CollationKey getCollationKey(String source) {
			throw new UnsupportedOperationException("use Sort.createSortKey() instead");
		}

		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			SrtCollator that = (SrtCollator) o;

			if (codepage != that.codepage) return false;
			return true;
		}

		public int hashCode() {
			return codepage;
		}

		class PositionIterator implements Iterator<Integer> {
			private final char[] chars;
			private final int len;
			private final int type;

			private int pos;

			private int expStart;
			private int expEnd;
			private int expPos;

			PositionIterator(char[] chars, int type) {
				this.chars = chars;
				this.len = chars.length;
				this.type = type;
			}

			public boolean hasNext() {
				return pos < len || expPos != 0;
			}

			/**
			 * Get the next sort order value for the input string. Does not ever return values
			 * that are ignorable. Returns NO_ORDER at (and beyond) the end of the string, this
			 * value sorts less than any other and so makes shorter strings sort first.
			 * @return The next non-ignored sort position. At the end of the string it returns
			 * NO_ORDER.
			 */
			public Integer next() {
				int next;
				if (expPos == 0) {

					do {
						if (pos >= len) {
							next = NO_ORDER;
							break;
						}

						// Get the first non-ignorable at this level
						int c = chars[(pos++ & 0xff)];
						if (!hasPage(c >>> 8)) {
							next = 0;
							continue;
						}

						int nExpand = (getFlags(c) >> 4) & 0x3;
						// Check if this is an expansion.
						if (nExpand > 0) {
							expStart = getPrimary(c) - 1;
							expEnd = expStart + nExpand;
							expPos = expStart;
							next = expansions.get(expPos).getPosition(type);

							if (++expPos > expEnd)
								expPos = 0;
						} else {
							next = getPos(type, c);
						}

					} while (next == 0);
				} else {
					next = expansions.get(expPos).getPosition(type);
					if (++expPos > expEnd)
						expPos = 0;
				}

				return next;
			}

			public void remove() {
				throw new UnsupportedOperationException("remove not supported");
			}
		}
	}
}
