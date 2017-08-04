/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package uk.me.parabola.imgfmt.app.net;

import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.BitWriter;
import uk.me.parabola.imgfmt.app.lbl.City;
import uk.me.parabola.imgfmt.app.lbl.Zip;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.CityInfo;
import uk.me.parabola.mkgmap.general.ZipCodeInfo;

import static uk.me.parabola.imgfmt.app.net.NumberStyle.*;

/**
 * Class to prepare the bit stream of the house numbering information.
 *
 * There are multiple ways to encode the same numbers, the trick is to find a way that is reasonably
 * small. We recognise a few common cases to reduce the size of the bit stream, but mostly just concentrating
 * on clarity and correctness. Optimisations only made a few percent difference at most.
 *
 * @author Steve Ratcliffe
 */
public class NumberPreparer {
	private static final Logger log = Logger.getLogger(NumberPreparer.class);
	private final List<Numbers> numbers;
	private boolean valid;

	// The minimum values of the start and end bit widths.
	private static final int START_WIDTH_MIN = 5;
	private static final int END_WIDTH_MIN = 2;

	private BitWriter bw;
	private boolean swappedDefaultStyle;
	CityZipWriter zipWriter;
	CityZipWriter cityWriter;

	public NumberPreparer(List<Numbers> numbers) {
		this.numbers = numbers;
		this.zipWriter = new CityZipWriter("zip", 0, 0);
		this.cityWriter = new CityZipWriter("city", 0, 0);
	}

	
	public NumberPreparer(List<Numbers> numbers, Zip zip, City city, int numCities, int numZips) {
		this.numbers = numbers;
		
		zipWriter = new CityZipWriter("zip",(zip == null) ? 0: zip.getIndex(), numZips);
		cityWriter = new CityZipWriter("city",(city == null) ? 0: city.getIndex(), numCities);
	}

	public boolean prepare(){
		fetchBitStream();
		if (!valid)
			return false;
		zipWriter.compile(numbers);
		cityWriter.compile(numbers);
		return true;
	}
	/**
	 * Make the bit stream and return it. This is only done once, if you call this several times
	 * the same bit writer is returned every time.
	 * @return A bit writer containing the computed house number stream.
	 */
	public BitWriter fetchBitStream() {
		if (bw != null)
			return bw;
		int initialValue = setup();

		// Write the bitstream
		bw = new BitWriter();

		try {

			// Look at the numbers and calculate some optimal values for the bit field widths etc.
			State state = new GatheringState(initialValue);
			process(new BitWriter(), state);

			// Write the initial values.
			writeWidths(state);
			writeInitialValue(state);

			state = new WritingState(state);
			process(bw, state);

			// If we get this far and there is something there, the stream might be valid!
			if (bw.getLength() > 1)
				valid = true;
		} catch (Abandon e) {
			log.error(e.getMessage());
			valid = false;
		}

		return bw;
	}
	
	/**
	 * Do some initial calculation and sanity checking of the numbers that we are to
	 * write.
	 * @return The initial base value that all other values are derived from.
	 */
	private int setup() {
		// Should we use the swapped default numbering style EVEN/ODD rather than
		// ODD/EVEN and the initialValue.
		for (Iterator<Numbers> iterator = numbers.listIterator(); iterator.hasNext(); ) {
			Numbers n = iterator.next();
			if (n.getLeftNumberStyle() == NONE && n.getRightNumberStyle() == NONE)
				iterator.remove();
		}
		if (numbers.isEmpty())
			throw new Abandon("no numbers");

		Numbers first = numbers.get(0);
		if (first.getLeftNumberStyle() == EVEN && first.getRightNumberStyle() == ODD)
			swappedDefaultStyle = true;

		// Calculate the initial value we want to use
		int initial = 0;
		if (first.getLeftNumberStyle() != NONE)
			initial = first.getLeftStart();

		int rightStart = 0;
		if (first.getRightNumberStyle() != NONE)
			rightStart = first.getRightStart();

		if (initial == 0)
			initial = rightStart;

		if (first.getLeftStart() > first.getLeftEnd() || first.getRightStart() > first.getRightEnd())
			initial = Math.max(initial, rightStart);
		else if (rightStart > 0)
			initial = Math.min(initial, rightStart);
		return initial;
	}

