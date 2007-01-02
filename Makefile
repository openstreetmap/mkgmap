
#OSMDATA = area.osm
OSMDATA = big.osm

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
	# 51.539 -0.2313

test_shape:
	rm -fr gmapsupp/ 32860003
	rm -f 32860003.img
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeTestPolygonMap 51.539 -0.2313
	imgdecode 32860003.img

test_point:
	rm -fr gmapsupp/ 32860003
	rm -f 32860003.img
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeTestPointMap 51.539 -0.2313
	imgdecode 32860003.img

tl:
	mount /media/gps
	cp 32860003.img /media/gps/Garmin/gmapsupp.img
	umount /media/gps

munge:
	cp pp.bak pp
	python munger.py
	cp pp gmapsupp.img

changelog:
	svn update
	svn2cl --group-by-day --reparagraph
