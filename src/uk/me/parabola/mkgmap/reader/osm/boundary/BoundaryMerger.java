/*
 * Copyright (C) 2006, 2011.
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
package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.awt.geom.Area;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

import uk.me.parabola.mkgmap.reader.osm.boundary.BoundaryUtil.BoundaryFileFilter;

/**
 * The class merges two directories with precompiled bounds files. Both directory may contain files covering
 * the same area. These files are merged so that the resulting file contains all boundaries of both
 * files. Boundaries with the same OSM-id that are contained in both files are merged with a union of its covered area.
 * <br/><br/>
 * Using this merger it is possible to create several smaller bounds compilations (e.g. one country only) and merge 
 * these compilations together afterwards.
 * 
 * @author WanMil
 */
public class BoundaryMerger {

	public BoundaryMerger() {
		
	}
	
	
	private static class BoundsComparator implements Comparator<Boundary>{

		public int compare(Boundary o1, Boundary o2) {
			if (o1 == o2) {
				return 0;
			}
			String id1 = o1.getTags().get("mkgmap:boundaryid");
			String id2 = o2.getTags().get("mkgmap:boundaryid");
			return id1.compareTo(id2);
		}
		
	}
	
	public Boundary merge(Boundary b1, Boundary b2) {
		Area mergedArea = b1.getArea();
		mergedArea.add(b2.getArea());
		
		// Use the tags of the second boundary as we suppose they are more actual.
		// Usually one should use the merge only with files compiled from the 
		// same planet file.
		return new Boundary(mergedArea, b2.getTags());
	}
	
	public List<Boundary> merge(List<Boundary> bl1, List<Boundary> bl2) {
		BoundsComparator bComp = new BoundsComparator();
		Collections.sort(bl1, bComp);
		Collections.sort(bl2, bComp);
		
		List<Boundary> result = new ArrayList<Boundary>();
		
		ListIterator<Boundary> i1 = bl1.listIterator();
		ListIterator<Boundary> i2 = bl2.listIterator();
		
		while (i1.hasNext()||i2.hasNext()) {
			Boundary b1 = (i1.hasNext() ? i1.next() : null);
			Boundary b2 = (i2.hasNext() ? i2.next() : null);
			
			if (b1 == null) {
				result.add(b2);
			} else if (b2 == null) {
				result.add(b1);
			} else {
				int cmp = bComp.compare(b1, b2);
				if (cmp < 0) {
					result.add(b1);
					i2.previous();
				} else if (cmp > 0) {
					result.add(b2);
					i1.previous();
				} else {
					Boundary merge = merge(b1,b2);
					result.add(merge);
				}
				
			}
		}
		return result;
	}
	
	private void copy(File file, File to) {
		String filename = file.getName();
		
		try {
	      FileChannel in = (new FileInputStream(file)).getChannel();
	      FileChannel out = (new FileOutputStream(new File(to,filename))).getChannel();
	      in.transferTo(0, file.length(), out);
	      in.close();
	      out.close();
		} catch (IOException exp) {
			System.err.println(exp);
		}
	}
	
	public void merge(File dir1, File dir2, File targetDir) throws IOException {
		
		File[] bndList1 = dir1.listFiles(new BoundaryFileFilter());
		File[] bndList2 = dir2.listFiles(new BoundaryFileFilter());
		
		Comparator<File> snSort = new Comparator<File>() {
			public int compare(File o1, File o2) {
				String name1 = o1.getName();
				String name2 = o2.getName();
				return name1.compareTo(name2);
			}
		};
		
		List<File> fl1 = new ArrayList<File>(Arrays.asList(bndList1));
		List<File> fl2 = new ArrayList<File>(Arrays.asList(bndList2));
		
		Collections.sort(fl1,snSort);
		Collections.sort(fl2,snSort);
		
		ListIterator<File> fl1Iter = fl1.listIterator();
		ListIterator<File> fl2Iter = fl2.listIterator();
		
		BoundarySaver bSave = new BoundarySaver(targetDir);
		bSave.setCreateEmptyFiles(false);
		
		List<File> copy = new ArrayList<File>();
		
		int processed = 0;
		int all = fl1.size()+fl2.size();
		
		while (fl1Iter.hasNext() || fl2Iter.hasNext()) {
			File f1 = (fl1Iter.hasNext() ? fl1Iter.next(): null);
			File f2 = (fl2Iter.hasNext() ? fl2Iter.next(): null);

			if (f1 == null) {
				copy.add(f2);
			} else if (f2 == null) {
				copy.add(f1);
			} else {
				int cmp = snSort.compare(f1, f2);
				if (cmp < 0) {
					copy.add(f1);
					fl2Iter.previous();
				} else if (cmp > 0) {
					copy.add(f2);
					fl1Iter.previous();
				} else {
					List<Boundary> lb1 = BoundaryUtil.loadBoundaryFile(f1, null);
					List<Boundary> lb2 = BoundaryUtil.loadBoundaryFile(f2, null);
					List<Boundary> merged = merge(lb1, lb2);
					bSave.addBoundaries(merged);
					processed += 2;
					System.out.println(processed+"/"+all+" processed");
				}
			}
		}
		bSave.end();
		
		for (File f : copy) {
			copy(f,targetDir);
			processed++;
			System.out.println(processed+"/"+all+" processed");
		}
	}
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			System.err.println("java uk.me.parabola.mkgmap.reader.osm.boundary.BoundaryMerger <bounds1> <bounds2> <merge>");
			return;
		}

		File b1 = new File(args[0]);
		File b2 = new File(args[1]);

		File merge = new File(args[2]);

		if (b1.exists() == false || b1.isDirectory() == false) {
			System.err.println(b1 + " does not exist or is not a directory");
			return;
		}

		if (b2.exists() == false || b2.isDirectory() == false) {
			System.err.println(b2 + " does not exist or is not a directory");
			return;
		}

		if (merge.exists() && merge.isDirectory() == false) {
			System.err.println(merge + " is not a directory");
		}

		if (merge.exists() == false) {
			merge.mkdirs();
		}
	
		BoundaryMerger merger = new BoundaryMerger();
		merger.merge(b1, b2, merge);
		
	}

}
