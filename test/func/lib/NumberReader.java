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
package func.lib;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.BitReader;
import uk.me.parabola.imgfmt.app.net.NumberStyle;
import uk.me.parabola.imgfmt.app.net.Numbers;

import static uk.me.parabola.imgfmt.app.net.NumberStyle.*;


/**
 * This is a test reader of the numbering streams. Since there are multiple ways of writing
 * the same set of house numbers, the only reasonable way of testing the write process is to
 * read the bit stream back and compare with the intended numbers.
 *
 * There is no attempt at efficiency given it is for testing, but it is believed to correctly
 * read numbers from any map.
 *
 * This code is derived directly from the NetDisplay class in the display project, so see that
 * to see the development of this file.
 * The algorithm that is required to read the bit stream was partly derived by studying the
 * the released GPL code of cGPSmapper by Stanislaw Kozicki.
 *
 * @author Steve Ratcliffe
 */
public class NumberReader {

	private final BitReader br;

	// For reading the start differences and end difference numbers.
	private VarBitReader startReader;
	private VarBitReader endReader;
	private VarBitReader savedStartReader;
	private VarBitReader savedEndReader;
	private boolean doRestoreBitWidths;

	// base numbers
	private int leftBase;
	private int rightBase;

	// numbering styles
	private NumberStyle leftStyle = ODD;
	private NumberStyle rightStyle = EVEN;

	// start numbers
	private int leftStart;
	private int rightStart;

	// end numbers
	private int leftEnd;
	private int rightEnd;

	// saved end numbers
	private int leftLastEndDiff;
	private int rightLastEndDiff;

	// Numbers are a range between nodes. Keep count of them here
	private int nodeCounter;

	// Track if the last thing we read were numbers, help to determine end of stream.
	private boolean lastReadNumbers;

	public NumberReader(BitReader br) {
		this.br = br;
	}

	/**
	 * Read the numbers into a list of Numbers classes.
	 * @param swap If the default starting position of left=ODD right=EVEN should be swapped.
	 * @return A list of the numbers that the input stream represents.
	 */
	public List<Numbers> readNumbers(boolean swap) {
		if (swap) {
			leftStyle = EVEN;
			rightStyle = ODD;
		}

		getBitWidths();

		getInitialBase();

		List<Numbers> numbers = new ArrayList<Numbers>();

		// To do this properly we need to know the number of nodes I think, this is the
		// best we can do: if there are more than 8 bits left, there must be another command
		// left.  We could leave a short command at the end.
		while ((br.getBitPosition() < br.getNumberOfBits() - 8) || !lastReadNumbers) {
			runCommand(numbers);
		}

		return numbers;
	}


	/**
	 * Get the bit widths for the start and end differences.
	 * Based on code for reading the RGN streams, but the signed bit is the
	 * opposite value.
	 * x is for start value differences.  y is for end value differences.
	 */
	private void getBitWidths() {
		startReader = new VarBitReader(br, 5);
		endReader = new VarBitReader(br, 2);
	}

	/**
	 * Decode the next command in the stream and run it.
	 * @param numbers When numbers are read, they are saved here.
	 */
	private void runCommand(List<Numbers> numbers) throws NumberException {
		int cmd = readCommand(); // fetch 1, 3 skip, 2 reload, 0 style

		switch (cmd) {
		case 0:
			changeStyles();
			break;
		case 1:
			fetchNumbers(numbers);
			break;
		case 2:
			useBits();
			break;
		case 6:
			skipNodes();
			break;
		default:
			fail("unimplemented command: " + cmd);
		}
	}

	/**
	 * Temporarily use a different bit width for the following number fetch.
	 */
	private void useBits() {
		if (!doRestoreBitWidths) {
			savedStartReader = startReader;
			savedEndReader = endReader;
		}
		doRestoreBitWidths = true;

		if (br.get1()) {
			endReader = new VarBitReader(br, 2);
		} else {
			startReader = new VarBitReader(br, 5);
		}
	}

	/**
	 * Skip nodes. For parts of a road that has no numbers.
	 */
	private void skipNodes() {
		boolean f = br.get1();
		int skip;
		if (f)
			skip = 1 + br.get(10);
		else
			skip = 1 + br.get(5);
		nodeCounter += skip;
	}

