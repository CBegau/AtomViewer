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

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

public final class VertexDataStorageLocalArrays extends VertexDataStorage {
	
	private int vertex;
	private FloatBuffer vertexBuffer;
	private int texture;
	private FloatBuffer textureBuffer;
	private int normal;
	private FloatBuffer normalBuffer;
	private int color;
	private FloatBuffer colorBuffer;
	private int cust0;
	private FloatBuffer cust0Buffer;
	private int cust1;
	private FloatBuffer cust1Buffer;
	private int cust2;
	private FloatBuffer cust2Buffer;
	private int cust3;
	private FloatBuffer cust3Buffer;
	
	private int[] boundBuffer;
	private int numElements, totalBuffers;
	
	private boolean indexed = false;
	
	public VertexDataStorageLocalArrays(GL3 gl, int vertex, int normal,
			int texture, int color, int cust0, int cust1, int cust2, int cust3) {
	
		totalBuffers = 0;
		if (vertex!=0) totalBuffers++;
		if (normal!=0) totalBuffers++;
		if (texture!=0) totalBuffers++;
		if (color!=0) totalBuffers++;
		if (cust0!=0) totalBuffers++;
		if (cust1!=0) totalBuffers++;
		if (cust2!=0) totalBuffers++;
		if (cust3!=0) totalBuffers++;
		totalBuffers++; //Indices
	
		this.vertex = vertex;
		this.normal = normal;
		this.texture = texture;
		this.color = color;
		this.cust0 = cust0;
		this.cust1 = cust1;
		this.cust2 = cust2;
		this.cust3 = cust3;
		
		boundBuffer = new int[totalBuffers];
		gl.glGenBuffers(totalBuffers, boundBuffer, 0);
	}
	
	
	@Override
	public void setVertex(float... v) {
		this.vertexBuffer = FloatBuffer.wrap(v);
	}

	@Override
	public void setTexCoord(float... t) {
		this.textureBuffer = FloatBuffer.wrap(t);
	}
	
	@Override
	public void setNormal(float... n) {
		this.normalBuffer = FloatBuffer.wrap(n);
	}

	@Override
	public void setColor(float... c) {
		this.colorBuffer = FloatBuffer.wrap(c);
	}

	@Override
	public void setCustom0(float... c) {
		this.cust0Buffer = FloatBuffer.wrap(c);
	}

	@Override
	public void setCustom1(float... c) {
		this.cust1Buffer = FloatBuffer.wrap(c);
	}

	@Override
	public void setCustom2(float... c) {
		this.cust2Buffer = FloatBuffer.wrap(c);
	}

	@Override
	public void setCustom3(float... c) {
		this.cust3Buffer = FloatBuffer.wrap(c);
	}

	@Override
	public void draw(GL3 gl, int mode) {
		this.bindStorageIfNeeded(gl);
		
		if (indexed){
			gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, boundBuffer[totalBuffers-1]);
			gl.glDrawElements(mode, numElements, GL.GL_UNSIGNED_INT, 0);
			gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
		} else 
			gl.glDrawArrays(mode, 0, numElements);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
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
	void bind(GL3 gl) {
		int usedBuffer = 0;
		//Setup one array per attribute
		if (vertex!=0){
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boundBuffer[usedBuffer++]);
			gl.glBufferData(GL.GL_ARRAY_BUFFER, vertexBuffer.capacity()*4, vertexBuffer, GL.GL_STATIC_DRAW);
			gl.glVertexAttribPointer(Shader.ATTRIB_VERTEX, vertex, GL.GL_FLOAT, false, 0, 0);
		}
		
		if (normal!=0){
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boundBuffer[usedBuffer++]);
			gl.glBufferData(GL.GL_ARRAY_BUFFER, normalBuffer.capacity()*4, normalBuffer, GL.GL_STATIC_DRAW);
			gl.glVertexAttribPointer(Shader.ATTRIB_NORMAL, normal, GL.GL_FLOAT, false, 0, 0);
		}
		
		if (texture!=0){
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boundBuffer[usedBuffer++]);
			gl.glBufferData(GL.GL_ARRAY_BUFFER, textureBuffer.capacity()*4, textureBuffer, GL.GL_STATIC_DRAW);
			gl.glVertexAttribPointer(Shader.ATTRIB_TEX0, texture, GL.GL_FLOAT, false, 0, 0);
		}
		
		if (color!=0){
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boundBuffer[usedBuffer++]);
			gl.glBufferData(GL.GL_ARRAY_BUFFER, colorBuffer.capacity()*4, colorBuffer, GL.GL_STATIC_DRAW);
			gl.glVertexAttribPointer(Shader.ATTRIB_COLOR, color, GL.GL_FLOAT, false, 0, 0);
		}

		if (cust0!=0){
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boundBuffer[usedBuffer++]);
			gl.glBufferData(GL.GL_ARRAY_BUFFER, cust0Buffer.capacity()*4, cust0Buffer, GL.GL_STATIC_DRAW);
			gl.glVertexAttribPointer(Shader.ATTRIB_CUSTOM0, cust0, GL.GL_FLOAT, false, 0, 0);
		}
		
		if (cust1!=0){
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boundBuffer[usedBuffer++]);
			gl.glBufferData(GL.GL_ARRAY_BUFFER, cust1Buffer.capacity()*4, cust1Buffer, GL.GL_STATIC_DRAW);
			gl.glVertexAttribPointer(Shader.ATTRIB_CUSTOM1, cust1, GL.GL_FLOAT, false, 0, 0);
		}
		
		if (cust2!=0){
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boundBuffer[usedBuffer++]);
			gl.glBufferData(GL.GL_ARRAY_BUFFER, cust2Buffer.capacity()*4, cust2Buffer, GL.GL_STATIC_DRAW);
			gl.glVertexAttribPointer(Shader.ATTRIB_CUSTOM2, cust2, GL.GL_FLOAT, false, 0, 0);
		}
		
		if (cust3!=0){
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boundBuffer[usedBuffer++]);
			gl.glBufferData(GL.GL_ARRAY_BUFFER, cust3Buffer.capacity()*4, cust3Buffer, GL.GL_STATIC_DRAW);
			gl.glVertexAttribPointer(Shader.ATTRIB_CUSTOM3, cust3, GL.GL_FLOAT, false, 0, 0);
		}
		if (indexed)
			gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, boundBuffer[totalBuffers-1]);
		else gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
	}

	@Override
	public void setNumElements(int numElements){
		this.numElements = numElements;
	}
	
	@Override
	public void setIndices(GL3 gl, int ... indices) {
		this.indexed = true;
		
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, boundBuffer[totalBuffers-1]);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, indices.length*4, IntBuffer.wrap(indices), GL.GL_STATIC_DRAW);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		
		this.numElements = indices.length;
	}
	
	@Override
	public void beginFillBuffer(GL3 gl) {
		this.invalidate();
	}

	@Override
	public void endFillBuffer(GL3 gl) {}

	@Override
	public void dispose(GL3 gl) {
		gl.glDeleteBuffers(totalBuffers, boundBuffer, 0);
	}

}