	/**
	 * Process the list of number ranges and compile them into a bit stream.
	 *
	 * This is done twice, once to calculate the sizes of the bit fields needed, and again
	 * to do the actual writing.
	 *
	 * @param bw The bit stream to write to.
	 * @param state Use to keep track of state during the construction process.
	 */
	private void process(BitWriter bw, State state) {
		if (swappedDefaultStyle)
			state.swapDefaults();

		int lastNode = -1;
		for (Numbers n : numbers) {
			if (!n.hasIndex())
				throw new Abandon("no r node set");
			// See if we need to skip some nodes
			if (n.getIndex() != lastNode + 1)
				state.writeSkip(bw, n.getIndex() - lastNode - 2);

			// Normal case write out the next node.
			state.setTarget(n);

			state.writeNumberingStyle(bw);
			state.calcNumbers();
			state.writeBitWidths(bw);
			state.writeNumbers(bw);
			state.restoreWriters();

			lastNode = n.getIndex();
		}
	}

	/**
	 * The initial base value is written out separately before anything else.
	 * All numbers are derived from differences from this value.
	 * @param state Holds the initial value to write.
	 */
	private void writeInitialValue(State state) {
		assert state.initialValue >= 0 : "initial value is not positive: " + state.initialValue;
		int width = 32 - Integer.numberOfLeadingZeros(state.initialValue);
		if (width > 20)
			throw new Abandon("Initial value too large: " + state.initialValue);

		if (width > 5) {
			bw.put1(false);
			bw.putn(width - 5, 4);
		} else {
			bw.put1(true);
			width = 5;
		}
		bw.putn(state.initialValue, width);
	}

	/**
	 * Write out a block that describes the number of bits to use. Numbers can be
	 * either all positive or all negative, or they can be signed and each bit field
	 * also has an extra sign bit. This is like how lines are encoded. See the LinePreparer
	 * class.
	 * @param state Holds the width information.
	 */
	private void writeWidths(State state) {
		state.getStartWriter().writeFormat();
		state.getEndWriter().writeFormat();
	}

	/**
	 * Returns true if the bit stream was calculated on the basis that the initial even/odd defaults
	 * should be swapped.
	 * @return True to signify swapped default, ie bit 0x20 in the net flags should be set.
	 */
	public boolean getSwapped() {
		return swappedDefaultStyle;
	}

	/**
	 * During development, any case that cannot be written correctly is marked invalid so it can
	 * be skipped on output.
	 *
	 * This will probably go away when complete.
	 *
	 * @return True if the preparer believes that the output is valid.
	 */
	public boolean isValid() {
		try {
			fetchBitStream();
		} catch (Exception e) {
		}
		return valid;
	}

	/**
	 * The current state of the writing process.
	 */
	static abstract class State {

		protected final Side left = new Side(true);
		protected final Side right = new Side(false);
		private int initialValue;

		State() {
			left.style = ODD;
			right.style = EVEN;
		}

		/**
		 * Set the initial value. All numbers are derived from this by adding differences.
		 */
		public void setInitialValue(int val) {
			initialValue = val;
			left.base = val;
			right.base = val;
		}

		/**
		 * Set the next number to output. Once the target is set, we then output commands to
		 * transform the current state into the target state.
		 * @param numbers The target numbers.
		 */
		public void setTarget(Numbers numbers) {
			left.setTargets(numbers.getLeftNumberStyle(), numbers.getLeftStart(), numbers.getLeftEnd());
			right.setTargets(numbers.getRightNumberStyle(), numbers.getRightStart(), numbers.getRightEnd());
		}

		/**
		 * If the target numbering style is different to the current one, then write out
		 * the command to change it.
		 */
		public void writeNumberingStyle(BitWriter bw) {
		}

		/**
		 * If we need a larger bit width for this node, then write out a command to
		 * change it. Changes are temporary and it reverts to the default after the
		 * next number output command.
		 */
		public void writeBitWidths(BitWriter bw) {
		}

		public void writeSkip(BitWriter bw, int n) {
		}

