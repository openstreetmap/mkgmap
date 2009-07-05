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

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.MappedByteBuffer;

import java.util.Vector;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

import com.sun.media.jai.codec.*;
import javax.media.jai.*;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.Raster;
import java.awt.image.DataBuffer;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.FormatException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.app.Area;
import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.imgfmt.app.map.Map;

import uk.me.parabola.mkgmap.build.MapBuilder;
import uk.me.parabola.mkgmap.general.MapDetails;
import uk.me.parabola.mkgmap.general.MapPoint;
import uk.me.parabola.mkgmap.general.MapLine;
import uk.me.parabola.mkgmap.general.LevelInfo;
import uk.me.parabola.mkgmap.general.LoadableMapDataSource;
import uk.me.parabola.mkgmap.reader.MapperBasedMapDataSource;
import uk.me.parabola.mkgmap.reader.osm.OsmConverter;
import uk.me.parabola.mkgmap.reader.osm.Style;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.mkgmap.osmstyle.StyleImpl;
import uk.me.parabola.mkgmap.osmstyle.StyledConverter;
import uk.me.parabola.mkgmap.osmstyle.eval.SyntaxException;
import uk.me.parabola.util.EnhancedProperties;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;

/**
 * Create contour lines using an algorithm similar to that described in 
 * <a href="http://mapcontext.com/autocarto/proceedings/auto-carto-5/pdf/an-adaptive-grid-contouring-algorithm.pdf">An Adaptive Grid Contouring Algorithm</a> by Downing and Zoraster. 
 */
public abstract class DEM 
{
    final static double epsilon = 1e-9;
    final static double delta = 1.5;
    final static int maxPoints=200000;
    final static double minDist = 15;
    final static double maxDist = 21;
    final static double step = 0.01;
    final static double semiMajorAxis = 6378137.0;
    final static double inverseFlattening = 298.257223563;

    static int M=1200;
    static int N=M;
    static double res = 1.0/N;
    static int id = -1;

    short values[] = null;
    int lat;
    int lon;
    
    abstract double ele(int x, int y);
    abstract void read(int minLon, int minLat, int maxLon, int maxLat);
    abstract void serializeCopyRight(Writer out) throws IOException;

    public static void createContours(LoadableMapDataSource mapData, EnhancedProperties config)
    {
	Area bounds = mapData.getBounds();

	double minLat = Utils.toDegrees(bounds.getMinLat());
	double minLon = Utils.toDegrees(bounds.getMinLong());
	double maxLat = Utils.toDegrees(bounds.getMaxLat());
	double maxLon = Utils.toDegrees(bounds.getMaxLong());
	
	System.out.printf("bounds: %f %f %f %f\n", minLat, minLon, maxLat, maxLon);
	DEM data;
	String demType = config.getProperty("dem-type", "CGIAR");

	String dataPath;
	if (demType.equals("ASTER")) {
	    dataPath = config.getProperty("dem-path", "ASTER");
	    data = new ASTERGeoTiff(dataPath, minLat, minLon, maxLat, maxLon);
	}
	else if (demType.equals("CGIAR")) {
	    dataPath = config.getProperty("dem-path", "CGIAR");
	    data = new CGIARGeoTiff(dataPath, minLat, minLon, maxLat, maxLon);
	}
	else {
	    dataPath = config.getProperty("dem-path", "SRTM");
	    data = new HGTDEM(dataPath, minLat, minLon, maxLat, maxLon);
	}
	
	Isolines lines = data.new Isolines(data, minLat, minLon, maxLat, maxLon);
	int increment = config.getProperty("dem-increment", 10);

	double minHeight = lines.getMinHeight();
	double maxHeight = lines.getMaxHeight();
	int maxLevels = config.getProperty("dem-maxlevels", 100);
	while ((maxHeight-minHeight)/increment > maxLevels)
	    increment *= 2;

	String loc = config.getProperty("style-file");
	if (loc == null)
	    loc = config.getProperty("map-features");
	String name = config.getProperty("style");
	
	if (loc == null && name == null)
	    name = "default";

	LoadableMapDataSource dest = mapData;
	if (config.getProperty("dem-separate-img", false)) {
	    dest = new DEMMapDataSource(mapData, config);
	}

	
	OsmConverter converter;
	try {
	    Style style = new StyleImpl(loc, name);
	    style.applyOptionOverride(config);
	    
	    converter = new StyledConverter(style, ((MapperBasedMapDataSource) dest).getMapper(), config);
	} catch (SyntaxException e) {
	    System.err.println("Error in style: " + e.getMessage());
	    throw new ExitException("Could not open style " + name);
	} catch (FileNotFoundException e) {
	    String name1 = (name != null)? name: loc;
	    throw new ExitException("Could not open style " + name1);
	}
	
	for (int level=0; level<maxHeight; level+=increment) {
	    if (level < minHeight) continue;

	    // create isolines
	    lines.addLevel(level);
	
    	    for (Isolines.Isoline line : lines.isolines) {
		Way way = new Way(id--, line.points);
		way.addTag("contour", "elevation");
		way.addTag("ele", String.format("%d", (int) line.level));
		converter.convertWay(way);
	    }
	    lines.isolines.clear();
	}
	
	if (config.getProperty("dem-separate-img", false)) {
	    MapBuilder builder = new MapBuilder();
	    builder.config(config);

	    FileSystemParam params = new FileSystemParam();
	    params.setMapDescription("contour lines");
	    long mapName = Integer.valueOf(config.getProperty("mapname", "63240000"));
	    try {
		Map map = Map.createMap(String.format("%08d", mapName+10000000), params);
		builder.makeMap(map, dest);
		map.close();
	    }
	    catch (Exception ex) {
		throw new RuntimeException(ex);
	    }
	}
    }

