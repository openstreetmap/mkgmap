package uk.me.parabola.mkgmap.reader.osm.boundary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;

import uk.me.parabola.log.Logger;
import uk.me.parabola.util.EnhancedProperties;

public class BoundaryPreparer extends Thread {
	private static final Logger log = Logger.getLogger(BoundaryPreparer.class);

	private final String boundaryFilename;
	private final String boundsDir;

	public BoundaryPreparer(EnhancedProperties properties) {
		this.boundaryFilename = properties
				.getProperty("createboundsfile", null);
		this.boundsDir = properties.getProperty("boundsdirectory", "bounds");
	}

	public void run() {
		if (boundaryFilename == null) {
			return;
		}
		try {
			BoundarySaver saver = new BoundarySaver(new File(boundsDir));
			Osm5BoundaryDataSource dataSource = new Osm5BoundaryDataSource(saver);
			log.info("Started loading", boundaryFilename);
			dataSource.load(boundaryFilename);
			log.info("Finished loading", boundaryFilename);
			saver.end();
		} catch (FileNotFoundException exp) {
			log.error("Boundary file " + boundaryFilename + " not found.");
		} 
	}






	public static void main(String[] args) throws IOException,
			ClassNotFoundException {
		FileInputStream in = new FileInputStream(
				"C:/Garmin/Tools/mkgmap/index/test.bnd");
		ObjectInputStream obj = new ObjectInputStream(in);

		do {
			Object next = obj.readObject();
			System.out.println(next.getClass());
			if (next instanceof Boundary) {
				Boundary b = (Boundary) next;
				System.out.println(b.getArea().getBounds());
			}
		} while (true);
	}

}