		/**
		 * Calculate the number difference to represent the current number range.
		 */
		public void calcNumbers() {
			if (left.style == NONE)
				left.base = right.base;

			equalizeBases();

			left.calc(right);
			right.calc(left);
		}

		/**
		 * See if we can set the bases of both sides of the road to be equal. Doesn't seem to be
		 * that useful, but does not cost any bits, as long as doing so doesn't cause you to write
		 * a difference when you wouldn't without.
		 * @return True if the bases have been set equal.  There are two cases, the left can be set equal to
		 * the right, or visa versa. The flags on the left/right objects will say which.
		 */
		private boolean equalizeBases() {
			left.equalized = right.equalized = false;

			// Don't if runs are in different directions
			if (left.direction != right.direction) {
				return false;
			}

			int diff = left.targetStart - left.base;

			// Do not lose the benefit of a 0 start.
			if (left.tryStart(left.base))
				diff = 0;

			if (right.tryStart(left.base + diff)) {
				left.equalized = true;
				right.base = left.base;
				left.startDiff = right.startDiff = diff;
				return true;
			}

			diff = right.targetStart - right.base;
			if (left.tryStart(right.base + diff)) {
				right.equalized = true;
				left.base = right.base;
				left.startDiff = right.startDiff = diff;
				return true;
			}

			return false;
		}

		/**
		 * Write the bit stream to the given bit writer.
		 *
		 * When this is called, all the calculations as to what is to be done have been made and
		 * it is just a case of translating those into the correct format.
		 *
		 * @param bw Bit writer to use. In the gathering phase this must be a throw away one.
		 */
		public void writeNumbers(BitWriter bw) {
			boolean doSingleSide = left.style == NONE || right.style == NONE;

			// Output the command that a number follows.
			bw.put1(true);

			boolean equalized = false;
			if (!doSingleSide) {
				equalized = left.equalized || right.equalized;
				bw.put1(equalized);
				if (equalized)
					bw.put1(left.equalized);
			}

			if (!doSingleSide) {
				bw.put1(!right.needOverride(left));
			}

			Side firstSide = left;
			if (doSingleSide && left.style == NONE)
				firstSide = right;

			boolean doStart = firstSide.startDiff != 0;
			boolean doEnd = firstSide.endDiff != 0;
			bw.put1(!doStart);
			bw.put1(!doEnd);

			if (doStart)
				writeStart(firstSide.startDiff);
			if (doEnd)
				writeEnd(firstSide.endDiff);

			firstSide.finish();

			if (doSingleSide) {
				left.base = right.base = firstSide.base;
				left.lastEndDiff = right.lastEndDiff = firstSide.lastEndDiff;
				return;
			}

			doStart = right.startDiff != 0;
			doEnd = right.endDiff != 0;

			if (!equalized)
				bw.put1(!doStart);
			if (right.needOverride(left))
				bw.put1(!doEnd);

			if (doStart && !equalized)
				writeStart(right.startDiff);
			if (doEnd)
				writeEnd(right.endDiff);

			right.finish();
		}

		protected void restoreWriters() {
		}

		/** Write a start difference */
		public abstract void writeStart(int diff);
		/** Write an end difference */
		public abstract void writeEnd(int diff);

		public abstract VarBitWriter getStartWriter();
		public abstract VarBitWriter getEndWriter();

		/**
		 * By default the left side of the road is odd numbered and the right even.
		 * Calling this swaps that around. If NONE or BOTH is needed then an explicit set of
		 * the numbering styles must be made.
		 */
		public void swapDefaults() {
			left.style = EVEN;
			right.style = ODD;
		}
	}

	/**
	 * Represents one side of the road.
	 */
	static class Side {
		private final boolean left;

		private NumberStyle style;
		private int base;

		// The calculated end number for the node. Might be different to the actual number
		// that are wanted that are in targetEnd.
		private int end;

		// These are the target start and end numbers for the node. The real numbers are different as there
		// is an adjustment applied.
		private NumberStyle targetStyle;
		private int targetStart;
		private int targetEnd;

		// Everything is represented as a difference from a previous value.
		private int startDiff;
		private int endDiff;
		private int lastEndDiff;

