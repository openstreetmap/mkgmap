/*
 * Copyright (C) 2017.
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
package uk.me.parabola.imgfmt.app.dem;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import uk.me.parabola.imgfmt.app.ImgFileWriter;

/**
 * This keeps the bit stream data and header info for a single DEM tile.
 * A tile contains the height values for a number of points organised in a width * height matrix.
 * The values of the matrix are not stored directly. Instead, a compression algorithm is used to reduce
 * the size of the needed bit stream. The algorithm uses different methods, mainly a delta encoding, run length encoding
 * and a variable number of bits and encoding methods to store the actual value.
 * The actually used methods are not stored, instead they are predicted from previously stored values (if any) and header data. 
 * Both the encoder and decoder have to implement exactly the same prediction algorithm.
 * As of 2017-12-13 only a subset of the algorithms used by Garmin is understood, but it seems to be enough to store valid data.
 * 
 * This code is based on the program and documentation created by Frank Stinner. 
 * 
 * @author Gerd Petermann
 *
 */
public class DEMTile {
	private final DEMSection section;
	private ByteArrayOutputStream bits;
	private int[] heights;
	private final int height;
	private final int width;
	private int offset;       		// offset from section.dataOffset2
	private final int baseHeight;		// base or minimum height in this tile 
	private final int maxDeltaHeight;	// delta between max height and base height
	private final byte encodingType;  // seems to determine how the highest values are displayed 

	private int bitPos;
	private byte currByte;
	private int currPlateauTablePos; // current position in plateau tables

	// fields used for debugging
	private final int tileNumberLat;
	private final int tileNumberLon;

	private enum EncType {
		HYBRID, LEN
	}

	private enum WrapType {
		WRAP_0, WRAP_1, WRAP_2
	}
	
	private enum CalcType {
		CALC_P_LEN, CALC_STD, CALC_PLATEAU_ZERO, CALC_PLATEAU_NON_ZERO
	}

	static final int[] plateauUnit = { 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 8, 8, 8, 8, 16, 16, 32, 32, 64, 64, 128 };
	static final int[] plateauBinBits = { 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 5, 5, 6, 6, 7, 8 };

