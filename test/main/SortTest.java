/*
 * Copyright (C) 2014.
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
package main;

import java.nio.charset.Charset;
import java.text.CollationKey;
import java.text.Collator;
import java.text.ParseException;
import java.text.RuleBasedCollator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;
import uk.me.parabola.mkgmap.srt.SrtTextReader;

/**
 * Test to compare sorting results and timings between sort keys and collator.
 *
 * Also have tested against java7 RuleBasedCollator and the ICU one.
 *
 * In general our implementation is fastest by a long way; key based sort 3 times faster, collation
 * based sort even more so.  The java collator does not result in the same sort as using sort keys.
 *
 * I also tried out the ICU collation with mixed results. Could not get the correct desired results with
 * it.  It was not faster than our implementation for a 1252 cp sort.
 */
public class SortTest {

	private static final int LIST_SIZE = 500000;
	private Sort sort;
	private boolean time;
	private boolean fullOutput;
	private boolean quiet;
	private boolean unicode;

	private void test() throws Exception {
		sort = SrtTextReader.sortForCodepage(unicode? 65001: 1252);

		//testPairs();

		Charset charset = sort.getCharset();

		Random rand = new Random(21909278L);

		List<String> list = createList(rand, charset);

		if (time) {
			// Run a few times without output, to warm up
			compareLists(sortWithKeys(list), sortWithKeys(list));
			compareLists(sortWithCollator(list), sortWithCollator(list));
			compareLists(sortWithJavaKeys(list), sortWithJavaKeys(list));
			compareLists(sortWithJavaCollator(list), sortWithJavaCollator(list));
			// re-create the list to make sure it wasn't too optimised to the data
			list = createList(rand, charset);
		}

		System.out.println("Compare key sort and collator sort");
		int n = compareLists(sortWithKeys(list), sortWithCollator(list));
		System.out.println("N errors " + n);

		if (!unicode) {
			System.out.println("Compare our sort with java sort");
			n = compareLists(sortWithKeys(list), sortWithJavaKeys(list));
			System.out.println("N errors " + n);
		}

		if (time) {
			System.out.println("Compare java keys with java collator");
			n = compareLists(sortWithJavaKeys(list), sortWithJavaCollator(list));
			System.out.println("N errors " + n);
		}
	}

	private List<String> createList(Random rand, Charset charset) {
		List<String> list = new ArrayList<>();

		for (int n = 0; n < LIST_SIZE; n++) {
			int len = rand.nextInt(6)+1;
			if (len < 2)
				len = rand.nextInt(5) + 2;

			if (unicode) {
				char[] c = new char[len];
				for (int i = 0; i < len; i++) {
					int ch;
					do {
						if (rand.nextInt(10) > 6)
							ch = rand.nextInt(6 * 256);
						else
							ch = rand.nextInt(256);
					} while (reject(rand, ch));

					c[i] = (char) ch;
				}
				list.add(new String(c));
			} else {
				byte[] b = new byte[len];
				for (int i = 0; i < len; i++) {

					int ch;
					do {
						ch = rand.nextInt(256);
						// reject unassigned. Also low chars most of the time
					} while (reject(rand, ch));

					b[i] = (byte) ch;
				}
				list.add(new String(b, charset));
			}
		}

		list = Collections.unmodifiableList(list);
		return list;
	}

	private int compareLists(List<String> r1, List<String> r2) {
		int count = 0;
		for (int i = 0; i < LIST_SIZE; i++) {
			String s1 = r1.get(i);
			String s2 = r2.get(i);
			String mark = "";
			if (!s1.equals(s2)) {
				mark = "*";
				count++;
			}

			if (fullOutput || (!mark.isEmpty() && !quiet))
				System.out.printf("%6d |%-10s |%-10s %s\n", i, s1, s2, mark);
		}
		return count;
	}

	private boolean reject(Random rand, int ch) {
		switch (ch) {
		case 0:
		case ' ':
		case '\n':case '\r':
		case 0x81:case 0x8d:case 0x8f:
		case 0x90:case 0x9d:
			return true;
		}
		switch (Character.getType(ch)) {
		case Character.UNASSIGNED:
			return true;
		case Character.CONTROL:
			return true;
		}

		// Reject low characters most of the time
		if (ch < 0x20 && rand.nextInt(100) < 95)
			return true;
		if (ch > 255 && rand.nextInt(100) > 99)
			return true;
		return false;
	}

	private List<String> sortWithKeys(List<String> list) {
		long start = System.currentTimeMillis();
		List<SortKey<String>> keys = new ArrayList<>();
		for (String s : list) {
			SortKey<String> key = sort.createSortKey(s, s);
			keys.add(key);
		}
		Collections.sort(keys);

		long end = System.currentTimeMillis();

		List<String> ret = new ArrayList<>();

		for (SortKey<String> key : keys) {
			ret.add(key.getObject());
		}
		System.out.println("time keys: " + (end-start) + "ms");
		return ret;
	}

	private List<String> sortWithCollator(List<String> list) {
		long start = System.currentTimeMillis();
		List<String> ret = new ArrayList<>(list);
		Collections.sort(ret, sort.getCollator());
		System.out.println("time coll: " + (System.currentTimeMillis() - start) + "ms");
		return ret;
	}