		// This is +1 if the numbers are ascending, and -1 if descending.
		private int direction;

		// Bases equalised to this side.
		private boolean equalized;

		Side(boolean left) {
			this.left = left;
		}

		/**
		 * Set the wanted values for start and end for this side of the road.
		 */
		public void setTargets(NumberStyle style, int start, int end) {
			this.targetStyle = style;
			this.targetStart = start;
			this.targetEnd = end;

			// In reality should use the calculated start and end values, not the targets. Real start and end
			// values are not ever the same (in this implementation) so that is why the case where start==end
			// is given the value +1.
			if (targetStart < targetEnd)
				direction = 1;
			else if (targetEnd < targetStart)
				direction = -1;
			else
				direction = 1;
		}

		/**
		 * Try a start value to see if it will work.  Obviously a value equal to the target will work
		 * but so will a value that equals it after rounding for odd/even.
		 * @param value The value to test.
		 * @return True if this value would result in the targetStart.
		 */
		private boolean tryStart(int value) {
			return value == targetStart || style.round(value, direction) == targetStart;
		}

		/**
		 *  For the right hand side, read and end value, or use the last end value as default.
		 *
		 * Otherwise, the same end diff is used for the right side as the left.
		 * @param left Reference to the left hand side.
		 */
		public boolean needOverride(Side left) {
			return endDiff != 0 || left.endDiff == 0;
		}

		/**
		 * There is more than one way to represent the same range of numbers. The idea is to pick one of
		 * the shorter ways. We don't make any effort to find the shortest, but just pick a reasonable
		 * strategy for some common cases, and making use of defaults where we can.
		 *
		 * @param other The details of the other side of the road.
		 *
		 */
		private void calc(Side other) {
			if (style == NONE)
				return;

			boolean equalized = this.equalized || other.equalized;

			if (!equalized)
				startDiff = tryStart(base)? 0: targetStart - base;

			endDiff = targetEnd - (base+startDiff) + direction;

			// Special for start == end, we can often do without an end diff.
			if (targetStart == targetEnd && base == targetStart && lastEndDiff == 0 && !equalized) {
				if (left || (other.endDiff == 0))
					endDiff = 0;
			}

			// Now that end is calculated we fix it and see if we can obtain it by default instead.
			end = base+startDiff+endDiff;

			if (left) {
				if (endDiff == lastEndDiff) endDiff = 0; // default is our last diff.

			} else if (other.style != NONE) {
				// right side (and left not NONE)
				if (other.endDiff == 0 && endDiff == lastEndDiff) endDiff = 0;   // No left diff, default is our last
				if (other.endDiff != 0 && other.endDiff == endDiff) endDiff = 0; // Left diff set, that's our default
			}
		}

		/**
		 * Called at the end of processing a number range. Sets up the fields for the next one.
		 */
		public void finish() {
			lastEndDiff = end - (base + startDiff);
			base = end;
		}
	}

	/**
	 * The calculations are run on this class first, which keeps track of the sizes required to
	 * write the values without actually writing them anywhere.
	 *
	 * When passing a BitWriter to any method on this class, it must be a throw away one, as it
	 * will actually be written to by some of the common methods.
	 */
	private class GatheringState extends State {
		class BitSizes {
			private boolean positive;
			private boolean negative;
			private int diff;

			private boolean isSigned() {
				return positive && negative;
			}

			private int calcWidth() {
				int n = diff;
				if (isSigned())
					n++;
				return 32 - Integer.numberOfLeadingZeros(n);
			}
		}

		private final BitSizes start = new BitSizes();
		private final BitSizes end = new BitSizes();

		public GatheringState(int initialValue) {
			setInitialValue(initialValue);
		}

		public void writeNumberingStyle(BitWriter bw) {
			left.style = left.targetStyle;
			right.style = right.targetStyle;
		}

		/**
		 * Calculate the size required for this write and keeps the maximum values.
		 * @param diff The value to examine.
		 */
		public void writeStart(int diff) {
			int val = testSign(start, diff);
			if (val > start.diff)
				start.diff = val;
		}

