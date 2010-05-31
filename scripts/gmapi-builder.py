#!/usr/bin/python

"""
    Simple convertor  for files in Garmins MapSource format to the directory
    structure RoadTrip for OS X uses.

    Copyright (c) 2009, Berteun Damman
    All rights reserved.

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions are met:
        * Redistributions of source code must retain the above copyright
          notice, this list of conditions and the following disclaimer.
        * Redistributions in binary form must reproduce the above copyright
          notice, this list of conditions and the following disclaimer in the
          documentation and/or other materials provided with the distribution.
        * Neither the name of the OpenStreetMap Project nor the
          names of its contributors may be used to endorse or promote products
          derived from this software without specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY
    EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
    WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
    DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
    DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
    (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
    LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
    ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
    (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
    SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

    First of all: This program is not a replacement for Garmins MapConverter.exe.
    It was specifically written to convert a bunch of .IMG and .TDB files of
    the OpenStreetmap project to a .gmapi directory structure which can be
    installed under OS X. As such it is geared towards the versions of the format
    used by OSM. This means it will most certain not work on non-OSM files (this
    has not been tested in anyway!).

    The conversion itself is fairly simple, under Windows you have:
    * Registry entries indicating the TDB-file, the Base Image and the Image dir,
      and optionally a TYP file and some other types.
    * A single TDB-file
    * A whole collection of IMG files.

    Under Mac you have:
    * An XML file which contains the information of the Windows Register
    * For each IMG file a directory with the same basename as the IMG-file,
      and with the subfiles of this IMG extracted into this directory; the IMG-files
      are in fact containers (they are similar to disk images).

    This program does the conversion.
"""

import logging
import optparse
import os
import shutil
import struct
import sys
import StringIO

# Logger setup
logging.NORMAL = logging.INFO + 5
logging.addLevelName(logging.NORMAL, 'NORMAL')
logger = logging.getLogger('logger')
logger.setLevel(logging.DEBUG)

# Program options can increase the verbosity
cons = logging.StreamHandler(sys.stdout)
cons.setLevel(logging.NORMAL)
logger.addHandler(cons)

def error_exit(msg):
    sys.exit(msg)

def write(msg, *args, **kwargs):
    logger.log(logging.NORMAL, msg, *args, **kwargs)

class EndOfFile(IOError):
    pass

class FileFormatError(Exception):
    pass

# Auxiliary functions that make life easier to read bytes, shorts, int and
# so on from a file.
def getX(length, fmt):
    def get(f):
        v = f.read(length)
        if len(v) < length:
            raise EndOfFile("End of file reached on '%s'." % f.name)
        return struct.unpack(fmt, v)[0]
    return get

# See the documentation of struct for an explanation of these
# format specifiers. Notably, the < indicates little endian
# format.
get_short  = getX(2, '<h')
get_ushort = getX(2, '<H')
get_int    = getX(4, '<i')
get_uint   = getX(4, '<I')
get_byte   = getX(1, 'B')

def get_str(f):
    c = f.read(1)
    s = ''
    while c != '\x00':
        s += c
        c = f.read(1)
    return s

def get_nstr(f, n):
    c = f.read(n)
    if len(c) < n:
        raise EndOfFile("End of file reached on '%s'." % f.name)
    s = struct.unpack('%ds' % n, c)[0]
    return s

# For the 4.X TDB file format, 24bit integers are used; already here
# for the future.
#def get_middle(f):
#    l = list(f.read(3))
#    if len(l) < 3:
#        raise EndOfFile("End of file reached on '%s'." % f.name)
#    sign, l[2] = l[2] >> 7, l[2] & 0x7F
#    v = -sign * (1<<23) + (l[2] << 16) + (l[1] << 8) + l[0]

def todegrees(n):
    return (n * 360.0) / (2 ** 32)

# A TDB file consists of a sequence of blocks, the format of each
# block is simply:
# 1 Byte: Block ID
# 2 Bytes: Block length (l):
# l Bytes: Data

class Block(object):
    def __init__(self, f):
        self.bid = get_byte(f)
        self.length = get_ushort(f)
        self.data = StringIO.StringIO(f.read(self.length))
        if len(self.data.buf) < self.length:
            raise EndOfFile("End of file reached early on '%s'." % f.name)

