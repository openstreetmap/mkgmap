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
# Create date: 18 Dec 2006
#

import sys
from imgfile import Imgfile
from struct import *

def main():
	myimg = Imgfile('gmapsupp.bad')
	workimg = Imgfile('pp')

	for dtype in ('LBL','RGN'):
		ld1 = myimg.get_dirent(dtype)
		ld2 = workimg.get_dirent(dtype)

		# Get first block of the sub file itself
		bl = ld1.get_block(0)
		ld2.write_block(0, bl)

	# So TRE is what kills it, investigate further.

	# copy our first block
	bl = myimg.get_block(0)
	workimg.write_block(0, bl)

	dtype = 'TRE'
	ld1 = myimg.get_dirent(dtype)
	ld2 = workimg.get_dirent(dtype)

	ld2.get_block(0)
	new = ld1.get_block(0)

	# Works
	#off = 0x10
	#new = wrk[0:off] + new[off:]

	off = 0x10
	ch1 = pack("<H10s2BBB", 0x78, "GARMIN TRE", 1, 0, 7, 0x07)
	new = ch1 + new[off:]
	ld2.write_block(0, new)

	workimg.set_time()
	workimg.close()
	#return

	# Copy over the directory entries apart from the block numbers
	for dtype in ('LBL', 'TRE', 'RGN'):
		ld1 = myimg.get_dirent(dtype)
		ld2 = workimg.get_dirent(dtype)

		# existing block
		bl = workimg.get_block(ld2.dir_block_num)

		# transfer first 32 bytes from our file
		bl2 = myimg.get_block(ld1.dir_block_num)
		#bl[0:0x20] = bl2[0:0x20]
		bl = bl2[0:0x20] + bl[0x20:]

		# write it back
		workimg.write_block(ld2.dir_block_num, bl)


if __name__ == '__main__':
	main()