		/**
		 * Calculate the size required to hold this write and keeps the maximum.
		 * @param diff The value to be examined.
		 */
		public void writeEnd(int diff) {
			int val = testSign(end, diff);
			if (val > end.diff)
				end.diff = val;
		}

		/**
		 * Checks the sign properties required for the write.
		 */
		private int testSign(BitSizes bs, int val) {
			if (val > 0) {
				bs.positive = true;
			} else if (val < 0) {
				bs.negative = true;
				return -val;
			}
			return val;
		}

		/**
		 * Construct a writer that uses a bit width and sign properties that are sufficient to write
		 * all of the values found in the gathering phase. This is for start differences.
		 */
		public VarBitWriter getStartWriter() {
			return getVarBitWriter(start, START_WIDTH_MIN);
		}
		/**
		 * Construct a writer that uses a bit width and sign properties that are sufficient to write
		 * all of the values found in the gathering phase. This is for end differences.
		 */
		public VarBitWriter getEndWriter() {
			return getVarBitWriter(end, END_WIDTH_MIN);
		}

		/**
		 * Common code to create the bit writer.
		 * @see #getStartWriter()
		 * @see #getEndWriter()
		 */
		private VarBitWriter getVarBitWriter(BitSizes bs, int minWidth) {
			VarBitWriter writer = new VarBitWriter(bw, minWidth);
			if (bs.isSigned())
				writer.signed = true;
			else if (bs.negative)
				writer.negative = true;
			int width = bs.calcWidth();
			if (width > minWidth)
				writer.bitWidth = width - minWidth;
			if (writer.bitWidth > 15)
				throw new Abandon("Difference too large");
			return writer;
		}
	}

	/**
	 * This is used to actually write the bit stream.
	 * @see GatheringState
	 */
	static class WritingState extends State {

		private VarBitWriter startWriter;
		private VarBitWriter endWriter;
		private boolean restoreBitWriters;
		private final VarBitWriter savedStartWriter;
		private final VarBitWriter savedEndWriter;

		public WritingState(State state) {
			setInitialValue(state.initialValue);
			left.base = state.initialValue;
			right.base = state.initialValue;

			startWriter = state.getStartWriter();
			endWriter = state.getEndWriter();
			this.savedStartWriter = startWriter;
			this.savedEndWriter = endWriter;
		}

		public void writeStart(int diff) {
			startWriter.write(diff);
		}

		public void writeEnd(int diff) {
			endWriter.write(diff);
		}

		public void writeNumberingStyle(BitWriter bw) {
			if (left.targetStyle != left.style || right.targetStyle != right.style) {
				bw.putn(0, 2);
				bw.putn(left.targetStyle.getVal(), 2);
				bw.putn(right.targetStyle.getVal(), 2);
				left.style = left.targetStyle;
				right.style = right.targetStyle;
			}
		}

		/**
		 * You can change the number of bits and the sign properties of the writers before writing a nodes
		 * numbers.  We don't try and work out the optimum sequence, but use this for tricky cases where
		 * we fail to work out the correct sizes in advance.
		 *
		 * This routine means that we will always be using writers that will deal with the next node numbers.
		 *
		 * @param bw The output stream writer.
		 */
		public void writeBitWidths(BitWriter bw) {
			newWriter(bw, startWriter, left.startDiff, right.startDiff, true);
			newWriter(bw, endWriter, left.endDiff, right.endDiff, false);
		}

		/**
		 * Common code for writeBitWidths. Calculate the width and the sign properties required to
		 * represent the two numbers.
		 * @param leftDiff One of the numbers to be represented.
		 * @param rightDiff The other number to be represented.
		 * @param start Set to true if this is the start writer, else it is for the end writer.
		 */
		private void newWriter(BitWriter bw, VarBitWriter writer, int leftDiff, int rightDiff, boolean start) {
			if (!writer.checkFit(leftDiff) || !writer.checkFit(rightDiff)) {
				int min = Math.min(leftDiff, rightDiff);
				int max = Math.max(leftDiff, rightDiff);
				boolean signed = false;
				boolean negative = false;
				if (max < 0)
					negative = true;
				else if (min < 0)
					signed = true;

				int val = Math.max(Math.abs(min), Math.abs(max));
				int width = 32 - Integer.numberOfLeadingZeros(val);
				if (signed) width++;

				restoreBitWriters = true;
				VarBitWriter nw;
				if (start) {
					startWriter = nw = new VarBitWriter(bw, START_WIDTH_MIN, negative, signed, width);
					bw.putn(2, 4); // change width start
				} else {
					endWriter = nw = new VarBitWriter(bw, END_WIDTH_MIN, negative, signed, width);
					bw.putn(0xa, 4); // change width end (0x8 | 0x2)
				}
				nw.writeFormat();
			}
		}