class TDBFile(object):
    """This represents the TDBFile with its known blocks, such as
       the header, copyright block, overview block and detailed maps."""
    def __init__(self, filename):
        self.f = open(filename, 'rb')
        self.header_block = None
        self.copyright_block = None
        self.overview_block = None
        self.trademark_block = None
        self.detail_blocks = []
        self._parse()

    def _parse(self):
        """Dispatches subparsers for *known* blocks"""
        block_parsers = {
            0x50: self.parse_header,
            0x44: self.parse_copyright,
            0x42: self.parse_overview,
            0x4C: self.parse_detail,
            0x52: self.parse_trademark,
        }

        while self.f.read(1):
            self.f.seek(-1, 1)
            b = Block(self.f)
            if b.bid in block_parsers:
                block_parsers[b.bid](b)
            else:
                logger.info('Unknown Block: %02X, length: %d, %s' % (b.bid, b.length, repr(b.data.buf)))
        self.f.close()

    def parse_header(self, block):
        hb = {}
        hb['Product ID'] = get_ushort(block.data)
        hb['Family ID']  = get_ushort(block.data)

        tdb_version = get_ushort(block.data)
        hb['TDB Major Version'] = tdb_version / 100
        hb['TDB Minor Version'] = tdb_version % 100
        hb['TDB Version'] = "%d.%02d" % (hb['TDB Major Version'], hb['TDB Minor Version'])

        hb['Map Series'] = get_str(block.data)

        prod_version = get_ushort(block.data)
        hb['Product Major Version'] = prod_version / 100
        hb['Product Minor Version'] = prod_version % 100
        hb['Product Version'] = "%d.%02d" % (hb['Product Major Version'], hb['Product Minor Version'])

        hb['Map Family'] = get_str(block.data)

        self.header_block = hb

    def parse_copyright(self, block):
        cl = []
        while block.data.pos < len(block.data.buf):
            copyright_code = get_byte(block.data)
            where_code = get_byte(block.data)
            extra = get_short(block.data)
            copyright_string = get_str(block.data)
            if copyright_code == 0x00:
                cl.append({'Source': copyright_string.decode('latin1').encode('utf-8')})
            elif copyright_code == 0x06:
                cl.append({'Copyright': copyright_string.decode('latin1').encode('utf-8')})
            elif copyright_code == 0x07:
                cl.append({'Bitmap': copyright_string, 'Scale factor': extra})
            else:
                cl.append({'Unknown (%02X)' % copyright_code: copyright_string, 'Extra': extra})
        self.copyright_block = cl

    def parse_overview(self, block):
        ob = {}
        ob['Map Number'] = get_uint(block.data)
        ob['Parent Map'] = get_uint(block.data)
        ob['Latitude North'] = "%7.4f" % todegrees(get_int(block.data))
        ob['Longitude East'] = "%7.4f" % todegrees(get_int(block.data))
        ob['Latitude South'] = "%7.4f" % todegrees(get_int(block.data))
        ob['Longitude West'] = "%7.4f" % todegrees(get_int(block.data))
        ob['Description'] = get_str(block.data)
        self.overview_block = ob

    def parse_detail(self, block):
        db = {}
        db['Map Number'] = get_uint(block.data)
        db['Parent Map'] = get_uint(block.data)
        db['Latitude North'] = "%7.4f" % todegrees(get_int(block.data))
        db['Longitude East'] = "%7.4f" % todegrees(get_int(block.data))
        db['Latitude South'] = "%7.4f" % todegrees(get_int(block.data))
        db['Longitude West'] = "%7.4f" % todegrees(get_int(block.data))
        db['Description'] = get_str(block.data)
        # Unknown
        block.data.seek(4, 1)
        # Could it be that these values have changed in v4?
        db['RGN Size'] = get_uint(block.data)
        db['TRE Size'] = get_uint(block.data)
        db['LBL Size'] = get_uint(block.data)
        self.detail_blocks.append(db)

    def parse_trademark(self, block):
        tb = {}
        block.data.seek(1, 1)
        tb['Trademark'] = get_str(block.data)
        self.trademark_block = tb

    def print_header(self):
        for f in ['TDB Version', 'Product ID', 'Family ID', 'Map Series', 'Map Family', 'Product Version']:
            write("%-20s%s" % (f + ':', str(self.header_block[f])))

    def print_copyright(self):
        for c in self.copyright_block:
            write('\n        '.join(["%-20s%s" % (f + ":", str(c[f])) for f in c]))

    def print_trademark(self):
        write("%-20s%s" % ('Trademark:', self.trademark_block['Trademark'].encode('utf-8')))

    def print_overview(self):
        logger.info('Overview map:')
        for f in ['Map Number', 'Parent Map', 'Latitude North', 'Longitude East',
            'Latitude South', 'Longitude West', 'Description']:
            logger.info("    %-20s%s" % (f + ':', str(self.overview_block[f])))

    def print_detail_blocks(self):
        logger.debug('Detailed maps:')
        for db in self.detail_blocks:
            logger.debug("   %-20s%s" % ('Map Number:', str(db['Map Number'])))
            logger.debug("   %-20s%s" % ('Parent Map:', str(db['Parent Map'])))
            logger.debug("   %-20s%s" % ('Description:', str(db['Description'])))
            logger.debug("   N: %s, S: %s, W: %s, E: %s" % (db['Latitude North'], db['Latitude South'],
                 db['Longitude West'], db['Longitude East']))
            logger.debug("   RGN: %d, TRE: %d, LBL: %d" % (db['RGN Size'], db['TRE Size'], db['LBL Size']))
            logger.debug("")


    def print_dump(self):
        """Gives a short textual description of the data."""
        if self.header_block:
            self.print_header()
        else:
            logger.warning('TDB file contains no header block.')
        write("")
        if self.copyright_block:
            self.print_copyright()
        else:
            logger.info('TDB file contains no copyright block.')
        write("")
        if self.trademark_block:
            self.print_trademark()
            write("")
        else:
            logger.info('TDB file contains no trademark block.')
            logger.info("")
        if self.overview_block:
            self.print_overview()
        else:
            logger.warning('TDB file contains no overview map.')
        logger.info("")
        if self.detail_blocks:
            self.print_detail_blocks()
        else:
            logger.warning('TDB file contains no detail blocks.')

