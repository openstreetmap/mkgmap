#
# File: imgfile.py
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
import time
from struct import *

class Imgfile:
	def __init__(self, name):
		self.name = name
		self.block_size = 512
		self.fp = open(name, 'r+')

	def close(self):
		self.fp.close()

	def get_block(self, bn):
		pos = bn * self.block_size
		self.fp.seek(pos)
		return self.fp.read(self.block_size)

	def write_block(self, bn, bl):
		pos = bn * self.block_size
		self.fp.seek(pos)
		return self.fp.write(bl)

	def get_dirent(self, type):
		for bn in xrange(2, 12):
			bl = self.get_block(bn)
			print bl[9:12], type
			if bl[9:12] == type:
				dent = Dirent(self, bl)
				dent.dir_block_num = bn
				return dent

	def set_time(self):
		self.fp.seek(0)
		bl = self.fp.read(self.block_size)
		t = time.localtime()
		s = pack("<HBBBBB", t[0], t[1], t[2], t[3], t[4], t[5])
		self.fp.seek(0x39)
		self.fp.write(s)

class Dirent:
	def __init__(self, img, block):
		self.img = img
		self.block = block

	def get_block(self, lbn):
		pbn = self.get_phys_block(lbn)
		return self.img.get_block(pbn)

	def write_block(self, lbn, bl):
		pbn = self.get_phys_block(lbn)
		self.img.write_block(pbn, bl)

	def get_phys_block(self, lbn):
		"""Get the physical block number from the logical."""
		loc = 0x20 + 2*lbn
		pbn = unpack("<H", self.block[loc:loc+2])
		return pbn[0]
