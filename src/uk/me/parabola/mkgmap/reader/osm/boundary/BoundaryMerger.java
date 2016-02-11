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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

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
	
	private void copy(File file, File to) {
		String filename = file.getName();
		try (FileChannel in = (new FileInputStream(file)).getChannel();
				FileChannel out = (new FileOutputStream(new File(to, filename))).getChannel()) {
			in.transferTo(0, file.length(), out);
			in.close();
			out.close();
		} catch (IOException exp) {
			System.err.println(exp);
		}
	}
	
	public void merge(File dir1, File dir2, File targetDir) throws IOException {
		List<String> fl1 = BoundaryUtil.getBoundaryDirContent(dir1.getAbsolutePath());
		List<String> fl2 = BoundaryUtil.getBoundaryDirContent(dir2.getAbsolutePath());
		
		Collections.sort(fl1);
		Collections.sort(fl2);
		
		ListIterator<String> fl1Iter = fl1.listIterator();
		ListIterator<String> fl2Iter = fl2.listIterator();
		
		BoundarySaver bSave = new BoundarySaver(targetDir, BoundarySaver.QUADTREE_DATA_FORMAT);
		bSave.setCreateEmptyFiles(false);
		
		List<File> copy = new ArrayList<File>();
		
		int processed = 0;
		int all = fl1.size()+fl2.size();
		
		while (fl1Iter.hasNext() || fl2Iter.hasNext()) {
			String f1 = (fl1Iter.hasNext() ? fl1Iter.next(): null);
			String f2 = (fl2Iter.hasNext() ? fl2Iter.next(): null);

			if (f1 == null) {
				copy.add(new File(dir2,f2));
			} else if (f2 == null) {
				copy.add(new File(dir1,f1));
			} else {
				int cmp = f1.compareTo(f2);
				if (cmp < 0) {
					copy.add(new File(dir1,f1));
					fl2Iter.previous();
				} else if (cmp > 0) {
					copy.add(new File(dir2,f2));
					fl1Iter.previous();
				} else {
					BoundaryQuadTree bqt1 = BoundaryUtil.loadQuadTree(dir1.getAbsolutePath(), f1);
					if (bqt1 == null){
						System.err.println("Failed to load quadtree for " + dir1.getAbsolutePath() + f1);
						System.exit(-1);
					}
					BoundaryQuadTree bqt2 = BoundaryUtil.loadQuadTree(dir2.getAbsolutePath(), f2);
					if (bqt2 == null){
						System.err.println("Failed to load quadtree for " + dir2.getAbsolutePath() + f2);
						System.exit(-1);
					}
					
					bqt1.merge(bqt2);
					bSave.saveQuadTree(bqt1, f1);
					processed += 2;
					System.out.println(processed+"/"+all+" processed");
				}
			}
		}
		bSave.end();
		
		for (File f: copy) {
			copy(f, targetDir);
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
			System.err.println("<bounds1> <bounds2> : directories with *.bnd files to merge");
			System.err.println("<merge> target directory, is created if it doesn't exist");
			
			return;
		}

		File b1 = new File(args[0]);
		File b2 = new File(args[1]);

		File merge = new File(args[2]);

		// TODO: maybe allow zip as input
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
