/*
 * Copyright (C) 2007 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: Nov 15, 2007
 */
package uk.me.parabola.mkgmap.combiners;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import uk.me.parabola.imgfmt.FileExistsException;
import uk.me.parabola.imgfmt.FileNotWritableException;
import uk.me.parabola.imgfmt.FileSystemParam;
import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.mdr.MdrConfig;
import uk.me.parabola.imgfmt.app.srt.SRTFile;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.mps.MapBlock;
import uk.me.parabola.imgfmt.mps.MpsFile;
import uk.me.parabola.imgfmt.mps.MpsFileReader;
import uk.me.parabola.imgfmt.mps.ProductBlock;
import uk.me.parabola.imgfmt.sys.FileImgChannel;
import uk.me.parabola.imgfmt.sys.FileLink;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.imgfmt.sys.Syncable;
import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.CommandArgs;

/**
 * Create the gmapsupp file.  There is nothing much special about this file
 * (as far as I know - there's not a public official spec or anything) it is
 * just a regular .img file which is why it works to rename a single .img file
 * and send it to the device.
 * <p/>
 * Effectively we just 'unzip' the constituent .img files and then 'zip' them
 * back into the gmapsupp.img file.
 * <p/>
 * In addition we need to create and add the MPS file, if we don't already
 * have one.
 *
 * @author Steve Ratcliffe
 */
public class GmapsuppBuilder implements Combiner {
	private static final Logger log = Logger.getLogger(GmapsuppBuilder.class);

	private static final String GMAPSUPP = "gmapsupp.img";

	private final Map<String, FileInfo> files = new LinkedHashMap<>();

	// all these need to be set in the init routine from arguments.
	private String areaName;
	private String mapsetName;

	private String overallDescription = "Combined map";
	private String outputDir;
	private MpsFile mpsFile;

	private boolean createIndex;	// True if we should create and add an index file

	// There is a separate MDR and SRT file for each family id in the gmapsupp
	private final Map<Integer, MdrBuilder> mdrBuilderMap = new LinkedHashMap<>();
	private final Map<Integer, Sort> sortMap = new LinkedHashMap<>();
	private MdrConfig mdrConfig; // one base config for all 
	private boolean hideGmapsuppOnPC;
	private int productVersion;

	private FileSystem imgFs;

	public void init(CommandArgs args) {
		areaName = args.get("area-name", null);
		mapsetName = args.get("mapset-name", "OSM map set");
		overallDescription = args.getDescription();
		outputDir = args.getOutputDir();
		hideGmapsuppOnPC = args.get("hide-gmapsupp-on-pc", false);
		productVersion = args.get("product-version", 100);
		mdrConfig = new MdrConfig();
		mdrConfig.setIndexOptions(args);

		try {
			imgFs = createGmapsupp();
		} catch (FileNotWritableException e) {
			throw new MapFailedException("Could not create gmapsupp.img file");
		}
	}

	/**
	 * Add or retrieve the MDR file for the given familyId.
	 * @param familyId The family id to create the mdr file for.
	 * @param sort The sort for this family id.
	 * @return If there is already an mdr file for this family then it is returned, else the newly created
	 * one.
	 */
	private MdrBuilder addMdrFile(int familyId, Sort sort) {
		MdrBuilder mdrBuilder = mdrBuilderMap.get(familyId);
		if (mdrBuilder != null)
			return mdrBuilder;

		mdrBuilder = new MdrBuilder();

		try {
			String imgname = String.format("%08d.MDR", familyId);
			ImgChannel chan = imgFs.create(imgname);
			mdrBuilder.initForDevice(chan, sort, mdrConfig);
		} catch (FileExistsException e) {
			System.err.println("Could not create duplicate MDR file");
		}

		mdrBuilderMap.put(familyId, mdrBuilder);
		return mdrBuilder;
	}

	/**
	 * Add the sort file for the given family id.
	 */
	private void addSrtFile(int familyId, FileInfo info) {
		Sort prevSort = sortMap.get(familyId);
		Sort sort = info.getSort();
		if (prevSort == null) {
			if (info.getKind() == FileKind.IMG_KIND) {
				sortMap.put(familyId, sort);
			}
		} else {
			if (prevSort.getCodepage() != sort.getCodepage())
				System.err.printf("WARNING: input file '%s' has a different code page (%d rather than %d)\n",
						info.getFilename(), sort.getCodepage(), prevSort.getCodepage());
			if (info.hasSortOrder() && prevSort.getSortOrderId() != sort.getSortOrderId())
				System.err.printf("WARNING: input file '%s' has a different sort order (%x rather than %x\n",
						info.getFilename(), sort.getSortOrderId(), prevSort.getSortOrderId());
		}
	}

