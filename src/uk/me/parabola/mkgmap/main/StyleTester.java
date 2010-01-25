/*
 * Copyright (C) 2010.
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

package uk.me.parabola.mkgmap.main;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.net.RoadDef;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.MapCollector;
import uk.me.parabola.mkgmap.general.MapElement;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.osmstyle.ActionRule;
import uk.me.parabola.mkgmap.osmstyle.ExpressionRule;
import uk.me.parabola.mkgmap.osmstyle.FixedRule;
import uk.me.parabola.mkgmap.osmstyle.RuleList;
import uk.me.parabola.mkgmap.osmstyle.RuleSet;
import uk.me.parabola.mkgmap.osmstyle.StyleFileLoader;
import uk.me.parabola.mkgmap.osmstyle.StyleImpl;
import uk.me.parabola.mkgmap.osmstyle.StyledConverter;
import uk.me.parabola.mkgmap.osmstyle.TypeReader;
import uk.me.parabola.mkgmap.osmstyle.actions.ActionList;
import uk.me.parabola.mkgmap.osmstyle.actions.ActionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.ExpressionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.Op;
import uk.me.parabola.mkgmap.osmstyle.eval.SyntaxException;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GType;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.mkgmap.scan.TokenScanner;


/**
 * Reads in a file that contains a test for the style rules.
 *
 * The format of the file is as follows
 *
 * <pre>
 * WAY 42
 * highway=primary
 * oneway=reverse
 *
 * <<<lines>>>
 * highway=primary [0x3 road_class=2 road_speed=2]
 * power=line [0x29 resolution 20]
 * </pre>
 *
 * You can have any number of ways, each must end with a blank line.
 * A way will be created with two points (1,1),(2,2) (so you can see the
 * action of oneway=reverse) and the tags that you specify.  If you give
 * a number after WAY it will be printed on output so that if you have more
 * than one you can tell which is which.  If the number is ommited it will
 * default to 1.
 *
 * You can have as many rules as you like after the <<<lines>>> and you
 * can include any other style files such as <<<options>>> or <<<info>>> if
 * you like.
 *
 * Everything is jammed into the one file to make it easier to run against
 * different versions.  But this will probably change when applied to the
 * style branch.
 *
 * @author Steve Ratcliffe
 */
public class StyleTester {
	private final List<MapElement> lines = new ArrayList<MapElement>();

	private static final Pattern SPACES_PATTERN = Pattern.compile(" +");
	private static final Pattern EQUAL_PATTERN = Pattern.compile("=");

	private final List<Way> ways = new ArrayList<Way>();
	private final PrintStream out = System.out;

	public static void main(String[] args) throws IOException {
		StyleTester sr = new StyleTester();
		sr.test(args[0]);
	}

	/**
	 * Run the test.  Read in the test file that describes the tags and rules
	 * to be tested and run the rules as implemented and also with a theoretical
	 * strict ordering.
	 * @param file The file containing the test information.
	 */
	private void test(String file) {
		try {
			readFile(file);
		} catch (IOException e) {
			System.err.println("Cant open file " + file);
			return;
		}

		try {
			StyledConverter conv = makeStyleConverter();
			StyledConverter strictConv = makeStrictStyleConverter();

			out.println("<<<results>>>");
			for (Way w : ways) {
				// Actual results that you get from the code of the version of
				// mkgmap that you are using.
				out.printf("WAY %d:\n", w.getId());
				conv.convertWay(w);
				String[] actual = formatResults();

				printResult(actual);

				// What would happen if the rules were applied one-by-one from
				// the top of the file downward in order.
				strictConv.convertWay(w);
				String[] expected = formatResults();
				if (!Arrays.deepEquals(actual, expected)) {
					out.println("  FAIL. If rules were run strictly in order this would be:");
					printResult(expected);
				}
				out.println();
			}
		} catch (FileNotFoundException e) {
			System.err.println("Cant open style: " + e);
		}

	}

	/**
	 * Print out the result obtained from {@link #formatResults()}.
	 * @param results An array of strings obtained from {@link #formatResults()}.
	 */
	private void printResult(String[] results) {
		for (String s : results) {
			out.println("    " + s);
		}
	}

