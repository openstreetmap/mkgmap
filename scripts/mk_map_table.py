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

import sys
import libxml2
import csv

features = {}

def main():

	# Read the garmin feature name to internal number file
	f = open('feature-list.csv')
	r = csv.reader(f, delimiter='|')
	for line in r:
		kind = line[0]
		key = (line[1], line[2])
		val = (line[4], line[5])
		features[key] = val

	# Get the osm to garmin map rules
	doc = libxml2.parseFile("osm2mpx.xml")
	root = doc.getRootElement()
	el = root.children
	while el:
		if el.name == 'rule':
			rule(el)

		el = el.next

def rule(el):
	etype = el.prop('e')
	key = el.prop('k')
	val = el.prop('v')
	vals = val.split('|')

	el2 = el.children
	while el2:
		if el2.type == 'element':
			type = el2.prop('type')
			subtype = el2.prop('subtype')
			kind = el2.name
			break

		el2 = el2.next

	if not subtype:
		subtype = ''

	for val in vals:
		feature = features[(type, subtype)]
		print "%s|%s|%s|%s|%s" % (
				kind, key, val, feature[0], feature[1])

if __name__ == '__main__':
	main()