		public void writeSkip(BitWriter bw, int n) {
			if (n < 0)
				throw new Abandon("bad skip value:" + n);

			bw.putn(6, 3);

			int width = 32 - Integer.numberOfLeadingZeros(n);
			if (width > 5) {
				bw.put1(true);
				width = 10;
			} else {
				bw.put1(false);
				width = 5;
			}
			bw.putn(n, width);
		}

		public VarBitWriter getStartWriter() {
			return startWriter;
		}

		public VarBitWriter getEndWriter() {
			return endWriter;
		}

		/**
		 * If we used an alternate writer for a node's numbers then we restore the default
		 * writers afterwards.
		 */
		protected void restoreWriters() {
			if (restoreBitWriters) {
				startWriter = savedStartWriter;
				endWriter = savedEndWriter;
				restoreBitWriters = false;
			}
		}
	}
	
}


/**
 * A bit writer that can be configured with different bit width and sign properties.
 *
 * The sign choices are:
 * negative: all numbers are negative and so can be represented without a sign bit. (or all positive
 * if this is false).
 * signed: numbers are positive and negative, and so have sign bit.
 *
 * The bit width is composed of two parts since it is represented as a difference between
 * a well known minimum value and the actual value.
 */
class VarBitWriter {
	private final BitWriter bw;
	private final int minWidth;
	int bitWidth;
	boolean negative;
	boolean signed;

	VarBitWriter(BitWriter bw, int minWidth) {
		this.bw = bw;
		this.minWidth = minWidth;
	}

	public VarBitWriter(BitWriter bw, int minWidth, boolean negative, boolean signed, int width) {
		this(bw, minWidth);
		this.negative = negative;
		this.signed = signed;
		if (width > minWidth)
			this.bitWidth = width - minWidth;
	}

	/**
	 * Write the number to the bit stream. If the number cannot be written
	 * correctly with this bit writer then an exception is thrown. This shouldn't
	 * happen since we check before hand and create a new writer if the numbers are not
	 * going to fit.
	 *
	 * @param n The number to be written.
	 */
	public void write(int n) {
		if (!checkFit(n))
			throw new Abandon("number does not fit bit space available");

		if (n < 0 && negative)
			n = -n;

		if (signed) {
			int mask = (1 << (minWidth + bitWidth+2)) - 1;
			n &= mask;
		}

		bw.putn(n, minWidth+bitWidth + ((signed)?1:0));
	}

	/**
	 * Checks to see if the number that we want to write can be written by this writer.
	 * @param n The number we would like to write.
	 * @return True if all is OK for writing it.
	 */
	boolean checkFit(int n) {
		if (negative) {
			if (n > 0)
				return false;
			else
				n = -n;
		} else if (signed && n < 0)
			n = -1 - n;

		int mask = (1 << minWidth + bitWidth) - 1;

		return n == (n & mask);
	}

	/**
	 * Write the format of this bit writer to the output stream. Used at the beginning and
	 * when changing the bit widths.
	 */
	public void writeFormat() {
		bw.put1(negative);
		bw.put1(signed);
		bw.putn(bitWidth, 4);
	}
}

/**
 * Exception to throw when we detect that we do not know how to encode a particular case.
 * This should not be thrown any more, when the preparers is called correctly.
 *
 * If it is, then the number preparer is marked as invalid and the data is not written to the
 * output file.
 */
class Abandon extends RuntimeException {
	Abandon(String message) {
		super("HOUSE NUMBER RANGE: " + message);
	}
}
	