class SubFile(object):
    """Represents a subfile in the IMG file

    The image is like a FAT file system; for each file it lists
    the sectors on which a part of the file can be found. If the
    file is larger than 240 sectors, it split into parts.
    """
    def __init__(self, name, extension):
        self.name = name
        self.extension = extension
        self.fullname = '%s.%s' % (name, extension)
        self.size = None
        self.part_list = []

    def add_part(self, part, sectors):
        self.part_list.append((part, sectors))

    def merge_parts(self):
        """Merges all the sectors in the parts into 
            a large (sorted) sector list which can be used to dump the file.
        """
        self.part_list.sort()
        for n in range(len(self.part_list)):
            if self.part_list[n][0] != n:
                raise FileFormatError('Missing part: %d of %s in IMG-file.' % (n, self.fullname))
        self.sector_list = []
        for (n, sl) in self.part_list:
            self.sector_list.extend(sl)

class IMGFile(object):
    """Very crude representation of the IMG file

    We only want to extract the subfiles, so we ignore lots of infomation in this file."""
    def __init__(self, filename):
        self.f = open(filename)
        self.basename = os.path.basename(filename[:filename.rfind('.')])
        self._check_file()
        self.block_size = self._get_block_size()
        self._read_files()

    def _check_file(self):
        first_b = get_byte(self.f)
        if first_b != 0x00:
            raise FileFormatError("%s is not a Garmin IMG file, or it is encrypted." % self.f.name)
        self.f.seek(0x10)
        if get_str(self.f) != "DSKIMG":
            raise FileFormatError("%s is not a Garmin IMG file, or is an unknown version." % self.f.name)
        self.f.seek(0x41)
        if get_str(self.f) != "GARMIN":
            raise FileFormatError("%s is not a Garmin IMG file, or is an unknown version." % self.f.name)

    def _get_block_size(self):
        # Probably 512
        self.f.seek(0x61)
        e1 = get_byte(self.f)
        e2 = get_byte(self.f)
        bs = 1<<(e1 + e2)
        return bs

    def _read_files(self):
        """Scans the FAT for files"""
        files = {}
        file_count = 0
        while True:
            # FAT starts at 0x600, each entry is
            # exactly 512 bytes, padded if necessary.
            self.f.seek(0x600 + file_count * 512)
            if get_byte(self.f) == 0:
                break

            filename = get_nstr(self.f, 8)
            file_type = get_nstr(self.f, 3)
            size = get_uint(self.f)

            self.f.seek(1, 1)
            part_no = get_byte(self.f)
            fullname ='%s.%s' % (filename, file_type)
            self.f.seek(14, 1)
            sector_list = []
            for n in range(240):
                sector_no = get_ushort(self.f)
                if sector_no != -1:
                    sector_list.append(sector_no)

            if not fullname in files:
                files[fullname] = SubFile(filename, file_type)
            if part_no == 0:
                files[fullname].size  = size

            files[fullname].add_part(part_no, sector_list)
            file_count += 1

        for fn in files:
            files[fn].merge_parts()
        self.files = files

    def dump(self, base_dir):
        """Dumps the subfiles of this IMG file"""
        output_dir = os.path.join(base_dir, self.basename)
        os.mkdir(output_dir)
        for sfn in self.files:
            subfile = self.files[sfn]
            out = open(os.path.join(output_dir, subfile.fullname), 'w')
            for s in subfile.sector_list:
                self.f.seek(s * self.block_size)
                out.write(self.f.read(self.block_size))
            out.truncate(subfile.size)
            logger.debug('Wrote: %s/%s' % (output_dir, subfile.fullname))
            out.close()

    def close(self):
        self.f.close()

    def print_info(self):
        logger.debug("Contents of %s:" % (self.basename))
        for fn in self.files:
            logger.debug("    %s: Size: %d" % (self.files[fn].fullname, self.files[fn].size))

