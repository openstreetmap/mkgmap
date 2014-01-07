package uk.me.parabola.mkgmap.filters;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.mkgmap.osmstyle.WrongAngleFixer;
//import uk.me.parabola.util.GpxCreator;
import uk.me.parabola.util.MultiHashMap;



public class ShapeMergeFilter{
	private static final Logger log = Logger.getLogger(ShapeMergeFilter.class);
	private final int resolution;

	public ShapeMergeFilter(int resolution) {
		this.resolution = resolution;
	}

	public List<MapShape> merge(List<MapShape> shapes, int subdivId) {
		if (shapes.size() <= 1)
			return shapes;
		
		MultiHashMap<Integer, Map<MapShape, List<ShapeHelper>>> topMap = new MultiHashMap<Integer, Map<MapShape,List<ShapeHelper>>>();
		List<MapShape> mergedShapes = new ArrayList<MapShape>();
		int i = 0;
		for (MapShape shape: shapes) {
			if (shape.getMinResolution() > resolution || shape.getMaxResolution() < resolution)
				continue;
			if (shape.getPoints().size() > 100){
				mergedShapes.add(shape);
				continue;
			}
			
//			GpxCreator.createGpx("e:/ld/tm_" + i++, shape.getPoints());
			List<Map<MapShape, List<ShapeHelper>>> sameTypeList = topMap.get(shape.getType());
			ShapeHelper sh = new ShapeHelper(shape);
			if (sameTypeList.isEmpty()){
				Map<MapShape, List<ShapeHelper>> lowMap = new LinkedHashMap<MapShape, List<ShapeHelper>>();
				ArrayList<ShapeHelper> list = new ArrayList<ShapeHelper>();
				list.add(sh);
				lowMap.put(shape, list);
				topMap.add(shape.getType(),lowMap);
				continue;
			}
			for (Map<MapShape, List<ShapeHelper>> lowMap : sameTypeList){
				boolean added = false;
				for (MapShape ms: lowMap.keySet()){
					if (ms.isSimilar(shape)){
						List<ShapeHelper> list = lowMap.get(ms);
						list = addWithoutCreatingHoles(list, sh);
						lowMap.put(ms, list);
						added = true;
						break;
					}
				}
				if (!added){
					ArrayList<ShapeHelper> list = new ArrayList<ShapeHelper>();
					list.add(sh);
					lowMap.put(shape, list);
				}
			}
		}
		i = 0;
		for (List<Map<MapShape, List<ShapeHelper>>> sameTypeList : topMap.values()){
			i++;
			int j = 0;
			for (Map<MapShape, List<ShapeHelper>> lowMap : sameTypeList){
				j++;
				int k = 0;
				Iterator<Entry<MapShape, List<ShapeHelper>>> iter = lowMap.entrySet().iterator();
				while (iter.hasNext()){
					k++;
					int l = 0;
					Entry<MapShape, List<ShapeHelper>> item = iter.next();
					MapShape ms = item.getKey();
					List<ShapeHelper> shapeHelpers = item.getValue();
					for (ShapeHelper sh:shapeHelpers){
						l++;
						MapShape newShape = ms.copy();
						assert sh.points.get(0) == sh.points.get(sh.points.size()-1);
//						int oldSize = sh.points.size();
//						GpxCreator.createGpx("e:/ld/mr_" + resolution+ "_" + subdivId + "_" + ms.getType() + "_"+i+"_"+j+"_"+k+"_"+l+ "m", sh.points);
						WrongAngleFixer.fixAnglesInShape(sh.points);
//						GpxCreator.createGpx("e:/ld/mr_" + resolution+ "_" + subdivId + "_" + ms.getType() + "_"+i+"_"+j+"_"+k+"_"+l+ "f", sh.points);
//						if (oldSize > sh.points.size() + 10){
//							long dd = 4;
//						}
						newShape.setPoints(sh.points);
						mergedShapes.add(newShape);
					}
				}
			}
		}
		return mergedShapes;
	}

