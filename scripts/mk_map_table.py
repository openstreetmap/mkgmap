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
# The OSMGarminMap project contains two files that map
# OSM map-features to the codes used in Garmin maps.  This
# script does a join between them to produce one file.
#

import sys
import libxml2
import csv

# The list of garmin features.
FEATURE_LIST_FILE = 'garmin_feature_list.csv'

# The mapping between OSM and garmin features
FEATURE_MAP_FILE = 'osm_garmin_map.csv'

if len(sys.argv) > 2:
	FEATURE_LIST_FILE = sys.argv[1]
	FEATURE_MAP_FILE = sys.argv[2]

features = {}

def main():

	# Read the garmin feature name to internal number file
	f = open(FEATURE_LIST_FILE)
	r = csv.reader(f, delimiter='|')
	for line in r:
		kind = line[0]
		if kind[0] == '#': continue
		key = (line[1], line[2])
		val = (line[4], line[5])
		try:
			ft = features[kind]
		except KeyError:
			ft = {}
			features[kind] = ft
		ft[key] = val

	# Get the osm to garmin map rules
	f = open(FEATURE_MAP_FILE)
	r = csv.reader(f, delimiter='|')
	w = csv.writer(sys.stdout, delimiter='|', lineterminator='\n')

	for line in r:
		kind = line[0]
		if kind[0] == '#': continue
		key = (line[3], line[4])
		try:
			val = features[kind][key]
			l = []
			l.append(kind)
			l.append(line[1])
			l.append(line[2])
			l.append(val[0])
			if val[1]:
				l.append(val[1])
			else:
				l.append('')
			w.writerow(l)
		except KeyError:
			print >>sys.stderr, "No garmin entry at", kind, key


if __name__ == '__main__':
	main()
