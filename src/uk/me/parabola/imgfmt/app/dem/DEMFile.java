/*
 */
package uk.me.parabola.imgfmt.app.dem;

import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.fs.ImgChannel;

/**
 * The DEM file. This consists of information about elevation. It is used for hill shading
 * and to calculation the "ele" values in gpx tracks. Based on work of Frank Stinner. 
 *
 * @author Gerd Petermann
 */
public class DEMFile extends ImgFile {
	private final DEMHeader demHeader = new DEMHeader();

	public DEMFile(ImgChannel chan) {
		setHeader(demHeader);
		setWriter(new BufferedImgFileWriter(chan));
		position(DEMHeader.HEADER_LEN);
	}

}
