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
		return (buf[3]) << 24 | (buf[2]&0xff) << 16 | (buf[1]&0xff) << 8 | (buf[0]&0xff);
	}
	
	public final float readLittleEndianFloat() throws IOException {
		super.readFully(buf, 0, 4);
		int i = (buf[3]) << 24 | (buf[2]&0xff) << 16 | (buf[1]&0xff) << 8 | (buf[0]&0xff);
		return Float.intBitsToFloat(i);
	}
	
	public final double readLittleEndianDouble() throws IOException {
		super.readFully(buf, 0, 8);
		long l = ( (long)buf[7] << 56 | (long)(buf[6]&0xff) << 48 | (long)(buf[5]&0xff) << 40 | (long)(buf[4]&0xff) << 32 
				| (long)(buf[3]&0xff) << 24 | (long)(buf[2]&0xff) << 16 | (long)(buf[1]&0xff) << 8 | (long)buf[0]&0xff );
		return Double.longBitsToDouble(l);
	}
	
	public final long readLittleEndianLong() throws IOException {
		super.readFully(buf, 0, 8);
		return ( (long)buf[7] << 56 | (long)(buf[6]&0xff) << 48 | (long)(buf[5]&0xff) << 40 | (long)(buf[4]&0xff) << 32 
				| (long)(buf[3]&0xff) << 24 | (long)(buf[2]&0xff) << 16 | (long)(buf[1]&0xff) << 8 | (long)buf[0]&0xff );
	}
}
