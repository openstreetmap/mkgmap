
#
# This is not used to build mkgmap.
#
# To build it use ant
#
#

#OSMDATA = areas.osm
OSMDATA = /opt/data/planet-070207-gb-london.osm
OSMDATA = maps/lon.mp
#OSMDATA = /opt/data/uk-070530.osm
#OSMDATA = /opt/data/uk-070815.osm
#OSMDATA = /tmp/63253506
#OSMDATA = ~/in/germany-070823.osm
#OSMDATA = vbig.osm
#OSMDATA = clondon.osm
TIME=/usr/bin/time --format 'Real: %E, %S+%U'

# Source code of OSMGarminMap
OSM_GARMIN_MAP = /home/steve/src/osm/applications/utils/export/osmgarminmap

makemap:
	rm -f gmapsupp/* mkgmap.log out.log
	rm -f 63240001.img
	#java -Dlog.filename=mkgmap.log -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeMap $(OSMDATA)
	$(TIME) java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeMap $(OSMDATA)
	cp 63240001.img gmapsupp.img
	imgdecode gmapsupp.img

load:
	sleep 2
	-surun mount /media/disk
	surun cp gmapsupp.img /media/disk/Garmin/gmapsupp.img
	surun umount /media/disk


map_features:
	python scripts/mk_map_table.py resources/garmin_feature_list.csv \
		resources/osm_garmin_map.csv | sort > resources/feature_map.csv

test:
	rm -fr gmapsupp/
	rm -f 32860003.img
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeTestMap

test_shape:
	rm -fr gmapsupp/ 32860003
	rm -f 32860003.img
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeTestPolygonMap $$BASE_LAT $$BASE_LONG
	imgdecode 32860003.img

test_point:
	rm -fr gmapsupp/ 32860003
	rm -f 32860003.img
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeTestPointMap $$BASE_LAT $$BASE_LON
	imgdecode 32860003.img

test_lang:
	rm -fr gmapsupp/ 32860003
	rm -f 32860003.img
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeTestLangMap $$BASE_LAT $$BASE_LON
	imgdecode 32860003.img

test_lang10:
	rm -fr gmapsupp/ 32860003
	rm -f 32860003.img
	java -ea -Dlog.filename=out.log -cp build/classes uk.me.parabola.mkgmap.main.MakeTestLang10Map $$BASE_LAT $$BASE_LON
	imgdecode 32860003.img

tests:
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeMap test/maps/63243936
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeMap test/maps/63247525
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeMap test/maps/63253506
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeMap /opt/data/germany-070823.osm
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeMap /opt/data/uk-070815.osm


tl:
	-mount /media/disk
	cp 32860003.img /media/disk/Garmin/gmapsupp.img
	umount /media/disk

munge:
	cp pp.bak pp
	python munger.py
	cp pp gmapsupp.img

changelog:
	svn update
	svn2cl --group-by-day --reparagraph