	/**
	 * This is called when the map is complete. We collect information about the map to be used in the TDB file and for
	 * preparing the gmapsupp file.
	 *
	 * @param info Information about the img file.
	 */
	public void onMapEnd(FileInfo info) {
		files.put(info.getFilename(), info);

		if (info.isImg()) {
			int familyId = info.getFamilyId();
			if (createIndex) {
				MdrBuilder mdrBuilder = addMdrFile(familyId, info.getSort());
				mdrBuilder.onMapEnd(info);
			}

			addSrtFile(familyId, info);
		}
	}

	/**
	 * The complete map set has been processed. Creates the gmapsupp file.  This is done by stepping through each img file,
	 * reading all the sub files and copying them into the gmapsupp file.
	 */
	public void onFinish() {

		for (MdrBuilder mdrBuilder : mdrBuilderMap.values()) {
			mdrBuilder.onFinishForDevice();
		}

		try {

			addAllFiles(imgFs);

			writeSrtFile(imgFs);
			writeMpsFile();

		} catch (FileNotWritableException e) {
			log.warn("Could not create gmapsupp file");
			System.err.println("Could not create gmapsupp file");
		} finally {
			Utils.closeFile(imgFs);
		}
	}

	/**
	 * Write the SRT file.
	 *
	 * @param imgFs The filesystem to create the SRT file in.
	 * @throws FileNotWritableException If it cannot be created.
	 */
	private void writeSrtFile(FileSystem imgFs) throws FileNotWritableException {
		for (Map.Entry<Integer, Sort> ent : sortMap.entrySet()) {
			Sort sort = ent.getValue();
			int familyId = ent.getKey();

			if (sort.getId1() == 0 && sort.getId2() == 0)
				return;

			try {
				ImgChannel channel = imgFs.create(String.format("%08d.SRT", familyId));
				SRTFile srtFile = new SRTFile(channel);
				srtFile.setSort(sort);
				srtFile.write();
			} catch (FileExistsException e) {
				// well it shouldn't exist!
				log.error("could not create SRT file as it exists already");
				throw new FileNotWritableException("already existed", e);
			}
		}
	}

	/**
	 * Write the MPS file.  The gmapsupp file will work without this, but it important if you want to include more than one
	 * map family and be able to turn them on and off separately.
	 */
	private void writeMpsFile() throws FileNotWritableException {
		try {
			mpsFile.sync();
		} catch (IOException e) {
			throw new FileNotWritableException("Could not finish write to MPS file", e);
		}
	}

	private MapBlock makeMapBlock(FileInfo info) {
		MapBlock mb = new MapBlock(info.getCodePage());
		mb.setMapNumber(info.getMapnameAsInt());
		mb.setHexNumber(info.getHexname());
		mb.setMapDescription(info.getDescription());
		mb.setAreaName(areaName != null ? areaName : "Area " + info.getMapname());

		mb.setSeriesName(info.getSeriesName());
		mb.setIds(info.getFamilyId(), info.getProductId());
		return mb;
	}

	private ProductBlock makeProductBlock(FileInfo info) {
		ProductBlock pb = new ProductBlock(info.getCodePage());
		pb.setFamilyId(info.getFamilyId());
		pb.setProductId(info.getProductId());
		pb.setDescription(info.getFamilyName());
		return pb;
	}

	private void addAllFiles(FileSystem outfs) {
		for (FileInfo info : files.values()) {

			switch (info.getKind()) {
			case IMG_KIND:
				addImg(outfs, info);
				addMpsEntry(info);
				break;
			case GMAPSUPP_KIND:
				addImg(outfs, info);
				addMpsFile(info);
				break;
			case APP_KIND:
			case TYP_KIND:
				addFile(outfs, info);
				break;
			default:
				break;
			}
		}
	}

	private void addImg(FileSystem outfs, FileInfo info) {
		FileCopier fc = new FileCopier(info.getFilename());
		List<SubFileInfo> subFiles = info.subFiles();

		for (SubFileInfo sf : subFiles) {
			try {
				ImgChannel chan = outfs.create(sf.getName());
				Syncable sync = fc.add(sf.getName(), chan);

				((FileLink)chan).link(sf, sync);
			} catch (FileExistsException e) {
				log.warn("Could not copy " + sf.getName(), e);
			}
		}
	}

	private void addFile(FileSystem outfs, FileInfo info) {
		String filename = info.getFilename();
		FileCopier fc = new FileCopier(filename);

		try {
			ImgChannel chan = outfs.create(createImgFilename(filename));
			((FileLink) chan).link(info.subFiles().get(0), fc.file(chan));
		} catch (FileExistsException e) {
			log.warn("Counld not copy " + filename, e);
		}
	}

