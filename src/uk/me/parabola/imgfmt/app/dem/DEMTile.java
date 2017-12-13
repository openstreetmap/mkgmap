package uk.me.parabola.imgfmt.app.dem;

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
	private DEMSection section;
	private ByteBuffer bits;
	private int[] deltas;
	private int tileNumberLat;
	private int tileNumberLon;
	private int height;
	private int width;
	private int offset;       		// offset from section.dataOffset2
	private int dataLength;			// number of bytes for compressed elevation data
	private int baseHeight;		// base or minimum height in this tile 
	private int differenceHeight;	// maximum height above baseHeight (elevation?)
	private byte encodingType;  // seems to determine how the highest values are displayed 

	private int bitPos;
	private byte currByte;
	private int currTablePos; // current position in plateau tables
	
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

	public DEMTile (int width, int height, int[] realHeights) {
		this.width = width;
		this.height = height;
		if (realHeights != null)
			createBitStream(realHeights);
	}
	
	public void createBitStream(int[] realHeights) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int h : realHeights) {
			if (h > max)
				max = h;
			if (h < min)
				min = h;
		}
		baseHeight = min;
		differenceHeight = max - min;
		if (min == max) {
			return; // all heights equal 
		}
		deltas = new int[realHeights.length];
		// normalize the heigh
		for (int i = 0; i < realHeights.length; i++) {
			deltas[i] = (realHeights[i] - min);
		}
		encodeDeltas();
		// cleanup 
		deltas = null;
	}
	
	private void addBit(boolean bit) {
		if (bit) {
			currByte |= 1 << (7-bitPos);
		}
		bitPos++;
		if (bitPos > 7) {
			bitPos = 0;
			bits.put(currByte);
		}
	}
	
	private void encodeDeltas() {
		CalcType ct = null;
		int pos = 0;
		while (true) {
			int n = pos % width;
			int m = pos / height;
			int deltaUpper = getDelta(n, m - 1);
			int deltaLeft = getDelta(n - 1, m);
			int dDiff = deltaUpper - deltaLeft;
			if (dDiff == 0) {
				ct = CalcType.CALC_P_LEN;
				int pLen = calcPlateauLen(n, m);
				writePlateauLen(pLen, n);
				pos += pLen;
			} else {
			}
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
			int unit = plateauUnit[currTablePos++];
			len -= unit;
			addBit(true);
			x += unit;
			if (x > width) 
				currTablePos--;
			if (x >= width)
				return;
		}
		if (currTablePos > 0)
			currTablePos--;
		addBit(false); // separator bit
		if (len > 0) {
			writeValAsBin(len, plateauBinBits[currTablePos]);
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
	 * @param numBits
	 */
	private void writeValHybrid(int val, int hunit) {
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
		int maxm = getMaxLengthZeroBits(differenceHeight);
		if (lenPart <= maxm) {
			writeValAsNumberOfZeroBits(lenPart); // write length encoded part
			writeValAsBin(binPart, numBits); // write binary encoded part
			addBit(val > 0); // sign bit
		} else {
			throw new RuntimeException("Cannot encode value " + val + " as hybrid with hunit " + hunit);
		}
	}

	/**
	 * Write an unsigned binary value with the given number of bits, MSB first. 
	 * @param val
	 * @param numBits
	 */
	private void writeValBigBin (int val) {
		// TODO
	}


	/**
	 * A plateau is a sequence of equal delta values. Calculate the length.
	 * @param col current column
	 * @param row current row
	 * @return the length of the plateau, which might be zero.
	 */
	private int calcPlateauLen(int col, int row) {
		int len = 0;
		int v = getDelta(col, row);
		while (col + len < width) {
			if (v == getDelta(col + len, row)) {
				++len;
			} else 
				break;
			
		}
		return len;
	}

	private int getDelta(int col, int row) {
		if (deltas == null)
			return 0;
		if (row < 0) {
			// virtual 1st row
			return 0;
		}
		if (col < 0) {
			return row == 0 ? 0 : deltas[(row - 1) * width]; 
		}
		return deltas[col + row * width];
	}

	public void writeHeader(ImgFileWriter writer) {
		writer.putN(section.getOffsetSize(), offset);
		if (section.getBaseSize() == 1)
			writer.put((byte) baseHeight);
		else 
			writer.putChar((char) baseHeight);
		writer.putN(section.getDifferenceSize(), differenceHeight);
		if (section.isHasExtra())
			writer.put(encodingType);
	}


	public void writeBitStreamData(ImgFileWriter writer) {
		if (bits != null)
			writer.put(bits);
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
	 * 
	 * @param hu
	 * @return
	 */
	private static int normalizeHUnit(int hu) {
		if (hu > 0) {
			return Integer.highestOneBit(hu);
		}
		return 0;
	}
	

	private class ValPredicter {
		private EncType encType;
		private WrapType wrapType;
		private int SumH;
		private int SumL;
		private int ElemCount;
		private int hunit;
		private final CalcType type;
		private final int unitDelta;
		private final int maxHeight;
		private int followerDDiff;
		
		public ValPredicter(CalcType type, int maxHeight) {
			super();
			this.type = type;
			this.maxHeight = maxHeight;
			
			unitDelta = Math.max(0, maxHeight - 0x5f) / 0x40; // TODO what does it mean? 
			encType = EncType.HYBRID;
			wrapType = WrapType.WRAP_0;
			hunit = getStartHUnit(maxHeight);
		}

		public EncType getEncType() {
			return encType;
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
				if (followerDDiff > 0) {
					delta1 = -delta1; 
				}
					
			}
			return delta1;
		}

		public int getHUnit() {
			return hunit;
		}

		public void setDDiff(int followerDDiff) {
			this.followerDDiff = followerDDiff; 
		}
	}
	
	static int getMaxLengthZeroBits(int maxHeight) {
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

	
}
