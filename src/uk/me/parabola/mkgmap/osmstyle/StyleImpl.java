/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: Feb 17, 2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.Option;
import uk.me.parabola.mkgmap.OptionProcessor;
import uk.me.parabola.mkgmap.Options;
import uk.me.parabola.mkgmap.build.LocatorUtil;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LineAdder;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.reader.osm.FeatureKind;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.StyleInfo;
import uk.me.parabola.mkgmap.scan.SyntaxException;
import uk.me.parabola.mkgmap.scan.TokenScanner;
import uk.me.parabola.util.EnhancedProperties;

/**
 * A style is a collection of files that describe the mapping between the OSM
 * features and the garmin features.  This file reads in those files and
 * provides methods for using the information.
 *
 * The files are either contained in a directory, in a package or in a zip'ed
 * file.
 *
 * @author Steve Ratcliffe
 */
public class StyleImpl implements Style {
	private static final Logger log = Logger.getLogger(StyleImpl.class);

	public static final boolean WITH_CHECKS = true; 
	public static final boolean WITHOUT_CHECKS = false;
	
	// This is max the version that we understand
	private static final int VERSION = 1;

	// General options just have a value and don't need any special processing.
	private static final Collection<String> OPTION_LIST = new ArrayList<String>(
			Arrays.asList("levels", "overview-levels", "extra-used-tags"));

	// File names
	private static final String FILE_VERSION = "version";
	private static final String FILE_INFO = "info";
	private static final String FILE_OPTIONS = "options";
	private static final String FILE_OVERLAYS = "overlays";

	// Patterns
	private static final Pattern COMMA_OR_SPACE_PATTERN = Pattern.compile("[,\\s]+");

	// A handle on the style directory or file.
	private final StyleFileLoader fileLoader;
	private final String location;

	// The general information in the 'info' file.
	private StyleInfo info = new StyleInfo();

	// Set if this style is based on another one.
	private final List<StyleImpl> baseStyles = new ArrayList<StyleImpl>();

	// A list of tag names to be used as the element name
	private List<String> nameTagList;

	// Options from the option file that are used outside this file.
	private final Map<String, String> generalOptions = new HashMap<String, String>();

	private final RuleSet lines = new RuleSet();
	private final RuleSet polygons = new RuleSet();
	private final RuleSet nodes = new RuleSet();
	private final RuleSet relations = new RuleSet();

	private OverlayReader overlays;
	private final boolean performChecks;
	
	
	/**
	 * Create a style from the given location and name.
	 * @param loc The location of the style. Can be null to mean just check
	 * the classpath.
	 * @param name The name.  Can be null if the location isn't.  If it is
	 * null then we just check for the first version file that can be found.
	 * @throws FileNotFoundException If the file doesn't exist.  This can
	 * include the version file being missing.
	 */
	public StyleImpl(String loc, String name) throws FileNotFoundException {
		this(loc, name, new EnhancedProperties(), WITHOUT_CHECKS);
	}
	
	/**
	 * Create a style from the given location and name.
	 * @param loc The location of the style. Can be null to mean just check
	 * the classpath.
	 * @param name The name.  Can be null if the location isn't.  If it is
	 * null then we just check for the first version file that can be found.
	 * @param props optional program properties (may be null) 
	 * @throws FileNotFoundException If the file doesn't exist.  This can
	 * include the version file being missing.
	 */
	public StyleImpl(String loc, String name, EnhancedProperties props, boolean performChecks) throws FileNotFoundException {
		location = loc;
		fileLoader = StyleFileLoader.createStyleLoader(loc, name);
		this.performChecks = performChecks;
		nameTagList = LocatorUtil.getNameTags(props);
		
		// There must be a version file, if not then we don't create the style.
		checkVersion();

		readInfo();

		for (String baseName : info.baseStyles())
			readBaseStyle(baseName, props);

		for (StyleImpl baseStyle : baseStyles)
			mergeOptions(baseStyle);

		readOptions();
		
		// read overlays before the style rules to be able to ignore overlaid "wrong" types. 
		readOverlays(); 
		
		readRules();

		ListIterator<StyleImpl> listIterator = baseStyles.listIterator(baseStyles.size());
		while (listIterator.hasPrevious())
			mergeRules(listIterator.previous());

		// OR: other way
		//for (StyleImpl s : baseStyles)
		//	mergeRules(s);
	}

