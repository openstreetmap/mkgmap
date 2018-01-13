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
import java.util.Arrays;

import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.mkgmap.reader.hgt.HGTReader;

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
	private CalcType currCalcType;
	
	private final static boolean DEBUG = false;
	private StringBuilder bs;
	

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

	public DEMTile (DEMSection parent, int col, int row, int width, int height, short[] realHeights) {
		this.section = parent;
		this.width = width;
		this.height = height;
		this.tileNumberLon = col;
		this.tileNumberLat = row;
		
		// check values in matrix
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		int countInvalid = 0;
		for (int h : realHeights) {
			if (h == HGTReader.UNDEF)
				++countInvalid;
			else {
				if (h > max)
					max = h;
				if (h < min)
					min = h;
			}
		}
		if (min == Integer.MAX_VALUE) {
			// all values are invalid 
			encodingType = 2;
			min = 0;
			max = 1;
			// seems we still need a bit stream in this case
			realHeights = new short[width * height];
			Arrays.fill(realHeights, HGTReader.UNDEF);
		} else if (countInvalid > 0) {
			// some values are invalid
			encodingType = 2; // don't display highest value 
			max++;
		} else {
			// all height values are valid
			encodingType = 0;
		}
		if (encodingType != 0)
			section.setHasExtra(true);

		this.baseHeight = min;
		this.maxDeltaHeight = max - min;
		if (min == max) {
			return; // all heights equal 
		}
		createBitStream(realHeights);
	}
	
	public int getBaseHeight() {
		return baseHeight;
	}

	public int getMaxHeight() {
		return baseHeight + maxDeltaHeight; // TODO: does it depend on value in encodingType?
	}
	
	private void createBitStream(short[] realHeights) {
		bits = new ByteArrayOutputStream(1024); 
		heights = new int[realHeights.length];
		// normalise the height matrix
		for (int i = 0; i < realHeights.length; i++) {
			if (realHeights[i] == HGTReader.UNDEF)
				heights[i] = maxDeltaHeight;
			else 
				heights[i] = (realHeights[i] - baseHeight);
		}
		// all values in heights are now expected to be between 0 .. maxDeltaHeight
		if (DEBUG) {
			bs = new StringBuilder();
		}
		encodeDeltas();
		// cleanup 
		bs = null;
		heights = null;
	}
	
	private void addBit(boolean bit) {
		if (DEBUG) {
			bs.append(bit ? '1':'0');
		}
		if (bit) {
			currByte |= 1 << (7-bitPos);
		}
		bitPos++;
		if (bitPos > 7) {
			bitPos = 0;
			bits.write(currByte);
			currByte = 0;
		}
	}
	
	/**
	 * The main loop to calculate the bit stream data.
	 */
	private void encodeDeltas() {
		int pos = 0;
		currCalcType = null;
		ValPredicter encStandard = new ValPredicter(CalcType.CALC_STD, maxDeltaHeight);
		ValPredicter encPlateauF0 = new ValPredicter(CalcType.CALC_PLATEAU_ZERO, maxDeltaHeight);
		ValPredicter encPlateauF1 = new ValPredicter(CalcType.CALC_PLATEAU_NON_ZERO, maxDeltaHeight);
		ValPredicter encoder = null;
		boolean writeFollower = false;
		while (pos < heights.length) {
			if (DEBUG) {
				bs.setLength(0);
			}
			int n = pos % width;
			int m = pos / width;
			int hUpper = getHeight(n, m - 1);
			int hLeft = getHeight(n - 1, m);
			int dDiff = hUpper - hLeft;
			if (writeFollower) {
				encoder = (dDiff == 0) ? encPlateauF0 : encPlateauF1;
				writeFollower = false;
			} else if (dDiff == 0) {
				currCalcType = CalcType.CALC_P_LEN;
				int pLen = calcPlateauLen(n, m);
				writePlateauLen(pLen, n);
				pos += pLen;
				writeFollower = (pos % width != 0 || pLen == 0);
				continue;
			} else {
				encoder = encStandard;
			}
			currCalcType = encoder.type;
			encoder.setDDiff(dDiff);
			int v;
			int h = getHeight(n, m);
			if (currCalcType == CalcType.CALC_STD) {
				int predict;
				int hUpLeft = getHeight(n-1, m-1);
				int hdiffUp = hUpper- hUpLeft;
				if (hdiffUp >= maxDeltaHeight - hLeft) {
					predict = -1;
				} else if (hdiffUp <= -hLeft) {
					predict = 0; 
				} else {
					predict = hLeft + hdiffUp;
				}
				if (dDiff > 0)
					v = -h + predict;
				else 
					v = h -predict;
				
			} else {
				// plateau follower: predicted value is upper height 
				v = h - hUpper;
			}
			if (DEBUG) {
				bs.setLength(0);
			}
			encoder.write(v);
			pos++;
		}
		if (bitPos > 0)
			bits.write(currByte);
	}

	/**
	 * Add the bits for a given plateau length using the current value of lastTablePos 
	 * @param pLen
	 */
	private void writePlateauLen(int pLen, int col) {
		int len = pLen;
		int x = col;

		if (col + len >= width) {
			// this is not really needed but sometimes produces fewer bits
			// compared to the loop in the else branch
			while (x < width) {
				int unit = plateauUnit[currPlateauTablePos++];
				len -= unit;
				x += unit;
				addBit(true);
			}
			if (x != width) {
				currPlateauTablePos--;
			}
		} else {
			while (true) {
				int unit = plateauUnit[currPlateauTablePos];
				if (len < unit)
					break;
				currPlateauTablePos++;
				len -= unit;
				addBit(true);
				x += unit;
				if (x > width)
					currPlateauTablePos--;
				if (x >= width) {
					return;
				}
			}
			if (currPlateauTablePos > 0)
				currPlateauTablePos--;
			
			addBit(false); // separator bit
			int binBits = plateauBinBits[currPlateauTablePos];
			if (binBits > 0) {
				writeValAsBin(Math.abs(len), binBits);
			}
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
			throw new MapFailedException("Number too big for binary encoding with " + numBits + " bits:" + val);
		while (t > 0) {
			addBit((val & t) != 0);
			t >>= 1;
		}
	}

	/**
	 * Write a length encoded value as a sequence of 0-bits followed by a 1-bit. 
	 * @param val
	 */
	private void writeNumberOfZeroBits(int val) {
		for (int i = 0; i < val; i++)
			addBit(false);
		addBit(true); // terminating 1-bit
	}

	/**
	 * Write an unsigned binary value with the given number of bits, MSB first. 
	 * @param val
	 * @param hunit
	 * @param type 
	 * @return 
	 */
	private boolean writeValHybrid(int val, int hunit, int maxZeroBits) {
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
			writeNumberOfZeroBits(lenPart); // write length encoded part
			writeValAsBin(binPart, numBits); // write binary encoded part
			addBit(val > 0); // sign bit, 1 means positive
			return true;
		}
		return false;
	}

	/**
	 * Write a signed binary value with the given number of bits, MSB first, sign bit last 
	 * @param val
	 * @param numZeroBits number of zero bits that is considered valid 
	 */
	private void writeValBigBin (int val, int numZeroBits) {
		// signal big bin by writing an invalid number of zero bits
		writeNumberOfZeroBits(numZeroBits + 1);
		int bits = getBigBinBits(maxDeltaHeight);
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
		int v = getHeight(col-1, row);
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
		if (maxDeltaHeight == 0)
			offset = 0;
		switch (section.getOffsetSize()) {
		case 1:
			writer.put1(offset);
			break;
		case 2:
			writer.put2(offset);
			break;
		case 3:
			writer.put3(offset);
			break;
		default:
			writer.putInt(offset);
		}
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
		private int sumH;
		private int sumL;
		private int elemCount;
		private int hunit;
		private final CalcType type;
		private final int unitDelta;
		private int dDiff;
		private final int maxZeroBits;
		final int l0WrapUp, l0WrapDown, l1WrapUp, l1WrapDown, l2WrapUp, l2WrapDown,hWrapUp, hWrapDown;
		
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

			// calculate threshold values for wrapping
			if (maxHeight % 2 == 0) {
				l0WrapDown = maxHeight / 2;
				l0WrapUp = -maxHeight / 2;
				l1WrapDown = (maxHeight + 2) / 2;
				l1WrapUp = -maxHeight / 2;
				l2WrapDown = maxHeight / 2;
				l2WrapUp = -maxHeight / 2;
			} else {
				l0WrapDown = (maxHeight + 1) / 2;
				l0WrapUp = -(maxHeight - 1) / 2;
				l1WrapDown = (maxHeight + 1) / 2;
				l1WrapUp = -(maxHeight - 1) / 2;
				l2WrapDown = (maxHeight - 1) / 2;
				l2WrapUp = -(maxHeight + 1) / 2;
			}

			hWrapDown = (maxHeight + 1) / 2;
			hWrapUp = -(maxHeight - 1) / 2;
		}

		/**
		 * This 
		 * @param data
		 * @return
		 */
		private int wrap(int data) {
			int v = data;
			int down,up;
			if (encType == EncType.HYBRID) {
				down = hWrapDown;
				up = hWrapUp;
			} else {
				if (wrapType == WrapType.WRAP_0) {
					down = l0WrapDown;
					up = l0WrapUp;
				} else if (wrapType == WrapType.WRAP_1) {
					down = l1WrapDown;
					up = l1WrapUp;
				} else {
					down = l2WrapDown;
					up = l2WrapUp;
				}
			}
			
			if (v > down)
				v = v - (maxDeltaHeight + 1);
			if (v < up)
				v = v + maxDeltaHeight + 1;
			return v; 
		}
		
		public void write(int val) {
			
			int wrapped = wrap(val);
			int delta1 = wrapped;
			if (type == CalcType.CALC_PLATEAU_ZERO) {
				if (delta1 <= 0)
					delta1++;
			} else if (type == CalcType.CALC_PLATEAU_NON_ZERO) {
				if (dDiff > 0) {
					delta1 = -delta1; 
				}
			}
			int delta2;
			if (wrapType == WrapType.WRAP_0)
				delta2 = delta1;
			else if (wrapType == WrapType.WRAP_1)
				delta2 = 1 - delta1;
			else delta2 = -delta1;
			boolean written = false;
			if (encType == EncType.HYBRID) {
				written = writeValHybrid(delta2, hunit, getCurrentMaxZeroBits());
			} else {
				// EncType.LEN 
				// 2 * Math.Abs(data) - (Math.Sign(data) + 1) / 2
				int n0;
				if (delta2 < 0) {
					n0 = -delta2 * 2;
				} else if (delta2 > 0){
					n0 = (delta2 -1) * 2 + 1;
				} else { 
					n0 = 0;
				}
				if (n0 <= getCurrentMaxZeroBits()) {
					writeNumberOfZeroBits(n0);
					written = true;
				}
			}
			if (!written)
				writeValBigBin(delta2, getCurrentMaxZeroBits());
			processVal(delta1);
		}

		/**
		 * This looks wrong but seems to be needed. For a plateau follower we reduce the max value.
		 * The effect seems to be that a Big Bin is written although it would fit in the normal number.
		 * Maybe a BigBin signals that a flag should be reset or currPlateauTablePos is too high?
		 * @return number of 0 bits that are considered okay.
		 */
		int getCurrentMaxZeroBits() {
			if (currCalcType == CalcType.CALC_PLATEAU_NON_ZERO || currCalcType == CalcType.CALC_PLATEAU_ZERO)
				return maxZeroBits - plateauBinBits[currPlateauTablePos];
			return maxZeroBits;
		}
		
		private void processVal(int delta1) {
			if (type == CalcType.CALC_STD) {
					
				// calculate threshold sum hybrid 
				sumH += delta1 > 0 ? delta1 : -delta1;
				if (sumH + unitDelta  + 1 >= 0xffff)
					sumH -= 0x10000;

				// calculate threshold sum for length encoding
				int workData = delta1;
				if (elemCount == 63) {
					// special case
					if (sumL > 0) { // pos. SumL
						if ((sumL + 1) % 4 == 0) {
							if (workData % 2 != 0)
								workData--;
						} else {
							if (workData % 2 == 0)
								workData--;
						}

					} else { // neg. SumL
						if ((sumL - 1) % 4 == 0) {
							if (workData % 2 != 0)
								workData++;
						} else {
							if (workData % 2 == 0)
								workData++;
						}
					}
				}
				int eval = evalSumSpec(sumL, elemCount, workData);
				sumL += eval;

				// now update elem counter
				elemCount++;

				if (elemCount == 64) {
					elemCount = 32;
					sumH = ((sumH - unitDelta) >> 1) - 1;
					
					sumL /= 2;
					if (sumL % 2 != 0) {
						sumL++;
					}
				}

				// calculate new hunit
				hunit = normalizeHUnit((unitDelta + sumH + 1) / (elemCount + 1));

				// finally determine encode type for next value
				wrapType = WrapType.WRAP_0;
				if (hunit > 0) {
					encType = EncType.HYBRID;
				} else {
					encType = EncType.LEN;
					if (sumL > 0)
						wrapType = WrapType.WRAP_1;
				}
			} else if (type == CalcType.CALC_PLATEAU_ZERO) {
				// calculate threshold sum hybrid 
				sumH += delta1 > 0 ? delta1 : 1 - delta1; // different to standard
				if (sumH + unitDelta  + 1 >= 0xffff)
					sumH -= 0x10000;
				
				// calculate threshold sum for length encoding
				sumL += delta1 <= 0 ? -1 : 1;
				elemCount++;
				
				if (elemCount == 64) {
					elemCount = 32;
					sumH = ((sumH - unitDelta) >> 1) - 1;
					sumL /= 2;
					if (sumL % 2 != 0) {
						sumL++;
					}
				}

				// calculate new hunit
				hunit = normalizeHUnit((unitDelta + sumH + 1 - elemCount / 2) / (elemCount + 1));
				// finally determine encode type for next value
				wrapType = WrapType.WRAP_0;
				if (hunit > 0) {
					encType = EncType.HYBRID;
				} else {
					encType = EncType.LEN;
					if (sumL >= 0)
						wrapType = WrapType.WRAP_1;
				}
			} else {
				assert type == CalcType.CALC_PLATEAU_NON_ZERO;
				// calculate threshold sum hybrid 
				sumH += delta1 < 0 ? -delta1 : delta1; // simple absolute sum
				if (sumH + unitDelta  + 1 >= 0xffff)
					sumH -= 0x10000;

				// calculate threshold sum for length encoding
				sumL += delta1 <= 0 ? -1 : 1;
				elemCount++;
				
				if (elemCount == 64) {
					elemCount = 32;
					sumH = ((sumH - unitDelta) >> 1) - 1;
					sumL /= 2;
					if (sumL % 2 != 0) {
						sumL--; // different to CALC_PLATEAU_ZERO !
					}
				}

				// calculate new hunit
				hunit = normalizeHUnit((unitDelta + sumH + 1) / (elemCount + 1));
				// finally determine encode type for next value
				wrapType = WrapType.WRAP_0;
				if (hunit > 0) {
					encType = EncType.HYBRID;
				} else {
					encType = EncType.LEN;
					if (sumL <= 0)
						wrapType = WrapType.WRAP_2;
				}
			}
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

	static int getBigBinBits(int maxHeight) {
		int bits;
		if (maxHeight <16384) {
			int n = Integer.highestOneBit(maxHeight);
			bits = Integer.numberOfTrailingZeros(n) + 1;
		} else 
			bits = 15;
		return bits;
//		if (maxHeight < 2)
//			return 1;
//		else if (maxHeight < 4)
//			return 2;
//		else if (maxHeight < 8)
//			return 3;
//		else if (maxHeight < 16)
//			return 4;
//		else if (maxHeight < 32)
//			return 5;
//		else if (maxHeight < 64)
//			return 6;
//		else if (maxHeight < 128)
//			return 7;
//		else if (maxHeight < 256)
//			return 8;
//		else if (maxHeight < 512)
//			return 9;
//		else if (maxHeight < 1024)
//			return 10;
//		else if (maxHeight < 2048)
//			return 11;
//		else if (maxHeight < 4096)
//			return 12;
//		else if (maxHeight < 8192)
//			return 13;
//		else if (maxHeight < 16384)
//			return 14;
//		else
//			return 15;
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

	public int getBitStreamLen() {
		if (bits == null)
			return 0;
		else 
			return bits.size();
	}

	public void setOffset(int off) {
		this.offset = off;
	}

	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return tileNumberLat + " " + tileNumberLon + " w=" + width + " h=" + height;
	}

	public int getMaxDeltaHeight() {
		return maxDeltaHeight;
	}

}
