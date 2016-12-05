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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

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

import static java.nio.file.StandardOpenOption.*;

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
	private Path tiles;
	private Path gmapDir;
	private EnhancedProperties initArgs;

	public GmapiBuilder(Map<String, Combiner> combinerMap) {
		overviewBuilder = (OverviewBuilder) combinerMap.get("img");
		tdbBuilder = (TdbBuilder) combinerMap.get("tdb");
	}

	/**
	 * Initialise with the command line arguments.  This is called after all
	 * the command line arguments have been processed, but before any calls to
	 * the {@link #onMapEnd} methods.
	 *
	 * @param args The command line arguments.
	 */
	public void init(CommandArgs args) {
		initArgs = args.getProperties();
		String overviewMapname = args.get("overview-mapname", "osmmap");

		Path base = Paths.get(args.getOutputDir(), overviewMapname + ".gmapi");
		gmapDir = base.resolve(overviewMapname + ".gmap");
		tiles = gmapDir.resolve("Product1");

		tiles.toFile().mkdirs();

		writeXmlFile(gmapDir);

	}

	/**
	 * This is called when an individual map is complete.
	 *
	 * @param info An interface to read the map.
	 */
	public void onMapEnd(FileInfo info) {

		String fn = info.getFilename();
		System.out.println("FN is  " + fn);
		String mapname = info.getMapname();
		unzipImg(fn, mapname);
	}

	private void unzipImg(String fn, String mapname) {
		try {
			FileSystem fs = ImgFS.openFs(fn);

			for (DirectoryEntry ent : fs.list()) {
				String fullname = ent.getFullName();

				try (ImgChannel f = fs.open(fullname, "r")) {
					String name = displayName(fullname);
					if (Objects.equals(name, "."))
						continue;

					System.out.format("Copying %-15s %d\n", name, ent.getSize());
					copyToFile(f, mapname, name);

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * The complete map set has been processed.  Finish off anything that needs
	 * doing.
	 */
	public void onFinish() {
		Path tdbPath = Paths.get(tdbBuilder.getFilename());
		String overviewMapname = initArgs.getProperty("overview-mapname", "osmmap");
		try {

			Files.copy(tdbPath, gmapDir.resolve("Product1").resolve(overviewMapname + ".tdb"));
		} catch (IOException e) {
			e.printStackTrace();
		}

		unzipImg(overviewBuilder.getFilename(), overviewMapname);
	}

	private void copyToFile(ImgChannel f, String mapName, String subName) {
		Path mapDir = tiles.resolve(mapName);
		mapDir.toFile().mkdirs();
		Path outName = mapDir.resolve(subName);

		ByteBuffer buf = ByteBuffer.allocate(8 * 1024);
		try (ByteChannel outchan = Files.newByteChannel(outName, CREATE, WRITE, TRUNCATE_EXISTING)) {
			while (f.read(buf) > 0) {
				buf.flip();
				outchan.write(buf);
				buf.compact();
			}
		} catch (IOException e) {
			throw new ExitException("Cannot write file " + e);
		}
	}

	private String displayName(String fullname) {
		return fullname.trim().replace("\000", "");
	}

	/**
	 * An xml file contains similar information that is contained in the windows registry.
	 *
	 * @param gDir The directory where the Info.xml file will be created.
	 */
	private void writeXmlFile(Path gDir) {
		Path infoFile = gDir.resolve("Info.xml");

		XMLOutputFactory factory = XMLOutputFactory.newFactory();

		try (Writer stream = Files.newBufferedWriter(infoFile)) {

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
			xmlElement(writer, "Directory", "Product1");
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
}