    public static class HGTDEM extends DEM
    {
	MappedByteBuffer buffer = null;

	public HGTDEM(String dataPath, double minLat, double minLon, double maxLat, double maxLon)
	{
	    this.lat = (int) minLat;
	    this.lon = (int) minLon;
	    if (maxLat > lat+1 || maxLon > lon+1)
		throw new RuntimeException("Area too large (must not span more than one SRTM file)");

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
		throw new RuntimeException(e);
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

    public static class CGIARGeoTiff extends DEM
    {
	Raster raster;
	String fileName;
	int minLat, minLon, maxLat, maxLon;
	PlanarImage image;

	public CGIARGeoTiff(String dataPath, double minLat, double minLon, double maxLat, double maxLon)
	{
	    this.lat = ((int) (minLat/5))*5;
	    this.lon = ((int) (minLon/5))*5;
	    if (maxLat > lat+5 || maxLon > lon+5)
		throw new RuntimeException("Area too large (must not span more than one CGIAR GeoTIFF)");

	    int tileX, tileY;
	    tileX = (180 + lon)/5 + 1;
	    tileY = (60 - lat)/5;
	    this.fileName = String.format("%s/srtm_%02d_%02d.tif", dataPath, tileX, tileY);
	    init();
	}

	public void serializeCopyRight(Writer out) throws IOException
	{
	    out.write("  <copyright>\n");
	    out.write("  Contour lines generated from improved SRTM data by CIAT-CSI (see http://srtm.csi.cgiar.org)\n");
	    out.write("  </copyright>\n");
	}
	
	public  void read(int minLon, int minLat, int maxLon, int maxLat)
	{
	    this.minLon = minLon;
	    this.minLat = minLat;
	    this.maxLon = maxLon;
	    this.maxLat = maxLat;
	    raster = image.getData(new java.awt.Rectangle(minLon, 6000-maxLat-1, maxLon-minLon+1, maxLat-minLat+1));
	    System.out.printf("read: %d %d %d %d\n", minLon, 6000-maxLat-1, maxLon-minLon+1, maxLat-minLat+1);
	}

	void init()
	{
	    System.out.printf("CGIAR GeoTIFF: %s\n", fileName);
	    N = 6000;
	    M = 6000;
	    res = 5.0/M;
	    
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
		throw new RuntimeException(e);
	    }
	}

	public double ele(int x, int y)
	{
	    try {
		int elevation = raster.getPixel(x, 6000-y-1, (int[])null)[0];
		return elevation+delta;
	    }
	    catch (ArrayIndexOutOfBoundsException ex) {
		System.out.printf("ele: (%d, %d) (%d, %d, %d, %d)  %s\n", 
				  x, 6000-y-1, 
				  raster.getMinX(), raster.getMinY(), 
				  raster.getWidth(), raster.getHeight(), ex.toString());
		throw ex;
	    }
	}
    }

    public static class ASTERGeoTiff extends DEM
    {
	Raster raster;
	String fileName;
	int minLat, minLon, maxLat, maxLon;
	PlanarImage image;

	public ASTERGeoTiff(String dataPath, double minLat, double minLon, double maxLat, double maxLon)
	{
	    this.lat = (int) minLat;
	    this.lon = (int) minLon;
	    if (maxLat > lat+1 || maxLon > lon+1)
		throw new RuntimeException("Area too large (must not span more than one ASTER GeoTIFF)");

	    String northSouth = lat < 0 ? "S" : "N";
	    String eastWest = lon > 0 ? "E" : "W";
	    fileName = String.format("%s/ASTGTM_%s%02d%s%03d_dem.tif", dataPath, 
				     northSouth, lat < 0 ? -lat : lat, 
				     eastWest, lon < 0 ? -lon : lon);
	    init();
	}

	public void serializeCopyRight(Writer out) throws IOException
	{
	    out.write("  <copyright>\n");
	    out.write("  Contour lines generated from DGM data by ASTER (see https://wist.echo.nasa.gov/~wist/api/imswelcome)\n");
	    out.write("  </copyright>\n");
	}
	
