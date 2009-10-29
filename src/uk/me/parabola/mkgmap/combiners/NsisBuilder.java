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


public class NsisBuilder implements Combiner {
	private String baseFilename;
	private String nsisFilename;
	private String seriesName;
	private String id;
	private final List<String> mapList = new ArrayList<String>();

	public void init(CommandArgs args) {
		int familyId = args.get("family-id", 0);

		baseFilename = args.get("overview-mapname", "osm");
		seriesName = args.get("series-name", "OSM maps");

		String tmpId = Integer.toHexString(0x10000 | familyId);

		id = tmpId.substring(3, 5) + tmpId.substring(1, 3);

		nsisFilename = baseFilename + ".nsi";
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

			pw.format(Locale.ROOT, "SetCompressor /SOLID lzma\n");
			pw.println();

			pw.format(Locale.ROOT, "; Includes\n");
			pw.format(Locale.ROOT, "!include \"MUI2.nsh\"\n");
			pw.println();

			pw.format(Locale.ROOT, "; Installer pages\n");
			pw.format(Locale.ROOT, "!insertmacro MUI_PAGE_WELCOME\n");
			pw.format(Locale.ROOT, "!insertmacro MUI_PAGE_DIRECTORY\n");
			//pw.format(Locale.ROOT, "!insertmacro MUI_PAGE_STARTMENU Application \n");
			pw.format(Locale.ROOT, "!insertmacro MUI_PAGE_INSTFILES\n");
			pw.format(Locale.ROOT, "!insertmacro MUI_PAGE_FINISH\n");
			pw.println();

			pw.format(Locale.ROOT, "; Uninstaller pages\n");
			pw.format(Locale.ROOT, "!define MUI_UNPAGE_INSTFILES\n");
			pw.println();

			pw.format(Locale.ROOT, "!insertmacro MUI_LANGUAGE \"English\"\n");
			pw.println();

			pw.format(Locale.ROOT, "Name \"%s\"\n", seriesName);
			pw.format(Locale.ROOT, "OutFile \"%s.exe\"\n", seriesName);
			pw.format(Locale.ROOT, "InstallDir \"C:\\Garmin\\Maps\\%s\"\n", seriesName);
			pw.println();

			pw.format(Locale.ROOT, "Section \"MainSection\" SectionMain\n");
			pw.format(Locale.ROOT, "  SetOutPath \"$INSTDIR\"\n");
			pw.format(Locale.ROOT, "  File \"%s.img\"\n", baseFilename);
			pw.format(Locale.ROOT, "  File \"%s_mdr.img\"\n", baseFilename);
			pw.format(Locale.ROOT, "  File \"%s.mdx\"\n", baseFilename);
			pw.format(Locale.ROOT, "  File \"%s.tdb\"\n", baseFilename);
			for (String file : mapList) {
				pw.format(Locale.ROOT, "  File \"%s.img\"\n", file);
			}
			pw.println();
			pw.format(Locale.ROOT, "  WriteRegBin HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\" \"ID\" %s\n", seriesName, id);
			pw.format(Locale.ROOT, "  WriteRegStr HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\" \"IDX\" \"$INSTDIR\\%s.mdx\"\n", seriesName, baseFilename);
			pw.format(Locale.ROOT, "  WriteRegStr HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\" \"MDR\" \"$INSTDIR\\%s_mdr.img\"\n", seriesName, baseFilename);
			pw.format(Locale.ROOT, "  WriteRegStr HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\\1\" \"BMAP\" \"$INSTDIR\\%s.img\"\n", seriesName, baseFilename);
			pw.format(Locale.ROOT, "  WriteRegStr HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\\1\" \"LOC\" \"$INSTDIR\"\n", seriesName);
			pw.format(Locale.ROOT, "  WriteRegStr HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\\1\" \"TDB\" \"$INSTDIR\\%s.tdb\"\n", seriesName, baseFilename);
			pw.println();
			pw.format(Locale.ROOT, "  WriteUninstaller \"$INSTDIR\\Uninstall.exe\"\n");
			pw.println();
			pw.format(Locale.ROOT, "SectionEnd\n");
			pw.println();

			pw.format(Locale.ROOT, "Section \"Uninstall\"\n");
			pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\%s.img\"\n", baseFilename);
			pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\Uninstall.exe\"\n");
			pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\%s_mdr.img\"\n", baseFilename);
			pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\%s.mdx\"\n", baseFilename);
			pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\%s.tdb\"\n", baseFilename);
			for (String file : mapList) {
				pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\%s.img\"\n", file);
			}
			pw.println();
			pw.format(Locale.ROOT, "  RmDir \"$INSTDIR\"\n");
			pw.println();
			pw.format(Locale.ROOT, "  DeleteRegValue HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\" \"ID\"\n", seriesName);
			pw.format(Locale.ROOT, "  DeleteRegValue HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\" \"IDX\"\n", seriesName);
			pw.format(Locale.ROOT, "  DeleteRegValue HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\" \"MDR\"\n", seriesName);
			pw.format(Locale.ROOT, "  DeleteRegValue HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\\1\" \"BMAP\"\n", seriesName);
			pw.format(Locale.ROOT, "  DeleteRegValue HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\\1\" \"LOC\"\n", seriesName);
			pw.format(Locale.ROOT, "  DeleteRegValue HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\\1\" \"TDB\"\n", seriesName);
			pw.format(Locale.ROOT, "  DeleteRegKey /IfEmpty HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\\1\"\n", seriesName);
			pw.format(Locale.ROOT, "  DeleteRegKey /IfEmpty HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\%s\"\n", seriesName);
			pw.println();
			pw.format(Locale.ROOT, "SectionEnd\n");

		} catch (IOException e) {
			System.err.println("Could not write NSIS file");
		} finally {
			Utils.closeFile(w);
		}
	}
}
