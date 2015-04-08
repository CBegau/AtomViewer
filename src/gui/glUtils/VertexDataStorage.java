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

import javax.media.opengl.GL;
import javax.media.opengl.GL3;

public abstract class VertexDataStorage {
	private static VertexDataStorage lastBoundStorage = null;
	private boolean invalidated = true;
	
	public abstract void setVertex(float ... v);
	public abstract void setTexCoord(float ... t);
	public abstract void setColor(float ... c);
	public abstract void setNormal(float ... n);
	public abstract void setCustom0(float ... c);
	public abstract void setCustom1(float ... c);
	public abstract void setCustom2(float ... c);
	public abstract void setCustom3(float ... c);
	
	public abstract void draw(GL3 gl, int mode);
	public abstract void multiDraw(GL3 gl, int mode, int numDraws, int offsetPerDraw);
	
	public abstract void beginFillBuffer(GL3 gl);
	public abstract void endFillBuffer(GL3 gl);
	public abstract void setIndices(GL3 gl, int ... indices);
	public abstract void dispose(GL3 gl);
	
	public abstract void setNumElements(int numElements);
	
	abstract void bind(GL3 gl);
	
	void invalidate(){
		this.invalidated = true;
	}
	
	void bindStorageIfNeeded(GL3 gl){
		if (lastBoundStorage!=this || invalidated){
			this.bind(gl);
			invalidated = false;
		}
		lastBoundStorage = this; 
	}
	
	public static void unbindAll(GL3 gl){
		VertexDataStorage.lastBoundStorage = null;
		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
	}
}

