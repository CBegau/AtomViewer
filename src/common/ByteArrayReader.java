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
            return ByteToPrimitives.toFloatLittleEndian(b, offset);
        }

        @Override
        public int readInt(byte[] b, int offset) {
            return ByteToPrimitives.toIntLittleEndian(b, offset);
        }

        @Override
        public int readIntSingle(byte[] b, int offset) {
            return ByteToPrimitives.toIntLittleEndian(b, offset);
        }
	}
	
	private static final class BigEndianSinglePrecisionWrapper extends ByteArrayReader{
        @Override
        public float readFloat(byte[] b, int offset) {
            return ByteToPrimitives.toFloatBigEndian(b, offset);
        }

        @Override
        public int readInt(byte[] b, int offset) {
            return ByteToPrimitives.toIntBigEndian(b, offset);
        }

        @Override
        public int readIntSingle(byte[] b, int offset) {
            return ByteToPrimitives.toIntBigEndian(b, offset);
        }
    }
	
	private static final class LittleEndianDoublePrecisionWrapper extends ByteArrayReader{
        @Override
        public float readFloat(byte[] b, int offset) {
            return (float)ByteToPrimitives.toDoubleLittleEndian(b, offset);
        }

        @Override
        public int readInt(byte[] b, int offset) {
            return (int)ByteToPrimitives.toLongLittleEndian(b, offset);
        }

        @Override
        public int readIntSingle(byte[] b, int offset) {
            return ByteToPrimitives.toIntLittleEndian(b, offset);
        }
	}
	
	private static final class BigEndianDoublePresicionWrapper extends ByteArrayReader{
	    @Override
        public float readFloat(byte[] b, int offset) {
            return (float)ByteToPrimitives.toDoubleBigEndian(b, offset);
        }

        @Override
        public int readInt(byte[] b, int offset) {
            return (int)ByteToPrimitives.toLongBigEndian(b, offset);
        }

        @Override
        public int readIntSingle(byte[] b, int offset) {
            return ByteToPrimitives.toIntBigEndian(b, offset);
        }
	}
	
	private static class ByteToPrimitives{
	    public static int toIntBigEndian(byte[] b, int offset){
	        return (b[offset+0]) << 24 | (b[offset+1]&0xff) << 16 | (b[offset+2]&0xff) << 8 | (b[offset+3]&0xff);
	    }
	    public static long toLongBigEndian(byte[] b, int offset){
	        return ( (long)(b[offset+0])      << 56 | (long)(b[offset+1]&0xff) << 48 | 
	                 (long)(b[offset+2]&0xff) << 40 | (long)(b[offset+3]&0xff) << 32 |
	                 (long)(b[offset+4]&0xff) << 24 | (long)(b[offset+5]&0xff) << 16 |
	                 (long)(b[offset+6]&0xff) << 8  | (long)(b[offset+7]&0xff)       );
	    }
	    
	    public static double toDoubleBigEndian(byte[] b, int offset){
	        return Double.longBitsToDouble(toLongBigEndian(b, offset));
	    }
	    
	    public static float toFloatBigEndian(byte[] b, int offset){
	        return Float.intBitsToFloat(toIntBigEndian(b, offset));
	    }
	    
	    public static int toIntLittleEndian(byte[] b, int offset){
	        return (b[offset+3]) << 24 | (b[offset+2]&0xff) << 16 | (b[offset+1]&0xff) << 8 | (b[offset+0]&0xff);
	    }
	    
	    public static long toLongLittleEndian(byte[] b, int offset){
	        return ( (long)(b[offset+7])      << 56 | (long)(b[offset+6]&0xff) << 48 | 
	                 (long)(b[offset+5]&0xff) << 40 | (long)(b[offset+4]&0xff) << 32 |
	                 (long)(b[offset+3]&0xff) << 24 | (long)(b[offset+2]&0xff) << 16 |
	                 (long)(b[offset+1]&0xff) << 8  | (long)(b[offset+0]&0xff)       );
	    }
	    
	    public static double toDoubleLittleEndian(byte[] b, int offset){
	        return Double.longBitsToDouble(toLongLittleEndian(b, offset));
	    }
	    
	    public static float toFloatLittleEndian(byte[] b, int offset){
	        return Float.intBitsToFloat(toIntLittleEndian(b, offset));
	    }
	}
}
