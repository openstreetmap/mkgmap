
#OSMDATA=area.osm
OSMDATA=big.osm

all:
	rm -f gmapsupp/* mkgmap.log out.log
	rm -f 63240001.img
	java -Dlog.filename=mkgmap.log -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeMap $(OSMDATA)
	cp 63240001.img gmapsupp.img
	imgdecode gmapsupp.img

test:
	rm -fr gmapsupp/
	rm -f 32860003.img
	java -ea -cp build/classes uk.me.parabola.mkgmap.main.MakeTestMap
	# 51.539 -0.2313

load:
	sleep 2
	-surun mount /media/gps
	surun cp gmapsupp.img /media/gps/Garmin/gmapsupp.img
	surun umount /media/gps

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
