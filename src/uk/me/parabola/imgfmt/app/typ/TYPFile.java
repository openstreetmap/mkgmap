/*
 * Copyright (C) 2006 Steve Ratcliffe
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
 * Create date: 03-Dec-2006
 * Change: Thomas Lußnig <gps@suche.org>
 */
package uk.me.parabola.imgfmt.app.typ;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import uk.me.parabola.imgfmt.app.BufferedImgFileReader;
import uk.me.parabola.imgfmt.app.BufferedImgFileWriter;
import uk.me.parabola.imgfmt.app.ImgFile;
import uk.me.parabola.imgfmt.app.Writeable;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.log.Logger;

/**
 * The TYP file.
 *
 * @author Thomas Lußnig
 */
public class TYPFile extends ImgFile {
	private static final Logger log = Logger.getLogger(TYPFile.class);

	private final TYPHeader header = new TYPHeader();

	private final List<BitmapImage> images = new LinkedList<BitmapImage>();
	private final List<PointInfo> pointInfo = new LinkedList<PointInfo>();
	private final List<DrawOrder> drawOrder = new LinkedList<DrawOrder>();

	public TYPFile(ImgChannel chan, boolean write) {
		setHeader(header);
		if (write) {
			setWriter(new BufferedImgFileWriter(chan));
			position(TYPHeader.HEADER_LEN);
		} else {
			setReader(new BufferedImgFileReader(chan));
			header.readHeader(getReader());
		}
	}

	public void setCodePage(char code) {
		header.setCodePage(code);
	}

	public void setFamilyId(char code) {
		header.setFamilyId(code);
	}

	public void setProductId(char code) {
		header.setProductId(code);
	}

	public void write() {
		// HEADER_LEN => 1. Image
		Collections.sort(images, BitmapImage.comperator());

		getWriter().position(TYPHeader.HEADER_LEN);

		int pos = getWriter().position();
		header.getPointData().setPosition(pos);

		for (Writeable w : images)
			w.write(getWriter());
		int len = getWriter().position() - pos;
		header.getPointData().setSize(len);

		if (len < 0x100)
			header.getPointIndex().setItemSize((char) 3);
		pos = getWriter().position();
		for (PointInfo w : pointInfo)
			w.write(getWriter(), header.getPointData().getSize());
		header.getPointIndex().setSize(getWriter().position() - pos);

		pos = getWriter().position();
		for (Writeable w : drawOrder)
			w.write(getWriter());
		header.getShapeStacking().setSize(getWriter().position() - pos);

	}

	public void writePost() {
		log.debug("syncing TYP file");
		position(0);
		getHeader().writeHeader(getWriter());
	}

	public static BitmapImage parseXpm(int type, int subtype, int day, String xpm) {
		try {
			BufferedReader br = new BufferedReader(new StringReader(xpm));
			String[] header = br.readLine().split(" ");

			int w = Integer.parseInt(header[0]);
			int h = Integer.parseInt(header[1]);
			int c = Integer.parseInt(header[2]);
			int cpp = Integer.parseInt(header[3]);

			Map<String, Rgb> colors = new HashMap<String, Rgb>();
			for (int i = 0; i < c; i++) {
				String l = br.readLine();
				String[] ci = l.split("\t");
				int r = Integer.parseInt(ci[1].substring(3, 5), 16);
				int g = Integer.parseInt(ci[1].substring(5, 7), 16);
				int b = Integer.parseInt(ci[1].substring(7, 9), 16);
				colors.put(ci[0], new Rgb(r, g, b, (byte) i));
			}
			
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < h; i++) sb.append(br.readLine());
			return new BitmapImage((byte) type, (byte) subtype, (byte) day, w, colors, cpp,
					sb.toString());
		} catch (IOException e) {
			log.error("failed to parse bitmap", e);
			return null;
		}
	}

	public void createDrawOrder(byte typ, byte sub) {
		drawOrder.add(new DrawOrder((char) (typ | sub << 8), (char) 0, (byte) 0));
	}

	public List<BitmapImage> getImages() {
		return images;
	}

	public List<PointInfo> getPointInfo() {
		return pointInfo;
	}

	public List<DrawOrder> getDrawOrder() {
		return drawOrder;
	}
}
