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

import java.io.*;
import java.nio.ByteBuffer;

/**
 * Implementation of DataInputStream to read float, int, long and double from a little endian source 
 * @author Begau
 *
 */
public class LittleEndianDataInputStream extends DataInputStream{

	private final byte[] buf = new byte[8];
	
	public LittleEndianDataInputStream(InputStream in){
		super(in);
	}
	
	public final int readLittleEndianInt() throws IOException {
		super.readFully(buf, 0, 4);
		return Integer.reverseBytes(ByteBuffer.wrap(buf).getInt());
	}
	
	public final float readLittleEndianFloat() throws IOException {
		return Float.intBitsToFloat(readLittleEndianInt());
	}
	
	public final double readLittleEndianDouble() throws IOException {
		return Double.longBitsToDouble(readLittleEndianLong());
	}
	
	public final long readLittleEndianLong() throws IOException {
		super.readFully(buf, 0, 8);
		return Long.reverseBytes(ByteBuffer.wrap(buf).getLong());
	}
}
