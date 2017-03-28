package uk.me.parabola.mkgmap.osmstyle;

import java.io.Writer;
import java.util.Formatter;
import java.util.Map;
import java.util.Map.Entry;

import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.StyleInfo;

/**
 * Class for printing out a style.
 * Code extracted from StyleImpl, but still needs information from the
 * implementation of the style.
 */
public class StylePrinter {
	private final Style style;

	private Map<String, String> generalOptions;
	private RuleSet nodes;
	private RuleSet lines;
	private RuleSet polygons;
	private RuleSet relations;

	public StylePrinter(Style style) {
		this.style = style;
	}

	/**
	 * Writes out this file to the given writer in the single file format. This
	 * produces a valid style file, although it is mostly used for testing.
	 */
	public void dumpToFile(Writer out) {
		Formatter fmt = new Formatter(out);
		fmt.format("<<<version>>>\n0\n");

		dumpInfo(fmt);

		dumpOptions(fmt);

		if (relations != null)
			dumpRuleSet(fmt, "relations", relations);

		if (nodes != null)
			dumpRuleSet(fmt, "points", nodes);

		if (lines != null)
			dumpRuleSet(fmt, "lines", lines);

		if (polygons != null)
			dumpRuleSet(fmt, "polygons", polygons);

		fmt.flush();
	}

	private void dumpOptions(Formatter fmt) {
		fmt.format("<<<options>>>\n");

		if (generalOptions != null) {
			for (Entry<String, String> entry : generalOptions.entrySet())
				fmt.format("%s: %s\n", entry.getKey(), entry.getValue());
		}
	}

	private void dumpInfo(Formatter fmt) {
		fmt.format("<<<info>>>\n");
		StyleInfo styleInfo = style.getInfo();
		fmt.format("version %s\n", dumpInfoVal(styleInfo.getVersion()));
		fmt.format("summary %s\n", dumpInfoVal(styleInfo.getSummary()));

		// The base styles are combined already, so should not be output. Retained as comments for
		// documentation/testing purposes.
		for (String name : styleInfo.baseStyles())
			fmt.format("# base-style %s\n", dumpInfoVal(name));

		fmt.format("description %s\n", dumpInfoVal(styleInfo.getLongDescription()));
	}

	private String dumpInfoVal(String str) {
		if (str.indexOf('\n') >= 0)
			return "{\n" + str + "\n}";
		else
			return ": " + str;
	}

	static void dumpRuleSet(Formatter fmt, String name, RuleSet set) {
		fmt.format("<<<%s>>>\n", name);
		for (Rule rule : set) {
			fmt.format("%s\n", rule.toString());
		}
	}



	void setGeneralOptions(Map<String, String> generalOptions) {
		this.generalOptions = generalOptions;
	}

	void setNodes(RuleSet nodes) {
		this.nodes = nodes;
	}

	void setLines(RuleSet lines) {
		this.lines = lines;
	}

	void setPolygons(RuleSet polygons) {
		this.polygons = polygons;
	}

	public void setRelations(RuleSet relations) {
		this.relations = relations;
	}
}
