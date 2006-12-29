#
# File: namesum.py
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
# Create date: 11 Dec 2006
#

import sys
from imgfile import Imgfile

def main(name):

	img = Imgfile(name)

	bl = img.get_block(0)

	ranges = [
			(0x39, 0x5d),
			(0x65, 0x84)
			]

	sums = []
	sn = 0

	sum = 0
	for r in ranges:
		for i in xrange(r[0], r[1]):
			b = ord(bl[i])
			sum += b
		sums.append( sum)
		sum = 0
		sn += 1

	headb = img.get_block(2)
	for i in xrange(0x20, 0x30, 2):
		bn = headb[i]
		bn = ord(bn)
		if bn <= 2:
			continue
		if bn == 255:
			break
		bl = img.get_block(bn)
		for i in xrange(0x20, 256):
			b = ord(bl[i])
			sum += b
		sums.append(sum)
		sum = 0
		sn += 1

	tot = 0
	for sum in sums:
		print hex(sum),
		tot += sum

	print 'tot', tot, hex(tot)
	print
	return
	print sum1, hex(sum1), sum2, hex(sum2)
	tot = sum1+sum2
	print tot, hex(tot)



if __name__ == '__main__':
	for name in sys.argv[1:]:
		main(name)
