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
package uk.me.parabola.mkgmap.reader.dem.optional;

import java.awt.*;
import java.awt.image.Raster;
import java.awt.image.renderable.ParameterBlock;
import java.io.IOException;
import java.io.Writer;

import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.mkgmap.reader.dem.DEM;

import com.sun.media.jai.codec.FileSeekableStream;
import com.sun.media.jai.codec.SeekableStream;
import com.sun.media.jai.codec.TIFFDecodeParam;

public abstract class GeoTiffDEM extends DEM
{
    Raster raster;
    String fileName;
    int minLat, minLon, maxLat, maxLon;
    PlanarImage image;

    void initData()
    {
	
	try {
	    SeekableStream s = new FileSeekableStream(fileName);
	    ParameterBlock pb = new ParameterBlock();
	    pb.add(s);
	    
	    TIFFDecodeParam param = new TIFFDecodeParam();
	    pb.add(param);
	    
	    RenderedOp op = JAI.create("tiff", pb);
	    image = op.createInstance();
	    System.out.printf("Image: %d %d %d %d\n", image.getWidth(), image.getHeight(), 
			      image.getNumXTiles(), image.getNumYTiles());
	}
	catch (Exception e) {
	    throw new ExitException("Failed to open/process " + fileName, e);
	}
    }

    protected static class CGIAR extends GeoTiffDEM
    {
	public CGIAR(String dataPath, double minLat, double minLon, double maxLat, double maxLon)
	{
	    this.lat = ((int) (minLat/5))*5;
	    this.lon = ((int) (minLon/5))*5;
	    if (maxLat > lat+5 || maxLon > lon+5)
		throw new ExitException("Area too large (must not span more than one CGIAR GeoTIFF)");
	    
	    int tileX = (180 + lon) / 5 + 1;
		int tileY = (60 - lat) / 5;
	    this.fileName = String.format("%s/srtm_%02d_%02d.tif", dataPath, tileX, tileY);
	    System.out.printf("CGIAR GeoTIFF: %s\n", fileName);
	    N = 6000;
	    M = 6000;
	    res = 5.0/M;
	    
	    initData();
	}
	
	public void serializeCopyRight(Writer out) throws IOException
	{
	    out.write("  <copyright>\n");
	    out.write("  Contour lines generated from improved SRTM data by CIAT-CSI (see http://srtm.csi.cgiar.org)\n");
	    out.write("  </copyright>\n");
	}
	
	protected void read(int minLon, int minLat, int maxLon, int maxLat)
	{
	    this.minLon = minLon;
	    this.minLat = minLat;
	    this.maxLon = maxLon;
	    this.maxLat = maxLat;
	    raster = image.getData(new Rectangle(minLon, 6000-maxLat-1, maxLon-minLon+1, maxLat-minLat+1));
	    System.out.printf("read: %d %d %d %d\n", minLon, 6000-maxLat-1, maxLon-minLon+1, maxLat-minLat+1);
	}

	public double ele(int x, int y)
	{
		int elevation = raster.getPixel(x, 6000-y-1, (int[])null)[0];
		return elevation+delta;
	}
    }
    
    protected static class ASTER extends GeoTiffDEM
    {
	
	public ASTER(String dataPath, double minLat, double minLon, double maxLat, double maxLon)
	{
	    this.lat = (int) minLat;
	    this.lon = (int) minLon;
	    if (maxLat > lat+1 || maxLon > lon+1)
		throw new ExitException("Area too large (must not span more than one ASTER GeoTIFF)");
	    
	    String northSouth = lat < 0 ? "S" : "N";
	    String eastWest = lon > 0 ? "E" : "W";
	    fileName = String.format("%s/ASTGTM_%s%02d%s%03d_dem.tif", dataPath, 
				     northSouth, lat < 0 ? -lat : lat, 
				     eastWest, lon < 0 ? -lon : lon);
	    
	    System.out.printf("ASTER GeoTIFF: %s\n", fileName);
	    N = 3600;
	    M = 3600;
	    res = 1.0/M;
	    initData();
	}
	
	public void serializeCopyRight(Writer out) throws IOException
	{
	    out.write("  <copyright>\n");
	    out.write("  Contour lines generated from DGM data by ASTER (see https://wist.echo.nasa.gov/~wist/api/imswelcome)\n");
	    out.write("  </copyright>\n");
	}
        
	protected void read(int minLon, int minLat, int maxLon, int maxLat)
	{
	    this.minLon = minLon;
	    this.minLat = minLat;
	    this.maxLon = maxLon;
	    this.maxLat = maxLat;
	    raster = image.getData(new Rectangle(minLon, 3601-maxLat-1, maxLon-minLon+1, maxLat-minLat+1));
	    System.out.printf("read: %d %d %d %d\n", minLon, 3601-maxLat-1, maxLon-minLon+1, maxLat-minLat+1);
	}

	public double ele(int x, int y)
	{
		int elevation = raster.getPixel(x, 3601-y-1, (int[])null)[0];
		return elevation+delta;
	}
    }

}

