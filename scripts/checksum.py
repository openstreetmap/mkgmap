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
# Create date: 10 Dec 2006
#

#
# Sets the check sum for the file.  It is possible that the check sum
# is not actually checked by anything.
#

import sys

file = sys.argv[1]
print "file is", file

f = open(file)

cksum = 0
while 1:
	block = f.read(512)
	if not block: break
	for c in block:
		cksum -= ord(c)

cksum = (cksum & 0xff)
print "checksum is", cksum, hex(cksum)

if cksum != 0:
	fw = open(file, "r+b")
	fw.seek(15)
	print "setting checksum, please re-check afterwards"
	ch = chr(cksum)
	print 'chr', ord(ch)
	fw.write(ch)
	fw.close()

