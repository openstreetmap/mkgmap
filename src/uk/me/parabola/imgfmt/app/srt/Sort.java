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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.ExitException;

/**
 * Represents the sorting positions for all the characters in a codepage.
 * @author Steve Ratcliffe
 */
public class Sort {

	private static final byte[] ZERO_KEY = new byte[3];

	private int codepage;
	private int id1; // Unknown - identifies the sort
	private int id2; // Unknown - identifies the sort

	private String description;
	private Charset charset;

	private final byte[] primary = new byte[256];
	private final byte[] secondary = new byte[256];
	private final byte[] tertiary = new byte[256];
	private final byte[] flags = new byte[256];

	private final List<CodePosition> expansions = new ArrayList<CodePosition>();
	private int maxExpSize = 1;

	private CharsetEncoder encoder;

	public void add(int ch, int primary, int secondary, int tertiary, int flags) {
		if (this.primary[ch & 0xff] != 0)
			throw new ExitException(String.format("Repeated primary index 0x%x", ch & 0xff));
		this.primary[ch & 0xff] = (byte) primary;
		this.secondary[ch & 0xff] = (byte) secondary;
		this.tertiary[ch & 0xff] = (byte) tertiary;

		this.flags[ch & 0xff] = (byte) flags;
	}

	/**
	 * Return a table indexed by a character value in the target codepage, that gives the complete sort
	 * position of the character.
	 * @return A table of sort positions.
	 */
	public char[] getSortPositions() {
		char[] tab = new char[256];

		for (int i = 1; i < 256; i++) {
			tab[i] = (char) (((primary[i] << 8) & 0xff00) | ((secondary[i] << 4) & 0xf0) | (tertiary[i] & 0xf));
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
		// If there is a cache then look up and return the key.
		// This is primarily for memory management, not for speed.
		byte[] key;
		if (cache != null) {
			key = cache.get(s);
			if (key != null)
				return new SrtSortKey<T>(object, key, second);
		}

		CharBuffer inb = CharBuffer.wrap(s);
		try {
			ByteBuffer out = encoder.encode(inb);
			byte[] bval = out.array();

			// In theory you could have a string where every character expands into maxExpSize separate characters
			// in the key.  However if we allocate enough space to deal with the worst case, then we waste a
			// vast amount of memory. So allocate a minimal amount of space, try it and if it fails reallocate the
			// maximum amount.
			//
			// We need +1 for the null bytes, we also +2 for a couple of expanded characters. For a complete
			// german map this was always enough in tests.
			key = new byte[(bval.length + 1 + 2) * 3];
			try {
				fillCompleteKey(bval, key);
			} catch (ArrayIndexOutOfBoundsException e) {
				// Ok try again with the max possible key size allocated.
				key = new byte[bval.length * 3 * maxExpSize + 3];
			}

			if (cache != null)
				cache.put(s, key);

			return new SrtSortKey<T>(object, key, second);
		} catch (CharacterCodingException e) {
			return new SrtSortKey<T>(object, ZERO_KEY);
		}
	}

	public <T> SortKey<T> createSortKey(T object, String s, int second) {
		return createSortKey(object, s, second, null);
	}

	public <T> SortKey<T> createSortKey(T object, String s) {
		return createSortKey(object, s, 0, null);
	}

	/**
	 * Fill in the key from the given byte string.
	 *
	 * @param bval The string for which we are creating the sort key.
	 * @param key The sort key. This will be filled in.
	 */
	private void fillCompleteKey(byte[] bval, byte[] key) {
		int start = fillKey(Collator.PRIMARY, primary, bval, key, 0);
		start = fillKey(Collator.SECONDARY, secondary, bval, key, start);
		fillKey(Collator.TERTIARY, tertiary, bval, key, start);
	}

	/**
	 * Fill in the output key for a given strength.
	 *
	 * @param sortPositions An array giving the sort position for each of the 256 characters.
	 * @param input The input string in a particular 8 bit codepage.
	 * @param outKey The output sort key.
	 * @param start The index into the output key to start at.
	 * @return The next position in the output key.
	 */
	private int fillKey(int type, byte[] sortPositions, byte[] input, byte[] outKey, int start) {
		int index = start;
		for (byte inb : input) {
			int b = inb & 0xff;

			int exp = (flags[b] >> 4) & 0x3;
			if (exp == 0) {
				// I am guessing that a sort position of 0 means that the character is ignorable at this
				// strength. In other words it is as if it is not present in the string.  This appears to
				// be true for shield symbols, but perhaps not for other kinds of control characters.
				byte pos = sortPositions[b];
				if (pos != 0)
					outKey[index++] = pos;
			} else {
				// now have to redirect to a list of input chars, get the list via the primary value always.
				byte idx = primary[b];
				//List<CodePosition> list = expansions.get(idx-1);

				for (int i = idx - 1; i < idx + exp; i++) {
					byte pos = expansions.get(i).getPosition(type);
					if (pos != 0)
						outKey[index++] = pos;
				}
			}
		}

		outKey[index++] = '\0';
		return index;
	}

	public byte getPrimary(int ch) {
		return primary[ch];
	}

	public byte getSecondary(int ch) {
		return secondary[ch];
	}

	public byte getTertiary(int ch) {
		return tertiary[ch];
	}

	public byte getFlags(int ch) {
		return flags[ch];
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
		if (codepage == 0)
			charset = Charset.forName("cp1252");
		else if (codepage == 65001)
			charset = Charset.forName("UTF-8");
		else if (codepage == 932)
			// Java uses "ms932" for code page 932
			// (Windows-31J, Shift-JIS + MS extensions)
			charset = Charset.forName("ms932");
		else
			charset = Charset.forName("cp" + codepage);
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
	 * @param bval The code point of this letter in the code page.
	 * @param inFlags The initial flags, eg if it is a letter or not.
	 * @param expansionList The letters that this letter sorts as, as code points in the codepage.
	 */
	public void addExpansion(byte bval, int inFlags, List<Byte> expansionList) {
		int idx = bval & 0xff;
		flags[idx] = (byte) ((inFlags & 0xf) | (((expansionList.size()-1) << 4) & 0x30));

		// Check for repeated definitions
		if (primary[idx] != 0)
			throw new ExitException(String.format("repeated code point %x", idx));

		primary[idx] = (byte) (expansions.size() + 1);
		secondary[idx] = 0;
		tertiary[idx] = 0;
		maxExpSize = Math.max(maxExpSize, expansionList.size());

		for (Byte b : expansionList) {
			CodePosition cp = new CodePosition();
			cp.setPrimary(primary[b & 0xff]);
			cp.setSecondary(secondary[b & 0xff]);
			cp.setTertiary((byte) (tertiary[b & 0xff] + 2));
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

	/**
	 * Create a default sort that simply sorts by the values of the characters.
	 * It has to pretend to be associated with a particular code page, otherwise
	 * it will not be recognised at all.
	 *
	 * This is not likely to be very useful. You need to create a sort description for your language
	 * to make things work properly.
	 *
	 * @return A default sort.
	 * @param codepage The code page that we are pretending to be.
	 */
	public static Sort defaultSort(int codepage) {
		Sort sort = new Sort();
		for (int i = 1; i < 256; i++) {
			sort.add(i, i, 0, 0, 0);
		}
		sort.charset = Charset.forName("ascii");
		sort.encoder = sort.charset.newEncoder();
		sort.setDescription("Default sort");
		sort.setCodepage(codepage);
		return sort;
	}

	public int getExpansionSize() {
		return expansions.size();
	}

	public String toString() {
		return String.format("sort cp=%d order=%08x", codepage, getSortOrderId());
	}

	/**
	 * A collator that works with this sort. This should be used if you just need to compare two
	 * strings against each other once.
	 *
	 * The sort key is better when the comparison must be done several times as in a sort operation.
	 */
	private class SrtCollator extends Collator {
		private final int codepage;

		private SrtCollator(int codepage) {
			this.codepage = codepage;
		}

		public int compare(String source, String target) {
			CharBuffer in1 = CharBuffer.wrap(source);
			CharBuffer in2 = CharBuffer.wrap(target);
			byte[] bytes1;
			byte[] bytes2;
			try {
				bytes1 = encoder.encode(in1).array();
				bytes2 = encoder.encode(in2).array();
			} catch (CharacterCodingException e) {
				throw new ExitException("character encoding failed unexpectedly", e);
			}

			int strength = getStrength();
			int res = compareOneStrength(bytes1, bytes2, primary, Collator.PRIMARY);

			if (res == 0 && strength != PRIMARY) {
				res = compareOneStrength(bytes1, bytes2, secondary, Collator.SECONDARY);
				if (res == 0 && strength != SECONDARY) {
					res = compareOneStrength(bytes1, bytes2, tertiary, Collator.TERTIARY);
				}
			}

			if (res == 0) {
				if (source.length() < target.length())
					res = -1;
				else if (source.length() > target.length())
					res = 1;
			}
			return res;
		}

		/**
		 * Compare the bytes against primary, secondary or tertiary arrays.
		 * @param bytes1 Bytes for the first string in the codepage encoding.
		 * @param bytes2 Bytes for the second string in the codepage encoding.
		 * @param typePositions The strength array to use in the comparison.
		 * @return Comparison result -1, 0 or 1.
		 */
		@SuppressWarnings({"AssignmentToForLoopParameter"})
		private int compareOneStrength(byte[] bytes1, byte[] bytes2, byte[] typePositions, int type) {
			int res = 0;

			PositionIterator it1 = new PositionIterator(bytes1, typePositions, type);
			PositionIterator it2 = new PositionIterator(bytes2, typePositions, type);

			while (it1.hasNext() && it2.hasNext()) {
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
			private final byte[] bytes;
			private final byte[] sortPositions;
			private final int len;
			private final int type;

			private int pos;

			private int expStart;
			private int expEnd;
			private int expPos;

			PositionIterator(byte[] bytes, byte[] sortPositions, int type) {
				this.bytes = bytes;
				this.sortPositions = sortPositions;
				this.len = bytes.length;
				this.type = type;
			}

			public boolean hasNext() {
				return pos < len || expPos != 0;
			}

			public Integer next() {
				int next;
				if (expPos == 0) {
					int in = pos++ & 0xff;
					byte b = bytes[in];
					int n = (flags[b & 0xff] >> 4) & 0x3;
					if (n > 0) {
						expStart = primary[b & 0xff] - 1;
						expEnd = expStart + n;
						expPos = expStart;
						next = expansions.get(expPos).getPosition(type);

						if (++expPos > expEnd)
							expPos = 0;

					} else {
						for (next = sortPositions[bytes[in] & 0xff]; next == 0 && pos < len; ) {
							next = sortPositions[bytes[pos++ & 0xff] & 0xff];
						}
					}
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