def parse_options(option_list):
    usage = 'usage: %prog: [-h] [-o <outputdir>] [-s <typfile>] -t <tdbfile> -b <base-image> <file1.img> [<file2.img> [<file3.img> ... ]]'
    oparser = optparse.OptionParser(usage=usage)
    oparser.add_option("-o", "--output", dest="output", default="./",
            help="specify the output directory for the .gmapi folder")
    oparser.add_option("-t", "--tdbfile", dest="tdbfile",
            help="the name of this mapset's TDB file")
    oparser.add_option("-b", "--baseimg", dest="baseimg",
            help="the name of the base img")
    oparser.add_option("-i", "--idxfile", dest="mdx",
            help="the name of this mapset's index (mdx) file")
    oparser.add_option("-m", "--mdrfile", dest="mdr",
            help="the name of this mapset's MDR file")
    oparser.add_option("-v", "--verbose", dest="verbosity",
            action="count", help="verbosity")
    oparser.add_option("-s", "--style", dest="style",
            help="an optional style-file (TYP)")
    oparser.add_option("-d", "--dryrun", dest="dryrun",
            action="store_true", default=False)

    (options, args) = oparser.parse_args(option_list)

    if options.verbosity:
        if options.verbosity == 1:
            cons.setLevel(logging.INFO)
        elif options.verbosity > 1:
            cons.setLevel(logging.DEBUG)

    if not options.tdbfile:
        oparser.print_help()
        error_exit("\nError: You must specify a TDB-file with -t!")

    if not options.baseimg:
        oparser.print_help()
        error_exit("\nError: You must specify the base image with -b!")
    elif not os.path.isfile(options.baseimg):
            error_exit("\nError: Baseimage not found.")

    if options.style:
        if not os.path.isfile(options.style):
            error_exit("\nError: Style file not found.")

    if options.mdx:
        if not os.path.isfile(options.mdx):
            error_exit("\nError: Index (mdx) file not found.")

    if options.mdr:
        if not os.path.isfile(options.mdr):
            error_exit("\nError: MDR file not found.")
            
    if not args:
        oparser.print_help()
        error_exit('No filenames specified!')

    return (options, args)

