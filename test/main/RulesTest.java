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
package main;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;

import javax.xml.bind.DatatypeConverter;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.main.StyleTester;
import uk.me.parabola.mkgmap.osmstyle.ExpressionArranger;
import uk.me.parabola.mkgmap.osmstyle.eval.AndOp;
import uk.me.parabola.mkgmap.osmstyle.eval.BinaryOp;
import uk.me.parabola.mkgmap.osmstyle.eval.EqualsOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ExpressionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.GTEOp;
import uk.me.parabola.mkgmap.osmstyle.eval.LTOp;
import uk.me.parabola.mkgmap.osmstyle.eval.NodeType;
import uk.me.parabola.mkgmap.osmstyle.eval.NotEqualOp;
import uk.me.parabola.mkgmap.osmstyle.eval.NotOp;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.osmstyle.eval.OrOp;
import uk.me.parabola.mkgmap.osmstyle.eval.RegexOp;
import uk.me.parabola.mkgmap.osmstyle.eval.ValueOp;
import uk.me.parabola.mkgmap.osmstyle.function.FunctionFactory;
import uk.me.parabola.mkgmap.osmstyle.function.GetTagFunction;
import uk.me.parabola.mkgmap.osmstyle.function.LengthFunction;
import uk.me.parabola.mkgmap.reader.osm.FeatureKind;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokenScanner;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static uk.me.parabola.mkgmap.osmstyle.ExpressionArranger.isSolved;
import static uk.me.parabola.mkgmap.osmstyle.eval.NodeType.*;

public class RulesTest {
	private static final Random rand = new Random();

	private static final EnumSet<NodeType> INVALID_TOP = EnumSet.of(NOT, NOT_EXISTS, NOT_EQUALS);
	private static final EnumSet<NodeType> USEFUL_NODES;

	private static final String TEST_FILE_NAME = "tmp.test";
	private static final int TEST_TIMEOUT = 500;  // In Milli seconds

	static {
		USEFUL_NODES = EnumSet.allOf(NodeType.class);
		USEFUL_NODES.removeAll(INVALID_TOP);
		USEFUL_NODES.removeAll(EnumSet.of(OR, AND, VALUE));
	}

	private static boolean onlyErrors;
	private static int minErrors = 1;
	private static int minRules = 0;
	private static long seed = 8799543;
	private static boolean debug;
	private static boolean saveErrors;
	private static boolean stopOnFail;

	private final String[] values = {
			"1", "2"
	};
	private final String[] names = {
			"a", "b"
	};

	private int syntaxErrors;

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	private static ExpressionArranger arranger;

	private static void processOptions(String... args) {
		for (int i = 0, argsLength = args.length; i < argsLength; i++) {
			String s = args[i];
			if (s.startsWith("--errors")) {
				onlyErrors = true;
			} else if (s.startsWith("--save")) {
				saveErrors = true;
			} else if (s.startsWith("--min-e")) {
				minErrors = Integer.parseInt(args[++i]);
			} else if (s.startsWith("--min-r")) {
				minRules = Integer.parseInt(args[++i]);
			} else if (s.startsWith("--arr")) {
				arranger = new ExpressionArranger();
			} else if (s.startsWith("--stop")) {
				stopOnFail = true;
			} else if (s.startsWith("--seed")) {
				seed = Long.parseLong(args[++i]);
			} else if (s.startsWith("--rand")) {
				seed = System.currentTimeMillis();
			} else if (s.startsWith("--debug")) {
				debug = true;
			} else {
				System.out.println("Unrecognised option: Usage:\n" +
						"--arrange      use the ExpressionArranger instead of style tester\n" +
						"--min-errors   run until at least this many errors\n" +
						"--min-rules    run at least this many rules\n" +
						"--stop-on-fail stop on test errors (not syntax errors)\n" +
						"--seed N       change the starting place for the random numbers\n" +
						"--rand         set the random seed to the current time\n" +
						"--save-errors  save errors as style tester files\n")
				;
				System.exit(2);
			}

		}
	}

	public static void main(String... args) {
		processOptions(args);
		rand.setSeed(seed);

		RulesTest srt = new RulesTest();
		srt.run();
	}

