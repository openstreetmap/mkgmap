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

import java.util.List;

import uk.me.parabola.imgfmt.app.BitReader;
import uk.me.parabola.imgfmt.app.BitWriter;
import uk.me.parabola.log.Logger;

import static uk.me.parabola.imgfmt.app.net.NumberStyle.EVEN;
import static uk.me.parabola.imgfmt.app.net.NumberStyle.ODD;

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

	private final List<Numbering> numbers;
	private boolean valid;

	// The minimum values of the start and end bit widths.
	private static final int START_WIDTH_MIN = 5;
	private static final int END_WIDTH_MIN = 2;

	private final State state;
	private BitWriter bw;

	public NumberPreparer(List<Numbering> numbers) {
		this.numbers = numbers;
		state = new State();
	}

	public BitWriter makeBitStream() {
		// Write the bitstream
		bw = new BitWriter();

		try {
			// Look at the numbers and calculate some optimal values for the bit field widths etc.
			analyze();

			// Write the initial values.
			writeWidths();
			writeInitialValue();

			for (Numbering n : numbers) {
				state.setTarget(n);

				state.writeNumberingStyle();
				state.writeBitWidths();
				if (state.needSkip())
					state.writeSkip();
				else
				state.writeNumbers(bw);
			}

			// TODO remove, just for debugging
			printBits(bw);

			// If we get this far and there is something there, the stream might be valid!
			if (bw.getLength() > 1)
				valid = true;
		} catch (Abandon e) {
			valid = false;
		}

		return bw;
	}

	/** For debugging */
	private void printBits(BitWriter bw) {
		StringBuilder sb = new StringBuilder();
		BitReader br = new BitReader(bw.getBytes());
		for (int i = 0; i < bw.getLength() * 8; i++) {
			sb.insert(0, br.get1() ? "1" : "0");
		}
		System.out.println(sb.toString());
	}

	/**
	 * Analyse the numbers to work out the best way of writing it. Need to obtain the optimal bit widths to
	 * use and note if the numbers are always positive or negative, or vary.
	 *
	 * Will probably have to do all the steps required to write without actually writing.
	 *
	 */
	private void analyze() {
		Numbering first = numbers.get(0);
		if (first.getLeftNumberStyle() != ODD || first.getRightNumberStyle() != EVEN)
			fail("initial even/odd");

		state.setInitialValue(Math.min(first.getLeftStart(), first.getRightStart()));

		// For now ignore actual numbers and just create some default writers.
		VarBitWriter vbw = new VarBitWriter(bw, START_WIDTH_MIN);
		vbw.bitWidth = 0;
		state.savedStartWriter = state.startWriter = vbw;

		vbw = new VarBitWriter(bw, END_WIDTH_MIN);
		vbw.bitWidth = 3;
		state.savedEndWriter = state.endWriter = vbw;
	}

	/**
	 * The initial base value is written out separately before anything else.
	 * All numbers are derived from differences from this value.
	 */
	private void writeInitialValue() {
		int width = calcWidth(state.initialValue);
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
	 */
	private void writeWidths() {
		state.startWriter.writeFormat();
		state.endWriter.writeFormat();
	}

	private int calcWidth(int n) {
		return 32 - Integer.numberOfLeadingZeros(n);
	}

	/**
	 * Temporary routine to bail out on an unimplemented condition.
	 */
	private static void fail(String msg) {
		System.out.println("NOT YET: " + msg);
		throw new Abandon();
	}

	/**
	 * Returns true if the bit stream was calculated on the basis that the initial even/odd defaults
	 * should be swapped.
	 * @return True to signify swapped default, ie bit 0x20 in the net flags should be set.
	 */
	public boolean getSwaped() {
		return false;
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

	static class Abandon extends RuntimeException {}

	/**
	 * The current state of the writing process.
	 */
	static class State {
		private VarBitWriter startWriter;
		private VarBitWriter endWriter;

		private boolean restoreBitWriters;
		private VarBitWriter savedStartWriter;
		private VarBitWriter savedEndWriter;

		private final Side left = new Side();
		private final Side right = new Side();
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
		public void setTarget(Numbering numbers) {
			left.setTargets(numbers.getLeftNumberStyle(), numbers.getLeftStart(), numbers.getLeftEnd());
			right.setTargets(numbers.getRightNumberStyle(), numbers.getRightStart(), numbers.getRightEnd());
		}

		/**
		 * If the target numbering style is different to the current one, then write out
		 * the command to change it.
		 */
		public void writeNumberingStyle() {
			if (left.targetStyle != left.style)
				fail("left numbering");
			if (right.targetStyle != right.style)
				fail("right numbering");
		}

		/**
		 * If we need a larger bit width for this node, then write out a command to
		 * change it. Changes are temporary and it reverts to the default after the
		 * next number output command.
		 */
		public void writeBitWidths() {
			// TODO
		}

		public boolean needSkip() {
			return false;
		}

		public void writeSkip() {
		}

		public void writeNumbers(BitWriter bw) {
			// write the number command sequence
			bw.put1(true);

			boolean equalizedBases = equalizeBases(bw);

			int startDiff = left.getStartDiff();
			int endDiff = left.getEndDiff();

			right.setOtherEndDiff(endDiff);

			boolean doRightOverride = right.needOverride();
			bw.put1(!doRightOverride);
			bw.put1(startDiff == 0);
			bw.put1(endDiff == 0);

			if (startDiff > 0)
				startWriter.write(startDiff);

			if (endDiff > 0)
				endWriter.write(endDiff);

			left.finish();

			startDiff = right.getStartDiff();
			endDiff = right.getEndDiff();

			if (!equalizedBases)
				bw.put1(startDiff == 0);
			if (doRightOverride)
				bw.put1(endDiff == 0);

			if (startDiff > 0)
				startWriter.write(startDiff);

			if (endDiff > 0)
				endWriter.write(endDiff);

			if (restoreBitWriters)
				fail("restore bit writers");
		}

		private boolean equalizeBases(BitWriter bw) {
			bw.put1(false); // Not yet
			return false;
		}
	}

	static class Side {
		private int base;

		// The calculated start and end numbers for the node. These might be different to the actual numbers
		// that are wanted that are in targetStart and End.
		private NumberStyle style;
		private int start;
		private int end;

		// These are the target start and end numbers for the node. The real numbers are different as there
		// is an adjustment applied.
		private NumberStyle targetStyle;
		private int targetStart;
		private int targetEnd;

		private int endAdj;
		private int roundDirection = 1;

		private int startDiff;
		private int endDiff;
		private int lastEndDiff;
		private int otherEndDiff;

		public void init() { // XXX incorrect?
			if (targetStart > targetEnd)
				endAdj = 1;
			else if (targetEnd < targetStart) {
				endAdj = -1;
				roundDirection = -1;
			}
			else
				endAdj = 0;

			otherEndDiff = -1;
		}

		public void setTargets(NumberStyle style, int start, int end) {
			this.targetStyle = style;
			this.targetStart = start;
			this.targetEnd = end;
			init();
		}

		/**
		 * Returns true if the start is equal to the base taking account the odd/even adjustment that will be
		 * made in the reading process.
		 * @return True if the start is the 'same' as the base after any adjustment.
		 */
		private boolean startCompatibleBase() {
			if (targetStart == base)
				return true;

			// Otherwise apply the rounding rules.
			int adjBase = round(base);
			if (targetStart == adjBase)
				return true;

			return false;
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

		public int getStartDiff() {
			int diff;
			if (startCompatibleBase())
				diff = 0;
			else
				diff = targetStart - base;
			startDiff = diff;
			return diff;
		}

		public int getEndDiff() {
			int diff = targetEnd - (base + startDiff) + 1;
			endDiff = diff;
			return diff;
		}

		public void setOtherEndDiff(int otherEndDiff) {
			this.otherEndDiff = otherEndDiff;
		}

		public void finish() {
			start = base + startDiff;
			end = start + endDiff;
			base = end;
			lastEndDiff = endDiff;
		}
	}

	static class VarBitWriter {
		private final BitWriter bw;
		private final int minWidth;
		private int bitWidth;
		private boolean negative;
		private boolean signed;

		VarBitWriter(BitWriter bw, int minWidth) {
			this.bw = bw;
			this.minWidth = minWidth;
		}

		public void write(int n) {
			if (n == 0)
				fail("zero");
			if (Integer.signum(n) != 1)
				fail("negative and zero not yet");

			int neededWidth = 32 - Integer.numberOfLeadingZeros(n);
			if (neededWidth > bitWidth + minWidth)
				fail("number too large " + n + ", (w=" + neededWidth); // when complete this will be a real error

			bw.putn(n, minWidth+bitWidth);
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
}