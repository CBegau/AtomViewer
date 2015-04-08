// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2015  ICAMS, Ruhr-Universität Bochum
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
package gui.glUtils;

import java.util.ArrayList;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;

import common.RingBuffer;

/**
 * A singleton class that provides a RingBuffer of CellRenderBuffer objects
 */
public class CellRenderBufferRing {

	private final static int SIZE_CELL_RENDER_RING = 8;
	private static RingBuffer<CellRenderBuffer> cellRenderRingBuffer = null;
	private static int sizePerElement = 0;
	
	/**
	 * Returns a single instance of a RingBuffer containing CellRenderBuffer
	 * The buffers are large enough to hold the number given in floatsPerElement   
	 * times the constant ObjectRenderData.MAX_ELEMENTS_PER_CELL
	 * @param gl
	 * @param floatsPerElement size per elements in Floats (4 Bytes)
	 * @return
	 */
	public static RingBuffer<CellRenderBuffer> getCellBufferRing(GL3 gl, int floatsPerElement){
		//create a new RingBuffer if non exists or a larger one is required 
		if (cellRenderRingBuffer == null || floatsPerElement > CellRenderBufferRing.sizePerElement){
			CellRenderBufferRing.dispose(gl);
			CellRenderBufferRing.create(gl, floatsPerElement);
		}
		
		return cellRenderRingBuffer;
	}
	
	private static void create(GL3 gl, int sizePerElement){
		ArrayList<CellRenderBuffer> ringElements = new ArrayList<CellRenderBuffer>();
		for (int i = 0; i < SIZE_CELL_RENDER_RING; i++) {
			CellRenderBuffer crb = new CellRenderBuffer();
			crb.init(gl, ObjectRenderData.MAX_ELEMENTS_PER_CELL * sizePerElement * Float.SIZE / 8, GL.GL_ARRAY_BUFFER);
			ringElements.add(crb);
		}			
		cellRenderRingBuffer = new RingBuffer<CellRenderBuffer>(ringElements);
		CellRenderBufferRing.sizePerElement = sizePerElement;
	}
	
	public static void dispose(GL3 gl){
		if (cellRenderRingBuffer != null){
			for (int i=0; i<cellRenderRingBuffer.size(); i++)
				cellRenderRingBuffer.getNext().dispose(gl);
		}
		cellRenderRingBuffer = null;
		sizePerElement = 0;
	}
	
	
	public static class CellRenderBuffer{
		int buffer;
		long bufferSize;
		long fence = -1;
		int bufferType;
		int vertexArrayObject = -1;
		
		void init(GL3 gl, long size, int bufferType){
			this.bufferType = bufferType;
			int[] buf = new int[1];
			
			gl.glGenVertexArrays(1, buf, 0);
			this.vertexArrayObject = buf[0];
			
			gl.glGenBuffers(1, buf, 0);
	
			gl.glBindBuffer(bufferType, buf[0]);
			gl.glBufferData(bufferType, size, null, GL3.GL_STREAM_DRAW);
			gl.glBindBuffer(bufferType, 0);
	
			this.buffer = buf[0];
			this.bufferSize = size;
		}
		
		void dispose(GL3 gl){
			int[] buffers = new int[1];
			buffers[0] = buffer;
			gl.glDeleteBuffers(1, buffers, 0);
			buffers[0] = vertexArrayObject;
			gl.glDeleteVertexArrays(1, buffers, 0);
			if (fence != -1)
				gl.glDeleteSync(fence);
		}
		
		public void resetVAO(GL3 gl){
			int[] buffers = new int[1];
			buffers[0] = vertexArrayObject;
			gl.glDeleteVertexArrays(1, buffers, 0);
			
			gl.glGenVertexArrays(1, buffers, 0);
			this.vertexArrayObject = buffers[0];
		}
	}
}
