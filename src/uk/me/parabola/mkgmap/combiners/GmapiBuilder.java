/*
 * Copyright (C) 2016.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.mkgmap.CommandArgs;
import uk.me.parabola.mkgmap.reader.overview.OverviewMapDataSource;
import uk.me.parabola.util.EnhancedProperties;

/**
 * Create a map in the gmapi format.
 *
 * This is directory tree containing an XML file describing the contents, and exploded versions of
 * each .img file.
 */
public class GmapiBuilder implements Combiner {
	private final static String NS = "http://www.garmin.com/xmlschemas/MapProduct/v1";

	private final OverviewBuilder overviewBuilder;
	private final TdbBuilder tdbBuilder;
	private File tiles;

	public GmapiBuilder() {
		OverviewMap overviewSource = new OverviewMapDataSource();
		overviewBuilder = new OverviewBuilder(overviewSource);
		tdbBuilder = new TdbBuilder(overviewBuilder);
	}

	/**
	 * Initialise with the command line arguments.  This is called after all
	 * the command line arguments have been processed, but before any calls to
	 * the {@link #onMapEnd} methods.
	 *
	 * @param args The command line arguments.
	 */
	public void init(CommandArgs args) {

		String overviewMapname = args.get("overview-mapname", "osmmap");

		File base = new File(args.getOutputDir(), overviewMapname + ".gmapi");
		File gmapDir = new File(base, overviewMapname + ".gmap");
		tiles = new File(gmapDir, "OSMTiles");

		tiles.mkdirs();

		writeXmlFile(gmapDir);

		EnhancedProperties properties = args.getProperties();
		properties.setProperty("output-dir", gmapDir.getAbsolutePath());
		args = new CommandArgs(properties);

		overviewBuilder.init(args);
		tdbBuilder.init(args);
	}

	/**
	 * An xml file contains similar information that is contained in the windows registry.
	 *
	 * @param gDir The directory where the Info.xml file will be created.
	 */
	private void writeXmlFile(File gDir) {
		File infoFile = new File(gDir, "Info.xml");

		XMLOutputFactory factory = XMLOutputFactory.newFactory();
		try (Writer stream = new OutputStreamWriter(new FileOutputStream(infoFile))) {

			XMLStreamWriter writer = factory.createXMLStreamWriter(stream);

			writer.writeStartDocument("utf-8", "1.0");
			writer.setDefaultNamespace(NS);

			writer.writeStartElement(NS,"MapProduct");
			writer.writeDefaultNamespace(NS);

			xmlElement(writer, "Name", "OSM map");
			xmlElement(writer, "DataVersion", "100");
			xmlElement(writer, "DataFormat", "Original");
			xmlElement(writer, "ID", "6324");

			writer.writeStartElement(NS, "SubProduct");
			xmlElement(writer, "Name", "OSM map");
			xmlElement(writer, "ID", "1");
			xmlElement(writer, "BaseMap", "osmmap");
			xmlElement(writer, "TDB", "osmmap.tdb");
			xmlElement(writer, "Directory", "OSMTiles");
			writer.writeEndElement();

			writer.writeEndElement();
			writer.writeEndDocument();
			writer.flush();
		} catch (XMLStreamException | IOException e) {
			throw new ExitException("Could not create file " + infoFile + "; " + e);
		}
	}

	private void xmlElement(XMLStreamWriter writer, String name, String value) throws XMLStreamException {
		writer.writeStartElement(NS, name);
		writer.writeCharacters(value);
		writer.writeEndElement();
	}

	/**
	 * This is called when an individual map is complete.
	 *
	 * @param info An interface to read the map.
	 */
	public void onMapEnd(FileInfo info) {
		overviewBuilder.onMapEnd(info);
		tdbBuilder.onMapEnd(info);

		String fn = info.getFilename();
		System.out.println("FN is  " + fn);
		try {
			FileSystem fs = ImgFS.openFs(fn);

			for (DirectoryEntry ent : fs.list()) {
				String fullname = ent.getFullName();

				try (ImgChannel f = fs.open(fullname, "r")) {
					String name = displayName(fullname);
					if (Objects.equals(name, "."))
						continue;

					System.out.format("Copying %-15s %d\n", name, ent.getSize());
					copyToFile(f, info.getMapname(), name);

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private void copyToFile(ImgChannel f, String mapName, String subName) {
		File mapDir = new File(tiles, mapName);
		mapDir.mkdirs();
		File outName = new File(mapDir, subName);

		ByteBuffer buf = ByteBuffer.allocate(8 * 1024);
		try (FileOutputStream os = new FileOutputStream(outName)) {
			FileChannel outchan = os.getChannel();
			while (f.read(buf) > 0) {
				buf.flip();
				outchan.write(buf);
				buf.compact();
			}
		} catch (IOException e) {
			throw new ExitException("Cannot write file " + e.getMessage());
		}
	}

	private String displayName(String fullname) {
		return fullname.trim().replace("\000", "");
	}

	/**
	 * The complete map set has been processed.  Finish off anything that needs
	 * doing.
	 */
	public void onFinish() {
		overviewBuilder.onFinish();
		tdbBuilder.onFinish();
	}
}