	/**
	 * Add a complete pre-existing mps file to the mps file we are currently
	 * building for this gmapsupp.
	 * @param info The details of the gmapsupp file that we need to extract the
	 */
	private void addMpsFile(FileInfo info) {
		String name = info.getFilename();
		try (FileSystem fs = ImgFS.openFs(name)) {
			MpsFileReader mr = new MpsFileReader(fs.open(info.getMpsName(), "r"), info.getCodePage());
			for (MapBlock block : mr.getMaps())
				mpsFile.addMap(block);

			for (ProductBlock b : mr.getProducts())
				mpsFile.addProduct(b);
			mr.close();
		} catch (IOException e) {
			log.error("Could not read MPS file from gmapsupp", e);
		}
	}

	/**
	 * Add a single entry to the mps file.
	 * @param info The img file information.
	 */
	private void addMpsEntry(FileInfo info) {
		mpsFile.addMap(makeMapBlock(info));

		// Add a new product block if we have found a new product
		mpsFile.addProduct(makeProductBlock(info));
	}

	private MpsFile createMpsFile(FileSystem outfs) throws FileNotWritableException {
		try {
			ImgChannel channel = outfs.create("MAKEGMAP.MPS");
			return new MpsFile(channel);
		} catch (FileExistsException e) {
			// well it shouldn't exist!
			log.error("could not create MPS file as it already exists");
			throw new FileNotWritableException("already existed", e);
		}
	}


	/**
	 * Create a suitable filename for use in the .img file from the external
	 * file name.
	 *
	 * The external file name might look something like /home/steve/foo.typ
	 * or c:\maps\foo.typ and we need to take the filename part and make
	 * sure that it is no more than 8+3 characters.
	 *
	 * @param pathname The external filesystem path name.
	 * @return The filename part, will be restricted to 8+3 characters and all
	 * in upper case.
	 */
	private String createImgFilename(String pathname) {
		File f = new File(pathname);
		String name = f.getName().toUpperCase(Locale.ENGLISH);
		int dot = name.lastIndexOf('.');

		String base = name.substring(0, dot);
		if (base.length() > 8)
			base = base.substring(0, 8);

		String ext = name.substring(dot + 1);
		if (ext.length() > 3)
			ext = ext.substring(0, 3);

		return base + '.' + ext;
	}

	/**
	 * Create the output file.
	 *
	 * @return The gmapsupp file.
	 * @throws FileNotWritableException If it cannot be created for any reason.
	 */
	private FileSystem createGmapsupp() throws FileNotWritableException {

		// Create this file, containing all the sub files
		FileSystemParam params = new FileSystemParam();
		params.setMapDescription(overallDescription);
		params.setGmapsupp(true);
		params.setHideGmapsuppOnPC(hideGmapsuppOnPC);
		params.setProductVersion(productVersion);

		FileSystem outfs = ImgFS.createFs(Utils.joinPath(outputDir, GMAPSUPP), params);

		mpsFile = createMpsFile(outfs);
		mpsFile.setMapsetName(mapsetName);

		return outfs;
	}

	public void setCreateIndex(boolean create) {
		this.createIndex = create;
	}
}

/**
 * Copies files from the source img to the gmapsupp.
 *
 * Each sub file has to be copied separately to a different 'file'.  This class makes sure
 * that the source file is only opened once.
 */
class FileCopier {
	private final String filename;
	private FileSystem fs;
	private int refCount;

	public FileCopier(String filename) {
		this.filename = filename;
	}

	Syncable add(String name, ImgChannel fout) {
		refCount++;
		return () -> sync(name, fout);
	}

	Syncable file(ImgChannel fout) {
		return () -> sync(fout);
	}

	/**
	 * This version of sync() is used for single files.
	 */
	public void sync(ImgChannel fout) throws IOException {
		ImgChannel fin = new FileImgChannel(filename, "r");
		copyFile(fin, fout);
	}

	/**
	 * This version of sync is used for subfiles within a .img file.
	 * @param name The sub file name.
	 * @param fout Where to copy the file
	 */
	void sync(String name, ImgChannel fout) throws IOException {
		if (fs == null)
			fs = ImgFS.openFs(filename);

		copyFile(name, fout);

		refCount--;
		if (refCount <= 0) {
			fs.close();
		}
	}

	private void copyFile(String name, ImgChannel fout) throws IOException {
		try (ImgChannel fin = fs.open(name, "r")) {
			copyFile(fin, fout);
		}
	}

	private void copyFile(ImgChannel fin, ImgChannel fout) throws IOException {
		ByteBuffer buf = ByteBuffer.allocate(1024);
		while (fin.read(buf) > 0) {
			buf.flip();
			fout.write(buf);
			buf.compact();
		}
	}
}
