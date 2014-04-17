package uk.me.parabola.mkgmap.filters;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.util.MultiHashMap;



public class LineMergeFilter{
	private static final Logger log = Logger.getLogger(LineMergeFilter.class);

	private List<MapLine> linesMerged;
	private final MultiHashMap<Coord, MapLine> startPoints = new MultiHashMap<Coord, MapLine>();
	private final MultiHashMap<Coord, MapLine> endPoints = new MultiHashMap<Coord, MapLine>();

	private void addLine(MapLine line) {
		linesMerged.add(line);
		List<Coord> points = line.getPoints();
		startPoints.add(points.get(0), line);
		endPoints.add(points.get(points.size()-1), line);
	}
	
	private void mergeLines(MapLine line1, MapLine line2) {
		// Removes the first line,
		// Merges the points in the second one
		List<Coord> points1 = line1.getPoints();
		List<Coord> points2 = line2.getPoints();
		startPoints.remove(points1.get(0), line1);
		endPoints.remove(points1.get(points1.size()-1), line1);
		startPoints.remove(points2.get(0), line2);
		startPoints.add(points1.get(0), line2);
		line2.insertPointsAtStart(points1);
		linesMerged.remove(line1);
	}

	private void addPointsAtStart(MapLine line, List<Coord> additionalPoints) {
		log.info("merged lines before " + line.getName());
		List<Coord> points = line.getPoints();
		startPoints.remove(points.get(0), line);
		line.insertPointsAtStart(additionalPoints);
		startPoints.add(points.get(0), line);
	}
	
	private void addPointsAtEnd(MapLine line, List<Coord> additionalPoints) {
		log.info("merged lines after " + line.getName());
		List<Coord> points = line.getPoints();
		endPoints.remove(points.get(points.size()-1), line);
		line.insertPointsAtEnd(additionalPoints);
		endPoints.add(points.get(points.size()-1), line);
	}

	//TODO: This routine has a side effect: it modifies some of the MapLine instances
	// instead of creating copies. It seems that this has no bad effect, but it is not clean
	public List<MapLine> merge(List<MapLine> lines, int res) {
		linesMerged = new ArrayList<MapLine>(lines.size());	//better use LinkedList??
		for (MapLine line : lines) {
			if (line.getMinResolution() > res || line.getMaxResolution() < res)
				continue;
			
			if (line.isRoad()){
				linesMerged.add(line);
				continue;
			}
			
			boolean isMerged = false;
			List<Coord> points = line.getPoints();
			Coord start = points.get(0); 
			Coord end = points.get(points.size()-1); 

			// Search for start point in hashlist 
			// (can the end of current line connected to an existing line?)
			for (MapLine line2 : startPoints.get(end)) {
				if (line.isSimilar(line2)) {
					addPointsAtStart(line2, points);
					// Search for endpoint in hashlist
					// (if the other end (=start of line =start of line2) could be connected to an existing line,
					//  both lines has to be merged and one of them dropped)
					for (MapLine line1 : endPoints.get(start)) {
						if (line2.isSimilar(line1)
						 && !line2.equals(line1)) // don't make a closed loop a double loop
						{
							mergeLines(line1, line2);
							break;
						}
					}						
					isMerged = true;
					break;
				}
			}
			if (isMerged)
				continue;

			// Search for endpoint in hashlist
			// (can the start of current line connected to an existing line?)
			for (MapLine line2 : endPoints.get(start)) {
				if (line.isSimilar(line2)) {
					addPointsAtEnd(line2, points);
					isMerged = true;
					break;
				}
			}
			if (isMerged)
				continue;

			// No matching, create a copy of line
			MapLine l = line.copy();
			List<Coord> p = new ArrayList<Coord>(line.getPoints());	//use better LinkedList for performance?
			l.setPoints(p);				
			addLine(l);
		}
		return linesMerged;
	}

}

