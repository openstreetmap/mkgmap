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

import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.srt.Sort;
import uk.me.parabola.imgfmt.app.srt.SortKey;
import uk.me.parabola.mkgmap.srt.SrtTextReader;

public class SortTest {

	private static final int LIST_SIZE = 100_000;
	private Sort sort;

	private void test() throws Exception {
		sort = SrtTextReader.sortForCodepage(1252);

		//testPairs();

		Charset charset = sort.getCharset();

		Random rand = new Random(22909278L);

		List<String> list = new ArrayList<>();

		for (int n = 0; n < LIST_SIZE; n++) {
			int len = rand.nextInt(6)+1;
			if (len < 2)
				len = rand.nextInt(5) + 2;
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

		list = Collections.unmodifiableList(list);

		compareKeysAndCollator(list);

		List<String> r2;
		List<String> r1;
		//r2 = sortWithCollator(list);
		r2 = sortWithKeys(list);
		//r1 = sortJavaCollator(list);
		r1 = sortWithJavaKeys(list);
		//r1 = sortWithICUKeys(list);

		for (int i = 0; i < LIST_SIZE; i++) {
			String s1 = r1.get(i);
			String s2 = r2.get(i);
			String mark = "";
			if (!s1.equals(s2))
				mark = "*";
			System.out.printf("%6d |%-10s |%-10s %s\n", i, s1, s2, mark);
		}
	}

	/**
	 * Test every pair of characters and make sure that if A&lt;B the B>A and if A=B then B=A.
	 */
	private void testPairs() {
		List<Label> labels = new ArrayList<>();
		for (int i = 0; i < 256; i++) {
			for (int j = 0; j < 256; j++) {
				char[] ch = new char[2];
				ch[0] = (char) i;
				ch[1] = (char) j;
				Label label = new Label(ch);
				labels.add(label);
			}
		}

		for (int i = 0; i < 256 * 256; i++) {
		for (int j = 0; j < 256 * 256; j++) {
			SortKey<Object> k1 = sort.createSortKey(null, labels.get(i));
			SortKey<Object> k2 = sort.createSortKey(null, labels.get(j));

			if (i == j) {
				if (k1.compareTo(k2) != 0)
					System.out.println("ERROR: k1!=k2 for " + i + "," + j);
				if (k2.compareTo(k1) != 0)
					System.out.println("ERROR: k2!=k1 for " + i + "," + j);
			} else {
				int r1 = k1.compareTo(k2);
				//if (r1 == 0)
				//	System.out.println("ERROR: k1==k2 for " + i + "," + j);
				int r2 = k2.compareTo(k1);
				if (r1 != -r2)
					System.out.println("ERROR: not commutative for " + i + "," + j );
			}
		}
		}
	}

	private void compareKeysAndCollator(List<String> list) {
		List<String> list1 = sortWithKeys(list);
		List<String> list2 = sortWithCollator(list);

		list1.equals(list2);
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
		// Reject low characters most of the time
		return (ch < 0x20 && rand.nextInt(100) < 95);
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
		List<String> ret = new ArrayList<>(list);
		long start = System.currentTimeMillis();
		Collections.sort(ret, sort.getCollator());
		long end = System.currentTimeMillis();
		System.out.println("time coll: " + (end - start) + "ms");
		//for (String s: list) {
		//	System.out.println(s);
		//}
		return ret;
	}

	private List<String> sortWithJavaKeys(List<String> list) {

		long start = System.currentTimeMillis();
		List<CollationKey> keys = new ArrayList<>();
		Collator jcol;
		// jcol = Collator.getInstance(Locale.ENGLISH);
		try {
			jcol = new RuleBasedCollator(getRules());
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

	private String getRules() {
		return "='\u0008'='\u000e'='\u000f'='\u0010'='\u0011'='\u0012'='\u0013'='\u0014'='\u0015'='\u0016'"
				+ "='\u0017' ='\u0018' = '\u0019' ='\u001a' ='\u001b'= '\u001c' ='\u001d'= '\u001e'= '\u001f' "
				+ "='\u007f' ='\u00ad'"
				+ ", '\u0001', '\u0002', '\u0003', '\u0004' ,'\u0005' ,'\u0006', '\u0007'"
				+ "< '\u0009' < '\n' < '\u000b' < '\u000c' < '\r' < '\u0020','\u00a0'"
				+ "< '_' < '-' < '–' < '—' < '\u002c' < '\u003b' < ':' < '!' < '¡' < '?' < '¿'"
				+ "< '.' < '·' "
				//+"&'·' < \\' "  // ICU
				+ "&'·' < ''' "  // java 7
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

	//private List<String> sortWithICUKeys(List<String> list) throws Exception {
	//	long start = System.currentTimeMillis();
	//	List<com.ibm.icu.text.CollationKey> keys = new ArrayList<>();
	//	com.ibm.icu.text.RuleBasedCollator jcol = new com.ibm.icu.text.RuleBasedCollator(getRules());
	//	System.out.println(jcol.getRules(true));
	//	for (String s : list) {
	//		com.ibm.icu.text.CollationKey key = jcol.getCollationKey(s);
	//		keys.add(key);
	//	}
	//	Collections.sort(keys);
	//
	//	long end = System.currentTimeMillis();
	//
	//	List<String> ret = new ArrayList<>();
	//	for (com.ibm.icu.text.CollationKey key : keys) {
	//		ret.add(key.getSourceString());
	//	}
	//	System.out.println("time ICU keys: " + (end - start) + "ms");
	//	return ret;
	//}

	public static void main(String[] args) throws Exception {
		(new SortTest()).test();
	}
}
