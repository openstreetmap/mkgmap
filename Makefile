
#
# This is not used to build mkgmap.
#
# To build it use ant
#
#

#OSMDATA = areas.osm
OSMDATA = /opt/data/planet-070207-gb-london.osm
#OSMDATA = 63240001.mp
OSMDATA = localtest/osm5/cricklewood-5.osm
#OSMDATA = london.osm
#OSMDATA = maps/lon.mp
#OSMDATA = --mapname=90000001 test-map:all-elements
#OSMDATA = /opt/data/uk-070530.osm.gz
#OSMDATA = /opt/data/uk-070815.osm.gz
OSMDATA = /opt/data/uk-070919-1.osm.gz
OSMDATA = /opt/data/uk-071010-1.osm.gz /opt/data/uk-071010-2.osm.gz
#OSMDATA = ~/in/germany-070823.osm
#OSMDATA = vbig.osm
#OSMDATA = clondon.osm
#OSMDATA = /opt/data/multi/6324*
#OSMDATA = test/osm5/srtm.osm
#OSMDATA = --levels=0:24,1:23,2:22,3:21,4:20,5:19,6:18,7:17 /opt/data/planet-070207-gb-london.osm
#OSMDATA = --gmapsupp --latin1 /opt/data/osmworld/*.gz
#OSMDATA = /opt/data/osmworld/63260003
#OSMDATA = --gmapsupp ~/tmp/batch/*.osm
#OSMDATA = /opt/data/uk-071114.osm.gz
#OSMDATA = --gmapsupp /opt/data/uk/63*
#OSMDATA = reg40.osm
#OSMDATA = reg71.osm
OSMDATA = longline.osm


TIME=/usr/bin/time --format 'Real: %E, %S+%U'

# Source code of OSMGarminMap
OSM_GARMIN_MAP = /home/steve/src/osm/applications/utils/export/osmgarminmap

#OPTS= --levels='0=24,1=22,2=20'
OPTS= --gmapsupp

makemap:
	rm -f gmapsupp/* mkgmap.log out.log
	rm -f gmapsupp.img 6324*.img 6324*.tdb
	$(TIME) java -cp build/classes uk.me.parabola.mkgmap.main.Main $(OPTS) $(OSMDATA)
	#java -Dlog.config=l -ea -cp build/classes uk.me.parabola.mkgmap.main.Main $(OPTS) $(OSMDATA)
	#cp 63240001.img gmapsupp.img
	#imgdecode gmapsupp.img

load:
	sleep 2
	-surun mount /media/disk
	surun cp gmapsupp.img /media/disk/Garmin/gmapsupp.img
	surun umount /media/disk


map-features map_features:
	python scripts/mk_map_table.py resources/garmin_feature_list.csv \
		resources/osm_garmin_map.csv | sort > resources/map-features.csv

test:
	rm -fr gmapsupp/
	rm -f 32860003.img
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeTestMap

# Set values of BASE_LAT and BASE_LONG in your environment to be near your
# present location.
test_element:
	rm -fr gmapsupp/ 32860003
	rm -f 32860003.img
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeTestElementMap $$BASE_LAT $$BASE_LONG
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
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm/63243936
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm/63247525
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm/63253506
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm5/reg40.osm
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm5/reg71.osm
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm5/cricklewood-5.osm
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main /opt/data/germany-070823.osm.gz
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main /opt/data/uk-070815.osm.gz
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm5/bit-assert-fail.osm


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
