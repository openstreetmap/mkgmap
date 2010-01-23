
#
# This is NOT used to build mkgmap.  To build use ant.
#
#
#

#OSMDATA = areas.osm
#OSMDATA = 63240001.mp
OSMDATA = localtest/osm5/cricklewood-5.osm
#OSMDATA = maps/lon.mp
#OSMDATA = --mapname=90000001 test-map:all-elements
#OSMDATA = /opt/data/uk-071010-1.osm.gz /opt/data/uk-071010-2.osm.gz
# takes 31s on trunk-681
OSMDATA = /opt/data/uk-071010-1.osm.gz
#OSMDATA = test/osm5/srtm.osm
#OSMDATA = --latin1 /opt/data/osmworld/*.gz
#OSMDATA = /opt/data/uk-071114.osm.gz
#OSMDATA = localtest/osm5/reg40.osm
#OSMDATA = localtest/osm5/longline.osm
#OSMDATA = localtest/osm5/reg04.osm
#OSMDATA = --charset=unicode localtest/osm/czech_test.osm
#OSMDATA = --tdbfile maps/img/*.img
#OSMDATA = --tdbfile --gmapsupp /opt/data/uk/63*
#OSMDATA = test/samples/test.osm
#OSMDATA = seqld.osm.gz
#OSMDATA = --net --style=default --name-tag-list='name:en int_name name' test.osm
#OSMDATA = /opt/data/uk-test-1.osm.gz
#OSMDATA = /opt/data/uk-lon.osm
#OSMDATA = other.mp


TIME=/usr/bin/time --format 'Real: %E, %S+%U'

OPTS= --tdbfile
#OPTS= --route --tdbfile --tdb-v4 
OPTS=--tdbfile --family-id=909 --series-name="other map"  --description='A test map'

makemap: clean
	$(TIME) java -Xmx500m -cp build/classes uk.me.parabola.mkgmap.main.Main $(OPTS) $(OSMDATA)
	#cp 63240001.img gmapsupp.img
	#imgdecode gmapsupp.img

t:
	java -Dlog.config=l -ea -cp build/classes uk.me.parabola.mkgmap.main.Main $(OPTS) $(OSMDATA)

base: clean
	$(TIME) java -cp build/classes uk.me.parabola.mkgmap.main.Main /opt/data/uk-test-1.osm.gz
	cp 63240001.img gmapsupp.img
	imgdecode gmapsupp.img

other: clean
	java -Dlog.config=l -ea -cp build/classes uk.me.parabola.mkgmap.main.Main \
		--tdbfile --tdb-v4 --levels=0:24 --route 63240001.mp
	cp 63240001.img gmapsupp.img
	imgdecode gmapsupp.img

.PHONY: clean
clean:
	rm -f 63240001/* gmapsupp/* mkgmap.log out.log
	rm -f gmapsupp.img 632*
	rm -f 11112222.img 11112222.tdb
	rm -f osmmap*

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
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm5/empty.osm
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm/63243936
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm/63247525
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm/63253506
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm5/reg40.osm
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm5/reg71.osm
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm5/cricklewood-5.osm
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm5/reg04.osm
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm5/reg04b.osm
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.Main localtest/osm5/rus.osm
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
