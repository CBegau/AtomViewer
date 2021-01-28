// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2016  ICAMS, Ruhr-Universität Bochum
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
import java.nio.ByteBuffer;

public abstract class ByteArrayReader {
    
    /**
     * Creates a specialized class to read the different IMD binary formats (big-/little-endian, single-/double-precision
     * more efficient and encapsulates endianess conversions from a byte array
     * @param doublePrecision if true, double-precision values are read, otherwise single-precision
     * @param litteEndian if true, the data is read as little endian, otherwise as big endian
     * @return An instance of ByteArrayReader with the ability to read the required format
     * @throws FileNotFoundException
     */
	public static ByteArrayReader getReader (boolean doublePrecision, boolean littleEndian) {
		if (littleEndian && !doublePrecision) return new LittleEndianSinglePrecisionWrapper();
		if (littleEndian && doublePrecision) return new LittleEndianDoublePrecisionWrapper();
		if (!littleEndian && !doublePrecision) return new BigEndianSinglePrecisionWrapper();
		return new BigEndianDoublePresicionWrapper();
	}
	
	public abstract float readFloat(byte[] b, int offset);
	public abstract int readInt(byte[] b, int offset);
	public abstract int readIntSingle(byte[] b, int offset);
	
	private static final class LittleEndianSinglePrecisionWrapper extends ByteArrayReader{
        @Override
        public float readFloat(byte[] b, int offset) {
            return Float.intBitsToFloat(Integer.reverseBytes(ByteBuffer.wrap(b, offset, 4).getInt()));
        }

        @Override
        public int readInt(byte[] b, int offset) {
            return Integer.reverseBytes(ByteBuffer.wrap(b, offset, 4).getInt());
        }

        @Override
        public int readIntSingle(byte[] b, int offset) {
            return Integer.reverseBytes(ByteBuffer.wrap(b, offset, 4).getInt());
        }
	}
	
	private static final class BigEndianSinglePrecisionWrapper extends ByteArrayReader{
        @Override
        public float readFloat(byte[] b, int offset) {
            return ByteBuffer.wrap(b, offset, 4).getFloat();
        }

        @Override
        public int readInt(byte[] b, int offset) {
            return ByteBuffer.wrap(b, offset, 4).getInt();
        }

        @Override
        public int readIntSingle(byte[] b, int offset) {
            return ByteBuffer.wrap(b, offset, 4).getInt();
        }
    }
	
	private static final class LittleEndianDoublePrecisionWrapper extends ByteArrayReader{
        @Override
        public float readFloat(byte[] b, int offset) {
            return (float)Double.longBitsToDouble(Long.reverseBytes(ByteBuffer.wrap(b, offset, 8).getLong()));
        }

        @Override
        public int readInt(byte[] b, int offset) {
            return (int)Long.reverseBytes(ByteBuffer.wrap(b, offset, 8).getLong());
        }

        @Override
        public int readIntSingle(byte[] b, int offset) {
            return Integer.reverseBytes(ByteBuffer.wrap(b, offset, 4).getInt());
        }
	}
	
	private static final class BigEndianDoublePresicionWrapper extends ByteArrayReader{
	    @Override
        public float readFloat(byte[] b, int offset) {
            return (float)ByteBuffer.wrap(b, offset, 8).getDouble();
        }

        @Override
        public int readInt(byte[] b, int offset) {
            return (int)ByteBuffer.wrap(b, offset, 8).getLong();
        }

        @Override
        public int readIntSingle(byte[] b, int offset) {
            return ByteBuffer.wrap(b, offset, 4).getInt();
        }
	}
}
