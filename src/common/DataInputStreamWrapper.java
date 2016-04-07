// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2013  ICAMS, Ruhr-Universität Bochum
//
// AtomViewer is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// AtomViewer is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with AtomViewer. If not, see <http://www.gnu.org/licenses/> 

package common;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Factory to create dataInputStreams to read the different IMD binary formats (big-/little-Endian, single-/double-precision
 * more efficient and encapsulates all conversions fully transparent
 * E.g. readFloat() will always return the next floating point value in the IMD-files,
 * no matter what endianess and accuracy is used 
 */
public abstract class DataInputStreamWrapper {

	private LittleEndianDataInputStream ledis;
	private long bytesRead;
	
	/**
	 * Creates a specialized dataInputStream to read the different IMD binary formats (big-/little-endian, single-/double-precision
	 * more efficient and encapsulates endianess conversions
	 * @param is the input stream to read data from
	 * @param doublePrecision if true, double-precision values are read, otherwise single-precision
	 * @param litteEndian if true, the data is read as little endian, otherwise as big endian
	 * @return An instance of DataInputStreamWrapper with the ability to read the required format
	 * @throws FileNotFoundException
	 */
	public static DataInputStreamWrapper getDataInputStreamWrapper (InputStream is, boolean doublePrecision, boolean litteEndian) {
		if (litteEndian && !doublePrecision) return new LittleEndianSinglePrecisionDIS(is);
		if (litteEndian && doublePrecision) return new LittleEndianDoublePrecisionDIS(is);
		if (!litteEndian && !doublePrecision) return new BigEndianSinglePrecisionDIS(is);
		return new BigEndianDoublePresicionDIS(is);
	}
	
	public abstract float readFloat() throws IOException;
	public abstract int readInt() throws IOException;
	public abstract void skip() throws IOException;
	public abstract int readIntSingle() throws IOException;
	
	public byte readByte() throws IOException{
		bytesRead++;
		return ledis.readByte();
	}
	
	public long getBytesRead() {
		return bytesRead;
	}
	
	public void close() throws IOException{
		if (ledis!=null) ledis.close();
	}
	
	
	private static final class LittleEndianSinglePrecisionDIS extends DataInputStreamWrapper{
		private byte skip[] = new byte[4];
		public LittleEndianSinglePrecisionDIS(InputStream is) {
			super.ledis = new LittleEndianDataInputStream(is);
		}
		
		public float readFloat() throws IOException{
			super.bytesRead += 4l;
			return super.ledis.readLittleEndianFloat();
		}
		
		public int readInt() throws IOException{
			super.bytesRead += 4l;
			return super.ledis.readLittleEndianInt();
		}
		
		public int readIntSingle() throws IOException{
			super.bytesRead += 4l;
			return super.ledis.readLittleEndianInt();
		}
		@Override
		public void skip() throws IOException {
			super.bytesRead += 4l;
			super.ledis.read(skip);
		}
	}
	
	private static final class BigEndianSinglePrecisionDIS extends DataInputStreamWrapper{
		private byte skip[] = new byte[4];
		public BigEndianSinglePrecisionDIS(InputStream is) {
			super.ledis = new LittleEndianDataInputStream(is);
		}
		
		public float readFloat() throws IOException{
			super.bytesRead += 4l;
			return super.ledis.readFloat();
		}
		
		public int readInt() throws IOException{
			super.bytesRead += 4l;
			return super.ledis.readInt();
			
		}
		
		public int readIntSingle() throws IOException{
			super.bytesRead += 4l;
			return super.ledis.readInt();
		}
		
		@Override
		public void skip() throws IOException {
			super.bytesRead += 4l;
			super.ledis.read(skip);
		}
	}
	
	private static final class LittleEndianDoublePrecisionDIS extends DataInputStreamWrapper{
		private byte skip[] = new byte[8];
		public LittleEndianDoublePrecisionDIS(InputStream is) {
			super.ledis = new LittleEndianDataInputStream(is);
		}
		
		public float readFloat() throws IOException{
			super.bytesRead += 8l;
			return (float)super.ledis.readLittleEndianDouble();
		}
		
		public int readInt() throws IOException{
			super.bytesRead += 8l;
			return (int)super.ledis.readLittleEndianLong();
		}
		
		public int readIntSingle() throws IOException{
			super.bytesRead += 4l;
			return super.ledis.readLittleEndianInt();
		}
		
		@Override
		public void skip() throws IOException {
			super.bytesRead += 8l;
			super.ledis.read(skip);
		}
	}
	
	private static final class BigEndianDoublePresicionDIS extends DataInputStreamWrapper{
		private byte skip[] = new byte[8];
		public BigEndianDoublePresicionDIS(InputStream is) {
			super.ledis = new LittleEndianDataInputStream(is);
		}
		
		public float readFloat() throws IOException{
			super.bytesRead += 8l;
			return (float)super.ledis.readDouble();
		}
		
		public int readInt() throws IOException{
			super.bytesRead += 8l;
			return (int)(super.ledis.readLong()>>>32);
		}
		
		public int readIntSingle() throws IOException{
			super.bytesRead += 4l;
			return super.ledis.readInt();
		}
		@Override
		public void skip() throws IOException {
			super.bytesRead += 8l;
			super.ledis.read(skip);
		}
	}
	
}
