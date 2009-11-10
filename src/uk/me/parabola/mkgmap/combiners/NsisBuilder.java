/*
 * Copyright (C) 2009.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */

package uk.me.parabola.mkgmap.combiners;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.mkgmap.CommandArgs;
import uk.me.parabola.mkgmap.Version;


public class NsisBuilder implements Combiner {
	private String baseFilename;
	private String nsisFilename;
	private String licenseFilename;
	private String seriesName;
	private String id;
	private int productId;
	private Boolean hasIndex = true;
	private final List<String> mapList = new ArrayList<String>();

	public void init(CommandArgs args) {
		int familyId = args.get("family-id", 0);
		productId = args.get("product-id", 1);

		baseFilename = args.get("overview-mapname", "osm");
		seriesName = args.get("series-name", "OSM map");

		String tmpId = Integer.toHexString(0x10000 | familyId);

		id = tmpId.substring(3, 5) + tmpId.substring(1, 3);

		nsisFilename = baseFilename + ".nsi";
		licenseFilename = baseFilename + "_license.txt";
		
		hasIndex = args.exists("index");
	}

	public void onMapEnd(FileInfo finfo) {
		if (!finfo.isImg())
			return;

		mapList.add(finfo.getMapname());
	}

	public void onFinish() {
		writeNSISFile();
	}


