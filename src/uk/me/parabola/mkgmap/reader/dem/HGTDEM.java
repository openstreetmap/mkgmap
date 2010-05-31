/*
 * Copyright (C) 2009 Christian Gawron
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Christian Gawron
 * Create date: 03-Jul-2009
 */
package uk.me.parabola.mkgmap.reader.dem;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.MappedByteBuffer;

import uk.me.parabola.imgfmt.ExitException;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;

public class HGTDEM extends DEM
{
    private MappedByteBuffer buffer ;
    
    public HGTDEM(String dataPath, double minLat, double minLon, double maxLat, double maxLon)
    {
	this.lat = (int) minLat;
	this.lon = (int) minLon;
	if (maxLat > lat+1 || maxLon > lon+1)
	    throw new ExitException("Area too large (must not span more than one SRTM file)");
	
	String northSouth = lat < 0 ? "S" : "N";
	String eastWest = lon > 0 ? "E" : "W";
	String fileName = String.format("%s/%s%02d%s%03d.hgt", dataPath, 
					northSouth, lat < 0 ? -lat : lat, 
					eastWest, lon < 0 ? -lon : lon);
	try {
	    FileInputStream is = new FileInputStream(fileName);
	    buffer = is.getChannel().map(READ_ONLY, 0, 2*(M+1)*(M+1));
	}
	catch (Exception e) {
	    throw new ExitException("failed to open " + fileName, e);
	}
    }
    
    public  void read(int minLon, int minLat, int maxLon, int maxLat)
    {
    }
    
    public double ele(int x, int y)
    {
	return buffer.getShort(2*((M-y)*(M+1)+x))+delta;
    }
    
    public void serializeCopyRight(Writer out) throws IOException
    {
	out.write("  <copyright>\n");
	out.write("  Contour lines generated from DEM data by NASA\n");
	out.write("  </copyright>\n");
    }
}
