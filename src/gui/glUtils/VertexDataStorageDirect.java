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

package gui.glUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;

public final class VertexDataStorageDirect extends VertexDataStorage {

	private float[] texState;
	private float[] colorState;
	private float[] normalState;
	private float[] cust0State;
	private float[] cust1State;
	private float[] cust2State;
	private float[] cust3State;
	
	private int vertex;
	private int texture;
	private int normal;
	private int color;
	private int cust0;
	private int cust1;
	private int cust2;
	private int cust3;
	
	private int[] boundBuffer = new int[2];
	private int pointer = 0;
	private int numElements, totalSize;
	
	private FloatBuffer buffer;
	
	private boolean indexed = false;
	
	public VertexDataStorageDirect(GL3 gl, int size, int vertex, int normal, int texture, int color, int cust0, int cust1, int cust2, int cust3){
		this.totalSize = Float.SIZE/8 * size * (vertex+normal+texture+color+cust0+cust1+cust2+cust3);
		
		this.texState = new float[texture];
		this.colorState = new float[color];
		this.normalState = new float[normal];
		this.cust0State = new float[cust0];
		this.cust1State = new float[cust1];
		this.cust2State = new float[cust2];
		this.cust3State = new float[cust3];
		
		this.vertex = vertex;
		this.normal = normal;
		this.texture = texture;
		this.color = color;
		this.cust0 = cust0;
		this.cust1 = cust1;
		this.cust2 = cust2;
		this.cust3 = cust3;
		
		this.pointer = 0;
		this.numElements = size;
		this.boundBuffer = new int[2];
		
		gl.glGenBuffers(2, boundBuffer, 0);
	}
	
	
	@Override
	public final void setVertex(float... v) {
		assert (v.length == vertex);
		//Every time a vertex is send, the other vertex data are taken from the state
		if (pointer>=buffer.capacity())
			throw new RuntimeException("Exceeding elements in buffer");
		
		for (int i=0; i<vertex; i++)
			buffer.put(v[i]);
		for (int i=0; i<normal; i++)
			buffer.put(normalState[i]);
		for (int i=0; i<texture; i++)
			buffer.put(texState[i]);
		for (int i=0; i<color; i++)
			buffer.put(colorState[i]);
		for (int i=0; i<cust0; i++)
			buffer.put(cust0State[i]);
		for (int i=0; i<cust1; i++)
			buffer.put(cust1State[i]);
		for (int i=0; i<cust2; i++)
			buffer.put(cust2State[i]);
		for (int i=0; i<cust3; i++)
			buffer.put(cust3State[i]);
		
		pointer++;
	}

	@Override
	public void setTexCoord(float... t) {
		assert (t.length == texture);
		this.texState = t;
	}
	
	@Override
	public void setNormal(float... n) {
		assert (n.length == normal);
		this.normalState = n;		
	}

	@Override
	public void setColor(float... c) {
		assert (c.length == color);
		this.colorState = c;
	}

	@Override
	public void setCustom0(float... c) {
		assert (c.length == cust0);
		this.cust0State = c;
	}

	@Override
	public void setCustom1(float... c) {
		assert (c.length == cust1);
		this.cust1State = c;

	}

	@Override
	public void setCustom2(float... c) {
		assert (c.length == cust2);
		this.cust2State = c;
	}

	@Override
	public void setCustom3(float... c) {
		assert (c.length == cust3);
		this.cust3State = c;
	}

	@Override
	public void draw(GL3 gl, int mode) {
		this.bindStorageIfNeeded(gl);
		
		if (indexed){
			gl.glDrawElements(mode, numElements, GL.GL_UNSIGNED_INT, 0);
		} else 
			gl.glDrawArrays(mode, 0, numElements);
	}