	public DEMTile (DEMSection parent, int col, int row, int width, int height, int[] realHeights) {
		this.section = parent;
		this.width = width;
		this.height = height;
		this.tileNumberLon = col;
		this.tileNumberLat = row;
		
		// check values in matrix
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int h : realHeights) {
			if (h > max)
				max = h;
			if (h < min)
				min = h;
		}
		if (min == Integer.MAX_VALUE) {
			// all values are invalid -> don't encode anything
			encodingType = 0; // not used
		} else if (max == Integer.MAX_VALUE) {
			// some values are invalid
			encodingType = 2; // don't display highest value 
			max++;
		} else {
			// all height values are valid
			encodingType = 0;
		}
		this.baseHeight = min;
		this.maxDeltaHeight = max - min;
		if (min == max) {
			return; // all heights equal 
		}
		if (realHeights != null)
			createBitStream(realHeights);
	}
	
	public void createBitStream(int[] realHeights) {
		bits = new ByteArrayOutputStream(128); 
		heights = new int[realHeights.length];
		// normalise the height matrix
		for (int i = 0; i < realHeights.length; i++) {
			if (realHeights[i] == Integer.MAX_VALUE)
				heights[i] = maxDeltaHeight;
			else 
				heights[i] = (realHeights[i] - baseHeight);
		}
		// all values in heights are now expected to be between 0 .. maxDeltaHeight
		encodeDeltas();
		// cleanup 
		heights = null;
	}
	
	private void addBit(boolean bit) {
		if (bit) {
			currByte |= 1 << (7-bitPos);
		}
		bitPos++;
		if (bitPos > 7) {
			bitPos = 0;
			bits.write(currByte);
		}
	}
	
	/**
	 * The main loop to calculate the bit stream data.
	 */
	private void encodeDeltas() {
		CalcType ct = null;
		int pos = 0;
		ValPredicter encStandard = new ValPredicter(CalcType.CALC_STD, maxDeltaHeight);
		ValPredicter encPlateauF0 = new ValPredicter(CalcType.CALC_PLATEAU_ZERO, maxDeltaHeight);
		ValPredicter encPlateauF1 = new ValPredicter(CalcType.CALC_PLATEAU_NON_ZERO, maxDeltaHeight);
		ValPredicter encoder = null;
		ValPredicter lastEncoder = null;
		while (pos < heights.length) {
			int n = pos % width;
			int m = pos / height;
			int hUpper = getHeight(n, m - 1);
			int hLeft = getHeight(n - 1, m);
			int dDiff = hUpper - hLeft;
			if (lastEncoder != null && lastEncoder.type == CalcType.CALC_P_LEN) {
				encoder = (dDiff == 0) ? encPlateauF0 : encPlateauF1; 
			} else if (dDiff == 0) {
				ct = CalcType.CALC_P_LEN;
				int pLen = calcPlateauLen(n, m);
				writePlateauLen(pLen, n);
				pos += pLen;
				lastEncoder = encoder;
				continue;
			} else {
				encoder = encStandard;
			}
			ct = encoder.type;
			encoder.setDDiff(dDiff);
			int v;
			int h = getHeight(n, m);
			if (ct == CalcType.CALC_STD) {
				int predict;
				int hUpLeft = getHeight(n-1, m-1);
				int hdiffUp = hUpper- hUpLeft;
				int sgnDDiff = Integer.signum(dDiff);
				if (sgnDDiff == 0) {
					throw new RuntimeException("error: unexpected 0 value in sgnDiff"); 
				}
				if (hdiffUp >= maxDeltaHeight - hLeft) {
					predict = -1;
				} else if (hdiffUp <= -hLeft) {
					predict = 0; 
				} else {
					predict = hLeft + hdiffUp;
				}
				v = predict - h / sgnDDiff;
			} else {
				// platea follower: predicted value is upper height 
				v = h - hUpper;
			}
			encoder.write(v);
			lastEncoder = encoder;
		}
	}

	/**
	 * Add the bits for a given plateau length using the current value of lastTablePos 
	 * @param pLen
	 */
	private void writePlateauLen(int pLen, int col) {
		int len = pLen;
		int x = col;
		while (len > 0) {
			int unit = plateauUnit[currPlateauTablePos++];
			len -= unit;
			addBit(true);
			x += unit;
			if (x > width) 
				currPlateauTablePos--;
			if (x >= width)
				return;
		}
		if (currPlateauTablePos > 0)
			currPlateauTablePos--;
		addBit(false); // separator bit
		if (len > 0) {
			writeValAsBin(len, plateauBinBits[currPlateauTablePos]);
		}
	}

	/**
	 * Write an unsigned binary value with the given number of bits, MSB first. 
	 * @param val
	 * @param numBits
	 */
	private void writeValAsBin(int val, int numBits) {
		if (numBits == 0 && val == 0)
			return;
		
		int t = 1 << (numBits - 1);
		if (val >= t << 1)
			throw new RuntimeException("Number too big for binary encoding with " + numBits + " bits:" + val);
		while (t > 0) {
			addBit((val & t) != 0);
			t >>= 1;
		}
	}

	/**
	 * Write a length encoded value as a sequence of 0-bits followed by a 1-bit. 
	 * @param val
	 */
	private void writeValAsNumberOfZeroBits(int val) {
		for (int i = 0; i < val; i++)
			addBit(false);
		addBit(true); // terminating 1-bit
	}

	/**
	 * Write an unsigned binary value with the given number of bits, MSB first. 
	 * @param val
	 * @param hunit
	 * @param type 
	 */
	private void writeValHybrid(int val, int hunit, int maxZeroBits) {
		assert hunit > 0;
		assert Integer.bitCount(hunit) == 1;
		int numBits = Integer.numberOfTrailingZeros(hunit);
		int binPart;
		int lenPart;
		if (val > 0) {
			binPart = (val - 1) % hunit;
			lenPart = (val - 1 - binPart) / hunit;
		} else {
			binPart = -val % hunit;
			lenPart = (-val - binPart) / hunit;
		}
		if (lenPart <= maxZeroBits) {
			writeValAsNumberOfZeroBits(lenPart); // write length encoded part
			writeValAsBin(binPart, numBits); // write binary encoded part
			addBit(val > 0); // sign bit, 1 means positive
		} else {
			writeValBigBin(val, maxZeroBits);
		}
	}

	/**
	 * Write a signed binary value with the given number of bits, MSB first, sign bit last 
	 * @param val
	 * @param numBits number of bits including sign bit
	 */
	private void writeValBigBin (int val, int numZeroBits) {
		// signal big bin by writing an invalid number of zero bits
		writeValAsNumberOfZeroBits(numZeroBits);
		int bits = getMaxLengthZeroBits(maxDeltaHeight);
		if (val < 0)
			writeValAsBin(-val - 1, bits - 1);
		else
			writeValAsBin(val - 1, bits - 1);
		addBit(val <= 0); // sign bit, 0 means positive
	}

	/**
	 * A plateau is a sequence of equal delta values. Calculate the length.
	 * @param col current column
	 * @param row current row
	 * @return the length of the plateau, which might be zero.
	 */
	private int calcPlateauLen(int col, int row) {
		int len = 0;
		int v = getHeight(col, row);
		while (col + len < width) {
			if (v == getHeight(col + len, row)) {
				++len;
			} else 
				break;
			
		}
		return len;
	}

	private int getHeight(int col, int row) {
		if (heights == null)
			return 0;
		if (row < 0) {
			// virtual 1st row
			return 0;
		}
		if (col < 0) {
			return row == 0 ? 0 : heights[(row - 1) * width]; 
		}
		return heights[col + row * width];
	}

	public void writeHeader(ImgFileWriter writer) {
		writer.putN(section.getOffsetSize(), offset);
		if (section.getBaseSize() == 1)
			writer.put((byte) baseHeight);
		else 
			writer.putChar((char) baseHeight);
		writer.putN(section.getDifferenceSize(), maxDeltaHeight);
		if (section.isHasExtra())
			writer.put(encodingType);
	}


	public void writeBitStreamData(ImgFileWriter writer) {
		if (bits != null) {
			writer.put(bits.toByteArray());
		}
	}
	
	/**
	 * 
	 * @param oldsum
	 * @param elemcount
	 * @param newdata
	 * @return
	 */
	private static int evalSumSpec(int oldsum, int elemcount, int newdata) {
        /*
        D < -2 – (ls + 3*k)/2   -1 – ls – k	
        D < 0 – (ls + k)/2      2*(d + k) + 3	
        D < 2 – (ls – k)/2      2*d – 1	
        D < 4 – (ls – 3*k)/2    2*(d – k) - 5	
                                1 – ls + k	
      */

		int v = 0;

		if (newdata < -2 - ((oldsum + 3 * elemcount) >> 1)) {
			v = -1 - oldsum - elemcount;
		} else if (newdata < -((oldsum + elemcount) >> 1)) {
			v = 2 * (newdata + elemcount) + 3;
		} else if (newdata < 2 - ((oldsum - elemcount) >> 1)) {
			v = 2 * newdata - 1;
		} else if (newdata < 4 - ((oldsum - 3 * elemcount) >> 1)) {
			v = 2 * (newdata - elemcount) - 5;
		} else {
			v = 1 - oldsum + elemcount;
		}
//		System.out.println(oldsum + " " + elemcount + " " + newdata + " -> " + v);
		return v;
	}


	/**
	 * This class keeps statistics about the previously encoded values and tries to predict the next value.
	 * It also calculates the encoding method to use. 
	 * Based on findings of Frank Stinner. 
	 */
	private class ValPredicter {
		private EncType encType;
		private WrapType wrapType;
		private int SumH;
		private int SumL;
		private int ElemCount;
		private int hunit;
		private final CalcType type;
		private final int unitDelta;
		private int dDiff;
		private final int maxZeroBits;
		
		public ValPredicter(CalcType type, int maxHeight) {
			super();
			this.type = type;
			int numZeroBits = getMaxLengthZeroBits(maxHeight);
			if (type == CalcType.CALC_PLATEAU_NON_ZERO || type == CalcType.CALC_PLATEAU_ZERO)
				--numZeroBits;
			maxZeroBits = numZeroBits;
			
			unitDelta = Math.max(0, maxHeight - 0x5f) / 0x40; // TODO what does it mean? 
			encType = EncType.HYBRID;
			wrapType = WrapType.WRAP_0;
			hunit = getStartHUnit(maxHeight);
		}

		private int wrap(int v) {
			return v; // TODO
		}
		
		public void write(int val) {
			int wrapped = wrap(val);
			int delta1 = processVal(wrapped);
			int delta2;
			if (wrapType == WrapType.WRAP_0)
				delta2 = delta1;
			else if (wrapType == WrapType.WRAP_1)
				delta2 = 1 - delta1;
			else delta2 = -delta1;
			if (encType == EncType.HYBRID) {
				writeValHybrid(delta2, hunit, maxZeroBits);
			} else {
				assert delta2 >= 0;
				if (delta2 > maxZeroBits) { 
					writeValBigBin(delta2, maxZeroBits);
				} else { 
					writeValAsNumberOfZeroBits(delta2);
				}
			}
		}

		private int processVal(int delta1) {
			if (type == CalcType.CALC_STD) {
					
				// calculate threshold sum hybrid 
				SumH += delta1 > 0 ? delta1 : -delta1;

				// calculate threshold sum for length encoding
				int workData = delta1;
				if (ElemCount == 63) {
					// special case
					if (SumL > 0) { // pos. SumL
						if ((SumL + 1) % 4 == 0) {
							if (workData % 2 != 0)
								workData--;
						} else {
							if (workData % 2 == 0)
								workData--;
						}

					} else { // neg. SumL
						if ((SumL - 1) % 4 == 0) {
							if (workData % 2 != 0)
								workData++;
						} else {
							if (workData % 2 == 0)
								workData++;
						}
					}
				}
				int eval = evalSumSpec(SumL, ElemCount, workData);
				SumL += eval;

				// now update elem counter
				ElemCount++;

				if (ElemCount == 64) {
					ElemCount = 32;
					SumH = ((SumH - unitDelta) >> 1) - 1;
					SumL /= 2;
					if (SumL % 2 != 0) {
						SumL++;
					}
				}

				// calculate new hunit
				hunit = normalizeHUnit((unitDelta + SumH + 1) / (ElemCount + 1));

				// finally determine encode type for next value
				wrapType = WrapType.WRAP_0;
				if (hunit > 0) {
					encType = EncType.HYBRID;
				} else {
					encType = EncType.LEN;
					if (SumL > 0)
						wrapType = WrapType.WRAP_1;
				}
			} else if (type == CalcType.CALC_PLATEAU_ZERO) {
				// calculate threshold sum hybrid 
				SumH += delta1 > 0 ? delta1 : 1 - delta1; // different to standard
				// calculate threshold sum for length encoding
				SumL += delta1 <= 0 ? -1 : 1;
				ElemCount++;
				
				if (ElemCount == 64) {
					ElemCount = 32;
					SumH = ((SumH - unitDelta) >> 1) - 1;
					SumL /= 2;
					if (SumL % 2 != 0) {
						SumL++;
					}
				}

				// calculate new hunit
				hunit = normalizeHUnit((unitDelta + SumH + 1 - ElemCount / 2) / (ElemCount + 1));
				// finally determine encode type for next value
				wrapType = WrapType.WRAP_0;
				if (hunit > 0) {
					encType = EncType.HYBRID;
				} else {
					encType = EncType.LEN;
					if (SumL >= 0)
						wrapType = WrapType.WRAP_1;
				}
				if (delta1 <= 0)
					--delta1;

			} else {
				assert type == CalcType.CALC_PLATEAU_NON_ZERO;
				// calculate threshold sum hybrid 
				SumH += delta1 < 0 ? -delta1 : delta1; // simple absolute sum 
				// calculate threshold sum for length encoding
				SumL += delta1 <= 0 ? -1 : 1;
				ElemCount++;
				
				if (ElemCount == 64) {
					ElemCount = 32;
					SumH = ((SumH - unitDelta) >> 1) - 1;
					SumL /= 2;
					if (SumL % 2 != 0) {
						SumL--; // different to CALC_PLATEAU_ZERO !
					}
				}

				// calculate new hunit
				hunit = normalizeHUnit((unitDelta + SumH + 1) / (ElemCount + 1));
				// finally determine encode type for next value
				wrapType = WrapType.WRAP_0;
				if (hunit > 0) {
					encType = EncType.HYBRID;
				} else {
					encType = EncType.LEN;
					if (SumL <= 0)
						wrapType = WrapType.WRAP_2;
				}
				if (dDiff > 0) {
					delta1 = -delta1; 
				}
					
			}
			return delta1;
		}

		public void setDDiff(int dDiff) {
			this.dDiff = dDiff; 
		}
	}

	/**
	 * Calculate the longest sequence of zero bits that is considered as a valid number.
	 * The number depends on the field maxDeltaHeight in the header structure.
	 * @param maxHeight
	 * @return the number of bits
	 */
	private static int getMaxLengthZeroBits(int maxHeight) {
		if (maxHeight < 2)
			return 15;
		if (maxHeight < 4)
			return 16;
		if (maxHeight < 8)
			return 17;
		if (maxHeight < 16)
			return 18;
		if (maxHeight < 32)
			return 19;
		if (maxHeight < 64)
			return 20;
		if (maxHeight < 128)
			return 21;
		if (maxHeight < 256)
			return 22;
		if (maxHeight < 512)
			return 25;
		if (maxHeight < 1024)
			return 28;
		if (maxHeight < 2048)
			return 31;
		if (maxHeight < 4096)
			return 34;
		if (maxHeight < 8192)
			return 37;
		if (maxHeight < 16384)
			return 40;
		return 43;
	}

	/**
	 * Calculate the start hunit value. 
	 * The number depends on the field maxDeltaHeight in the header structure.
	 * @param maxHeight
	 * @return the hunit value 
	 */
	private static int getStartHUnit(int maxHeight) {
		if (maxHeight < 0x9f)
			return 1;
		else if (maxHeight < 0x11f)
			return 2;
		else if (maxHeight < 0x21f)
			return 4;
		else if (maxHeight < 0x41f)
			return 8;
		else if (maxHeight < 0x81f)
			return 16;
		else if (maxHeight < 0x101f)
			return 32;
		else if (maxHeight < 0x201f)
			return 64;
		else if (maxHeight < 0x401f)
			return 128;
		return 256;
	}

	/**
	 * The hunit value must be a positive multiple of 2 (including 0).  
	 * @param hu the result of a division to calculate the new hunit
	 * @return the normalised value
	 */
	private static int normalizeHUnit(int hu) {
		if (hu > 0) {
			return Integer.highestOneBit(hu);
		}
		return 0;
	}
	
	
}
