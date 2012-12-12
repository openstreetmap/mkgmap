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

import java.util.Iterator;
import java.util.List;

import uk.me.parabola.imgfmt.app.BitWriter;
import uk.me.parabola.log.Logger;

import static uk.me.parabola.imgfmt.app.net.NumberStyle.*;

/**
 * Class to prepare the bit stream of the house numbering information.
 *
 * There are multiple ways to encode the same numbers, the trick is to find a way that is reasonably
 * small. Will start out with a basic implementation and then optimise as we go on.
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

	public NumberPreparer(List<Numbers> numbers) {
		this.numbers = numbers;
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
			process(bw, state);

			// Write the initial values.
			writeWidths(state);
			writeInitialValue(state);

			state = new WritingState(state);
			process(bw, state);

			// If we get this far and there is something there, the stream might be valid!
			if (bw.getLength() > 1)
				valid = true;
		} catch (Abandon e) {
			System.out.println(e.getMessage());
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

	private void process(BitWriter bw, State state) {
		if (swappedDefaultStyle)
			state.swapDefaults();

		int lastNode = -1;
		for (Numbers n : numbers) {
			if (!n.hasRnodNumber())
				throw new Abandon("no r node set");

			// See if we need to skip some nodes
			if (n.getRnodNumber() != lastNode + 1)
				state.writeSkip(bw, n.getRnodNumber() - lastNode - 2);

			// Normal case write out the next node.
			state.setTarget(n);

			state.writeNumberingStyle(bw);
			state.calcNumbers();
			state.writeBitWidths(bw);
			state.writeNumbers(bw);

			lastNode = n.getRnodNumber();
		}
	}

	/**
	 * The initial base value is written out separately before anything else.
	 * All numbers are derived from differences from this value.
	 * @param state Holds the initial value to write.
	 */
	private void writeInitialValue(State state) {
		assert state.initialValue > 0 : "initial value is not positive: " + state.initialValue;
		int width = 32 - Integer.numberOfLeadingZeros(state.initialValue);
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
		return valid;
	}


	/**
	 * The current state of the writing process.
	 */
	static abstract class State {

		protected final Side left = new Side();
		protected final Side right = new Side();
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

		public void calcNumbers() {
			if (left.style == NONE)
				left.base = right.base;

			equalizeBases();

			left.calcCommon(right, true);
			right.calcCommon(left, false);
		}

		private boolean equalizeBases() {
			left.equalized = false;
			right.equalized = false;
			return false;
		}

		public abstract void writeNumbers(BitWriter bw);

		public abstract VarBitWriter getStartWriter();
		public abstract VarBitWriter getEndWriter();

		public void swapDefaults() {
			left.style = EVEN;
			right.style = ODD;
		}
	}

	static class Side {
		private NumberStyle style;
		private int base;

		// The calculated start and end numbers for the node. These might be different to the actual numbers
		// that are wanted that are in targetStart and End.
		private int start;
		private int end;

		// These are the target start and end numbers for the node. The real numbers are different as there
		// is an adjustment applied.
		private NumberStyle targetStyle;
		private int targetStart;
		private int targetEnd;

		private int startDiff;
		private int endDiff;
		private int lastEndDiff;

		private int endAdj;
		private int roundDirection = 1;

		// Bases equalised to this side.
		private boolean equalized;

		public void init() {
			if (targetStart < targetEnd) {
				endAdj = 1;
				roundDirection = 1;
			} else if (targetEnd < targetStart) {
				endAdj = -1;
				roundDirection = -1;
			} else {
				endAdj = 0;
				roundDirection = 1;
			}
		}

		public void setTargets(NumberStyle style, int start, int end) {
			this.targetStyle = style;
			this.targetStart = start;
			this.targetEnd = end;
			init();
		}

		private boolean tryStart(int value) {
			return value == targetStart || round(value) == targetStart;
		}

		private boolean tryEnd(int value) {
			return (value + endAdj) == targetEnd || round(value + endAdj) == targetEnd;
		}

		private int round(int value) {
			int adjValue = value;
			if ((style == EVEN) && ((base & 1) == 1)) adjValue += roundDirection;
			if ((style == ODD) && ((base & 1) == 0)) adjValue += roundDirection;
			return adjValue;
		}

		public boolean needOverride() {
			return true;
		}

		private void calcCommon(Side side, boolean left) {
			if (style == NONE)
				return;

			if (targetStart == targetEnd) {
				// Deal with the case where the range is a single number. This makes it easier for
				// the general case below. Perhaps this special casing can be removed later.
				if (tryStart(base))
					startDiff = 0;
				else
					startDiff = targetStart - base;

				if (left) {
					// default left start diff is 0
					// default left end diff is the previous one
					if (base == targetStart && lastEndDiff == 0)
						endDiff = 0;
					else
						endDiff = (style==BOTH)? 1: 2;
				} else {
					// The default right start diff is 0
					// the default right end diff is the left end diff, unless we have doRightOverride or
					// a left end diff was not read, in which case it defaults to the last right end diff.
					if (base == targetStart && lastEndDiff == 0 && side.endDiff == 0)
						endDiff = 0;
					else
						endDiff = (style==BOTH)?1: 2;
				}
				return;
			}

			if (tryStart(base))
				startDiff = 0;
			else
				startDiff = targetStart - base;

			endDiff = targetEnd - (base + startDiff) + endAdj;
		}

		public void finish() {
			lastEndDiff = endDiff;
			start = base + startDiff;
			end = start + endDiff;
			base = end;
		}
	}

	private class GatheringState extends State {
		private boolean negative;
		private boolean positive;
		private int maxStartDiff;
		private int maxEndDiff;

		public GatheringState(int initialValue) {
			setInitialValue(initialValue);
		}

		public void writeNumberingStyle(BitWriter bw) {
			left.style = left.targetStyle;
			right.style = right.targetStyle;
		}

		public void writeNumbers(BitWriter bw) {
			int val = left.startDiff;
			val = testSign(val);
			if (val > maxStartDiff)
				maxStartDiff = val;

			val = right.startDiff;
			val = testSign(val);
			if (val > maxStartDiff)
				maxStartDiff = val;

			val = left.endDiff;
			val = testSign(val);
			if (val > maxEndDiff)
				maxEndDiff = val;

			val = right.endDiff;
			val = testSign(val);
			if (val > maxEndDiff)
				maxEndDiff = val;
		}

		private int testSign(int val) {
			if (val > 0) {
				positive = true;
			} else if (val < 0) {
				negative = true;
				return -val;
			}
			return val;
		}

		public VarBitWriter getStartWriter() {
			return getVarBitWriter(calcWidth(maxStartDiff), START_WIDTH_MIN);
		}

		public VarBitWriter getEndWriter() {
			return getVarBitWriter(calcWidth(maxEndDiff), END_WIDTH_MIN);
		}

		private int calcWidth(int n) {
			if (isSigned())
				n++;
			return 32 - Integer.numberOfLeadingZeros(n);
		}

		private boolean isSigned() {
			return positive && negative;
		}

		private VarBitWriter getVarBitWriter(int width, int minWidth) {
			VarBitWriter writer = new VarBitWriter(bw, minWidth);
			if (isSigned())
				writer.signed = true;
			else if (negative)
				writer.negative = true;
			if (width > minWidth)
				writer.bitWidth = width - minWidth;
			return writer;
		}
	}

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
				bw.put1(!right.needOverride());
			}

			Side firstSide = left;
			if (doSingleSide && left.style == NONE)
				firstSide = right;

			boolean doStart = firstSide.startDiff != 0;
			boolean doEnd = firstSide.endDiff != 0;
			bw.put1(!doStart);
			bw.put1(!doEnd);

			if (doStart)
				startWriter.write(firstSide.startDiff);
			if (doEnd)
				endWriter.write(firstSide.endDiff);

			firstSide.finish();

			if (doSingleSide) {
				left.base = right.base = firstSide.base;
				left.lastEndDiff = right.lastEndDiff = firstSide.lastEndDiff;
				restoreWriters();
				return;
			}

			doStart = right.startDiff != 0;
			doEnd = right.endDiff != 0;

			if (!equalized)
				bw.put1(!doStart);
			if (right.needOverride())
				bw.put1(!doEnd);

			if (doStart)
				startWriter.write(right.startDiff);
			if (doEnd)
				endWriter.write(right.endDiff);

			right.finish();
			restoreWriters();
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
		 * writer afterwards.
		 */
		private void restoreWriters() {
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
 * a well known minimum value and a the actual value.
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

class Abandon extends RuntimeException {
	Abandon(String message) {
		super("NOT YET " + message);
	}
}
