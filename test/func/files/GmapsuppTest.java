/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
/* Create date: 17-Feb-2009 */
package func.files;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import uk.me.parabola.imgfmt.Utils;
import uk.me.parabola.imgfmt.fs.DirectoryEntry;
import uk.me.parabola.imgfmt.fs.FileSystem;
import uk.me.parabola.imgfmt.fs.ImgChannel;
import uk.me.parabola.imgfmt.mps.MapBlock;
import uk.me.parabola.imgfmt.mps.MpsFileReader;
import uk.me.parabola.imgfmt.mps.ProductBlock;
import uk.me.parabola.imgfmt.sys.ImgFS;
import uk.me.parabola.mkgmap.main.Main;

import func.Base;
import func.lib.Args;
import func.lib.Outputs;
import func.lib.TestUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class GmapsuppTest extends Base {
	private static final String GMAPSUPP_IMG = "gmapsupp.img";

	@Test
	public void testBasic() throws IOException {
		File f = new File(GMAPSUPP_IMG);
		assertFalse("does not pre-exist", f.exists());

		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--gmapsupp",
				Args.TEST_RESOURCE_IMG + "63240001.img",
				Args.TEST_RESOURCE_IMG + "63240002.img"
		});

		assertTrue("gmapsupp.img is created", f.exists());

		FileSystem fs = openFs(GMAPSUPP_IMG);
		DirectoryEntry entry = fs.lookup("63240001.TRE");
		assertNotNull("first file TRE", entry);
		assertEquals("first file TRE size", getFileSize(Args.TEST_RESOURCE_IMG + "63240001.img", "63240001.TRE"), entry.getSize());

		entry = fs.lookup("63240002.TRE");
		assertNotNull("second file TRE", entry);
		assertEquals("second file TRE size", getFileSize(Args.TEST_RESOURCE_IMG + "63240002.img", "63240002.TRE"), entry.getSize());
	}

	/**
	 * Check the values inside the MPS file, when the family id etc is
	 * common to all files.
	 */
	@Test
	public void testMpsFile() throws IOException {
		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--gmapsupp",
				"--family-id=150",
				"--product-id=24",
				"--series-name=tst series",
				"--family-name=tst family",
				"--area-name=tst area",
				Args.TEST_RESOURCE_IMG + "63240001.img",
				Args.TEST_RESOURCE_IMG + "63240002.img"
		});

		MpsFileReader reader = getMpsFile();
		List<MapBlock> list = reader.getMaps();
		reader.close();
		assertEquals("number of map blocks", 2, list.size());

		// All maps will have the same parameters apart from map name here
		int count = 0;
		for (MapBlock map : list) {
			assertEquals("map number", 63240001 + count++, map.getMapNumber());
			assertEquals("family id", 150, map.getFamilyId());
			assertEquals("product id", 24, map.getProductId());
			assertEquals("series name", "tst series", map.getSeriesName());
			assertEquals("area name", "tst area", map.getAreaName());
			assertEquals("map description", "uk test " + count, map.getMapDescription());
		}
	}

	/**
	 * Test combining gmapsupp files.  The family id etc should be taken from
	 * the MPS file in the gmapsupp.
	 */
	@Test
	public void testCombiningSupps() throws IOException {
		TestUtils.registerFile("g1.img", "g2.img");
		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--gmapsupp",
				"--family-id=150",
				"--product-id=24",
				"--series-name=tst series",
				"--family-name=tst family",
				"--area-name=tst area",
				Args.TEST_RESOURCE_IMG + "63240001.img",
		});

		File f = new File("gmapsupp.img");
		f.renameTo(new File("g1.img"));

		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--gmapsupp",
				"--family-id=152",
				"--product-id=26",
				"--series-name=tst series 2",
				"--family-name=tst family 2",
				"--area-name=tst area 2",
				Args.TEST_RESOURCE_IMG + "63240002.img",
		});
		f.renameTo(new File("g2.img"));

		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--gmapsupp",
				"g1.img",
				"g2.img"
		});


		MpsFileReader reader = getMpsFile();
		List<MapBlock> list = reader.getMaps();
		assertEquals("number of map blocks", 2, list.size());

		for (MapBlock map : list) {
			if (map.getMapNumber() == 63240001) {
				assertEquals("family id", 150, map.getFamilyId());
				assertEquals("product id", 24, map.getProductId());
				assertEquals("series name", "tst series", map.getSeriesName());
				assertEquals("area name", "tst area", map.getAreaName());
				assertEquals("hex name", 63240001, map.getHexNumber());
				assertEquals("map description", "uk test 1", map.getMapDescription());
			} else if (map.getMapNumber() == 63240002) {
				assertEquals("family id", 152, map.getFamilyId());
				assertEquals("product id", 26, map.getProductId());
				assertEquals("series name", "tst series 2", map.getSeriesName());
				assertEquals("area name", "tst area 2", map.getAreaName());
				assertEquals("hex name", 63240002, map.getHexNumber());
				assertEquals("map description", "uk test 2", map.getMapDescription());
			} else {
				assertTrue("Unexpected map found", false);
			}
		}
	}

	/**
	 * Test the case where we are combining img files with different family
	 * and product ids.
	 */
	@Test
	public void testDifferentFamilies() throws IOException {
		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--gmapsupp",

				"--family-id=101",
				"--product-id=1",
				"--series-name=tst series1",
				Args.TEST_RESOURCE_IMG + "63240001.img",

				"--family-id=102",
				"--product-id=2",
				"--series-name=tst series2",
				Args.TEST_RESOURCE_IMG + "63240002.img"
		});

		MpsFileReader reader = getMpsFile();
		List<MapBlock> list = reader.getMaps();
		reader.close();
		assertEquals("number of map blocks", 2, list.size());

		// Directly check the family id's
		assertEquals("family in map1", 101, list.get(0).getFamilyId());
		assertEquals("family in map2", 102, list.get(1).getFamilyId());

		// Check more things
		int count = 0;
		for (MapBlock map : list) {
			count++;
			assertEquals("family in map" + count, 100 + count, map.getFamilyId());
			assertEquals("product in map" + count, count, map.getProductId());
			assertEquals("series name in map" + count, "tst series" + count, map.getSeriesName());
		}
	}

	/**
	 * The mps file has a block for each family/product in the map set.
	 */
	@Test
	public void testProductBlocks() throws IOException {
		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--gmapsupp",

				"--family-id=101",
				"--product-id=1",
				"--family-name=tst family1",
				"--series-name=tst series1",
				Args.TEST_RESOURCE_IMG + "63240001.img",

				"--family-id=102",
				"--product-id=2",
				"--family-name=tst family2",
				"--series-name=tst series2",
				Args.TEST_RESOURCE_IMG + "63240002.img"
		});

		MpsFileReader reader = getMpsFile();

		List<ProductBlock> products = reader.getProducts();
		Collections.sort(products, new Comparator<ProductBlock>() {
			public int compare(ProductBlock o1, ProductBlock o2) {
				if (o1.getFamilyId() == o2.getFamilyId())
					return 0;
				else if (o1.getFamilyId() > o2.getFamilyId())
					return 1;
				else return -1;
			}
		});

		ProductBlock block = products.get(0);
		assertEquals("product block first family", 101, block.getFamilyId());
		assertEquals("product block first product id", 1, block.getProductId());
		assertEquals("product block first family name", "tst family1", block.getDescription());
		
		block = products.get(1);
		assertEquals("product block second family", 102, block.getFamilyId());
		assertEquals("product block first product id", 2, block.getProductId());
		assertEquals("product block first family name", "tst family2", block.getDescription());
	}

	/**
	 * Make sure that if we have multiple maps in the same family, which after
	 * all is the common case, that we only get one product block.
	 */
	@Test
	public void testProductWithSeveralMaps() throws IOException {
		Main.main(new String[]{
						Args.TEST_STYLE_ARG,
						"--gmapsupp",

						"--family-id=101",
						"--product-id=1",
						"--family-name=tst family1",
						"--series-name=tst series1",
						Args.TEST_RESOURCE_IMG + "63240001.img",
						Args.TEST_RESOURCE_IMG + "63240002.img"
				});

		MpsFileReader reader = getMpsFile();
		assertEquals("number of map blocks", 2, reader.getMaps().size());
		assertEquals("number of product blocks", 1, reader.getProducts().size());
	}

	@Test
	public void testWithIndex() throws IOException {
		new File("osmmap_mdr.img").delete();
		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--gmapsupp",
				"--index",
				"--latin1",

				"--family-id=101",
				"--product-id=1",
				"--family-name=tst family1",
				"--series-name=tst series1",
				Args.TEST_RESOURCE_IMG + "63240001.img",
				Args.TEST_RESOURCE_IMG + "63240002.img"
		});

		assertFalse(new File("osmmap_mdr.img").exists());

		// All we are doing here is checking that the file was created and that it is
		// not completely empty.
		FileSystem fs = openFs(GMAPSUPP_IMG);
		ImgChannel r = fs.open("00000101.MDR", "r");
		r.position(2);
		ByteBuffer buf = ByteBuffer.allocate(1024);
		
		int read = r.read(buf);
		assertEquals(1024, read);

		buf.flip();
		byte[] b = new byte[3];
		buf.get(b, 0, 3);
		assertEquals('G', b[0]);
	}

	@Test
	public void testWithTwoIndexes() throws IOException {
		TestUtils.registerFile("osmmap_mdr.img", "osmmap.img", "osmmap.tbd", "osmmap.mdx");

		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--gmapsupp",
				"--index",
				"--tdbfile",
				"--latin1",

				"--family-id=101",
				"--product-id=1",
				"--family-name=tst family1",
				"--series-name=tst series1",
				Args.TEST_RESOURCE_IMG + "63240001.img",
				Args.TEST_RESOURCE_IMG + "63240002.img"
		});

		assertTrue(new File("osmmap_mdr.img").exists());

		// All we are doing here is checking that the file was created and that it is
		// not completely empty.
		FileSystem fs = openFs(GMAPSUPP_IMG);
		ImgChannel r = fs.open("00000101.MDR", "r");
		r.position(2);
		ByteBuffer buf = ByteBuffer.allocate(1024);

		int read = r.read(buf);
		assertEquals(1024, read);

		buf.flip();
		byte[] b = new byte[3];
		buf.get(b, 0, 3);
		assertEquals('G', b[0]);
	}

	/**
	 * If there are files in two (or more) families then there should be a MDR and SRT for each.
	 */
	@Test
	public void testTwoFamilyIndex() throws IOException {
		TestUtils.registerFile("osmmap_mdr.img", "osmmap.img", "osmmap.tbd", "osmmap.mdx");

		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--gmapsupp",
				"--index",
				"--latin1",

				"--family-id=101",
				"--product-id=1",
				"--family-name=tst family1",
				"--series-name=tst series1",
				Args.TEST_RESOURCE_OSM + "uk-test-1.osm.gz",
				"--family-id=202",
				"--family-name=tst family2",
				"--series-name=tst series2",
				Args.TEST_RESOURCE_OSM + "uk-test-2.osm.gz"
		});

		assertFalse(new File("osmmap_mdr.img").exists());

		// All we are doing here is checking that the file was created and that it is
		// not completely empty.
		FileSystem fs = openFs(GMAPSUPP_IMG);
		ImgChannel r = fs.open("00000101.MDR", "r");
		r.position(2);
		ByteBuffer buf = ByteBuffer.allocate(1024);
		int read = r.read(buf);
		assertEquals(1024, read);

		fs = openFs(GMAPSUPP_IMG);
		r = fs.open("00000202.MDR", "r");
		r.position(2);
		buf.clear();
		read = r.read(buf);
		assertEquals(1024, read);

		r = fs.open("00000202.SRT", "r");
		buf = ByteBuffer.allocate(512);
		read = r.read(buf);
		assertEquals(512, read);
		r = fs.open("00000101.SRT", "r");
		buf.clear();
		read = r.read(buf);
		assertEquals(512, read);
	}

	/**
	 * If no code page is given for the index, it is taken from the input files.
	 */
	@Test
	public void testImplicitCodePageIndex() throws IOException {
		TestUtils.registerFile("osmmap_mdr.img", "osmmap.img", "osmmap.tbd", "osmmap.mdx");

		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--code-page=1256",

				Args.TEST_RESOURCE_OSM + "uk-test-1.osm.gz",
		});

		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--gmapsupp",
				"--index",

				"63240001.img",
		});

		assertFalse(new File("osmmap_mdr.img").exists());

		FileSystem fs = openFs(GMAPSUPP_IMG);
		ImgChannel r = fs.open("00006324.MDR", "r");

		ByteBuffer buf = ByteBuffer.allocate(1024);
		buf.order(ByteOrder.LITTLE_ENDIAN);

		r.read(buf);
		assertEquals(1256, buf.getChar(0x15));
		assertEquals(0x010009, buf.getInt(0x17));
	}

	/**
	 * If there are mis-matching code-pages in the input files there should be a warning.
	 */
	@Test
	public void testWarningOnMismatchedCodePages() throws IOException {
		TestUtils.registerFile("osmmap.img");

		Main.main(new String[]{
				Args.TEST_STYLE_ARG,
				"--route",
				"--code-page=1256",
				Args.TEST_RESOURCE_OSM + "uk-test-1.osm.gz",

				"--latin1",
				Args.TEST_RESOURCE_OSM + "uk-test-2.osm.gz",
		});

		Outputs outputs = TestUtils.run(Args.TEST_STYLE_ARG,
				"--gmapsupp",
				"--index",

				"63240001.img",
				"63240002.img"
		);

		outputs.checkError("different code page");
	}

	private MpsFileReader getMpsFile() throws IOException {
		FileSystem fs = openFs(GMAPSUPP_IMG);
		MpsFileReader reader = new MpsFileReader(fs.open("MAKEGMAP.MPS", "r"));
		TestUtils.registerFile(reader);
		return reader;
	}

	private int getFileSize(String imgName, String fileName) throws IOException {
		FileSystem fs = ImgFS.openFs(imgName);
		try {
			return fs.lookup(fileName).getSize();
		} finally {
			Utils.closeFile(fs);
		}
	}
}