class CityZipWriter {
	private ByteArrayOutputStream buf; 
	private final String type;
	private final int numItems;
	private final int defaultIndex;
	int []lastEncodedIndexes = {-1, -1};
	
	public CityZipWriter(String type, int defIndex, int numItems) {
		this.type = type;
		this.defaultIndex = defIndex;
		this.numItems = numItems;
		buf = new ByteArrayOutputStream();
	}
	
	public ByteArrayOutputStream getBuffer(){
		return buf;
	}

	public boolean compile(List<Numbers> numbers){
		try {
			// left and right entry in zip or city table
			int []indexes = {defaultIndex, defaultIndex}; // current num 
			int []refIndexes = {defaultIndex, defaultIndex}; // previous num 
			int lastEncodedNodeIndex = -1;
			boolean needsWrite = false;
			for (Numbers num : numbers){
				for (int side = 0; side < 2; side++){
					indexes[side] = defaultIndex;
					boolean left = (side == 0);
					switch (type) {
					case "zip":
						ZipCodeInfo zipInfo = num.getZipCodeInfo(left);
						if (zipInfo != null){
							if (zipInfo.getImgZip() != null){
								indexes[side] = zipInfo.getImgZip().getIndex();
							}
						}
						break;
					case "city": 
						CityInfo cityInfo = num.getCityInfo(left);
						if (cityInfo != null){
							if (cityInfo.getImgCity() != null){
								indexes[side] = cityInfo.getImgCity().getIndex();
							}
						}
						break;
					default:
						break;
					}
				}
				if (indexes[0] == refIndexes[0] && indexes[1] == refIndexes[1])
					continue;
				needsWrite = true;
				if (num.getIndex() > 0){
					int range = num.getIndex() - 1;
					if (lastEncodedNodeIndex > 0)
						range -= lastEncodedNodeIndex;
					encode(range, refIndexes);
				}
				refIndexes[0] = indexes[0];
				refIndexes[1] = indexes[1];
				lastEncodedNodeIndex = num.getIndex(); 
			}
			if (needsWrite){
				int lastIndexWithNumbers = numbers.get(numbers.size()-1).getIndex();
				int range = lastIndexWithNumbers - lastEncodedNodeIndex;
				encode(range, indexes);
			}
			else {
				buf.reset(); // probably not needed
			}
		} catch (Abandon e) {
			return false;
		}
		return true;
	}
	
	private void encode(int skip, int[] indexes) {
		// we can signal new values for left and / or right side 
		
		int sidesFlag = 0;  
		if (indexes[0] <= 0 && indexes[1] <= 0){
			sidesFlag |= 4; // signal end of a zip code/city interval
			if (indexes[0] == 0)
				sidesFlag |= 1;
			if (indexes[1] == 0)
				sidesFlag |= 2;
		} else {
			if (indexes[1] != indexes[0]){
				if (indexes[0] > 0 && indexes[0] != lastEncodedIndexes[0])
					sidesFlag |= 1;
				if (indexes[1] > 0 && indexes[1] != lastEncodedIndexes[1])
					sidesFlag |= 2;
			}
		}

		int initFlag = skip;
		if (initFlag > 31){
			// we have to write two bytes
			buf.write((byte) (initFlag & 0x1f | 0x7<<5));
			initFlag >>= 5;
		}
		initFlag |= sidesFlag << 5;
		buf.write((byte) (initFlag & 0xff));
		if ((sidesFlag & 4) == 0) {
			if (indexes[0] > 0 && (sidesFlag == 0 || (sidesFlag & 1) == 1))
				writeIndex(indexes[0]);
			if (indexes[1] > 0 && (sidesFlag & 2) != 0)
				writeIndex(indexes[1]);
		}
		lastEncodedIndexes[0] = indexes[0];
		lastEncodedIndexes[1] = indexes[1];
	}
	
	void writeIndex(int val){
		if (val <= 0)
			return;
		int ptrSize = Utils.numberToPointerSize(numItems);
		buf.write(val);
		if (ptrSize <= 1)
			return;
		buf.write(val >> 8);
		if (ptrSize <= 2)
			return;
		buf.write(val >> 16);
	}

}
