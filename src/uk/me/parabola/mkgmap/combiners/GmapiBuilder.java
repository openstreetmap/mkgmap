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
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

	private Path gmapDir;
	private final Map<Integer, ProductInfo> productMap = new HashMap<>();

	private String familyName;
	private int familyId;
	private String typFile;

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
		String overviewMapname = args.get("overview-mapname", "osmmap");

		gmapDir = Paths.get(args.getOutputDir(), String.format("%s.gmap", overviewMapname));
	}

	/**
	 * This is called when an individual map is complete.
	 *
	 * @param info An interface to read the map.
	 */
	public void onMapEnd(FileInfo info) {

		if (familyName == null) {
			familyName = info.getFamilyName();
			familyId = info.getFamilyId();
		}

		String fn = info.getFilename();
		String mapname = info.getMapname();

		int productId = info.getProductId();
		if (!productMap.containsKey(productId))
			productMap.put(productId, new ProductInfo(productId, info.getSeriesName(), info.getOverviewName()));

		// Unzip the image into the product tile directory.
		try {
			if (info.isImg())
				unzipImg(fn, mapname, productId);
			else if (info.getKind() == FileKind.TYP_KIND)
				typFile = info.getFilename();

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
			if (combinerMap.containsKey("mdx")) {
				File file = new File(getFilenameFor("mdx"));
				Files.copy(file.toPath(), gmapDir.resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
			}
			if (combinerMap.containsKey("mdr")) {
				File file = new File(getFilenameFor("mdr"));
				unzipImg(file.getCanonicalPath(), gmapDir.resolve(nameWithoutExtension(file)));
			}

			if (typFile != null) {
				File file = new File(typFile);
				Files.copy(file.toPath(), gmapDir.resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
			}

			for (ProductInfo info : productMap.values()) {
				finishTdbFile(info);
				unzipImg(getFilenameFor("img"), info.overviewName, info.id);
			}

			writeXmlFile(gmapDir);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String nameWithoutExtension(File file) {
		String name = file.getName();
		int len = name.length();
		if (len < 4)
			return name;
		return name.substring(0, len-4);
	}

	private void finishTdbFile(ProductInfo info) throws IOException {
		Path tdbPath = Paths.get(getFilenameFor("tdb"));

		Files.copy(tdbPath, gmapDir
				.resolve(String.format("Product%d", info.id))
				.resolve(String.format("%s.tdb", info.overviewName)), StandardCopyOption.REPLACE_EXISTING);
	}

	private void unzipImg(String srcImgName, String mapname, int productId) throws IOException {
		Path destDir = Paths.get(gmapDir.toString(), "Product" + productId, mapname);

		unzipImg(srcImgName, destDir);
	}

	private void unzipImg(String srcImgName, Path destDir) throws IOException {
		FileSystem fs = ImgFS.openFs(srcImgName);
		for (DirectoryEntry ent : fs.list()) {
			String fullname = ent.getFullName();

			try (ImgChannel f = fs.open(fullname, "r")) {
				String name = displayName(fullname);
				if (Objects.equals(name, "."))
					continue;

				Files.createDirectories(destDir);
				copyToFile(f, destDir.resolve(name));
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

	private String getFilenameFor(String kind) {
		return combinerMap.get(kind).getFilename();
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

			writer.writeStartDocument("UTF-8", "1.0");
			writer.setDefaultNamespace(NS);
			writer.writeCharacters("\n");

			writer.writeStartElement(NS,"MapProduct");
			writer.writeDefaultNamespace(NS);
			writer.writeCharacters("\n");

			xmlElement(writer, "Name", familyName);
			xmlElement(writer, "DataVersion", "100");
			xmlElement(writer, "DataFormat", "Original");
			xmlElement(writer, "ID", String.valueOf(familyId));

			if (combinerMap.containsKey("mdx")) {
				String mdxFile = getFilenameFor("mdx");

				File file = new File(mdxFile);
				xmlElement(writer, "IDX", file.getName());
			}

			if (combinerMap.containsKey("mdr")) {
				String mdrName = getFilenameFor("mdr");
				File file = new File(mdrName);
				xmlElement(writer, "MDR", nameWithoutExtension(file));
			}

			if (typFile != null) {
				File file = new File(typFile);
				xmlElement(writer, "TYP", file.getName());
			}

			for (ProductInfo prod : productMap.values()) {
				writer.writeStartElement(NS, "SubProduct");
				writer.writeCharacters("\n");

				xmlElement(writer, "Name", prod.seriesName);
				xmlElement(writer, "ID", String.valueOf(prod.id));
				xmlElement(writer, "BaseMap", prod.overviewName);
				xmlElement(writer, "TDB", String.format("%s.tdb", prod.overviewName));
				xmlElement(writer, "Directory", String.format("Product%s", prod.id));
				writer.writeEndElement();
				writer.writeCharacters("\n");
			}

			writer.writeEndElement();
			writer.writeEndDocument();
			writer.flush();
		} catch (XMLStreamException | IOException e) {
			throw new ExitException("Could not create file " + infoFile + "; " + e);
		}
	}

	private void xmlElement(XMLStreamWriter writer, String name, String value) throws XMLStreamException {
		writer.writeCharacters(" ");
		writer.writeStartElement(NS, name);
		writer.writeCharacters(value);
		writer.writeEndElement();
		writer.writeCharacters("\n");
	}

	private static class ProductInfo {

		private final String seriesName;
		private final String overviewName;
		private final int id;

		public ProductInfo(int id, String seriesName, String overviewName) {
			this.id = id;
			this.seriesName = seriesName;
			this.overviewName = overviewName;
		}
	}
}
