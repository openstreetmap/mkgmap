
#OSMDATA = area.osm
#OSMDATA = newbig.osm
OSMDATA = vbig.osm
#OSMDATA = clondon.osm

# Source code of OSMGarminMap
OSM_GARMIN_MAP = /home/steve/src/osm/utils/osmgarminmap

makemap:
	rm -f gmapsupp/* mkgmap.log out.log
	rm -f 63240001.img
	java -Dlog.filename=mkgmap.log -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeMap $(OSMDATA)
	cp 63240001.img gmapsupp.img
	imgdecode gmapsupp.img

load:
	sleep 2
	-surun mount /media/gps
	surun cp gmapsupp.img /media/gps/Garmin/gmapsupp.img
	surun umount /media/gps


map_features:
	python scripts/mk_map_table.py $(OSM_GARMIN_MAP)/feature-list.csv \
		$(OSM_GARMIN_MAP)/osm2mpx.xml > resources/feature_map.csv
	ant

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

tl:
	-mount /media/gps
	cp 32860003.img /media/gps/Garmin/gmapsupp.img
	umount /media/gps

munge:
	cp pp.bak pp
	python munger.py
	cp pp gmapsupp.img

changelog:
	svn update
	svn2cl --group-by-day --reparagraph