	public  void read(int minLon, int minLat, int maxLon, int maxLat)
	{
	    this.minLon = minLon;
	    this.minLat = minLat;
	    this.maxLon = maxLon;
	    this.maxLat = maxLat;
	    raster = image.getData(new java.awt.Rectangle(minLon, 3601-maxLat-1, maxLon-minLon+1, maxLat-minLat+1));
	    System.out.printf("read: %d %d %d %d\n", minLon, 3601-maxLat-1, maxLon-minLon+1, maxLat-minLat+1);
	}

	void init()
	{
	    System.out.printf("ASTER GeoTIFF: %s\n", fileName);
	    N = 3600;
	    M = 3600;
	    res = 1.0/M;
	    
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
		throw new RuntimeException(e);
	    }
	}

	public double ele(int x, int y)
	{
	    try {
		int elevation = raster.getPixel(x, 3601-y-1, (int[])null)[0];
		return elevation+delta;
	    }
	    catch (ArrayIndexOutOfBoundsException ex) {
		System.out.printf("ele: (%d, %d) (%d, %d, %d, %d)  %s\n", 
				  x, 3601-y-1, 
				  raster.getMinX(), raster.getMinY(), 
				  raster.getWidth(), raster.getHeight(), ex.toString());
		throw ex;
	    }
	}
    }

    private static void debug(String format, Object ... args)
    {
	//System.out.printf(format + "\n", args);
    }


    int lastXi = -1;
    int lastYi = -1;