def prepare_output_dir(tdbfile, options):
    dir_prefix = tdbfile.header_block['Map Series']
    if not os.path.isdir(options.output):
        error_exit('Output dir does not exist')
    gmapi = os.path.join(options.output, dir_prefix + '.gmapi')
    gmap = os.path.join(gmapi, dir_prefix + '.gmap')
    if os.path.exists(gmapi):
        logger.info("Removing existing file '%s' recursively" % gmapi)
        if os.path.isdir(gmapi):
            shutil.rmtree(gmapi)
        else:
            os.unlink(gmapi)
    if os.path.exists(gmapi):
        error_exit("Could not remove existing '%s', please do so yourself, or specify another output dir.")
    # This directory is empty indeed
    os.mkdir(gmapi)
    os.mkdir(gmap)
    return gmap

def write_xml_file(tdbfile, options, output_dir):
    def write_field(field, value, indent):
        f.write('%s<%s>%s</%s>\n' % (indent * '    ', field, value, field))
    f = open(os.path.join(output_dir, 'Info.xml'), 'w')
    f.write('<?xml version="1.0" encoding="UTF-8" standalone="no" ?>\n')
    f.write('<MapProduct xmlns="http://www.garmin.com/xmlschemas/MapProduct/v1">\n\n')
    write_field('Name', tdbfile.header_block['Map Family'], 1)
    f.write('\n')
    write_field('DataVersion', str(tdbfile.header_block['Product Major Version']) + ("%02d" % tdbfile.header_block['Product Minor Version']), 1)
    f.write('\n')
    write_field('DataFormat', 'Original', 1)
    f.write('\n')
    # It appears the windows converter does not write the family ID
    # if it is zero, so neither do we!"
    if tdbfile.header_block['Family ID'] > 0:
        write_field('ID', tdbfile.header_block['Family ID'], 1)
        f.write('\n')
    if options.mdx:
        write_field('IDX', os.path.basename(options.mdx), 1)
        f.write('\n')
    if options.mdr: 
        mdr = os.path.basename(options.mdr)
        mdr = mdr[:mdr.rfind('.')]
        write_field('MDR', mdr, 1)
        f.write('\n')
    if options.style:
        write_field('TYP', os.path.basename(options.style), 1)
        f.write('\n')
    f.write('    <SubProduct>\n')
    write_field('Name', tdbfile.header_block['Map Series'], 2)
    write_field('ID', tdbfile.header_block['Product ID'], 2)
    baseimg = os.path.basename(options.baseimg)
    baseimg = baseimg[:baseimg.rfind('.')]
    write_field('BaseMap', baseimg, 2)
    write_field('TDB', os.path.basename(options.tdbfile), 2)
    write_field('Directory', 'OSMTiles', 2)
    f.write('    </SubProduct>\n')
    f.write('</MapProduct>\n')
    f.close()

if __name__ == '__main__':
    (options, filenames) = parse_options(sys.argv[1:])
    try:
        tdbfile = TDBFile(options.tdbfile)
        tdbfile.print_dump()
    except IOError:
        error_exit("Could not open '%s' for reading." % options.tdbfile)

    if not options.dryrun:
        output_dir = prepare_output_dir(tdbfile, options)
        write_xml_file(tdbfile, options, output_dir)

    try:
        if not options.dryrun:
            imgoutput = os.path.join(output_dir, 'OSMTiles')
            os.mkdir(imgoutput)
            shutil.copy(options.tdbfile, os.path.join(imgoutput, os.path.basename(options.tdbfile)))
            if options.style:
                shutil.copy(options.style, os.path.join(output_dir, os.path.basename(options.style)))

            if options.mdx:
                shutil.copy(options.mdx, os.path.join(output_dir, os.path.basename(options.mdx)))

            if options.mdr:
                mdr_file = os.path.basename(options.mdr)

        for fn in filenames:
            logger.info("Processing " + fn)
            imgfile = IMGFile(fn)
            imgfile.print_info()
            if not options.dryrun:
                if fn != options.mdr:
                    imgfile.dump(imgoutput)
                else:
                    logger.info("MDR file")
                    imgfile.dump(output_dir)
            imgfile.close()
    except IOError:
            error_exit("Could not open '%s' for reading." % fn)
    except FileFormatError, m:
            error_exit(m)
