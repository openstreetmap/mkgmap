package uk.me.parabola.mkgmap.osmstyle.function;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.MultiPolygonRelation;
import uk.me.parabola.mkgmap.reader.osm.Way;

/**
 * Calculates the area size of a polygon in garmin units ^ 2.
 * @author WanMil
 */
public class AreaSizeFunction extends CachedFunction {

	private final DecimalFormat nf = new DecimalFormat("0.0#####################", DecimalFormatSymbols.getInstance(Locale.US));

	public AreaSizeFunction() {
		super(null);
	}

	protected String calcImpl(Element el) {
		if (el instanceof Way) {
			Way w = (Way)el;
			// a non closed way has size 0
			if (w.isClosed() == false) {
				return "0";
			}
			return nf.format(MultiPolygonRelation.calcAreaSize(((Way) el).getPoints()));
		}
		return null;
	}

	public boolean supportsWay() {
		return true;
	}

}
