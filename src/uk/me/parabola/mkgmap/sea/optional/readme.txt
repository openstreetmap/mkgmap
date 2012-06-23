The PrecompSeaGenerator reads a shapefile containing land polygons and 
creates precompiled sea tiles that can be used by mkgmap to create the
land and sea areas of a map. Land polygons can be downloaded from other
sites e.g. http://openstreetmapdata.com/data/land-polygons.

The PrecompSeaGenerator can be started with:
java -cp <mkgmap classes>;<libs> uk.me.parabola.mkgmap.sea.optional.PrecompSeaGenerator <shapefile> <projection> <outputdir>

Parameters:
shapefile: The .shp file of the ESRI shape files containing land polygons. Sea 
           polygon files are not supported.
projection: The projection used in the shapefile (e.g. WGS84 or EPSG:3857)
outputdir: The directory where the precompiled sea tiles are stored.       



The PrecompSeaGenerator is not included in the mkgmap.jar due to additional
dependencies. The following libs must be added in order to run the 
PrecompSeaGenerator tool:

Geotools library (http://sourceforge.net/projects/geotools/files/):
commons-pool-1.5.4.jar
gt-api-2.7.5.jar
gt-cql-2.7.5.jar
gt-data-2.7.5.jar
gt-main-2.7.5.jar
gt-metadata-2.7.5.jar
gt-opengis-2.7.5.jar
gt-referencing-2.7.5.jar
gt-shapefile-2.7.5.jar
jsr-275-1.0-beta-2.jar
jts-1.11.jar
vecmath-1.3.2.jar

Splitter (http://www.mkgmap.org.uk/page/tile-splitter):
splitter.jar