	private void run() {
		int errors = 0;

		int count;
		for (count = 0; count < minRules || errors < minErrors; ) {

			Op expr = generateExpr(true);

			boolean invalid = checkEmpty(expr);
			if (!invalid) {
				count++;
				String rule = expr + String.format(" {name 'n%d'} [0x2]", count);
				boolean ok = testRule(rule, count);
				if (!ok)
					errors++;
			}
		}

		executor.shutdownNow();
		System.out.printf("Tests: %s, failures=%d (%d non-syntax), passed=%d", count, errors, errors-syntaxErrors,
				count-errors);
	}

	/**
	 * Test the rule by writing a style-tester file and letting it run it.
	 */
	private boolean testRule(String rule, int id) {
		if (arranger != null) {
			return testCradle(rule, id, this::runArrangeTest);
		} else {
			return testCradle(rule, id, this::runStyleTester);
		}
	}

	private boolean runStyleTester(String rule, int id) {
		OutputStream buf = new ByteArrayOutputStream();
		StyleTester.setOut(new PrintStream(buf));

		try (BufferedWriter fw = new BufferedWriter(new FileWriter(TEST_FILE_NAME))) {

			fw.write(String.format("#\n# Rule: %s\n# Id: %d\n# Seed: %d\n# Date: %s\n#\n",
					rule, id, seed, SimpleDateFormat.getDateInstance().format(new Date())));

			writeWayDefs(fw);
			fw.write("<<<lines>>>\n");
			fw.write(rule);
			fw.newLine();

		} catch (IOException e) {
			e.printStackTrace();
			System.exit(2);
		}

		StyleTester.runSimpleTest(TEST_FILE_NAME);

		String output = buf.toString();
		if (output.contains("ERROR")) {
			System.out.println("ERROR: FAILED test: " + rule);
			System.out.println(output);

			// On error save the test file.
			saveFile(TEST_FILE_NAME, rule, id);
			checkStopOnFail();
			return false;
		} else {
			if (!onlyErrors)
				System.out.println("OK: " + rule);

			// remove since ok
			new File(TEST_FILE_NAME).delete();
			return true;
		}
	}

	private boolean runArrangeTest(String rule, int id) {
		TokenScanner scanner = new TokenScanner("test.file", new StringReader(rule));
		ExpressionReader er = new ExpressionReader(scanner, FeatureKind.POLYLINE);
		Op op = er.readConditions();

		Op result = arranger.arrange(op);

		boolean ok = isSolved(result);

		if (ok) {
			if (!onlyErrors)
				System.out.println("OK: " + rule);
		} else {
			System.out.println("ERROR: FAILED test: " + rule);
			checkStopOnFail();
		}
		return ok;
	}

