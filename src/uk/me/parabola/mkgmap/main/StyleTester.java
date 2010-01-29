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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import uk.me.parabola.imgfmt.FormatException;
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
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.Style;
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
 * You can give a --ordered flag and it will run style file in ordered mode,
 * that is each rule will be applied to the element without any attempt at
 * optimisation.  Untested, might be slow for large files.
 *
 * @author Steve Ratcliffe
 */
public class StyleTester implements OsmConverter {
	private static final Pattern SPACES_PATTERN = Pattern.compile(" +");
	private static final Pattern EQUAL_PATTERN = Pattern.compile("=");

	private static final String STYLETESTER_STYLE = "styletester.style";

	//private Style style;
	private final OsmConverter converter;

	private static boolean ordered;

	public StyleTester(MapCollector coll) throws FileNotFoundException {
		this("styletester.style", coll, false);
	}

	public StyleTester(String stylefile, MapCollector coll, boolean ordered) throws FileNotFoundException {
		if (ordered)
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

	private static String[] processOptions(String[] args) {
		List<String> a = new ArrayList<String>();
		for (String s : args) {
			if (s.startsWith("--ordered")) {
				ordered = true;
			} else
				a.add(s);
		}
		return a.toArray(new String[a.size()]);
	}

	private static void runTest(String stylefile, String mapfile) {
		MapCollector collector = new PrintingMapCollector();
		OsmConverter normal;
		try {
			normal = new StyleTester(stylefile, collector, ordered);
		} catch (FileNotFoundException e) {
			System.err.println("Could not open style file " + stylefile);
			return;
		}
		try {

			InputStream is = openFile(mapfile);
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
	private static void runSimpleTest(String filename) {
		try {
			List<Way> ways = readSimpleTestFile(filename);

			List<MapElement> results = new ArrayList<MapElement>();
			OsmConverter normal = new StyleTester("styletester.style", new LocalMapCollector(results), false);

			List<MapElement> strictResults = new ArrayList<MapElement>();
			OsmConverter strict = new StyleTester("styletester.style", new LocalMapCollector(strictResults), true);

			for (Way w : ways) {
				normal.convertWay(w);
				String[] actual = formatResults(results);
				results.clear();

				strict.convertWay(w);
				String[] expected = formatResults(strictResults);
				strictResults.clear();

				String prefix = "WAY " + w.getId() + ": ";
				printResult(prefix, actual);

				if (!Arrays.deepEquals(actual, expected)) {
					System.out.println("ERROR with strict ordering result would be:");
					printResult(prefix, expected);

				}
			}
		} catch (IOException e) {
			System.err.println("Cannot open test file " + filename);
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


	private static void printResult(String prefix, String[] results) {
		for (String s : results) {
			System.out.println(prefix + s);
		}
	}

	/**
	 * Read in the combined test file.  This contains some ways and a style.
	 * The style does not need to include 'version' as this is added for you.
	 */
	private static List<Way> readSimpleTestFile(String filename) throws IOException {
		FileReader reader = new FileReader(filename);
		BufferedReader br = new BufferedReader(reader);
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
	 * @param lines The resulting map elements.
	 */
	private static String[] formatResults(List<MapElement> lines) {
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
		Style style = new StrictOrderingStyle(stylefile, null);
		return new StyledConverter(style, coll, new Properties());
	}

	/**
	 * Open a file and apply filters necessary to reading it such as decompression.
	 *
	 * @param name The file to open.
	 * @return A stream that will read the file, positioned at the beginning.
	 * @throws FileNotFoundException If the file cannot be opened for any reason.
	 */
	private static InputStream openFile(String name) throws FileNotFoundException {
		InputStream is = new FileInputStream(name);
		if (name.endsWith(".gz")) {
			try {
				is = new GZIPInputStream(is);
			} catch (IOException e) {
				throw new FileNotFoundException( "Could not read as compressed file");
			}
		}
		return is;
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
					if (type != null && i != prevMatch) {
						if (type.isContinueSearch())
							prevMatch = i + 1;
						else
							prevMatch = 0;
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
	}

	/**
	 * A map collector that just adds any line or road we find to the end of
	 * a list.
	 */
	private static class PrintingMapCollector implements MapCollector {

		public void addToBounds(Coord p) { }

		// could save points in the same way as lines to test them
		public void addPoint(MapPoint point) { }

		public void addLine(MapLine line) {
			String[] strings = formatResults(Arrays.<MapElement>asList(line));
			printResult("", strings);
		}

		public void addShape(MapShape shape) { }

		public void addRoad(MapRoad road) {
			String[] strings = formatResults(Collections.<MapElement>singletonList(road));
			printResult("", strings);
		}

		public void addRestriction(CoordNode fromNode, CoordNode toNode, CoordNode viaNode, byte exceptMask) {
		}
	}
}