	/**
	 * Read in the test file.
	 */
	private void readFile(String filename) throws IOException {
		FileReader reader = new FileReader(filename);
		BufferedReader br = new BufferedReader(reader);

		String line;
		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.toLowerCase(Locale.ENGLISH).startsWith("way")) {
				readWayTags(br, line);
			} else if (line.startsWith("<<<")) {
				// read the rest of the file
				readStyles(br, line);
			} else if ("".equals(line)) {
				// ignore blank lines.
			} else if (line.startsWith("#")) {
				// ignore comment lines
			}
		}
		br.close();
	}

	/**
	 * You can have a number of ways defined in the file.  If you give a
	 * number after 'way' that is used as the way id so that you can identify
	 * it in the results.
	 *
	 * A list of tags are read and added to the way up until a blank line.
	 *
	 * @param br Read from here.
	 * @param waydef This will contain the way-id if one was given.  Otherwise
	 * the way id will be 1.
	 * @throws IOException If the file cannot be read.
	 */
	private void readWayTags(BufferedReader br, String waydef) throws IOException {
		int id = 1;
		String[] strings = SPACES_PATTERN.split(waydef);
		if (strings.length > 1)
			id = Integer.parseInt(strings[1]);

		out.printf("WAY %d\n", id);

		Way w = new Way(id);
		w.addPoint(new Coord(1, 1));
		w.addPoint(new Coord(2, 2));
		ways.add(w);

		String line;
		while ((line = br.readLine()) != null) {
			if (line.indexOf('=') < 0)
				break;
			String[] tagval = EQUAL_PATTERN.split(line, 2);
			if (tagval.length == 2) {
				w.addTag(tagval[0], tagval[1]);
				out.println(line);
			}
		}
		out.println();
	}

	/**
	 * Print out the garmin elements that were produced by the rules.
	 */
	private String[] formatResults() {
		String[] result = new String[lines.size()];
		int i = 0;
		for (MapElement el : lines) {
			String s;
			// So we can run against versions that do not have toString() methods
			if (el instanceof MapRoad)
				s = roadToString((MapRoad) el);
			else
				s = lineToString((MapLine) el);
			result[i++] = s;
		}
		lines.clear();
		return result;
	}

	/**
	 * This is so we can run against versions of mkgmap that do not have
	 * toString methods on MapLine and MapRoad.
	 */
	private String lineToString(MapLine el) {
		Formatter fmt = new Formatter();
		fmt.format("Line 0x%x, name=<%s>, ref=<%s>, res=%d-%d",
				el.getType(), el.getName(), el.getRef(),
				el.getMinResolution(), el.getMaxResolution());
		if (el.isDirection())
			fmt.format(" oneway");

		fmt.format(" ");
		for (Coord co : el.getPoints())
			fmt.format("(%s),", co);

		return fmt.toString();
	}

	/**
	 * This is so we can run against versions of mkgmap that do not have
	 * toString methods on MapLine and MapRoad.
	 */
	private String roadToString(MapRoad el) {
		StringBuffer sb = new StringBuffer(lineToString(el));
		sb.delete(0, 4);
		sb.insert(0, "Road");
		Formatter fmt = new Formatter(sb);
		fmt.format(" road class=%d speed=%d", el.getRoadDef().getRoadClass(),
				getRoadSpeed(el.getRoadDef()));
		return fmt.toString();
	}

	/**
	 * Implement a method to get the road speed from RoadDef.
	 */
	private int getRoadSpeed(RoadDef roadDef) {
		try {
			Field field = RoadDef.class.getDeclaredField("tabAInfo");
			field.setAccessible(true);
			int tabA = (Integer) field.get(roadDef);
			return tabA & 0x7;
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return 0;
	}

	/**
	 * Read the style definitions.  The rest of the file is just copied to
	 * a style file named 'styletester.style' so that it can be read in the
	 * normal manner.
	 * @param br Read from here.
	 * @param initLine The first line of the style definition that has already been read.
	 * @throws IOException If writing fails.
	 */
	private void readStyles(BufferedReader br, String initLine) throws IOException {
		FileWriter writer = new FileWriter("styletester.style");
		PrintWriter pw = new PrintWriter(writer);
		pw.println("<<<version>>>\n0");

		pw.println(initLine);
		out.println(initLine);

		boolean copyToOutput = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (line.startsWith("<<<results>>>"))
				copyToOutput = false;
			if (copyToOutput)
				out.println(line);
			pw.println(line);
		}

		pw.close();
	}

	/**
	 * A styled converter that should work exactly the same as the version of
	 * mkgmap you are using.
	 */
	private StyledConverter makeStyleConverter() throws FileNotFoundException {
		Style style = new StyleImpl("styletester.style", null);
		MapCollector coll = new LocalMapCollector();

		return new StyledConverter(style, coll, new Properties());
	}

	/**
	 * A special styled converted that attempts to produce the correct theoretical
	 * result of running the style rules in order by literally doing that.
	 * This should produce the same result as {@link #makeStyleConverter()} and
	 * can be used as a test of the strict style ordering branch.
	 */
	private StyledConverter makeStrictStyleConverter() throws FileNotFoundException {
		Style style = new StrictOrderingStyle("styletester.style", null);
		MapCollector coll = new LocalMapCollector();

		return new StyledConverter(style, coll, new Properties());
	}

	/**
	 * A style that is based on StyleImpl, except that it literally implements
	 * running each rule in the order they are defined on each element.
	 */
	private class StrictOrderingStyle extends StyleImpl {
		private final StyleFileLoader fileLoader;

		/**
		 * Create a style from the given location and name.
		 *
		 * @param loc The location of the style. Can be null to mean just check the
		 * classpath.
		 * @param name The name.  Can be null if the location isn't.  If it is null
		 * then we just check for the first version file that can be found.
		 * @throws FileNotFoundException If the file doesn't exist.  This can include
		 * the version file being missing.
		 */
		public StrictOrderingStyle(String loc, String name) throws FileNotFoundException {
			super(loc, name);
			fileLoader = StyleFileLoader.createStyleLoader(loc, name);
		}

		/**
		 * Throws away the rules as previously read and reads again using the
		 * SimpleRuleFileReader which does not re-order or optimise the rules
		 * in any way.
		 *
		 * @return A simple list of rules with a resolving method that applies
		 * each rule in turn to the element until there is match.
		 */
		public Rule getWayRules() {
			StrictOrderingRuleSet r = new StrictOrderingRuleSet();
			String l = LevelInfo.DEFAULT_LEVELS;
			LevelInfo[] levels = LevelInfo.createFromString(l);

			SimpleRuleFileReader ruleFileReader = new SimpleRuleFileReader(GType.POLYLINE, levels, r);
			try {
				ruleFileReader.load(fileLoader, "lines");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}

			return r;
		}

		/**
		 * Keeps each rule in an orderd list.
		 *
		 * Types are resolved by literally applying the rules in order to the
		 * element.  This would be too slow to do for real in mkgmap, however
		 * mkgmap should produce the same result as doing this.
		 *
		 * As long as the rules are added in the order they are encountered in
		 * the file, this should work.
		 */
		private class StrictOrderingRuleSet implements Rule {
			private final List<Rule> rules = new ArrayList<Rule>();
			private int prevMatch;

			public void addAll(RuleSet lines) {
				for (Map.Entry<String, RuleList> ent : lines.entrySet()) {
					add(ent.getValue());
				}
			}

			public void add(Rule rule) {
				rules.add(rule);
			}

			public GType resolveType(Element el) {
				for (int i = prevMatch; i < rules.size(); i++) {
					Rule r = rules.get(i);
					GType type = r.resolveType(el);

					// Because (on trunk) we get passed a copy of the way each
					// time this is called, all the tags that were set previously
					// have been lost and so we have to start from the beginning
					// again and keep going if we were at the previous match
					// point.
					// XXX TODO implement continue
					if (type != null && i != prevMatch) {
						if (type.isContinueSearch())
							prevMatch = 0;
						else
							prevMatch = i+1;
						return type;
					}

				}
				prevMatch = 0;
				return null;
			}

		}

		/**
		 * A reimplementation of RuleFileReader that does no optimisation but
		 * just reads the rules into a list.
		 */
		class SimpleRuleFileReader {
			private final TypeReader typeReader;

			private final StrictOrderingRuleSet rules;
			private TokenScanner scanner;

			public SimpleRuleFileReader(int kind, LevelInfo[] levels, StrictOrderingRuleSet rules) {
				this.rules = rules;
				typeReader = new TypeReader(kind, levels);
			}

			/**
			 * Read a rules file.
			 * @param loader A file loader.
			 * @param name The name of the file to open.
			 * @throws FileNotFoundException If the given file does not exist.
			 */
			public void load(StyleFileLoader loader, String name) throws FileNotFoundException {
				Reader r = loader.open(name);
				load(r, name);
			}

			void load(Reader r, String name) {
				scanner = new TokenScanner(name, r);
				scanner.setExtraWordChars("-:");

				ExpressionReader expressionReader = new ExpressionReader(scanner);
				ActionReader actionReader = new ActionReader(scanner);

				// Read all the rules in the file.
				scanner.skipSpace();
				while (!scanner.isEndOfFile()) {
					Op expr = expressionReader.readConditions();

					ActionList actions = actionReader.readActions();

					// If there is an action list, then we don't need a type
					GType type = null;
					if (scanner.checkToken("["))
						type = typeReader.readType(scanner);
					else if (actions == null)
						throw new SyntaxException(scanner, "No type definition given");

					saveRule(expr, actions, type);
					scanner.skipSpace();
				}
			}

			/**
			 * Save the expression as a rule.
			 */
			private void saveRule(Op op, ActionList actions, GType gt) {
				Rule rule;
				if (!actions.isEmpty())
					rule = new ActionRule(op, actions.getList(), gt);
				else if (op != null) {
					rule = new ExpressionRule(op, gt);
				} else {
					rule = new FixedRule(gt);
				}

				//System.out.println("save rule " + null + "/" + rule);
				rules.add(rule);
			}
		}
	}

	/**
	 * A map collector that just adds any line or road we find to the end of
	 * a list.
	 */
	private class LocalMapCollector implements MapCollector {
		public void addToBounds(Coord p) { }

		// could save points in the same way as lines to test them
		public void addPoint(MapPoint point) { }

		public void addLine(MapLine line) {
			lines.add(line);
		}

		public void addShape(MapShape shape) { }

		public void addRoad(MapRoad road) {
			lines.add(road);
		}
	}
}
