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
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.CoordNode;
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
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.TypeResult;
import uk.me.parabola.mkgmap.reader.osm.WatchableTypeResult;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.mkgmap.reader.osm.xml.Osm5XmlHandler;
import uk.me.parabola.mkgmap.scan.TokenScanner;
import uk.me.parabola.util.EnhancedProperties;

import org.xml.sax.SAXException;


/**
 * Test style rules by converting to a text format, rather than a .img file.
 * In addition you can specify a .osm file and a style file separately.
 *
 * <h2>Single test file</h2>
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
 * <h2>osm file mode</h2>
 * Takes two arguments, first the style file and then the osm file.
 *
 * You can give a --reference flag and it will run style file in reference mode,
 * that is each rule will be applied to the element without any attempt at
 * optimisation.  This acts as an independent check of the main style code
 * which may have more optimisations.
 *
 * @author Steve Ratcliffe
 */
public class StyleTester implements OsmConverter {
	private static final Pattern SPACES_PATTERN = Pattern.compile(" +");
	private static final Pattern EQUAL_PATTERN = Pattern.compile("=");

	private static final String STYLETESTER_STYLE = "styletester.style";

	private static PrintStream out = System.out;
	private static boolean reference;

	private final OsmConverter converter;

	// The file may contain a known good set of results.  They are saved here
	private final List<String> givenResults = new ArrayList<String>();
	private static boolean forceUseOfGiven;
	private static boolean showMatches;
	private static boolean print = true;

	private StyleTester(String stylefile, MapCollector coll, boolean reference) throws FileNotFoundException {
		if (reference)
			converter = makeStrictStyleConverter(stylefile, coll);
		else
			converter = makeStyleConverter(stylefile, coll);
	}

	public static void main(String[] args) throws IOException {
		String[] a = processOptions(args);
		if (a.length == 1)
			runSimpleTest(a[0]);
		else
			runTest(a[0], a[1]);
	}

	public static void setOut(PrintStream out) {
		StyleTester.out = out;
	}

	private static String[] processOptions(String[] args) {
		List<String> a = new ArrayList<String>();
		for (String s : args) {
			if (s.startsWith("--reference")) {
				System.out.println("# using reference method of calculation");
				reference = true;
			} else if (s.startsWith("--show-matches")) {
				if (!reference)
					System.out.println("# using reference method of calculation");
				reference = true;
				showMatches = true;
			} else if (s.startsWith("--no-print")) {
				print = false;
			} else
				a.add(s);
		}
		return a.toArray(new String[a.size()]);
	}

	private static void runTest(String stylefile, String mapfile) {
		PrintingMapCollector collector = new PrintingMapCollector();
		OsmConverter normal;
		try {
			normal = new StyleTester(stylefile, collector, reference);
		} catch (FileNotFoundException e) {
			System.err.println("Could not open style file " + stylefile);
			return;
		}
		try {

			InputStream is = Utils.openFile(mapfile);
			SAXParserFactory parserFactory = SAXParserFactory.newInstance();
			parserFactory.setXIncludeAware(true);
			parserFactory.setNamespaceAware(true);
			SAXParser parser = parserFactory.newSAXParser();

			try {
				EnhancedProperties props = new EnhancedProperties();
				props.put("preserve-element-order", "1");
				Osm5XmlHandler handler = new Osm5XmlHandler(props);
				handler.setCollector(collector);
				handler.setConverter(normal);
				handler.setEndTask(new Runnable() {
					public void run() {
					}
				});
				parser.parse(is, handler);
				System.err.println("Conversion time " + (System.currentTimeMillis() - collector.getStart()) + "ms");
			} catch (IOException e) {
				throw new FormatException("Error reading file", e);
			}
		} catch (SAXException e) {
			throw new FormatException("Error parsing file", e);
		} catch (ParserConfigurationException e) {
			throw new FormatException("Internal error configuring xml parser", e);
		} catch (FileNotFoundException e) {
			System.err.println("Cannot open file " + mapfile);
		}
	}
	

	/**
	 * Run a simple test with a combined test file.
	 * @param filename The test file contains text way definitions and a style
	 * file all in one.
	 */
	public static void runSimpleTest(String filename) {
		try {
			FileReader reader = new FileReader(filename);
			BufferedReader br = new BufferedReader(reader);
			List<Way> ways = readSimpleTestFile(br);

			List<MapElement> results = new ArrayList<MapElement>();
			OsmConverter normal = new StyleTester("styletester.style", new LocalMapCollector(results), false);

			List<MapElement> strictResults = new ArrayList<MapElement>();
			OsmConverter strict = new StyleTester("styletester.style", new LocalMapCollector(strictResults), true);

			List<String> all = new ArrayList<String>();
			for (Way w : ways) {
				String prefix = "WAY " + w.getId() + ": ";
				normal.convertWay(w.copy());
				String[] actual = formatResults(prefix, results);
				all.addAll(Arrays.asList(actual));
				results.clear();

				strict.convertWay(w.copy());
				String[] expected = formatResults(prefix, strictResults);
				strictResults.clear();

				printResult(actual);

				if (!Arrays.deepEquals(actual, expected)) {
					out.println("ERROR expected result is:");
					printResult(expected);
				}

				out.println();
			}

			List<String> givenList = ((StyleTester) strict).givenResults;
			String[] given = givenList.toArray(new String[givenList.size()]);
			if ((given.length > 0 || forceUseOfGiven) && !Arrays.deepEquals(all.toArray(), givenList.toArray())) {
				out.println("ERROR given results were:");
				printResult(given);
			}
		} catch (FileNotFoundException e) {
			System.err.println("Cannot open test file " + filename);
		} catch (IOException e) {
			System.err.println("Failure while reading test file " + filename);
		}
	}