	/**
	 * Call a test method in a timeout protected wrapper.
	 *
	 * @param rule The rule to be tested.
	 * @param id The id of the rule in this run.
	 * @param call The test method to call.
	 * @return True if the test passed, false if it failed, or there was an exception.
	 */
	private boolean testCradle(String rule, int id, BiFunction<String, Integer, Boolean> call) {
		Future<Boolean> future = executor.submit(() -> call.apply(rule, id));
		try {
			Boolean result = future.get(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
			if (result != null)
				return result;
		} catch (InterruptedException | TimeoutException e) {
			future.cancel(true);

			System.out.println("ERROR: Timeout - took too long: " + rule);
			System.out.println("  Message: " + e);

			// On error save the test file.
			saveFile(TEST_FILE_NAME, rule, id);
			checkStopOnFail();
		} catch (ExecutionException e) {
			future.cancel(true);

			Throwable ne = e.getCause();
			if (ne instanceof SyntaxException) {
				System.out.println("ERROR: Syntax: " + rule);
				syntaxErrors++;
			} else {
				System.out.println("ERROR: Failed with exception: " + rule);
				System.out.println("  exception: " + ne);
				checkStopOnFail();
			}
			System.out.println("  Message: " + ne.getMessage());

			// On error save the test file.
			saveFile(TEST_FILE_NAME, rule, id);
		}

		return false;
	}

	private void checkStopOnFail() {
		if (stopOnFail) {
			System.out.println("Stopping after first failure");
			executor.shutdownNow();
			System.exit(1);
		}

	}

	private void saveFile(String fromName, String rule, int id) {
		if (!saveErrors || fromName == null)
			return;

		String fileName;
		try {
			MessageDigest md5 = MessageDigest.getInstance("md5");
			byte[] digest = md5.digest(rule.getBytes("utf-8"));
			String s = DatatypeConverter.printHexBinary(digest).substring(0, 8);
			fileName = String.format("t-%s.test", s);
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			fileName = String.format("t-%06d.test", id);
		}

		try {
			Files.move(Paths.get(fromName), Paths.get(fileName), REPLACE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create ways for all combinations of the used variables and values.
	 * @param fw Write to here
	 */
	private void writeWayDefs(BufferedWriter fw) {
		int count = 1;
		for (int a = 0; a < 3; a++) {
			for (int b = 0; b < 3; b++) {

				try {
					fw.write(String.format("WAY %d\n", count));
					if (a > 0)
						fw.write(String.format("a=%d\n", a));
					if (b > 0)
						fw.write(String.format("b=%d\n", b));
					fw.newLine();
				} catch (IOException e) {
					e.printStackTrace();
				}
				count++;
			}
		}
	}

	/**
	 * Check if this rule would match an empty way.
	 *
	 * Such rules are not allowed.
	 *
	 * The problem case is length().  The length of the test way is 3.3 and we always make
	 * sure that length is only used in expressions with GTE and values of 1 or 2 so they
	 * are always true.  However if there is a NOT in front if it it will then be false and
	 * so the simple test of of an empty way returning true could miss this case.
	 *
	 * Therefore we create a new tree with all length() operations reversed and test that too.
	 */
	private static boolean checkEmpty(Op expr) {
		Way way = makeWay();

		boolean invalid = expr.eval(way);

		Op op = copyAndReverseLengthTest(expr);
		if (op.eval(way))
			invalid = true;

		return invalid;
	}

	private static Way makeWay() {
		Way way = new Way(1);
		way.addPoint(new Coord(1, 1));
		way.addPoint(new Coord(2, 2));
		return way;
	}

	private static Op copyAndReverseLengthTest(Op expr) {
		if (expr == null)
			return null;

		Op op;

		if (expr.isType(GTE) && expr.getFirst().isType(FUNCTION) && expr.getFirst() instanceof LengthFunction) {
			op = new LTOp().set(expr.getFirst(), expr.getSecond());
			return op;
		}

		if (expr.isType(AND) || expr.isType(OR) || expr.isType(NOT)) {
			op = expr.isType(AND)? new AndOp(): expr.isType(NOT)? new NotOp(): new OrOp();
			op.setFirst(expr.getFirst());
			if (op instanceof BinaryOp)
				 ((BinaryOp) op).setSecond(expr.getSecond());
		} else {
			return expr;
		}

		op.setFirst(copyAndReverseLengthTest(op.getFirst()));
		if (op instanceof BinaryOp)
				((BinaryOp) op).setSecond(copyAndReverseLengthTest(op.getSecond()));
		return op;
	}

	/**
	 * Generate a random expression.
	 */
	private Op generateExpr() {
		return generateExpr(false);
	}

	/**
	 * Generate a random expression.
	 *
	 * Picks a random operation from a hopefully representative list of possibilities.
	 * It is called recursively to fill in AND and OR subtrees.
	 *
	 * @param interesting Only return OR / AND for use as the starting term.  Otherwise
	 *   you get a lot of uninteresting a=2 type expressions.
	 */
	private Op generateExpr(boolean interesting) {
		int t = rand.nextInt(interesting ? 2: 8);
		switch (t) {
		case 0:
			return fillOp(new AndOp());
		case 1:
			return fillOp(new OrOp());
		case 2:
			return fillOp(new NotOp());
		case 3:
			return fillNameValue(new LTOp());
		case 4:
			return fillNameValue(new GTEOp());
		case 5:
			return fillNameValue(new RegexOp());
		case 6:
			return fillNameValue(new NotEqualOp());
		default:
			return fillNameValue(new EqualsOp());
		}
	}

	/**
	 * Fill the slots on a container operation eg AND, OR
	 */
	private Op fillOp(Op op) {
		op.setFirst(generateExpr());
		if (op instanceof BinaryOp)
			((BinaryOp) op).setSecond(generateExpr());
		return op;
	}

	/**
	 * Fill with an name and a value.
	 *
	 * There are a limited number of names and values available.
	 */
	private Op fillNameValue(BinaryOp op) {
		op.setFirst(genNameOp(op.getType()));
		op.setSecond(valueOp());
		return op;
	}

	private Op genNameOp(NodeType type) {
		if (type == GTE && rand.nextInt(4) == 0) {
			return FunctionFactory.createFunction("length");
		}

		String name = names[rand.nextInt(names.length)];
		return new GetTagFunction(name);
	}

	private Op valueOp() {
		return new ValueOp(values[rand.nextInt(values.length)]);
	}
}