	public String getOption(String name) {
		return generalOptions.get(name);
	}

	public StyleInfo getInfo() {
		return info;
	}

	public Rule getNodeRules() {
		nodes.prepare();
		return nodes;
	}

	public Rule getWayRules() {
		RuleSet r = new RuleSet();
		r.addAll(lines);
		r.addAll(polygons);
		r.prepare();
		return r;
	}

	public Rule getLineRules() {
		lines.prepare();
		return lines;
	}

	public Rule getPolygonRules() {
		polygons.prepare();
		return polygons;
	}
	
	public Rule getRelationRules() {
		relations.prepare();
		return relations;
	}

	public LineAdder getOverlays(final LineAdder lineAdder) {
		LineAdder adder = null;

		if (overlays != null) {
			adder = new LineAdder() {
				public void add(MapLine element) {
					overlays.addLine(element, lineAdder);
				}
			};
		}
		return adder;
	}

	public Set<String> getUsedTags() {
		Set<String> set = new HashSet<String>();
		set.addAll(relations.getUsedTags());
		set.addAll(lines.getUsedTags());
		set.addAll(polygons.getUsedTags());
		set.addAll(nodes.getUsedTags());

		// this is to allow style authors to say that tags are really used even
		// if they are not found in the style file.  This is mostly to work
		// around situations that we haven't thought of - the style is expected
		// to get it right for itself.
		String s = getOption("extra-used-tags");
		if (s != null && s.trim().isEmpty() == false)
			set.addAll(Arrays.asList(COMMA_OR_SPACE_PATTERN.split(s)));

		// These tags are passed on the command line and so must be added
		if (nameTagList != null)
			set.addAll(nameTagList);

		// There are a lot of tags that are used within mkgmap that 
		InputStream is = getClass().getResourceAsStream("/styles/builtin-tag-list");
		try {
			if (is != null) {
				BufferedReader br = new BufferedReader(new InputStreamReader(is));
				//System.out.println("Got built in list");
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.startsWith("#"))
						continue;
					//System.out.println("adding " + line);
					set.add(line);
				}
			}
		} catch (IOException e) {
			// the file doesn't exist, this is ok but unlikely
			System.err.println("warning: built in tag list not found");
		} finally {
			Utils.closeFile(is);
		}
		return set;
	}

	private void readRules() {
		String l = generalOptions.get("levels");
		if (l == null)
			l = LevelInfo.DEFAULT_LEVELS;
		LevelInfo[] levels = LevelInfo.createFromString(l);
		if (performChecks){
			if (levels[0].getBits() <= 10){
				System.err.println("Warning: Resolution values <= 10 may confuse MapSource: " + l);
			}
		}
		l = generalOptions.get("overview-levels");
		if (l != null){
			LevelInfo[] ovLevels = LevelInfo.createFromString(l);
			// TODO: make sure that the combination of the two level strings makes sense
			if (performChecks){
				if (ovLevels[0].getBits() <= 10){
					System.err.println("Warning: Resolution values <= 10 may confuse MapSource: " + l);
				}
				if (levels[0].getLevel() >= ovLevels[ovLevels.length-1].getLevel()){
					System.err.println("Warning: Overview level not higher than highest normal level. " + l);
				}
			}
			List<LevelInfo> tmp = new ArrayList<LevelInfo>();
			tmp.addAll(Arrays.asList(levels));
			tmp.addAll(Arrays.asList(ovLevels));
			levels = tmp.toArray(new LevelInfo[tmp.size()]);
			Arrays.sort(levels);
		}

		try {
			RuleFileReader reader = new RuleFileReader(FeatureKind.RELATION, levels, relations, performChecks, getOverlaidTypeMap());
			reader.load(fileLoader, "relations");
		} catch (FileNotFoundException e) {
			// it is ok for this file to not exist.
			log.debug("no relations file");
		}

		try {
			RuleFileReader reader = new RuleFileReader(FeatureKind.POINT, levels, nodes, performChecks, getOverlaidTypeMap());
			reader.load(fileLoader, "points");
		} catch (FileNotFoundException e) {
			// it is ok for this file to not exist.
			log.debug("no points file");
		}

		try {
			RuleFileReader reader = new RuleFileReader(FeatureKind.POLYLINE, levels, lines, performChecks, getOverlaidTypeMap());
			reader.load(fileLoader, "lines");
		} catch (FileNotFoundException e) {
			log.debug("no lines file");
		}

		try {
			RuleFileReader reader = new RuleFileReader(FeatureKind.POLYGON, levels, polygons, performChecks, getOverlaidTypeMap());
			reader.load(fileLoader, "polygons");
		} catch (FileNotFoundException e) {
			log.debug("no polygons file");
		}
	}

	/**
	 * If there is an options file, then read it and keep options that
	 * we are interested in.
	 *
	 * Only specific options can be set.
	 */
	private void readOptions() {
		try {
			Reader r = fileLoader.open(FILE_OPTIONS);
			Options opts = new Options(new OptionProcessor() {
				public void processOption(Option opt) {
					String key = opt.getOption();
					String val = opt.getValue();
					if (key.equals("name-tag-list")) {
						if ("name".equals(val) == false){
							System.err.println("Warning: option name-tag-list used in the style options is ignored. "  
									+ "Please use only the command line option to specify this value." );
						}
					} else if (OPTION_LIST.contains(key)) {
						// Simple options that have string value.  Perhaps we should allow
						// anything here?
						generalOptions.put(key, val);
					}
				}
			});

			opts.readOptionFile(r, FILE_OPTIONS);
		} catch (FileNotFoundException e) {
			// the file is optional, so ignore if not present, or causes error
			log.debug("no options file");
		}
	}

	/**
	 * Read the info file.  This is just information about the style.
	 */
	private void readInfo() {
		try {
			Reader br = new BufferedReader(fileLoader.open(FILE_INFO));
			info = new StyleInfo();

			Options opts = new Options(new OptionProcessor() {
				public void processOption(Option opt) {
					String word = opt.getOption();
					String value = opt.getValue();
					if (word.equals("summary"))
						info.setSummary(value);
					else if (word.equals("version")) {
						info.setVersion(value);
					} else if (word.equals("base-style")) {
						info.addBaseStyleName(value);
					} else if (word.equals("description")) {
						info.setLongDescription(value);
					}

				}
			});

			opts.readOptionFile(br, FILE_INFO);

		} catch (FileNotFoundException e) {
			// optional file..
			log.debug("no info file");
		}
	}

	private void readOverlays() {
		try {
			Reader r = fileLoader.open(FILE_OVERLAYS);
			overlays = new OverlayReader(r, FILE_OVERLAYS);
			overlays.readOverlays();
		} catch (FileNotFoundException e) {
			// this is perfectly normal
			log.debug("no overlay file");
		}
	}

	/**
	 * If this style is based upon another one then read it in now.  The rules
	 * for merging styles are that it is as-if the style was read just after
	 * the current styles 'info' section and any option or rule specified
	 * in the current style will override any corresponding item in the
	 * base style.
	 * @param name The name of the base style
	 * @param props program properties 
	 */
	private void readBaseStyle(String name, EnhancedProperties props) {
		if (name == null)
			return;

		try {
			baseStyles.add(new StyleImpl(location, name, props, performChecks));
		} catch (SyntaxException e) {
			System.err.println("Error in style: " + e.getMessage());
		} catch (FileNotFoundException e) {
			// not found, try on the classpath.  This is the common
			// case where you have an external style, but want to
			// base it on a built in one.
			log.debug("could not open base style file", e);

			try {
				baseStyles.add(new StyleImpl(null, name, props, performChecks));
			} catch (SyntaxException se) {
				System.err.println("Error in style: " + se.getMessage());
			} catch (FileNotFoundException e1) {
				log.error("Could not find base style", e);
			}
		}
	}

	/**
	 * Merge another style's options into this one.  The style will have a lower
	 * priority, in other words any option set in 'other' and this style will
	 * take the value given in this style.
	 *
	 * This is used to base styles on other ones, without having to repeat
	 * everything.
	 *
	 * @see #mergeRules(StyleImpl)
	 */
	private void mergeOptions(StyleImpl other) {
	for (Entry<String, String> ent : other.generalOptions.entrySet()) {
			String opt = ent.getKey();
			String val = ent.getValue();
			if (OPTION_LIST.contains(opt)) {
				// Simple options that have string value.  Perhaps we should allow
				// anything here?
				generalOptions.put(opt, val);
			}
		}
	}

	/**
	 * Merge rules from the base style.  This has to called after this
	 * style's rules are read.
	 *
	 * The other rules have a lower priority than the rules in this file; it is as if they
	 * were appended to the rule files of this style.
	 *
	 * @see #mergeOptions(StyleImpl) 
	 */
	private void mergeRules(StyleImpl other) {
		lines.merge(other.lines);
		polygons.merge(other.polygons);
		nodes.merge(other.nodes);
		relations.merge(other.relations);
	}

	private void checkVersion() throws FileNotFoundException {
		Reader r = fileLoader.open(FILE_VERSION);
		TokenScanner scan = new TokenScanner(FILE_VERSION, r);
		int version;
		try {
			version = scan.nextInt();
			log.debug("Got version", version);
		} catch (NumberFormatException e) {
			// default to 0 if no version can be found.
			version = 0;
		}

		if (version > VERSION) {
			System.err.println("Warning: unrecognised style version " + version +
			", but only versions up to " + VERSION + " are understood");
		}
	}

	/**
	 * Writes out this file to the given writer in the single file format.
	 * This produces a valid style file, although it is mostly used
	 * for testing.
	 */
	void dumpToFile(Writer out) {
		StylePrinter stylePrinter = new StylePrinter(this);
		stylePrinter.setGeneralOptions(generalOptions);
		stylePrinter.setRelations(relations);
		stylePrinter.setLines(lines);
		stylePrinter.setNodes(nodes);
		stylePrinter.setPolygons(polygons);
		stylePrinter.dumpToFile(out);
	}

	/**
	 * 
	 * @return null or the map that was read from the overlays file
	 */
	private Map<Integer, List<Integer>> getOverlaidTypeMap() {
		if (overlays != null)
			return overlays.getOverlays();
		return Collections.emptyMap();
	}

	/**
	 * Evaluate the style options and try to read the style.
	 * 
	 * The option --style-file give the location of an alternate file or
	 * directory containing styles rather than the default built in ones.
	 *
	 * The option --style gives the name of a style, either one of the
	 * built in ones or selects one from the given style-file.
	 *
	 * If there is no name given, but there is a file then the file should
	 * just contain one style.
	 *
	 * @param props the program properties
	 * @return A style instance or null in case of error. 
	 */
	public static Style readStyle(EnhancedProperties props) {
		String loc = props.getProperty("style-file");
		if (loc == null)
			loc = props.getProperty("map-features");
		String name = props.getProperty("style");

		if (loc == null && name == null)
			name = "default";

		if (name == null){
			StyleFileLoader loader = null;
			try {
				loader = StyleFileLoader.createStyleLoader(loc, null);
				int numEntries = loader.list().length;
				if (numEntries > 1)
					throw new ExitException("Style file " + loc + " contains multiple styles, use option --style to select one.");
			} catch (FileNotFoundException e) {
				throw new ExitException("Could not open style file " + loc);
			} finally {
				Utils.closeFile(loader);
			}
		}

		Style style;
		try {
			style = new StyleImpl(loc, name, props, WITHOUT_CHECKS);
		} catch (SyntaxException e) {
			System.err.println("Error in style: " + e.getMessage());
			throw new ExitException("Could not open style " + name);
		} catch (FileNotFoundException e) {
			String msg = "Could not open style ";
			if (name != null){
				msg += name;
				if (loc != null)
					msg += " in " + loc;
			}
			else 
				msg += loc + " . Make sure that it points to a style or add the --style option.";
			throw new ExitException(msg);
		}
		return style;
	}
	
	public static void main(String[] args) throws FileNotFoundException {
		String file = args[0];
		String name = null;
		if (args.length > 1)
			name = args[1];
		StyleImpl style = new StyleImpl(file, name, new EnhancedProperties(), WITH_CHECKS);

		style.dumpToFile(new OutputStreamWriter(System.out));
	}
}