    final static int bcInv[][]= {
	{ 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
	{ 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0}, 
	{ -3, 0, 0, 3, 0, 0, 0, 0, -2, 0, 0, -1, 0, 0, 0, 0},
	{ 2, 0, 0, -2, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0}, 
	{ 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 
	{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0}, 
	{ 0, 0, 0, 0, -3, 0, 0, 3, 0, 0, 0, 0, -2, 0, 0, -1}, 
	{ 0, 0, 0, 0, 2, 0, 0, -2, 0, 0, 0, 0, 1, 0, 0, 1}, 
	{ -3, 3, 0, 0, -2, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 
	{ 0, 0, 0, 0, 0, 0, 0, 0, -3, 3, 0, 0, -2, -1, 0, 0}, 
	{ 9, -9, 9, -9, 6, 3, -3, -6, 6, -6, -3, 3, 4, 2, 1, 2}, 
	{ -6, 6, -6, 6, -4, -2, 2, 4, -3, 3, 3, -3, -2, -1, -1, -2}, 
	{ 2, -2, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 
	{ 0, 0, 0, 0, 0, 0, 0, 0, 2, -2, 0, 0, 1, 1, 0, 0}, 
	{ -6, 6, -6, 6, -3, -3, 3, 3, -4, 4, 2, -2, -2, -2, -1, -1}, 
	{ 4, -4, 4, -4, 2, 2, -2, -2, 2, -2, -2, 2, 1, 1, 1, 1}
    };

    static int lastId=1000000000;
    static double lastX = 0;
    static double lastY = 0;

    static int edge[] = new int[2];
    
    static int off0[][] = {{ 0,  0},
			   { 0,  0},
			   { 0,  1},
			   { 1,  1}};
    
    static int off1[][] = {{ 1,  0},
			   { 0,  1},
			   { 1,  1},
			   { 1,  0}};
    
    static int brd[] = { 1, 2, 4, 8 };
    static int inv[] = { 4, 8, 1, 2 };
    
    static int rev[] = { 2, 3, 0, 1 };
    
    static int mov[][] = {{ 0, -1},
			  {-1,  0},
			  { 0,  1},
			  { 1,  0}};


    double bc[][] = new double[4][4];
    double bc_y[] = new double[4];
    double bc_y1[] = new double[4];
    double bc_y2[] = new double[4];
    double bc_y12[] = new double[4];
    double bc_Coeff[] = new double[16];
    double bc_x[] = new double[16];

    private void recalculateCoefficients(int xi, int yi)
    {
	double v00, vp0, v0p, vpp;
	double vm0, v0m, vpm, vmp, vmm, vmP, vPm; 
	double vP0, v0P, vPp, vpP, vPP;

	v00 = ele(xi, yi);
	v0p = ele(xi, yi+1);
	vpp = ele(xi+1, yi+1);
	vp0 = ele(xi+1, yi);

	vm0 = ele(xi-1, yi);
	v0m = ele(xi, yi-1);
	vmp = ele(xi-1, yi+1);
	vpm = ele(xi+1, yi-1);
	vmm = ele(xi-1, yi-1);
	vmP = ele(xi+2, yi-1);
	vPm = ele(xi-1, yi+2);

	vP0 = ele(xi+2, yi);
	v0P = ele(xi, yi+2);
	vPp = ele(xi+2, yi+1);
	vpP = ele(xi+1, yi+2);
	vPP = ele(xi+2, yi+2);

	bc_y[0] = v00;
	bc_y[1] = vp0;
	bc_y[2] = vpp;
	bc_y[3] = v0p;

	bc_y1[0] = (vp0-vm0)/2;
	bc_y1[1] = (vP0-v00)/2;
	bc_y1[2] = (vPp-v0p)/2;
	bc_y1[3] = (vpp-vmp)/2;

	bc_y2[0] = (v0p-v0m)/2;
	bc_y2[1] = (vpp-vpm)/2;
	bc_y2[2] = (vpP-vp0)/2;
	bc_y2[3] = (v0P-v00)/2;

	bc_y12[0] = (vpp - vpm - vmp + vmm) / 4;
	bc_y12[0] = (vPp - vPm - v0p + v0m) / 4;
	bc_y12[2] = (vPP - vP0 - v0P + v00) / 4;
	bc_y12[0] = (vpP - vp0 - vmP + vm0) / 4;

	int j, i;
	double s;

	for (i=0; i<4; i++) 
	{
	    bc_x[i]=bc_y[i];
	    bc_x[i+4]=bc_y1[i];
	    bc_x[i+8]=bc_y2[i];
	    bc_x[i+12]=bc_y12[i];
	}

	for (i=0; i<16; i++) 
	{
	    s = 0;
	    for (int k=0; k<16; k++) s += bcInv[i][k]*bc_x[k];
	    bc_Coeff[i] = s;
	}

	int l = 0;
	for (i=0; i<4; i++)
	    for (j=0; j<4; j++) 
		bc[i][j] = bc_Coeff[l++];
    }

    public double gradient(double lat, double lon, double[] grad)
    {
	grad[0] = 0;
	grad[1] = 0;

	double x = (lon-this.lon)/res;
	double y = (lat-this.lat)/res;
	
	int xi = (int) x;
	int yi = (int) y;

	if (lastXi != xi || lastYi != yi)
	{
	    debug("new Cell for interpolation: %d %d", xi, yi);
	    recalculateCoefficients(xi, yi);
	    lastXi = xi;
	    lastYi = yi;
	}

	double t = x - xi;
	double u = y - yi;

	if  (xi < 0 || xi > N+1 || yi < 0 || yi > N+1)
	    throw new IndexOutOfBoundsException(String.format("(%f, %f)->(%d, %d)", lat, lon, xi, yi));
	
	double val = 0;
	for (int i=3; i>=0; i--) 
	{
	    val = t*val + ((bc[i][3]*u + bc[i][2])*u + bc[i][1])*u + bc[i][0];
	    grad[0] = u*grad[0] + (3*bc[3][i]*t + 2*bc[2][i])*t + bc[1][i];
	    grad[1] = t*grad[1] + (3*bc[i][3]*t + 2*bc[i][2])*t + bc[i][1];
	}

	return val;
    }

    public double elevation(double lat, double lon)
    {
	double x = (lon-this.lon)/res;
	double y = (lat-this.lat)/res;
	
	int xi = (int) x;
	int yi = (int) y;

	if (lastXi != xi || lastYi != yi)
	{
	    debug("new Cell for interpolation: %d %d", xi, yi);
	    recalculateCoefficients(xi, yi);
	    lastXi = xi;
	    lastYi = yi;
	}

	double t = x - xi;
	double u = y - yi;

	if  (xi < 0 || xi > N+1 || yi < 0 || yi > N+1)
	    throw new IndexOutOfBoundsException(String.format("(%f, %f)->(%d, %d)", lat, lon, xi, yi));
	
	double val = 0;
	for (int i=3; i>=0; i--) 
	{
	    val = t*val + ((bc[i][3]*u + bc[i][2])*u + bc[i][1])*u + bc[i][0];
	}

	return val;
    }

    public double elevation(int x, int y)
    {
	if (x < 0 || x > N || y < 0 || y > N)
	    throw new IndexOutOfBoundsException(String.format("elevation: %d %d", x, y));
	return ele(x, y);
    }


    class Isolines
    {
	DEM data;
	int minX;
	int maxX; 
	int minY; 
	int maxY;

	double min;
	double max;

	Vector<Isoline> isolines = new Vector<Isoline>();

	class Isoline
	{
	    int id;
	    Vector<Coord> points;
	    double level;
	    boolean isClosed;
	    
	    private Isoline(double level)
	    {
		this.level = level;
		isClosed = false;
		id = lastId++;
		points = new Vector<Coord>();
	    }

	    private class Edge implements Brent.Function
	    {
		double x0, y0, x1, y1;
		Edge(double x0, double y0, double x1, double y1)
		{
		    this.x0 = x0;
		    this.y0 = y0;
		    this.x1 = x1;
		    this.y1 = y1;
		} 
		
		public double eval(double d)
		{
		    double f = data.elevation(x0 + d * (x1-x0), y0 + d * (y1-y0)) - level; 
		    //System.out.printf("evalEdge: %f %f\n", d, f); 
		    return f;
		}
	    }

	    private class FN implements Brent.Function
	    {
		double x0, y0;
		double dx, dy;

		public void setParameter(double x0, double y0, double dx, double dy)
		{
		    this.x0 = x0;
		    this.y0 = y0;
		    this.dx = dx;
		    this.dy = dy;
		}

		public double eval(double t) 
		{ 
		    double f = data.elevation(y0+t*dy, x0+t*dx) - level; 
		    return f; 
		}
	    }
	    
	    private FN fn = new FN();

	    double grad[] = new double[2];
	    double px[] = new double[4];
	    double py[] = new double[4];
	    int edges[] = new int[4];

	    boolean addCell(Position p, int direction)
	    {
		debug("addCell: %f %d %d %d %d", level, p.ix, p.iy, p.edge, direction);
		
		int c = 0;
		for (int k=0; k<4; k++)
		{
		    if (k == p.edge)
			continue;

		    int x0 = p.ix + off0[k][0];
		    int y0 = p.iy + off0[k][1];
		    int x1 = p.ix + off1[k][0];
		    int y1 = p.iy + off1[k][1];
		    
		    double l0 = elevation(x0, y0) - level;
		    double l1 = elevation(x1, y1) - level;
		    
		    if (Math.abs(l1) < epsilon || l0*l1 < 0)
		    {
			edges[c] = k;

			Brent.Function f = new Edge(data.lat + y0 * DEM.res, data.lon + x0 * DEM.res, data.lat + y1 * DEM.res, data.lon + x1 * DEM.res);
			double f0 = elevation(x0, y0) - level;
			double f1 = elevation(x1, y1) - level;
			double delta;
			
			if (Math.abs(1) < epsilon)
			{
			    delta = 1;
			}
			else if (Math.abs(f0) < epsilon)
			    throw new RuntimeException("implementation error!");
			else
			    delta = Brent.zero(f, epsilon, 1-epsilon);
			
			px[c] = data.lon + (x0+delta*(x1-x0))*DEM.res;
			py[c] = data.lat + (y0+delta*(y1-y0))*DEM.res;
			c++;
		    }
		}


		if (c == 1)
		{
		    p.edge = edges[0];

		    double px0 = p.x;
		    double py0 = p.y;
		    p.x = px[0];
		    p.y = py[0];
		    double px1 = p.x;
		    double py1 = p.y;
		    
		    double xMin = data.lon + p.ix * DEM.res;
		    double xMax = xMin + DEM.res;
		    double yMin = data.lat + p.iy * DEM.res;
		    double yMax = yMin + DEM.res;

		    refineAdaptively(xMin, yMin, xMax, yMax, px0, py0, px1, py1, direction, maxDist);

		    addPoint(p.x, p.y, direction);
		    p.moveCell();
		    return true;
		}
		else
		{
		    debug("addCellByStepping: %d", c);
		    return addCellByStepping(p, direction, c, edges, px, py);
		}		
	    }

	    private void refineAdaptively(double xMin, double yMin, double xMax, double yMax, 
					  double x0, double y0, double x1, double y1, 
					  int direction, double maxDist)
	    {
		double dist = quickDistance(x0, y0, x1, y1);
		if (dist > maxDist)
		{
		    double dx = x1-x0;
		    double dy = y1-y0;
		    double xm, ym, f0, f1, t0, t1, t;
		    Brent.Function f;
		    
		    xm = x0 + 0.5*dx;
		    ym = y0 + 0.5*dy;
		    double n = Math.sqrt(dx*dx+dy*dy);
		    fn.setParameter(xm, ym, -dy/n, dx/n);
		    f = fn;
		    t0 = -0.05*res;
		    t1 = 0.05*res;
		    f0 = f.eval(t0);
		    f1 = f.eval(t1);
		
		    int count = 0;
		    while (f0 * f1 > 0 && count++ < 20) {
			if ((count & 1) > 0)
			    t0 -= 0.05*res;
			else
			    t1 += 0.05*res; 
			f0 = f.eval(t0);
			f1 = f.eval(t1);
			debug("refine: %f %f %f %f", t0, t1, f0, f1);
		    }
			
		    if (f0 * f1 < 0)
		    {
			t = Brent.zero(f, t0, t1);
			xm -= t*dy;
			ym += t*dx;
		    }
		    else
		    {
			debug("refine failed: %f %f %f %f", t0, t1, f0, f1);
			if (false) throw new RuntimeException(String.format("refine failed: %f %f %f %f %f %f %f %f", xMin, yMin, xMax, yMax, x0, y0, x1, y1));
			return;
		    }

		    if (xm > xMin && xm < xMax && ym > yMin && ym < yMax)
			refineAdaptively(xMin, yMin, xMax, yMax, x0, y0, xm, ym, direction, maxDist*1.1);
		    addPoint(xm, ym, direction);
		    if (xm > xMin && xm < xMax && ym > yMin && ym < yMax)
			refineAdaptively(xMin, yMin, xMax, yMax, xm, ym, x1, y1, direction, maxDist*1.1);
		}
	    }

	    boolean addCellByStepping(Position p, int direction, int numEdges, int[] edges, double[] px, double[] py)
	    {
		debug("addCellByStepping: %f %d %d %d %d", level, p.ix, p.iy, p.edge, direction);


		double xMin = data.lon + p.ix * DEM.res;
		double xMax = xMin + DEM.res;
		double yMin = data.lat + p.iy * DEM.res;
		double yMax = yMin + DEM.res;

		double dt, t0, t1;
		double f0, f1;
		boolean edgeHit = false;
		double h[] = new double[4];
		
		int dir;
		double n2 = Math.sqrt(1.0/(grad[0]*grad[0] + grad[1]*grad[1]));
		double dx;
		double dy;

		int count=0;
		int iMin = -1;
		double md = 5000;
		
		for (int i=0; i<numEdges; i++) {
		    gradient(p.y, p.x, grad);
		    double dist = quickDistance(p.x, p.y, px[i], py[i]);
		    debug("distance %d: %f", i, dist);
		    
		    if (dist < md && (visited[p.iy*(N+1)+p.ix] & brd[edges[i]]) == 0) {
			md = dist;
			iMin = i;
		    }
		}

		p.edge = edges[iMin];
		
		double px0 = p.x;
		double py0 = p.y;
		p.x = px[iMin];
		p.y = py[iMin];
		double px1 = p.x;
		double py1 = p.y;
		
		xMin = data.lon + p.ix * DEM.res;
		xMax = xMin + DEM.res;
		yMin = data.lat + p.iy * DEM.res;
		yMax = yMin + DEM.res;
		
		refineAdaptively(xMin, yMin, xMax, yMax, px0, py0, px1, py1, direction, maxDist);
		
		addPoint(p.x, p.y, direction);
		p.moveCell();
		return true;
	    }

	    private void addPoint(double x, double y, int direction)
	    {
		double dist = quickDistance(x, y, lastX, lastY);
		debug("addPoint: %f %f %f", x, y, dist);
		
		if (dist > minDist)
		{
		    if (direction > 0)
			points.add(0, new Coord(y, x));
		    else
			points.add(points.size(), new Coord(y, x));
		    lastX = x;
		    lastY = y;
		}
	    }

	    private void close()
	    {
		points.add(points.size(), points.get(0));
		isClosed = true;
	    }

	    private void addMove(int x0, int y0, int x1, int y1, int direction)
	    {
		double x;
		double y;
		
		if (x0 < minX || x0 >= maxX || y0 < minY || y0 >= maxY)
		    return;
		double l0 = data.elevation(x0, y0);
		double l1 = data.elevation(x1, y1);
		debug("addMove: %d %d %d %d %.2f %.2f %d", x0, y0, x1, y1, l0, l1, direction);
		
		if ((l0 < level && l1 < level) || (l0 > level && l1 > level))
		    throw new RuntimeException("implementation error");
		
		if (l0 == level)
		{
		    x = data.lon + x0 * DEM.res;
		    y = data.lat + y0 * DEM.res;
		}
		else
		{
		    double delta = (l0-level)/(l0-l1);
		    x = data.lon + (x0 + delta * (x1-x0)) * DEM.res;
		    y = data.lat + (y0 + delta * (y1-y0)) * DEM.res;
		}
		double dist = quickDistance(x, y, lastX, lastY);
		debug("levels: %d %d %f, %d %d %f: %f %f %f", x0, y0, l0, x1, y1, l1, x, y, dist);
		
		if (dist > 1)
		{
		    if (direction > 0)
			points.add(0, new Coord(y, x));
		    else
			points.add(points.size(), new Coord(y, x));
		    lastX = x;
		    lastY = y;
		}
	    }
	}
	 
	public Isolines(DEM data, int minX, int maxX, int minY, int maxY)
	{
	    this.data = data;
	    this.minX = minX;
	    this.maxX = maxX;
	    this.minY = minY;
	    this.maxY = maxY;
	    
	    init();
	}

	public Isolines(DEM data, double  minLat, double minLon, double maxLat, double maxLon)
	{
	    System.out.printf("init: %f %f %f %f\n", minLat, minLon, maxLat, maxLon);

	    this.data = data;
	    this.minX = (int) ((minLon-data.lon)/data.res);
	    this.minY = (int) ((minLat-data.lat)/data.res);
	    this.maxX = (int) ((maxLon-data.lon)/data.res);
	    this.maxY = (int) ((maxLat-data.lat)/data.res);
	    
	    init();
	}

	private void init()
	{
	    System.out.printf("init: %d %d %d %d\n", minX, minY, maxX, maxY);
	    data.read(minX-2, minY-2, maxX+2, maxY+2); 
	    // we need some overlap for bicubic interpolation
	    max = -1000;
	    min = 10000;
	    for (int i=minX; i<maxX; i++)
		for (int j=minY; j<maxY; j++)
		{
		    if (data.elevation(i, j) < min) min = data.elevation(i, j);
		    if (data.elevation(i, j) > max) max = data.elevation(i, j);
		}
	
	    debug("min: %f, max: %f\n", min, max);
	}
	
	double getMinHeight()
	{
	    return min;
	}

	double getMaxHeight()
	{
	    return max;
	}

	public void addLevels(int start, int increment)
	{
	    for (int level=start; level<max; level+=increment)
		addLevel(level);
	}

	private class Edge implements Brent.Function
	{
	    double x0, y0, x1, y1, level;
	    Edge(double x0, double y0, double x1, double y1, double level)
	    {
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		this.level = level;
	    } 
	    
	    public double eval(double d)
	    {
		double f = data.elevation(x0 + d * (x1-x0), y0 + d * (y1-y0)) - level; 
		//System.out.printf("evalEdge: %f %f\n", d, f); 
		return f;
	    }
	}

	private class Position
	{
	    int ix, iy;
	    double x, y;
	    int edge;

	    Position(int ix, int iy, double x, double y, int edge)
	    {
		this.ix = ix;
		this.iy = iy;
		this.x = x;
		this.y = y;
		this.edge = edge;
	    } 

	    Position(Position p)
	    {
		this.ix = p.ix;
		this.iy = p.iy;
		this.x = p.x;
		this.y = p.y;
		this.edge = p.edge;
	    } 

	    void markEdge()
	    {
		debug("marking edge: %d %d %d %d", ix, iy, edge, brd[edge]);
		visited[iy*(N+1)+ix] |= brd[edge];
	    }

	    void moveCell()
	    {
		markEdge();
		ix += mov[edge][0];
		iy += mov[edge][1];
		edge = rev[edge];
		markEdge();
	    }
	}

	byte visited[] = new byte[(N+1)*(N+1)];

	public void addLevel(double level)
	{
	    if (level < min || level > max)
		return;
	    
	    System.out.printf("addLevel: %f\n", level);
	    java.util.Arrays.fill(visited, (byte) 0);

	    for (int y=minY; y<maxY; y++)
	    {
		for (int x=minX; x<maxX; x++)
		{
		    byte v = 0;
		    // Mark the borders of the cell, represented by the four points (i, j), (i+1, j), (i, j+1), (i+1, j+1), 
		    // which are intersected by the contour. The values are:
		    // 1: top
		    // 2: left
		    // 4: bottom
		    // 8: right
		    if (data.elevation(x, y) >= level)
		    {
			if (data.elevation(x+1, y) < level) { v |= 1; }
			if (data.elevation(x, y+1) < level) { v |= 2; }
		    }
		    else
		    {
			if (data.elevation(x+1, y) > level) { v |= 1; }
			if (data.elevation(x, y+1) > level) { v |= 2; }
		    }

		    int k=-1;
		   
		    if ((v&1) > 0 && (visited[y*(N+1)+x]&1) == 0)
		    {
			k=0;
		    }
		    else if ((v&2) > 0 && (visited[y*(N+1)+x]&2) == 0)
		    {
			k=1;
		    }

		    if (k>=0)
		    {
			int x0 = x + off0[k][0];
			int y0 = y + off0[k][1];
			int x1 = x + off1[k][0];
			int y1 = y + off1[k][1];

			try {
			    Brent.Function f = new Edge(data.lat + y0 * DEM.res, data.lon + x0 * DEM.res, 
							data.lat + y1 * DEM.res, data.lon + x1 * DEM.res, 
							level);
			    double f0 = elevation(x0, y0) - level;
			    double f1 = elevation(x1, y1) - level;
			    double delta;
			    if (Math.abs(f0) < epsilon)
			    {
				delta = 0;
			    }
			    else if (Math.abs(f1) < epsilon)
				continue;
			    else
				delta = Brent.zero(f, 0, 1-epsilon);
			    
			    Position p = new Position(x, y, data.lon + (x0+delta*(x1-x0))*DEM.res, data.lat + (y0+delta*(y1-y0))*DEM.res, k);
			    p.markEdge();
			    isolines.add(traceByStepping(level, p));
			}
			catch (RuntimeException ex)
			{
			    debug("error: %s", ex.toString());
			    ex.printStackTrace();
			    return;
			}
		    }
		}
	    }
	}

	private Isoline traceByStepping(double level, Position p)
	{
	    debug("traceByStepping: starting contour %f %d %d %f %f %d", level, p.ix, p.iy, p.x, p.y, p.edge);
	    int direction = 1;
	    int n = 0;
	    Position startP = new Position(p);
	    boolean foundEnd = false;

	    Isoline line = new Isoline(level);

	    while (true)
	    {
		debug("traceByStepping: %f %d %d %f %f %d", level, p.ix, p.iy, p.x, p.y, p.edge);
		visited[p.iy*(N+1)+p.ix] |= brd[p.edge];

		if (n>0 && p.ix == startP.ix && p.iy == startP.iy && quickDistance(p.x, p.y, startP.x, startP.y) < 5)
		{
		    debug("closed curve!");
		    line.close();
		    break;
		}
		else if (p.ix < minX || p.iy < minY || p.ix >= maxX || p.iy >= maxY)
		{
		    if (foundEnd) // did we already reach one end?
		    {
			debug("second border reached!");
			break;
		    }
		    else
		    {
			debug("border reached!");
			foundEnd = true;
			n = 0;
			direction *= -1;
			p = new Position(startP);
			p.moveCell();
			continue;
		    }
		}
		n++;
		if (!line.addCell(p, direction) || line.points.size() > maxPoints)
		{
		    debug("ending contour");
		    isolines.add(line);
		    return line; 
		}
	    }

	    return line;
	}

    }

    public static double quickDistance(double long1, double lat1, double long2, double lat2) 
    {
	double latDiff;
	if (lat1 < lat2)
	    latDiff = lat2 - lat1;
	else
	    latDiff = lat1 - lat2;	
	if (latDiff > 90)
	    latDiff -= 180;
	
	double longDiff;
	if (long1 < long2)
	    longDiff = long2 - long1;
	else
	    longDiff = long1 - long2;
	if (longDiff > 180)
	    longDiff -= 360;
	
	// scale longDiff by cosine of average latitude
	longDiff *= Math.cos(Math.PI / 180 * Math.abs((lat1 + lat2) / 2));
	
	double distDegSq = (latDiff * latDiff) + (longDiff * longDiff);

	return 40075000 * Math.sqrt(distDegSq) / 360;
    }

    /**
     * Returns the orthodromic distance between two geographic coordinates in WGS84 datum.
     * The orthodromic distance is the shortest distance between two points
     * on a sphere's surface. The orthodromic path is always on a great circle.
     *
     * @param  x1 Longitude of first  point (in degrees).
     * @param  y1 Latitude  of first  point (in degrees).
     * @param  x2 Longitude of second point (in degrees).
     * @param  y2 Latitude  of second point (in degrees).
     * @return The orthodromic distance (in meters).
     *
     */
    public static double orthodromicDistance(double x1, double y1, double x2, double y2) 
    {
        x1 = Math.toRadians(x1);
        y1 = Math.toRadians(y1);
        x2 = Math.toRadians(x2);
        y2 = Math.toRadians(y2);
        final int MAX_ITERATIONS = 100;
        final double EPS = 5.0E-14;
        final double F = 1 / inverseFlattening;
        final double R = 1 - F;
        double tu1 = R * Math.sin(y1) / Math.cos(y1);
        double tu2 = R * Math.sin(y2) / Math.cos(y2);
        double cu1 = 1 / Math.sqrt(tu1 * tu1 + 1);
        double cu2 = 1 / Math.sqrt(tu2 * tu2 + 1);
        double su1 = cu1 * tu1;
        double s = cu1 * cu2;
        double baz = s * tu2;
        double faz = baz * tu1;
        double x = x2 - x1;
        for (int i = 0; i < MAX_ITERATIONS; i++) 
	{
            final double sx = Math.sin(x);
            final double cx = Math.cos(x);
            tu1 = cu2 * sx;
            tu2 = baz - su1 * cu2 * cx;
            final double sy = Math.sqrt(tu1*tu1 + tu2*tu2);;
            final double cy = s * cx + faz;
            final double y = Math.atan2(sy, cy);
            final double SA = s * sx / sy;
            final double c2a = 1 - SA * SA;
            double cz = faz + faz;
            if (c2a > 0) {
                cz = -cz / c2a + cy;
            }
            double e = cz * cz * 2 - 1;
            double c = ((-3 * c2a + 4) * F + 4) * c2a * F / 16;
            double d = x;
            x = ((e * cy * c + cz) * sy * c + y) * SA;
            x = (1 - c) * x * F + x2 - x1;
            if (Math.abs(d - x) <= EPS) {
                x = Math.sqrt((1 / (R * R) - 1) * c2a + 1) + 1;
                x = (x - 2) / x;
                c = 1 - x;
                c = (x * x / 4 + 1) / c;
                d = (0.375 * x * x - 1) * x;
                x = e * cy;
                s = 1 - 2 * e;
                s = ((((sy * sy * 4 - 3) * s * cz * d / 6 - x) * d / 4 + cz) * sy * d + y) * c * R * semiMajorAxis;
                return s;
            }
        }
        final double LEPS = 1.0E-10;
        if (Math.abs(x1 - x2) <= LEPS && Math.abs(y1 - y2) <= LEPS) {
            return 0;
        }
        if (Math.abs(y1) <= LEPS && Math.abs(y2) <= LEPS) {
            return Math.abs(x1 - x2) * semiMajorAxis;
        }
        throw new ArithmeticException("no convergence");
    }

    private static class DEMMapDataSource extends MapperBasedMapDataSource implements LoadableMapDataSource
    {
	LoadableMapDataSource parent;
	List<String> copyright = new ArrayList<String>();

	DEMMapDataSource(LoadableMapDataSource parent, EnhancedProperties props)
	{
	    this.parent = parent;
	    config(props);
	}

	public boolean isFileSupported(String name)
	{
	    return false;
	}

	public void load(String name)
	    throws FileNotFoundException, FormatException
	{
	    throw new FormatException("load not supported");
	}

	public LevelInfo[] mapLevels()
	{
	    return parent.mapLevels();
	}

	public String[] copyrightMessages()
	{
	    return copyright.toArray(new String[1]);
	}
    }
}