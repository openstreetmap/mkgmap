package uk.me.parabola.mkgmap.osmstyle;

import java.io.Writer;
import java.util.Formatter;
import java.util.Map;

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
		if (style.getNameTagList() != null)
			fmt.format("name-tag-list: %s\n", fmtArray(style.getNameTagList()));

		if (generalOptions != null) {
			for (Map.Entry<String, String> entry : generalOptions.entrySet())
				fmt.format("%s: %s\n", entry.getKey(), entry.getValue());
		}
	}

	private void dumpInfo(Formatter fmt) {
		fmt.format("<<<info>>>\n");
		StyleInfo styleInfo = style.getInfo();
		fmt.format("version %s\n", dumpInfoVal(styleInfo.getVersion()));
		fmt.format("summary %s\n", dumpInfoVal(styleInfo.getSummary()));
		if (styleInfo.getBaseStyleName() != null)
			fmt.format("base-style %s\n", dumpInfoVal(styleInfo.getBaseStyleName()));
		fmt.format("description %s\n", dumpInfoVal(styleInfo.getLongDescription()));
	}

	private String dumpInfoVal(String str) {
		if (str.indexOf('\n') >= 0)
			return "{\n" + str + "\n}";
		else
			return ": " + str;
	}

	private String fmtArray(String[] strings) {
		StringBuffer sb = new StringBuffer();
		for (String s : strings) {
			sb.append(s);
			sb.append(", ");
		}
		sb.setLength(sb.length() - 2); // trim final separator
		return sb.toString();
	}

	private void dumpRuleSet(Formatter fmt, String name, RuleSet set) {
		fmt.format("<<<%s>>>\n", name);
		for (Map.Entry<String, Rule> ent : set.getMap().entrySet()) {
			Rule rule = ent.getValue();
			if (rule instanceof SequenceRule) {
				for (Rule sr : (SequenceRule) rule)
					dumpRule(fmt, ent.getKey(), sr);
			} else {
				dumpRule(fmt, ent.getKey(), rule);
			}
		}
	}

	private void dumpRule(Formatter fmt, String s, Rule rule) {
		if (rule instanceof FixedRule)
			fmt.format("%s %s\n", s, rule);
		else {
			String rulestr = rule.toString();
			fmt.format("rulestr:%s", rulestr);
			if (rulestr.startsWith("\n") || rulestr.matches("^[ \t\n].*") || rulestr.matches("^[ \t\n].*"))
				fmt.format("%s %s\n", s, rulestr);
			else
				fmt.format("%s & %s\n", s, rulestr);
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
