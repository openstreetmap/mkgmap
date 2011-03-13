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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
	private String familyName;
	private String id;
	private int productId;

	private boolean hasIndex;
	private boolean hasTyp;

	private final List<String> mapList = new ArrayList<String>();
	private String typName;

	public void init(CommandArgs args) {
		int familyId = args.get("family-id", CommandArgs.DEFAULT_FAMILYID);
		productId = args.get("product-id", 1);

		baseFilename = args.get("overview-mapname", "osmmap");
		familyName = args.get("family-name", "OSM map");

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
		InputStream inStream = null;

		try {
			inStream = new FileInputStream("resources/installer_template.nsi");
		} catch (Exception ex) {
			inStream = null;
		}

		if(inStream == null)	// If not loaded from disk use from jar file
			inStream = this.getClass().getResourceAsStream("/installer/installer_template.nsi");
		
		if (inStream == null) {
			System.err.println("Could not find the installer template.");
			return;
		}
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(inStream));
			w = new FileWriter(Utils.joinPath(outputDir, nsisFilename));
			PrintWriter pw = new PrintWriter(w);
			
		    String strLine;
		    while ((strLine = br.readLine()) != null)   {
		    	if (strLine.contains("INSERT_DEFINES_HERE"))
		    		writeDefines(pw);
		    	else if (strLine.contains("INSERT_REGBIN_HERE"))
		    		writeRegBin(pw);
		    	else if (strLine.contains("INSERT_ADDED_FILES_HERE"))
		    		writeAddedFiles(pw);
		    	else if (strLine.contains("INSERT_REMOVED_FILES_HERE"))
		    		writeRemovedFiles(pw);
		    	else 
		    		pw.format(Locale.ROOT, strLine + "\n");
		    }
		} catch (IOException e) {
			System.err.println("Could not write NSIS file");
		} finally {
			Utils.closeFile(w);
		}			
	}
	
	private void writeDefines(PrintWriter pw) {
			pw.format(Locale.ROOT, "!define DEFAULT_DIR \"C:\\Garmin\\Maps\\%s\"\n", familyName);
			pw.format(Locale.ROOT, "!define INSTALLER_DESCRIPTION \"%s\"\n", familyName);
			pw.format(Locale.ROOT, "!define INSTALLER_NAME \"%s\"\n", familyName);
			pw.format(Locale.ROOT, "!define MAPNAME \"%s\"\n", baseFilename);
			pw.format(Locale.ROOT, "!define PRODUCT_ID \"%s\"\n", productId);
			pw.format(Locale.ROOT, "!define REG_KEY \"%s\"\n", familyName);
			if (hasIndex)
				pw.format(Locale.ROOT, "!define INDEX\n");
			if (hasTyp)
				pw.format(Locale.ROOT, "!define TYPNAME \"%s\"\n", typName);
	}

	private void writeRegBin(PrintWriter pw) {
		// Ideally we should have a define for the family value but NSIS won't allow "hexadecimal" variables
		pw.format(Locale.ROOT, "  WriteRegBin HKLM \"SOFTWARE\\Garmin\\MapSource\\Families\\${REG_KEY}\" \"ID\" %s\n", id);
}	
			
	private void writeAddedFiles(PrintWriter pw) {
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
	}



	private void writeRemovedFiles(PrintWriter pw) {
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
	}


	/**
	 * We write out a license file that is included in the installer.
	 */
	private void writeLicenceFile() {
		Writer w = null;
		InputStream inStream = null;
		try {
			inStream = new FileInputStream("resources/license_template.txt");
		} catch (Exception ex) {
			inStream = null;
		}

		if(inStream == null)	// If not loaded from disk use from jar file
			inStream = this.getClass().getResourceAsStream("/installer/license_template.txt");
		
		if (inStream == null) {
			System.err.println("Could not find the license template.");
			return;
		}
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(inStream));
			w = new FileWriter(Utils.joinPath(outputDir, licenseFilename));
			PrintWriter pw = new PrintWriter(w);
			
		    String strLine;
		    while ((strLine = br.readLine()) != null)   {
		    	pw.format(Locale.ROOT, strLine + "\n");
		    }
	
			pw.format(Locale.ROOT, "Map created with mkgmap-r" + Version.VERSION +"\n");
		} catch (IOException e) {
			System.err.println("Could not write license file");
		} finally {
			Utils.closeFile(w);
		}
	}
}
