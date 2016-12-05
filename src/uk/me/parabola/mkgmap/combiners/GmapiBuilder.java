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

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
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

	private final Map<String, Combiner> combinerMap;
	private EnhancedProperties initArgs;

	private Path gmapDir;
	private final Map<Integer, ProductInfo> productMap = new HashMap<>();

	public GmapiBuilder(Map<String, Combiner> combinerMap) {
		this.combinerMap = combinerMap;
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
	}

	/**
	 * This is called when an individual map is complete.
	 *
	 * @param info An interface to read the map.
	 */
	public void onMapEnd(FileInfo info) {

		String fn = info.getFilename();
		String mapname = info.getMapname();

		// Unzip the image into the product tile directory.
		try {
			unzipImg(fn, mapname, info.getProductId());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * The complete map set has been processed.  Finish off anything that needs
	 * doing.
	 */
	public void onFinish() {
		try {
			String overviewMapname = finishTdbFile();

			unzipImg(getFilenameFor("img"), overviewMapname, 1);

			writeXmlFile(gmapDir);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String finishTdbFile() throws IOException {
		Path tdbPath = Paths.get(getFilenameFor("tdb"));
		String overviewMapname = initArgs.getProperty("overview-mapname", "osmmap");

		Files.copy(tdbPath, gmapDir.resolve("Product1").resolve(overviewMapname + ".tdb"));


		return overviewMapname;
	}

	private void unzipImg(String fn, String mapname, int productId) throws IOException {
		FileSystem fs = ImgFS.openFs(fn);

		for (DirectoryEntry ent : fs.list()) {
			String fullname = ent.getFullName();

			try (ImgChannel f = fs.open(fullname, "r")) {
				String name = displayName(fullname);
				if (Objects.equals(name, "."))
					continue;

				System.out.format("Copying %-15s %d\n", name, ent.getSize());
				Path imgPath = Paths.get(gmapDir.toString(),"Product" + productId, mapname, name);
				Files.createDirectories(imgPath.getParent());
				copyToFile(f, imgPath);

			}
		}
	}

	private void copyToFile(ImgChannel f, Path dest) {
		ByteBuffer buf = ByteBuffer.allocate(8 * 1024);
		try (ByteChannel outchan = Files.newByteChannel(dest, CREATE, WRITE, TRUNCATE_EXISTING)) {
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

	private String getFilenameFor(String kind) {
		return combinerMap.get(kind).getFilename();
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

	private static class ProductInfo {

	}
}