	@Override
	public void multiDraw(GL3 gl, int mode, int numDraws, int offsetPerDraw) {
		this.bindStorageIfNeeded(gl);
		
		if (indexed){
			for (int i=0; i<numDraws; i++)
				gl.glDrawElementsBaseVertex(mode, numElements,  GL.GL_UNSIGNED_INT, 0, offsetPerDraw*i);
		} else {
			int[] offsets = new int[numDraws];
			int[] numVertex = new int[numDraws];
			
			for (int i=0; i<numDraws; i++){
				offsets[i] = i*offsetPerDraw;
				numVertex[i] = offsetPerDraw;
			}
			
			gl.glMultiDrawArrays(mode, IntBuffer.wrap(offsets), IntBuffer.wrap(numVertex), numDraws);
		}
	}

	@Override
	public void setIndices(GL3 gl, int ... indices) {
		this.indexed = true;
		
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boundBuffer[1]);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, indices.length*4, IntBuffer.wrap(indices), GL.GL_STATIC_DRAW);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		
		this.numElements = indices.length;
	}

	
	@Override
	void bind(GL3 gl) {
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boundBuffer[0]);
		
		int floatPerVertex = vertex+normal+texture+color+cust0+cust1+cust2+cust3;
		int offset = 0;
		//Setup strided array
		gl.glVertexAttribPointer(Shader.ATTRIB_VERTEX, vertex,
				GL.GL_FLOAT, false, floatPerVertex * 4, offset*4);
		offset += vertex;
		
		if (normal != 0){
			gl.glVertexAttribPointer(Shader.ATTRIB_NORMAL, normal,
					GL.GL_FLOAT, false, floatPerVertex * 4, offset*4);
			offset += normal;
		}
		
		if (texture != 0){
			gl.glVertexAttribPointer(Shader.ATTRIB_TEX0, texture,
					GL.GL_FLOAT, false, floatPerVertex * 4, offset*4);
			offset += texture;
		}

		if (color != 0){
			gl.glVertexAttribPointer(Shader.ATTRIB_COLOR, color,
					GL.GL_FLOAT, false, floatPerVertex * 4, offset*4);
			offset += color;
		}
		
		if (cust0 != 0){
			gl.glVertexAttribPointer(Shader.ATTRIB_CUSTOM0, cust0,
					GL.GL_FLOAT, false, floatPerVertex * 4, offset*4);
			offset += cust0;
		}
		
		if (cust1 != 0){
			gl.glVertexAttribPointer(Shader.ATTRIB_CUSTOM1, cust1,
					GL.GL_FLOAT, false, floatPerVertex * 4, offset*4);
			offset += cust1;
		}
		
		if (cust2 != 0){
			gl.glVertexAttribPointer(Shader.ATTRIB_CUSTOM2, cust2,
					GL.GL_FLOAT, false, floatPerVertex * 4, offset*4);
			offset += cust2;
		}
		
		if (cust3 != 0){
			gl.glVertexAttribPointer(Shader.ATTRIB_CUSTOM3, cust3,
					GL.GL_FLOAT, false, floatPerVertex * 4, offset*4);
			offset += cust3;
		}
		
		if (indexed)
			gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, boundBuffer[1]);
		else gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
	}
	
	@Override
	public void beginFillBuffer(GL3 gl) {
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boundBuffer[0]);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, totalSize, null, GL.GL_DYNAMIC_DRAW);
		buffer = gl.glMapBuffer(GL.GL_ARRAY_BUFFER, GL.GL_WRITE_ONLY).asFloatBuffer();
		pointer = 0;
		buffer.rewind();
	}

	@Override
	public void endFillBuffer(GL3 gl) {
		gl.glUnmapBuffer(GL.GL_ARRAY_BUFFER);
	}
	
	@Override
	public void setNumElements(int numElements) {
		this.numElements = numElements;
	}
	
	@Override
	public void dispose(GL3 gl) {
		gl.glDeleteBuffers(2, boundBuffer, 0);
	}
	
	public int getArrayBufferIndex(){
		return boundBuffer[0];
	}
	
	public int getElementArrayBufferIndex(){
		return boundBuffer[1];
	}

}