	/**
	 * Read the next command from the stream. Commands are variable length in the bit
	 * stream.
	 * 0 - numbering style (none, odd, even, both)
	 * 1 - fetch numbers
	 * 2 - change bit widths
	 * 6 - skip nodes
	 * @return The command number
	 */
	private int readCommand() {
		int cmd = 0;
		if (br.get1()) {
			cmd |= 0x1;
			lastReadNumbers = true;
		} else {
			lastReadNumbers = false;
			if (br.get1()) {
				cmd |= 0x2;
				if (br.get1()) {
					cmd |= 0x4;
				}
			}
		}
		return cmd;
	}

	/**
	 * Read the house numbers for a stretch of road.
	 *
	 * The start and end positions of the the left hand side of the road is first, followed
	 * by the right hand side of the road.
	 *
	 * The differences to the last point are stored. It is also possible to
	 * @param numbers When numbers are read, they are saved here.
	 */
	private void fetchNumbers(List<Numbers> numbers) {

		// If one side has no numbers, then there is only one set of numbers to calculate, but
		// changes to base are applied to both sides.
		boolean doSingleSide = (leftStyle == NONE || rightStyle == NONE);

		if (leftStyle == NONE)
			leftBase = rightBase;

		// Check for command to copy the base number
		boolean doSameBase = false;
		if (!doSingleSide) {
			doSameBase = br.get1();
			if (doSameBase)
				copyBase();
		}

		//int abc = br.get(3);
		boolean doRightOverride = false;
		if (!doSingleSide)
			doRightOverride = !br.get1();
		boolean doReadStart = !br.get1();
		boolean doReadEnd = !br.get1();

		//item.addText("cmd: fetch numbers abc: %x", abc);

		int startDiff = 0, endDiff = leftLastEndDiff;

		if (doReadStart) {
			startDiff = startReader.read();
		}
		if (doReadEnd) {
			endDiff = endReader.read();
		}

		leftStart = leftBase + startDiff;
		leftEnd = leftStart + endDiff;

		leftBase = leftEnd;
		leftLastEndDiff = endDiff;

		if (doSingleSide) {
			printSingleSide(numbers);
			restoreReaders();
			return;
		}

		// *** Now for the right hand side numbers ***

		// Note that endDiff falls through to this part, but startDiff doesn't
		startDiff = 0;

		// If we didn't read an endDiff value for the left side or right is different then
		// default to the saved value.
		if (doRightOverride || !doReadEnd)
			endDiff = rightLastEndDiff;

		doReadStart = false;
		doReadEnd = false;

		if (!doSameBase)
			doReadStart = !br.get1();

		if (doRightOverride)
			doReadEnd = !br.get1();

		if (doReadStart)
			startDiff = startReader.read();

		if (doReadEnd)
			endDiff = endReader.read();

		rightStart = rightBase + startDiff;
		rightEnd = rightStart + endDiff;

		rightBase = rightEnd;
		rightLastEndDiff = endDiff;

		adjustValues();

		Numbers n = new Numbers();
		n.setNodeNumber(nodeCounter);

		n.setLeftNumberStyle(leftStyle);
		n.setLeftStart(leftStart);
		n.setLeftEnd(leftEnd);

		n.setRightNumberStyle(rightStyle);
		n.setRightStart(rightStart);
		n.setRightEnd(rightEnd);

		numbers.add(n);
		nodeCounter++;

		restoreReaders();
	}

	/**
	 * After a temporary bit width change.
	 */
	private void restoreReaders() {
		if (doRestoreBitWidths) {
			startReader = savedStartReader;
			endReader = savedEndReader;
			doRestoreBitWidths = false;
		}
	}

	/**
	 * If the road has numbers on just one side, then there is a shortened reading routine.
	 * The left variables are mostly used during reading regardless of which side of the
	 * road has numbers. Make everything work here.
	 * @param numbers The output list that the number record should be added to.
	 */
	private void printSingleSide(List<Numbers> numbers) {
		rightBase = leftBase;
		rightStart = leftStart;
		rightEnd = leftEnd;
		rightLastEndDiff = leftLastEndDiff;
		adjustValues();

		Numbers n = new Numbers();
		if (leftStyle == NONE) {
			n.setNodeNumber(nodeCounter);
			n.setRightNumberStyle(rightStyle);
			n.setRightStart(rightStart);
			n.setRightEnd(rightEnd);

			n.setLeftNumberStyle(NONE);
			n.setLeftStart(-1);
			n.setLeftEnd(-1);
		}
		else {
			n.setNodeNumber(nodeCounter);
			n.setLeftNumberStyle(leftStyle);
			n.setLeftStart(leftStart);
			n.setLeftEnd(leftEnd);

			n.setRightNumberStyle(NONE);
			n.setRightStart(-1);
			n.setRightEnd(-1);
		}
		numbers.add(n);
		nodeCounter++;
	}

