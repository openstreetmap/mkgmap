/*
 * File: NumberRangeTest.java
 * 
 * Copyright (C) 2012 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 or
 *  version 3 as published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 14 Dec 2012
 */

package main;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import uk.me.parabola.imgfmt.app.BitReader;
import uk.me.parabola.imgfmt.app.BitWriter;
import uk.me.parabola.imgfmt.app.net.NumberPreparer;
import uk.me.parabola.imgfmt.app.net.Numbers;
import uk.me.parabola.log.Logger;

import func.lib.NumberReader;

/**
 * 
 * @author Steve Ratcliffe
 */
public class NumberRangeTest {

	static protected Logger log = Logger.getLogger(NumberRangeTest.class);

	private int bytesUsed;

    public void testRandom() {
        Random rand = new Random(8866028);

        for (int iter = 0; iter < 1000000; iter++) {
            List<String> sl = new ArrayList<String>();
            for (int i = 0; i < 20; i++) {
                String n;
                do {
                    String r1 = getRange(rand);
                    String r2 = getRange(rand);

                    n = String.format("%d,%s,%s", i, r1, r2);
                } while (i == 0 && n.contains("N,-1,-1,N"));

                sl.add(n);
                if (rand.nextInt(3) > 1)
                    break;
            }

            if ((iter % 500000) == 0)
                System.out.println("Done " + iter);

            run(sl.toArray(new String[sl.size()]));
        }
        System.out.println("bytes used: " + bytesUsed);
    }

	private void run(String[] strings) {
		List<Numbers> numbers = new ArrayList<Numbers>();
		for (String s : strings) {
			Numbers n = new Numbers(s);
			n.setRnodNumber(n.getNodeNumber());
			numbers.add(n);
		}

		NumberPreparer np = new NumberPreparer(numbers);
		BitWriter bitWriter = np.fetchBitStream();
		bytesUsed += bitWriter.getLength();

		// Now read it back in
		byte[] bytes = new byte[bitWriter.getLength()];
		System.arraycopy(bitWriter.getBytes(), 0, bytes, 0, bytes.length);
		NumberReader nr = new NumberReader(new BitReader(bytes));
		nr.setNumberOfNodes(numbers.get(numbers.size()-1).getRnodNumber() + 1);
		List<Numbers> list = nr.readNumbers(np.getSwapped());

		// Have to fix up the node numbers
		for (Numbers n : list) {
			n.setNodeNumber(n.getRnodNumber());
		}

		// Test that they are the same.
		String orig = numbers.toString();
		String calculated = list.toString();

		if (!orig.equals(calculated)) {
			System.out.printf("Fail: expecting: %s\n            Got: %s\n", orig, calculated);
		}
	}

	private String getRange(Random rand) {
        char style = "NEEEOOOBB".charAt(rand.nextInt(9));
        //if (style == 'N') style = 'B';
        int max = 10;
        int r = rand.nextInt(20);
        if (r > 19) max = 200;
        if (r > 17) max = 30;

        int start = rand.nextInt(max)+1;
        int end = rand.nextInt(max)+1;
        if (style == 'O') {
            start |= 1;
            end |= 1;
        } else if (style == 'E') {
            start++; end++;
            start &= ~1;
            end &= ~1;
        } else if (style == 'N') {
            start = end = -1;
        }
        return String.format("%c,%d,%d", style, start, end);
    }

	public static void main(String[] args) {
		(new NumberRangeTest()).testRandom();
	}
}

