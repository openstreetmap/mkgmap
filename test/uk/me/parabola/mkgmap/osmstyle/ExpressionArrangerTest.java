/*
 * Copyright (C) 2017.
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

package uk.me.parabola.mkgmap.osmstyle;

import java.io.StringReader;

import uk.me.parabola.mkgmap.osmstyle.eval.ExpressionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.reader.osm.FeatureKind;
import uk.me.parabola.mkgmap.scan.TokenScanner;

import org.junit.Test;

import static org.junit.Assert.*;
import static uk.me.parabola.mkgmap.osmstyle.ExpressionArranger.fmtExpr;
import static uk.me.parabola.mkgmap.osmstyle.ExpressionArranger.isSolved;


public class ExpressionArrangerTest {
	private final ExpressionArranger arranger = new ExpressionArranger();

	@Test
	public void testChainedAnd() {

		Op op = createOp("(a>2 & b~h) & c=* & d=hello & fred<3 [0x2]");
		op = arranger.arrange(op);

		assertEquals("$d=hello[AND]$c=* & $a>2 & $b~h & $fred<3", fmtExpr(op));
	}

	@Test
	public void testChainAndWithBracketedAnd() {
		Op op = createOp("(a>2 & b~h) & (c=* & d=hello) & fred<3 [0x2]");
		op = arranger.arrange(op);

		assertTrue(fmtExpr(op).startsWith("$d=hello[AND]$c=* & "));
	}

	@Test
	public void testPoorInitialSwap() {
		Op op = createOp("!($b=1) & $b!=1 & $b!=2 & $b=1 {name 'n770'} [0x2]");
		op = arranger.arrange(op);

		assertTrue(fmtExpr(op).startsWith("$b=1[AND]"));
	}

	@Test
	public void testStartDoubleNot() {
		Op op = createOp("!!(a<2 & b=foo)");
		op = arranger.arrange(op);

		assertEquals("$b=foo & $a<2", op.toString());
	}

	@Test
	public void testBasicOr() {
		Op op = createOp("a<2 & b=1 | a<1 & b=2");
		op = arranger.arrange(op);

		assertTrue(isSolved(op));
	}

	@Test
	public void testChainedOr() {
		Op op = createOp("a<2 & b=1 | a<1 & b=2 | a<2 & b!=2 & c=hello");
		op = arranger.arrange(op);

		assertTrue(isSolved(op));
	}

	@Test
	public void testNotFailed() {
		Op op = createOp("!($a < 1 | $a != 2) & $a != 2");
		op = arranger.arrange(op);

		assertTrue(isSolved(op));
	}

	@Test
	public void testIsSolved() {
		String s = "$a=2 | $a=1 & $a!=1 | $b=2 & $a!=1 | $b<1";
		Op op = createOp(s);
		assertTrue(isSolved(op));

		op = arranger.arrange(op);
		assertTrue(isSolved(op));
	}

	@Test
	public void testDistributeFailure() {
		Op op = createOp("($b<2 | $b<1) & ($a=1 | $b~2 | !($a=1)) & $a!=2");
		System.out.println(fmtExpr(op));
		op = arranger.arrange(op);

		System.out.println(fmtExpr(op));
	}

	@Test
	public void testOrWithNotFailure() {
		Op op = createOp("$b<2 | !($b!=1 & $b!=2) | $a~1 {name 'n466'} [0x2]");
		System.out.println(fmtExpr(op));
		op = arranger.arrange(op);
		System.out.println(fmtExpr(op));
		assertTrue(isSolved(op));
	}

	@Test
	public void testComplex1() {
		Op op = createOp("($a=2 | $b~2 | $a=2 | $a<1) & ($a!=1 | $b<2 | !($a=1) | $b=1) & $b!=1 {name 'n866'} [0x2]");
		op = arranger.arrange(op);
		assertTrue(isSolved(op));
	}

	@Test
	public void testName() {
		Op op = createOp("$b!=1 & $a~2 [0x2]");
		op = arranger.arrange(op);
		boolean solved = isSolved(op);
		System.out.println(op);
		System.out.println(solved);
	}

	private Op createOp(String s) {
		TokenScanner scanner = new TokenScanner("test.file", new StringReader(s));
		ExpressionReader er = new ExpressionReader(scanner, FeatureKind.POLYLINE);
		Op op = er.readConditions();
		return op;
	}
}