	private List<String> sortWithJavaKeys(List<String> list) {

		long start = System.currentTimeMillis();
		List<CollationKey> keys = new ArrayList<>();
		Collator jcol;
		try {
			jcol = new RuleBasedCollator(getRules(false));
		} catch (ParseException e) {
			e.printStackTrace();
			return null;
		}
		for (String s : list) {
			CollationKey key = jcol.getCollationKey(s);
			keys.add(key);
		}
		Collections.sort(keys);

		long end = System.currentTimeMillis();

		List<String> ret = new ArrayList<>();
		for (CollationKey key : keys) {
			ret.add(key.getSourceString());
		}
		System.out.println("time J keys: " + (end - start) + "ms");
		return ret;
	}

	private List<String> sortWithJavaCollator(List<String> list) {

		long start = System.currentTimeMillis();

		List<String> out = new ArrayList<>(list);
		Collator jcol;
		try {
			jcol = new RuleBasedCollator(getRules(false));
			jcol.setStrength(Collator.TERTIARY);
		} catch (ParseException e) {
			e.printStackTrace();
			return null;
		}

		Collections.sort(out, jcol);

		System.out.println("time J collator: " + (System.currentTimeMillis() - start) + "ms");
		return out;
	}

	private String getRules(boolean forICU) {
		return "='\u0008'='\u000e'='\u000f'='\u0010'='\u0011'='\u0012'='\u0013'='\u0014'='\u0015'='\u0016'"
				+ "='\u0017' ='\u0018' = '\u0019' ='\u001a' ='\u001b'= '\u001c' ='\u001d'= '\u001e'= '\u001f' "
				+ "='\u007f' ='\u00ad'"
				+ ", '\u0001', '\u0002', '\u0003', '\u0004' ,'\u0005' ,'\u0006', '\u0007'"
				+ "< '\u0009' < '\n' < '\u000b' < '\u000c' < '\r' < '\u0020','\u00a0'"
				+ "< '_' < '-' < '–' < '—' < '\u002c' < '\u003b' < ':' < '!' < '¡' < '?' < '¿'"
				+ "< '.' < '·' "
				+ ((forICU)? "< \\' ": "< ''' ")
				+ "< '‘' < '’' < '‚' < '‹' < '›' < '“' < '”' < '„' < '«' < '»' "
				+ " < '\"' "
				+ "< '“' < '”' < '„' < '«'< '»' < '(' < ')' "
				+ "< '[' < ']' < '{' < '}' < '§' < '¶' < '@' < '*' < '/' < '\\' < '&' < '#' < '%'"
				+ "< '‰' < '†' < '‡' < '•' < '`' < '´' < '^' < '¯' < '¨' < '¸' < 'ˆ' < '°' < '©' < '®'"
				+ "< '+' < '±' < '÷' < '×' < '\u003c' < '\u003d' < '>' < '¬' < '|' < '¦' < '~' ; '˜' <  '¤'"
				+ "< '¢' < '$' < '£' < '¥' < '€' < 0 < 1,¹ < 2,² < 3,³ < 4 < 5 < 6 < 7 < 8 < 9"
				+ "< a,ª,A ; á,Á ; à,À ; â,Â ; å,Å ; ä,Ä ; ã,Ã"
				+ "< b,B"
				+ "< c,C ; ç,Ç"
				+ "< d,D ; ð,Ð"
				+ "< e,E ; é,É ; è,È ; ê,Ê ; ë,Ë"
				+ "< f,F"
				+ "< ƒ"
				+ "< g,G"
				+ "< h,H"
				+ "< i,I ; í,Í ; ì,Ì ; î,Î ; ï,Ï"
				+ "< j,J"
				+ "< k,K"
				+ "< l,L"
				+ "< m,M"
				+ "< n,N ; ñ,Ñ"
				+ "< o,º,O ; ó,Ó ; ò,Ò ; ô,Ô ; ö,Ö ; õ,Õ ; ø,Ø"
				+ "< p,P"
				+ "< q,Q"
				+ "< r,R"
				+ "< s,S ; š,Š"
				+ "< t,T"
				+ "< u,U ; ú,Ú ; ù,Ù ; û,Û ; ü,Ü"
				+ "< v,V"
				+ "< w,W"
				+ "< x,X"
				+ "< y,Y ; ý,Ý ; ÿ,Ÿ"
				+ "< z,Z ; ž,Ž"
				+ "< þ,Þ"
				+ "< µ"
				+ "&'1/4'=¼  &'1/2'=½  &'3/4'=¾"
				+ "&ae = æ &AE = Æ &ss = ß &OE= Œ  &oe= œ  &TM = ™  &'...' = … "
				;
	}

	public static void main(String[] args) throws Exception {
		SortTest sortTest = new SortTest();
		for (String arg : args) {
			switch (arg) {
			case "--time":
				sortTest.time = true;
				break;
			case "--full":
				sortTest.fullOutput = true;
				break;
			case "--quiet":
				sortTest.quiet = true;
				break;
			case "--unicode":
				sortTest.unicode = true;
				break;
			}
		}
		sortTest.test();
	}
}
