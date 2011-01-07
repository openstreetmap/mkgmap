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
	private String outputDir;
	private String seriesName;
	private String id;
	private int productId;

	private boolean hasIndex;
	private boolean hasTyp;

	private final List<String> mapList = new ArrayList<String>();
	private static final String HKLM_FAMILIES = "HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}";
	private String typName;

	public void init(CommandArgs args) {
		int familyId = args.get("family-id", 0);
		productId = args.get("product-id", 1);

		baseFilename = args.get("overview-mapname", "osmmap");
		seriesName = args.get("series-name", "OSM map");

		String tmpId = Integer.toHexString(0x10000 | familyId);

		id = tmpId.substring(3, 5) + tmpId.substring(1, 3);

		nsisFilename = baseFilename + ".nsi";
		licenseFilename = baseFilename + "_license.txt";
		
		outputDir = args.getOutputDir();
		
		hasIndex = args.exists("index");
	}

	public void onMapEnd(FileInfo info) {
		switch (info.getKind()) {
		case IMG_KIND:
			mapList.add(info.getMapname());
			break;
		case TYP_KIND:
			hasTyp = true;
			typName = info.getFilename();
			break;
		case MDR_KIND:
			hasIndex = true;
			break;
		case GMAPSUPP_KIND:
			break;
		}
	}

	public void onFinish() {
		writeNsisFile();
		writeLicenceFile();
	}

	private void writeNsisFile() {
		Writer w = null;
		try {
			w = new FileWriter(Utils.joinPath(outputDir, nsisFilename));
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

			// Install files
			pw.format(Locale.ROOT, "Section \"MainSection\" SectionMain\n");
			pw.format(Locale.ROOT, "  SetOutPath \"$INSTDIR\"\n");
			pw.format(Locale.ROOT, "  File \"${MAPNAME}.img\"\n");
			if (hasIndex) {
				pw.format(Locale.ROOT, "  File \"${MAPNAME}_mdr.img\"\n");
				pw.format(Locale.ROOT, "  File \"${MAPNAME}.mdx\"\n");
			}
			if (hasTyp)
				pw.format(Locale.ROOT, "  File \"%s\"\n", typName);

			pw.format(Locale.ROOT, "  File \"${MAPNAME}.tdb\"\n");
			for (String file : mapList)
				pw.format(Locale.ROOT, "  File \"%s.img\"\n", file);


			// Registry values
			pw.println();
			pw.format(Locale.ROOT, "  WriteRegBin " + HKLM_FAMILIES + "\" \"ID\" %s\n", id);
			if (hasIndex) {
				pw.format(Locale.ROOT, "  WriteRegStr " + HKLM_FAMILIES + "\" \"IDX\" \"$INSTDIR\\${MAPNAME}.mdx\"\n");
				pw.format(Locale.ROOT, "  WriteRegStr " + HKLM_FAMILIES + "\" \"MDR\" \"$INSTDIR\\${MAPNAME}_mdr.img\"\n");
			}
			if (hasTyp)
				pw.format(Locale.ROOT, "  WriteRegStr " + HKLM_FAMILIES + "\" \"TYP\" \"$INSTDIR\\%s\"\n", typName);

			pw.format(Locale.ROOT, "  WriteRegStr " + HKLM_FAMILIES + "\\${PRODUCT_ID}\" \"BMAP\" \"$INSTDIR\\${MAPNAME}.img\"\n");
			pw.format(Locale.ROOT, "  WriteRegStr " + HKLM_FAMILIES + "\\${PRODUCT_ID}\" \"LOC\" \"$INSTDIR\"\n");
			pw.format(Locale.ROOT, "  WriteRegStr " + HKLM_FAMILIES + "\\${PRODUCT_ID}\" \"TDB\" \"$INSTDIR\\${MAPNAME}.tdb\"\n");

			pw.println();
			pw.format(Locale.ROOT, "  WriteUninstaller \"$INSTDIR\\Uninstall.exe\"\n");
			pw.println();
			pw.format(Locale.ROOT, "SectionEnd\n");
			pw.println();

			// Un-install files
			pw.format(Locale.ROOT, "Section \"Uninstall\"\n");
			pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\${MAPNAME}.img\"\n");
			if (hasIndex) {
				pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\${MAPNAME}_mdr.img\"\n");
				pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\${MAPNAME}.mdx\"\n");
			}
			if (hasTyp)
				pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\%s\"\n", typName);
			pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\${MAPNAME}.tdb\"\n");
			for (String file : mapList) {
				pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\%s.img\"\n", file);
			}
			pw.format(Locale.ROOT, "  Delete \"$INSTDIR\\Uninstall.exe\"\n");
			pw.println();
			pw.format(Locale.ROOT, "  RmDir \"$INSTDIR\"\n");

			// Remove registry entries
			pw.println();
			pw.format(Locale.ROOT, "  DeleteRegValue " + HKLM_FAMILIES + "\" \"ID\"\n");
			if (hasIndex) {
				pw.format(Locale.ROOT, "  DeleteRegValue " + HKLM_FAMILIES + "\" \"IDX\"\n");
				pw.format(Locale.ROOT, "  DeleteRegValue " + HKLM_FAMILIES + "\" \"MDR\"\n");
			}
			if (hasTyp)
				pw.format(Locale.ROOT, "  DeleteRegValue " + HKLM_FAMILIES + "\" \"TYP\"\n");
			pw.format(Locale.ROOT, "  DeleteRegValue " + HKLM_FAMILIES + "\\${PRODUCT_ID}\" \"BMAP\"\n");
			pw.format(Locale.ROOT, "  DeleteRegValue " + HKLM_FAMILIES + "\\${PRODUCT_ID}\" \"LOC\"\n");
			pw.format(Locale.ROOT, "  DeleteRegValue " + HKLM_FAMILIES + "\\${PRODUCT_ID}\" \"TDB\"\n");
			pw.format(Locale.ROOT, "  DeleteRegKey /IfEmpty " + HKLM_FAMILIES + "\\${PRODUCT_ID}\"\n");
			pw.format(Locale.ROOT, "  DeleteRegKey /IfEmpty " + HKLM_FAMILIES + "\"\n");

			pw.println();
			pw.format(Locale.ROOT, "SectionEnd\n");

		} catch (IOException e) {
			System.err.println("Could not write NSIS file");
		} finally {
			Utils.closeFile(w);
		}
	}

	/**
	 * We write out a licence file that is included in the installer.
	 * TODO get a file from the resource directory of something similar.
	 */
	private void writeLicenceFile() {
		Writer w = null;
		try {
			w = new FileWriter(Utils.joinPath(outputDir, licenseFilename));
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
