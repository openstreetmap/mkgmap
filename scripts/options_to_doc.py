#
# File: options_conv.py
# 
# Copyright (C) 2013 Steve Ratcliffe
# 
#  This program is free software; you can redistribute it and/or modify
#  it under the terms of the GNU General Public License version 2 or
#  version 3 as published by the Free Software Foundation.
# 
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
# 
# 
# Author: Steve Ratcliffe
# Create date: 28 Jun 2013
#

import sys
import re

f = open('options')

NONE = 0
OPT = 1
DESC = 2

WS=(' ', '\t')

def count_indent(line):
	count = 0
	for c in line:
		if c == ' ':
			count += 1
		elif c == '\t':
			count += 4
		else:
			break
	return count

state = NONE
in_preserve = False
desc_indent = 0
last_indent = 0

def start_preserve():
	global in_preserve
	if in_preserve: return
	in_preserve = True
	print '<div class=preserve><nowiki>'

def end_preserve():
	global in_preserve
	if not in_preserve: return
	in_preserve = False
	print '</nowiki></div>'

print '= List of options ='

for line in f.xreadlines():
	line = line[:-1]

	if len(line) == 0 or len(line.strip()) == 0:
		if state == DESC:
			if in_preserve:
				print
			else:
				print '<p>'
			continue
		print
		continue

	if line[0] == '-':
		end_preserve()
		line = ';' + line
		state = OPT
	
	if state == OPT and line[0] in WS:
		state = DESC
		desc_indent = count_indent(line)
		last_indent = desc_indent
		print ':', line
		continue
	elif state == DESC and line[0] not in WS:
		if last_indent > desc_indent:
			end_preserve()
			print
		last_indent = 0
		state = NONE

	if state == DESC:
		new_indent = count_indent(line)
		line = re.sub(r'^(\t| {1,4})', '', line)
		if new_indent > last_indent:
			start_preserve()
		elif new_indent < last_indent and new_indent == desc_indent:
			end_preserve()
		last_indent = new_indent

	print line