	public void writeNSISFile() {
		Writer w = null;

		try {
			w = new FileWriter(nsisFilename);
			PrintWriter pw = new PrintWriter(w);

			pw.format(Locale.ROOT, "!define DEFAULT_DIR \"C:\\Garmin\\Maps\\%s\"\n", seriesName);
			pw.format(Locale.ROOT, "!define INSTALLER_DESCRIPTION \"%s\"\n", seriesName);
			pw.format(Locale.ROOT, "!define INSTALLER_NAME \"%s\"\n", seriesName);
			pw.format(Locale.ROOT, "!define MAPNAME \"%s\"\n", baseFilename);
			pw.format(Locale.ROOT, "!define PRODUCT_ID \"%s\"\n", productId);
			pw.format(Locale.ROOT, "!define REG_KEY \"%s\"\n", seriesName);
			pw.println();

			pw.format(Locale.ROOT, "SetCompressor /SOLID lzma\n");
			pw.println();

			pw.format(Locale.ROOT, "; Includes\n");
			pw.format(Locale.ROOT, "!include \"MUI2.nsh\"\n");
			pw.println();

			pw.format(Locale.ROOT, "; Installer pages\n");
			pw.format(Locale.ROOT, "!insertmacro MUI_PAGE_WELCOME\n");
			pw.format(Locale.ROOT, "!insertmacro MUI_PAGE_LICENSE %s\n", licenseFilename);
			pw.format(Locale.ROOT, "!insertmacro MUI_PAGE_DIRECTORY\n");
			pw.format(Locale.ROOT, "!insertmacro MUI_PAGE_INSTFILES\n");
			pw.format(Locale.ROOT, "!insertmacro MUI_PAGE_FINISH\n");
			pw.println();

			pw.format(Locale.ROOT, "; Uninstaller pages\n");
			pw.format(Locale.ROOT, "!define MUI_UNPAGE_INSTFILES\n");
			pw.println();

			pw.format(Locale.ROOT, "!insertmacro MUI_LANGUAGE \"English\"\n");
			pw.println();

			pw.format(Locale.ROOT, "Name \"${INSTALLER_DESCRIPTION}\"\n");
			pw.format(Locale.ROOT, "OutFile \"${INSTALLER_NAME}.exe\"\n");
			pw.format(Locale.ROOT, "InstallDir \"${DEFAULT_DIR}\"\n");
			pw.println();

			pw.format(Locale.ROOT, "Section \"MainSection\" SectionMain\n");
			pw.format(Locale.ROOT, "  SetOutPath \"$INSTDIR\"\n");
			pw.format(Locale.ROOT, "  File \"${MAPNAME}.img\"\n");
			if (hasIndex) {
				pw.format(Locale.ROOT, "  File \"${MAPNAME}_mdr.img\"\n");
				pw.format(Locale.ROOT, "  File \"${MAPNAME}.mdx\"\n");
			}
			pw.format(Locale.ROOT, "  File \"${MAPNAME}.tdb\"\n");
			for (String file : mapList) {
				pw.format(Locale.ROOT, "  File \"%s.img\"\n", file);
			}
			pw.println();
			pw.format(Locale.ROOT, "  WriteRegBin HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\" \"ID\" %s\n", id);
			if (hasIndex) {
				pw.format(Locale.ROOT, "  WriteRegStr HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\" \"IDX\" \"$INSTDIR\\${MAPNAME}.mdx\"\n");
				pw.format(Locale.ROOT, "  WriteRegStr HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\" \"MDR\" \"$INSTDIR\\${MAPNAME}_mdr.img\"\n");
			}
			pw.format(Locale.ROOT, "  WriteRegStr HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\\${PRODUCT_ID}\" \"BMAP\" \"$INSTDIR\\${MAPNAME}.img\"\n");
			pw.format(Locale.ROOT, "  WriteRegStr HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\\${PRODUCT_ID}\" \"LOC\" \"$INSTDIR\"\n");
			pw.format(Locale.ROOT, "  WriteRegStr HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\\${PRODUCT_ID}\" \"TDB\" \"$INSTDIR\\${MAPNAME}.tdb\"\n");
			pw.println();
			pw.format(Locale.ROOT, "  WriteUninstaller \"$INSTDIR\\Uninstall.exe\"\n");
			pw.println();
			pw.format(Locale.ROOT, "SectionEnd\n");
			pw.println();

			pw.format(Locale.ROOT, "Section \"Uninstall\"\n");
			pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\${MAPNAME}.img\"\n");
			pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\Uninstall.exe\"\n");
			if (hasIndex) {
				pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\${MAPNAME}_mdr.img\"\n");
				pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\${MAPNAME}.mdx\"\n");
			}
			pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\${MAPNAME}.tdb\"\n");
			for (String file : mapList) {
				pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\%s.img\"\n", file);
			}
			pw.println();
			pw.format(Locale.ROOT, "  RmDir \"$INSTDIR\"\n");
			pw.println();
			pw.format(Locale.ROOT, "  DeleteRegValue HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\" \"ID\"\n");
			if (hasIndex) {
				pw.format(Locale.ROOT, "  DeleteRegValue HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\" \"IDX\"\n");
				pw.format(Locale.ROOT, "  DeleteRegValue HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\" \"MDR\"\n");
			}
			pw.format(Locale.ROOT, "  DeleteRegValue HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\\${PRODUCT_ID}\" \"BMAP\"\n");
			pw.format(Locale.ROOT, "  DeleteRegValue HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\\${PRODUCT_ID}\" \"LOC\"\n");
			pw.format(Locale.ROOT, "  DeleteRegValue HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\\${PRODUCT_ID}\" \"TDB\"\n");
			pw.format(Locale.ROOT, "  DeleteRegKey /IfEmpty HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\\${PRODUCT_ID}\"\n");
			pw.format(Locale.ROOT, "  DeleteRegKey /IfEmpty HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\"\n");
			pw.println();
			pw.format(Locale.ROOT, "SectionEnd\n");

		} catch (IOException e) {
			System.err.println("Could not write NSIS file");
		} finally {
			Utils.closeFile(w);
		}
		
		try {
			w = new FileWriter(licenseFilename);
			PrintWriter pw = new PrintWriter(w);

			pw.format(Locale.ROOT, "OSM Street map\n");
			pw.format(Locale.ROOT, "http://www.openstreetmap.org/\n");
			pw.println();
			pw.format(Locale.ROOT, "Map data licenced under Creative Commons Attribution ShareAlike 2.0\n");
			pw.format(Locale.ROOT, "http://creativecommons.org/licenses/by-sa/2.0/\n");      
			pw.println();
			pw.format(Locale.ROOT, "Map created with mkgmap-r" + Version.VERSION +"\n");
		} catch (IOException e) {
			System.err.println("Could not write license file");
		} finally {
			Utils.closeFile(w);
		}
		
	}
}
