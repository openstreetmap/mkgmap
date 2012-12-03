/*
 * Copyright (C) 2012.
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
package uk.me.parabola.mkgmap.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import uk.me.parabola.imgfmt.ExitException;
import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.mkgmap.CommandArgs;

/**
 * Ensure that the TYP file has the correct family and product id's as given by the
 * command line arguments.
 *
 * If they are correct, then returns the original name and nothing else is done.
 *
 * If they are not correct, then a new file is created which patches the id's
 * and its name is returned.
 */
class TypSaver implements MapProcessor {
	public String makeMap(CommandArgs args, String filename) {
		String outfilename = filename;

		// These are the family and product id's that are wanted.
		int familyId = args.get("family-id", CommandArgs.DEFAULT_FAMILYID);
		int productId = args.get("product-id", CommandArgs.DEFAULT_PRODUCTID);

		FileInputStream in = null;
		try {
			in = new FileInputStream(filename);
			byte[] buf = new byte[256];
			int n = in.read(buf);

			ByteBuffer buffer = ByteBuffer.wrap(buf);
			buffer.order(ByteOrder.LITTLE_ENDIAN);

			// Get the product and family id's that are actually in the supplied TYP file.
			int foundFamily = buffer.getChar(0x2f);
			int foundProduct = buffer.getChar(0x31);

			if (familyId != foundFamily || productId != foundProduct) {
				buffer.putChar(0x2f, (char) familyId);
				buffer.putChar(0x31, (char) productId);

				outfilename = makeOutName(filename);
				writeAlteredTyp(outfilename, in, buf, n);
			}

		} catch (IOException e) {
			throw new ExitException("TYP file cannot be opened or read: " + filename);
		} finally {
			Utils.closeFile(in);
		}
		return outfilename;
	}

	/**
	 * Write out the altered TYP file.
	 *
	 * @param outFilename The name to write to.
	 * @param in The input file. This has already had the first block read from it.
	 * @param buf The buffer holding the first block of the file. It is already modified, so just needs
	 * to be written out.
	 * @param n The number of characters in the first block. The minimum size of the TYP file is
	 * less than the buffer size.
	 */
	private void writeAlteredTyp(String outFilename, FileInputStream in, byte[] buf, int n) {
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(outFilename);
			do {
				out.write(buf, 0, n);
			} while ((n = in.read(buf)) > 0);
		} catch (IOException e) {
			throw new ExitException("Could not write temporary TYP file: " + outFilename);
		} finally {
			Utils.closeFile(out);
		}
	}

	/**
	 * Create a name for the patched file.
	 *
	 * We pre-pend a 'x' to the name part of the path.
	 *
	 * If the name is already 8+3 characters, then the name that appears inside the gmapsupp, will
	 * not have the added character and so it is possible to have internal files with the same name.
	 * I don't think this matters, since I don't think that the name is important, but I could be wrong.
	 *
	 * @param path The original name
	 * @return The modified name.
	 */
	private String makeOutName(String path) {
		File f = new File(path);
		File dir = f.getParentFile();

		String name = f.getName();

		File out = new File(dir, "x" + name);
		return out.getPath();
	}
}