	/**
	 * Try to merge a shape with one or more of the shapes in the list.
	 *  If it cannot be merged, it is added to the list.   
	 * @param list
	 * @param toAdd
	 * @return
	 */
	private List<ShapeHelper> addWithoutCreatingHoles(List<ShapeHelper> list,
			final ShapeHelper toAdd) {
		assert toAdd.points.size() > 3;
		List<ShapeHelper> result = new ArrayList<ShapeHelper>();
		ShapeHelper toMerge = new ShapeHelper(toAdd.points);
		List<Coord>merged = null;
		IntArrayList positionsToCheck = new IntArrayList(); 
		for (ShapeHelper sh:list){
			int shSize = sh.points.size();
			int toMergeSize = toMerge.points.size();
			if (shSize + toMergeSize - 3 >= PolygonSplitterFilter.MAX_POINT_IN_ELEMENT){
				// don't merge because merged polygon would be split again
				result.add(sh);
				continue;
			}
			positionsToCheck.clear();
			for (Coord co: sh.points)
				co.resetShapeCount();
			for (Coord co: toMerge.points)
				co.resetShapeCount();
			// increment the shape counter for all points, but
			// only once 
			int numDistinctPoints = 0;
			for (Coord co : sh.points) {
				if (co.getShapeCount() == 0){
					co.incShapeCount();
					++numDistinctPoints;
				}
			}
			if (numDistinctPoints+1 != shSize){
				// shape is self-intersecting or has repeated identical points
				long dd = 4;
			}
			for (int i = 0; i+1 < toMergeSize; i++){ 
				int usage = toMerge.points.get(i).getShapeCount();			
				if (usage > 0)
					positionsToCheck.add(i);
			}
			if (positionsToCheck.isEmpty()){
				result.add(sh);
				continue;
			}
			if (positionsToCheck.size() + 1 == toMergeSize){
				// toMerge is equal to sh, ignore it
				continue;
			}
			for (int i: positionsToCheck)
				toMerge.points.get(i).incShapeCount();
			
			if (positionsToCheck.size() < 2){
//				GpxCreator.createGpx("e:/ld/sh", sh.points);
//				GpxCreator.createGpx("e:/ld/toadd", toMerge.points);
				long dd = 4; 
			}
			// TODO: merge also when only one point is shared
			for (int i = 0; i < shSize; i++) {
				Coord other = sh.points.get(i);
				if (other.getShapeCount() < 2)
					continue;
				if (merged != null)
					break;
				for (int j : positionsToCheck){
					if (merged != null)
						break;
					Coord toAddCurrent = toMerge.points.get(j);
					if (other == toAddCurrent){
						// shapes share one point
						Coord otherNext = sh.points.get((i == shSize-1) ? 1: i + 1);
						Coord toAddPrev = toMerge.points.get((j == 0) ? toMergeSize-2 : j - 1);
						Coord toAddNext = toMerge.points.get((j == toMergeSize-1) ? 1: j + 1);
						if (otherNext == toAddNext){
							// shapes share an edge, one is clockwise, one is not
							List<Coord> reversed = new ArrayList<>(toMerge.points);
							Collections.reverse(reversed);
							int jr = reversed.size()-1 - j;
							merged = combine(sh.points, reversed, i, jr, otherNext);
							toMerge = new ShapeHelper(merged);
						}
						else if (otherNext == toAddPrev){
							merged = combine(sh.points, toMerge.points, i, j, otherNext);
							toMerge = new ShapeHelper(merged);
						}
					}
				}
			}
			if (merged == null)
				result.add(sh);
			merged = null;
		}
		if (merged == null)
			result.add(toMerge);
		if (result.size() > list.size()+1 )
			log.error("result list size is wrong " + list.size() + " -> " + result.size());
		return result;
	}

	/**
	 * Combine to shapes that share at least one edge 
	 * @param shape1 
	 * @param shape2
	 * @param posIn1
	 * @param posIn2
	 * @param stop
	 * @return merged shape
	 */
	private List<Coord> combine(List<Coord> shape1, List<Coord> shape2, int posIn1, int posIn2, Coord stop){
		int n1 = shape1.size();
		int n2 = shape2.size();
		List<Coord> merged = new ArrayList<Coord>(n1 + n2 - 3);
		merged.addAll(shape1.subList(0, posIn1+1));
		int k = posIn2+1;
		while (true){
			if (k == n2)
				k = 1;
			Coord co =  shape2.get(k++);
			if (co == stop)
				break;
			merged.add(co);
		}
		k = posIn1+2;
		while(k < n1 && shape1.get(k) == merged.get(merged.size()-1)){
			// remove spike
			k++;
			merged.remove(merged.size()-1);
		}
		
		merged.addAll(shape1.subList(k-1, n1));
		if (merged.size() < 4 || merged.get(0) != merged.get(merged.size()-1)){
			log.error("merginf shapes failed for shapes near " + stop.toOSMURL());
//			GpxCreator.createGpx("e:/ld/sh", shape1);
//			GpxCreator.createGpx("e:/ld/toadd", shape2);
//			GpxCreator.createGpx("e:/ld/merged", merged);
		}
		return merged;
	}
	
	private class ShapeHelper{
		List<Coord> points;
		
		public ShapeHelper(MapShape shape) {
			this.points = shape.getPoints();
		}

		public ShapeHelper(List<Coord> merged) {
			this.points = merged;
		}
		
	}
	
	/*
	private class HighPrecCoord  {
		final Coord co;
		int usageCount;
		
		public HighPrecCoord(Coord co){
			this.co = co;
		}
		
		@Override
		public int hashCode() {
			return co.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (!(obj instanceof HighPrecCoord))
				return false;
			HighPrecCoord other = (HighPrecCoord) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (!co.highPrecEquals(other.co))
				return false;
			return true;
		}

		private ShapeMergeFilter getOuterType() {
			return ShapeMergeFilter.this;
		}
		
	}
	*/
}