	public void convertWay(Way way) {
		converter.convertWay(way);
	}

	public void convertNode(Node node) {
		converter.convertNode(node);
	}

	public void convertRelation(Relation relation) {
		converter.convertRelation(relation);
	}

	public void setBoundingBox(Area bbox) {
		converter.setBoundingBox(bbox);
	}

	public void end() {
	}


	private static void printResult(String[] results) {
		for (String s : results) {
			out.println(s);
		}
	}

	/**
	 * Read in the combined test file.  This contains some ways and a style.
	 * The style does not need to include 'version' as this is added for you.
	 */
	private static List<Way> readSimpleTestFile(BufferedReader br) throws IOException {
		List<Way> ways = new ArrayList<Way>();

		String line;
		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.toLowerCase(Locale.ENGLISH).startsWith("way")) {
				Way w = readWayTags(br, line);
				ways.add(w);
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

		return ways;
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
	private static Way readWayTags(BufferedReader br, String waydef) throws IOException {
		int id = 1;
		String[] strings = SPACES_PATTERN.split(waydef);
		if (strings.length > 1)
			id = Integer.parseInt(strings[1]);

		Way w = new Way(id);
		w.addPoint(new Coord(1, 1));
		w.addPoint(new Coord(2, 2));

		String line;
		while ((line = br.readLine()) != null) {
			if (line.indexOf('=') < 0)
				break;
			String[] tagval = EQUAL_PATTERN.split(line, 2);
			if (tagval.length == 2)
				w.addTag(tagval[0], tagval[1]);
		}

		return w;
	}

	/**
	 * Print out the garmin elements that were produced by the rules.
	 * @param prefix This string will be prepended to the formatted result.
	 * @param lines The resulting map elements.
	 */
	private static String[] formatResults(String prefix, List<MapElement> lines) {
		String[] result = new String[lines.size()];
		int i = 0;
		for (MapElement el : lines) {
			String s;
			// So we can run against versions that do not have toString() methods
			if (el instanceof MapRoad)
				s = roadToString((MapRoad) el);
			else
				s = lineToString((MapLine) el);
			result[i++] = prefix + s;
		}
		return result;
	}

	/**
	 * This is so we can run against versions of mkgmap that do not have
	 * toString methods on MapLine and MapRoad.
	 */
	private static String lineToString(MapLine el) {
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
	private static String roadToString(MapRoad el) {
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
	private static int getRoadSpeed(RoadDef roadDef) {
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
	private static void readStyles(BufferedReader br, String initLine) throws IOException {
		FileWriter writer = new FileWriter(STYLETESTER_STYLE);
		PrintWriter pw = new PrintWriter(writer);

		pw.println("<<<version>>>\n0");
		pw.println(initLine);

		try {
			String line;
			while ((line = br.readLine()) != null)
				pw.println(line);
		} finally {
			pw.close();
		}

	}

	/**
	 * A styled converter that should work exactly the same as the version of
	 * mkgmap you are using.
	 * @param stylefile The name of the style file to process.
	 * @param coll A map collecter to receive the created elements.

	 */
	private StyledConverter makeStyleConverter(String stylefile, MapCollector coll) throws FileNotFoundException {
		Style style = new StyleImpl(stylefile, null);
		return new StyledConverter(style, coll, new Properties());
	}

	/**
	 * A special styled converted that attempts to produce the correct theoretical
	 * result of running the style rules in order by literally doing that.
	 * This should produce the same result as {@link #makeStyleConverter} and
	 * can be used as a test of the strict style ordering branch.
	 * @param stylefile The name of the style file to process.
	 * @param coll A map collecter to receive the created elements.
	 */
	private StyledConverter makeStrictStyleConverter(String stylefile, MapCollector coll) throws FileNotFoundException {
		Style style = new ReferenceStyle(stylefile, null);
		return new StyledConverter(style, coll, new Properties());
	}

	public static void forceUseOfGiven(boolean force) {
		forceUseOfGiven = force;
	}

	/**
	 * This is a reference implementation of the style engine which is somewhat
	 * independent of the main implementation and does not have any kind of
	 * optimisations.  You can compare the results from the two implementations
	 * to find bugs and regressions.
	 */
	private class ReferenceStyle extends StyleImpl {
		private final StyleFileLoader fileLoader;
		private LevelInfo[] levels;

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
		public ReferenceStyle(String loc, String name) throws FileNotFoundException {
			super(loc, name);
			fileLoader = StyleFileLoader.createStyleLoader(loc, name);

			setupReader();
			readGivenResults();
		}

		private void setupReader() {
			String l = LevelInfo.DEFAULT_LEVELS;
			levels = LevelInfo.createFromString(l);
		}

		private void readGivenResults() {
			givenResults.clear();
			BufferedReader br = null;
			try {
				Reader reader = fileLoader.open("results");
				br = new BufferedReader(reader);
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty())
						continue;
					givenResults.add(line);
				}
			} catch (IOException e) {
				// there are no known good results given, that is OK
			} finally {
				Utils.closeFile(br);
			}
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
			ReferenceRuleSet r = new ReferenceRuleSet();

			SimpleRuleFileReader ruleFileReader = new SimpleRuleFileReader(GType.POLYLINE, levels, r);
			try {
				ruleFileReader.load(fileLoader, "lines");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}

			return r;
		}

		public Rule getRelationRules() {
			ReferenceRuleSet r = new ReferenceRuleSet();

			SimpleRuleFileReader ruleFileReader = new SimpleRuleFileReader(0, levels, r);
			try {
				ruleFileReader.load(fileLoader, "relations");
			} catch (FileNotFoundException e) {
				// its not a problem
			}

			return r;
		}

		public Set<String> getUsedTags() {
			return null;
		}

		/**
		 * Keeps each rule in an orderd list.
		 *
		 * Types are resolved by literally applying the rules in order to the
		 * element.
		 *
		 * As long as the rules are added in the order they are encountered in
		 * the file, this should work.
		 */
		private class ReferenceRuleSet implements Rule {
			private final List<Rule> rules = new ArrayList<Rule>();

			public void add(Rule rule) {
				rules.add(rule);
			}

			public void resolveType(Element el, TypeResult result) {
				String tagsBefore = wayTags(el);
				if (showMatches) {
					out.println("# Tags before: " + tagsBefore);
				}
				WatchableTypeResult a = new WatchableTypeResult(result);
				// Start by literally running through the rules in order.
				for (Rule rule : rules) {
					a.reset();
					rule.resolveType(el, a);

					if (showMatches) {
						if (a.isFound()) {
							out.println("# Matched: " + rule);
						} else if (a.isActionsOnly())
							out.println("# Matched for actions: " + rule);
					}

					if (a.isResolved())
						break;
				}
				if (showMatches && !tagsBefore.equals(wayTags(el)))
					out.println("# Way tags after: " + wayTags(el));
			}

			private String wayTags(Element el) {
				StringBuilder sb = new StringBuilder();
				for (String t : el) {
					sb.append(t);
					sb.append(",");
				}
				return sb.toString();
			}
		}

		/**
		 * A reimplementation of RuleFileReader that does no optimisation but
		 * just reads the rules into a list.
		 *
		 * Again this can be compared with the main implementation which may
		 * attempt more optimisations.
		 */
		class SimpleRuleFileReader {
			private final TypeReader typeReader;

			private final ReferenceRuleSet rules;
			private TokenScanner scanner;

			public SimpleRuleFileReader(int kind, LevelInfo[] levels, ReferenceRuleSet rules) {
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
				if (actions.isEmpty())
					rule = new ExpressionRule(op, gt);
				else
					rule = new ActionRule(op, actions.getList(), gt);

				rules.add(rule);
			}
		}
	}

	/**
	 * A map collector that just adds any line or road we find to the end of
	 * a list.
	 */
	private static class LocalMapCollector implements MapCollector {
		private final List<MapElement> lines;

		private LocalMapCollector(List<MapElement> lines) {
			this.lines = lines;
		}

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

		public void addRestriction(CoordNode fromNode, CoordNode toNode, CoordNode viaNode, byte exceptMask) {
		}

		public void addThroughRoute(long junctionNodeId, long roadIdA, long roadIdB) {
		}
	}

	/**
	 * A map collector that just prints elements found.
	 * (lines and roads only at present).
	 */
	private static class PrintingMapCollector implements MapCollector {
		private long start;

		public void addToBounds(Coord p) { if (start == 0) {
				System.err.println("start collection");
				start = System.currentTimeMillis();
			}}

		// could save points in the same way as lines to test them
		public void addPoint(MapPoint point) { }

		public void addLine(MapLine line) {
			if (start == 0) {
				System.err.println("start collection");
				start = System.currentTimeMillis();
			}
			if (print) {
				String[] strings = formatResults("", Arrays.<MapElement>asList(line));
				printResult(strings);
			}
		}

		public void addShape(MapShape shape) { }

		public void addRoad(MapRoad road) {
			if (print) {
				String[] strings = formatResults("", Collections.<MapElement>singletonList(road));
				printResult(strings);
			}
		}

		public void addRestriction(CoordNode fromNode, CoordNode toNode, CoordNode viaNode, byte exceptMask) {
		}

		public void addThroughRoute(long junctionNodeId, long roadIdA, long roadIdB) {
		}

		public long getStart() {
			return start;
		}
	}
}
