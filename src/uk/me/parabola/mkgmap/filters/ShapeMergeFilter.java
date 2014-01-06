package uk.me.parabola.mkgmap.filters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.general.MapShape;
import uk.me.parabola.util.GpxCreator;
import uk.me.parabola.util.MultiHashMap;



public class ShapeMergeFilter{
	private static final Logger log = Logger.getLogger(ShapeMergeFilter.class);
	private final int shift;

	public ShapeMergeFilter(int shift) {
		this.shift = shift;
	}

	public List<MapShape> merge(List<MapShape> shapes, int subdivId) {
		if (shapes.size() <= 1)
			return shapes;
		
		MultiHashMap<Integer, HashMap<MapShape, List<ShapeHelper>>> topMap = new MultiHashMap<Integer, HashMap<MapShape,List<ShapeHelper>>>();
		List<MapShape> mergedShapes = new ArrayList<MapShape>();
		int i = 0;
		for (MapShape shape: shapes) {
			if (shape.getPoints().size() > 100){
				mergedShapes.add(shape);
				continue;
			}
			
//			GpxCreator.createGpx("e:/ld/tm_" + i++, shape.getPoints());
			List<HashMap<MapShape, List<ShapeHelper>>> sameTypeList = topMap.get(shape.getType());
			ShapeHelper sh = new ShapeHelper(shape);
			if (sameTypeList.isEmpty()){
				HashMap<MapShape, List<ShapeHelper>> lowMap = new HashMap<MapShape, List<ShapeHelper>>();
				ArrayList<ShapeHelper> list = new ArrayList<ShapeHelper>();
				list.add(sh);
				lowMap.put(shape, list);
				topMap.add(shape.getType(),lowMap);
				continue;
			}
			for (HashMap<MapShape, List<ShapeHelper>> lowMap : sameTypeList){
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
		for (List<HashMap<MapShape, List<ShapeHelper>>> sameTypeList : topMap.values()){
			i++;
			int j = 0;
			for (HashMap<MapShape, List<ShapeHelper>> lowMap : sameTypeList){
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
						newShape.setPoints(sh.points);
//						GpxCreator.createGpx("e:/ld/mr_" + subdivId + "_"+i+"_"+j+"_"+k+"_"+l, newShape.getPoints());
						mergedShapes.add(newShape);
					}
				}
			}
		}
		return mergedShapes;
	}

	/**
	 * Try to merge a shape with one of the shapes in the list.
	 *  If it cannot be merged, it is added to the list.   
	 * @param list
	 * @param toAdd
	 * @return
	 */
	private List<ShapeHelper> addWithoutCreatingHoles(List<ShapeHelper> list,
			final ShapeHelper toAdd) {
		List<ShapeHelper> result = new LinkedList<ShapeHelper>();
		ShapeHelper toMerge = new ShapeHelper(toAdd.points);
		List<Coord>merged = null;
		for (ShapeHelper sh:list){
			int shSize = sh.points.size();
			for (int i = 0; i < shSize; i++) {
				if (merged != null)
					break;
				int toMergeSize = toMerge.points.size();
				if (shSize + toMergeSize - 3 >= PolygonSplitterFilter.MAX_POINT_IN_ELEMENT)
					continue;
				Coord p1 = sh.points.get(i);
				
				for (int j = 0; j < toMergeSize; j++){
					if (merged != null)
						break;
					Coord p2 = toMerge.points.get(j);
					if (p1 == p2){
						// shapes share one point
						Coord otherPrev = sh.points.get((i == 0) ? shSize-2 : i - 1);
						Coord otherNext = sh.points.get((i == shSize-1) ? 1: i + 1);
						Coord toAddPrev = toMerge.points.get((j == 0) ? toMergeSize-2 : j - 1);
						Coord toAddNext = toMerge.points.get((j == toMergeSize-1) ? 1: j + 1);
						if (otherNext == toAddNext){
							// shapes share an edge, one is clockwise, one is not
							merged = new ArrayList<Coord>(shSize + toMergeSize - 3);
							List<Coord> reversed = new ArrayList<>(toMerge.points);
							Collections.reverse(reversed);
							merged.addAll(sh.points.subList(0, i));
							int jr = reversed.size()-1 - j;
							if (jr > 0)
								merged.addAll(reversed.subList(jr,toMergeSize));
							else 
								merged.addAll(reversed.subList(jr,toMergeSize-1));
							if (jr > 1)
							merged.addAll(reversed.subList(0,jr - 1));
							merged.addAll(sh.points.subList(i+1, shSize));
//							GpxCreator.createGpx("e:/ld/sh", sh.points);
//							GpxCreator.createGpx("e:/ld/toadd", toMerge.points);
//							GpxCreator.createGpx("e:/ld/merged", merged);
							toMerge = new ShapeHelper(merged);
						}
						else if (otherNext == toAddPrev){
							merged = new ArrayList<Coord>(shSize + toMergeSize - 3);
							merged.addAll(sh.points.subList(0, i));
							if (j > 0)
								merged.addAll(toMerge.points.subList(j, toMergeSize));
							else 
								merged.addAll(toMerge.points.subList(j, toMergeSize-1));
							if (j > 1)
								merged.addAll(toMerge.points.subList(1, j-1));
							merged.addAll(sh.points.subList(i+1, shSize));
//							GpxCreator.createGpx("e:/ld/sh", sh.points);
//							GpxCreator.createGpx("e:/ld/toadd", toMerge.points);
//							GpxCreator.createGpx("e:/ld/merged", merged);
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

	
	private class ShapeHelper{
		List<Coord> points;
		
		public ShapeHelper(MapShape shape) {
			this.points = shape.getPoints();
		}

		public ShapeHelper(List<Coord> merged) {
			this.points = merged;
		}
		
	}
}

