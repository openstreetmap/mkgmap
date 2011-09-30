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
package uk.me.parabola.imgfmt.app.mdr;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * The string table. This is not used by the device.
 *
 * There is a compressed and non-compressed version of this section.
 * We are starting with the regular string version.
 *
 * @author Steve Ratcliffe
 */
public class Mdr15 extends MdrSection {
	private final OutputStream stringFile;
	private int nextOffset;

	private Map<String, Integer> strings = new HashMap<String, Integer>();
	private final Charset charset;
	private final File tempFile;

	public Mdr15(MdrConfig config) {
		setConfig(config);

		charset = config.getSort().getCharset();

		try {
			tempFile = File.createTempFile("strings", null, config.getOutputDir());
			tempFile.deleteOnExit();

			stringFile = new BufferedOutputStream(new FileOutputStream(tempFile), 64 * 1024);

			// reserve the string at offset 0 to be the empty string.
			stringFile.write(0);
			nextOffset = 1;

		} catch (IOException e) {
			throw new ExitException("Could not create temporary file");
		}
	}

	public int createString(String str) {
		Integer offset = strings.get(str);
		if (offset != null)
			return offset;

		int off;
		try {
			off = nextOffset;

			byte[] bytes = str.getBytes(charset);
			stringFile.write(bytes);
			stringFile.write(0);

			// Increase offset for the length of the string and the null byte
			nextOffset += bytes.length + 1;
		} catch (IOException e) {
			// Can't convert, return empty string instead.
			off = 0;
		}

		strings.put(str, off);
		return off;
	}

	/**
	 * Tidy up after reading files.
	 * Close the temporary file, and release the string table which is no longer
	 * needed.
	 */
	public void releaseMemory() {
		strings = null;
		try {
			stringFile.close();
		} catch (IOException e) {
			throw new MapFailedException("Could not close string temporary file");
		}
	}

	public void writeSectData(ImgFileWriter writer) {
		FileInputStream stream = null;
		try {
			stream = new FileInputStream(tempFile);
			FileChannel channel = stream.getChannel();
			ByteBuffer byteBuffer = channel.map(MapMode.READ_ONLY, 0, channel.size());
			writer.put(byteBuffer);
		} catch (IOException e) {
			throw new ExitException("Could not write string section of index");
		} finally {
			Utils.closeFile(stream);
		}
	}

	public int getItemSize() {
		// variable sized records.
		return 0;
	}

	/**
	 * The meaning of number of items for this section is the largest string
	 * offset possible.  We are taking the total size of the string section
	 * for this.
	 */
	public int getPointerSize() {
		return numberToPointerSize(nextOffset);
	}

	/**
	 * There is no use for this as the records are not fixed length.
	 *
	 * @return Always zero, could return the number of strings.
	 */
	protected int numberOfItems() {
		return 0;
	}
}
