package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import uk.me.parabola.mkgmap.reader.osm.Tags;

public class BoundaryLister {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		String boundsdir = args[0];
		String outDirName = boundsdir;
		if (args.length >= 2)
			outDirName = args[1];
		File outDir = new File (outDirName);
		
		if (outDir.exists() ){
			if (outDir.isDirectory() == false){
				System.err.println("target is not a directory, output is written to bounds.txt");
				outDir = new File(".");
			}
		}
		
		List<String> bndFileNames =  BoundaryUtil.getBoundaryDirContent(boundsdir);
		PrintWriter out = new PrintWriter(new File(outDir,"bounds.txt"), "UTF-8");
		// create an empty search bbox to speedup reading of the bnd file(s) 
		//uk.me.parabola.imgfmt.app.Area searchBbox = new uk.me.parabola.imgfmt.app.Area (0,0,0,0);
		for (String bndFile : bndFileNames) {
			out.println(bndFile + "****************");
			BoundaryQuadTree bqt = BoundaryUtil.loadQuadTree(boundsdir, bndFile);
			if (bqt == null)
				break;
			Map<String, Tags> map = bqt.getTagsMap(); 
			for ( Entry<String, Tags>  entry: map.entrySet()) {
				TreeMap<String,String> btree = new TreeMap<String, String>();
				String line = bndFile+ ":" + entry.getKey();
				Iterator<Entry<String,String>> tagIter = entry.getValue().entryIterator();
				while (tagIter.hasNext()) {
					Entry<String,String> tag = tagIter.next();
					btree.put(tag.getKey(),tag.getValue());
				}
				// print sorted tags
				for (Entry<String,String> e : btree.entrySet()){
					out.println(line + ";" + e);
				}
				
			}
		}
		out.close();
		

	}

}
