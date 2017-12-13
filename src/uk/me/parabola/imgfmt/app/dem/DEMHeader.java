/*
 */
package uk.me.parabola.imgfmt.app.dem;

import java.util.ArrayList;
import java.util.List;

import uk.me.parabola.imgfmt.ReadFailedException;
import uk.me.parabola.imgfmt.app.CommonHeader;
import uk.me.parabola.imgfmt.app.ImgFileReader;
import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * The header of the DEM file.
 * 
 * @author Gerd Petermann
 */
public class DEMHeader extends CommonHeader {
	public static final int HEADER_LEN = 41; // Other lengths are possible
	
	private final List<DEMSection> zoomLevels = new ArrayList<>();
	
	private int offset;

	public DEMHeader() {
		super(HEADER_LEN, "GARMIN DEM");
	}

	
	/**
	 * Write the rest of the header.  It is guaranteed that the writer will be set
	 * to the correct position before calling.
	 *
	 * @param writer The header is written here.
	 */
	protected void writeFileHeader(ImgFileWriter writer) {
		writer.putInt(1); // flags 1: elevation in metres
		writer.put2(zoomLevels.size());
		writer.putInt(0); // unknown
		writer.put2(60); // size of zoom level record
		writer.putInt(offset); // elevation in metres
		writer.putInt(1); // elevation in metres
		for (int i = 0; i < zoomLevels.size(); i++) {
			zoomLevels.get(i).writeHeader(writer);
		}
		for (int i = 0; i < zoomLevels.size(); i++) {
			zoomLevels.get(i).writeRest(writer);
		}
	}

	@Override
	protected void readFileHeader(ImgFileReader reader) throws ReadFailedException {
	}

}
