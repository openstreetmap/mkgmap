#
# File: p.py
# 
# Copyright (C) 2006 Steve Ratcliffe
# 
#  This program is free software; you can redistribute it and/or modify
#  it under the terms of the GNU General Public License version 2 as
#  published by the Free Software Foundation.
# 
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
# 
# 
# Author: Steve Ratcliffe
# Create date: 31 Dec 2006
#

#
# Create the new style files points, lines and polygons from a map-features file.
#

import sys
import csv

MAP_FEATURES = 'map-features.csv'

if len(sys.argv) > 1:
	MAP_FEATURES = sys.argv[1]

points = []
lines = []
polygons = []

features = {
		'polyline': lines,
		'polygon': polygons,
		'point': points,
	}
file_names = {
		'polyline': 'lines',
		'polygon': 'polygons',
		'point': 'points',
	}

def main():

	# Read the garmin feature name to internal number file
	f = open(MAP_FEATURES)
	r = csv.reader(f, delimiter='|')
	for line in r:
		kind = line[0]
		if kind[0] == '#': continue
		key = line[1] + '=' + line[2]
		value = makeval(line[3], line[4])
		res = int(line[5])

		line = "%s [%s resolution %d]" % (key, value, res)
		ft = features[kind]
		ft.append(line)


	# Write out the files
	for name in features.keys():
		f = open(file_names[name], 'w')
		ft = features[name]
		for line in ft:
			f.write(line)
			f.write("\n")

def makeval(a, b):
	if not b: return a
	return a + b[2:]

if __name__ == '__main__':
	main()