	/**
	 * When it is known if the numbers are odd or even, then a shorter bitstream is made
	 * by taking advantage of that fact. This leaves the start and end points needing
	 * adjustment to made them odd or even as appropriate.
	 */
	private void adjustValues() {
		int ldirection = 1; // direction start is adjusted in; end in the opposite direction.
		if (leftStart < leftEnd)
			leftEnd--;
		else if (leftStart > leftEnd) {
			leftEnd++;
			ldirection = -1;
		}

		int rdirection = 1; // direction start is adjusted in; end in the opposite direction.
		if (rightStart < rightEnd)
			rightEnd--;
		else if (rightStart > rightEnd) {
			rightEnd++;
			rdirection = -1;
		}

		if (leftStyle == EVEN) {
			if ((leftStart & 1) == 1) leftStart += ldirection;
			if ((leftEnd & 1) == 1) leftEnd -= ldirection;
		} else if (leftStyle == ODD) {
			if ((leftStart & 1) == 0) leftStart+=ldirection;
			if ((leftEnd & 1) == 0) leftEnd-=ldirection;
		}
		if (rightStyle == EVEN) {
			if ((rightStart & 1) == 1) rightStart+=rdirection;
			if ((rightEnd & 1) == 1) rightEnd-=rdirection;
		} else if (rightStyle == ODD) {
			if ((rightStart & 1) == 0) rightStart+=rdirection;
			if ((rightEnd & 1) == 0) rightEnd-=rdirection;
		}
	}

	/**
	 * Copy one of the bases to the other so they have the same value.
	 * The source is determined by reading a bit from the input.
	 */
	private void copyBase() {
		boolean f2 = br.get1();
		if (f2) {
			rightBase = leftBase;
		} else {
			leftBase = rightBase;
		}
	}

	/**
	 * Change the numbering styles for this section of roads.
	 */
	private void changeStyles() {
		leftStyle = fromInt(br.get(2));
		rightStyle = fromInt(br.get(2));
	}

	/**
	 * Get the initial base value. The first number for this section of road (although a diff
	 * can be applied to it).
	 *
	 * @throws NumberException
	 */
	private void getInitialBase() {
		int extra = 0;
		boolean b1 = br.get1();
		if (!b1)
			extra = br.get(4);

		leftBase = br.get(5 + extra);
		rightBase = leftBase;
	}

	/**
	 * For cases that are not implemented yet.
	 */
	private void fail(String s) throws NumberException {
		System.out.printf("ABANDON: %s\n", s);
		remainingBits();
		throw new NumberException();
	}

	/**
	 * Just print out any remaining bits.
	 *
	 * Was mostly used during development, before the whole stream was decoded.
	 */
	private void remainingBits() {
		StringBuilder sb = new StringBuilder();
		while (br.getBitPosition() < br.getNumberOfBits()) {
			sb.insert(0, br.get1() ? "1" : "0");
		}
		System.out.print(sb.toString());
	}

}

/**
 * Reads integers with specified numbers of bits and optionally with sign bits.
 */
class VarBitReader {
	private final boolean signed;   // read as signed values
	private final boolean negative; // all values are read as positive and then negated
	private final int width;        // the number of bits
	private final int off;    // a value to be added to width to get the true number to read.
	private final BitReader br;

	public VarBitReader(BitReader br, int off) {
		this.br = br;
		this.off = off;
		negative = br.get1();
		signed = br.get1();
		width = br.get(4);
	}

	public int read() {
		int val;
		if (signed) {
			val = br.sget2(width + off + 1);
		} else {
			val = br.get(width + off);
		}

		if (negative)
			val = -val;
		return val;
	}

	public String toString() {
		return String.format("sign=%b neg=%b width=%d+%d", signed, negative, width, off);
	}
}


class NumberException extends RuntimeException {
}
