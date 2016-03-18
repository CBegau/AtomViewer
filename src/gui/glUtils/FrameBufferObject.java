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
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

import com.jogamp.opengl.FBObject;

public class FrameBufferObject extends FBObject{
	
	private FBObject.TextureAttachment colorTexture, normalTexture, positionTexture;
	private boolean deferredRendering;
	private boolean filtered = false;
	
	public FrameBufferObject(int minWidth, int minHeight, GL3 gl){
		this(minWidth, minHeight, gl, false, true);
	}
	
	public FrameBufferObject(int minWidth, int minHeight, GL3 gl, boolean deferredRendering, boolean depthBuffer){
		super();
		this.init(gl, minWidth, minHeight, 0);
		this.deferredRendering = deferredRendering;
		
		if(depthBuffer)
			this.attachRenderbuffer(gl, GL3.GL_DEPTH_COMPONENT24);
		this.colorTexture = this.attachTexture2D(gl, 0, GL3.GL_RGBA ,GL3.GL_RGBA ,GL3.GL_UNSIGNED_BYTE, 
				GL3.GL_NEAREST, GL3.GL_NEAREST, GL.GL_CLAMP_TO_EDGE,GL.GL_CLAMP_TO_EDGE);
		
		if (deferredRendering){
			this.normalTexture = this.attachTexture2D(gl, 1, GL3.GL_RGBA32F, GL3.GL_RGBA, GL3.GL_FLOAT, 
					GL3.GL_NEAREST, GL3.GL_NEAREST, GL.GL_CLAMP_TO_EDGE,GL.GL_CLAMP_TO_EDGE);
			this.positionTexture = this.attachTexture2D(gl, 2, GL3.GL_RGBA32F, GL3.GL_RGBA, GL3.GL_FLOAT, 
					GL3.GL_NEAREST, GL3.GL_NEAREST, GL.GL_CLAMP_TO_EDGE,GL.GL_CLAMP_TO_EDGE);
		}
		
		if (getStatus() != GL.GL_FRAMEBUFFER_COMPLETE)
			throw new RuntimeException(getStatusString());
	}
	
	public BufferedImage textureToBufferedImage(int width, int height, GL3 gl){
		if (width > this.getWidth()) width = this.getWidth();
		if (height > this.getHeight()) height = this.getHeight();
		
		ByteBuffer bb = ByteBuffer.allocate(this.getHeight()*this.getWidth()*4);
		gl.glBindTexture(GL.GL_TEXTURE_2D, colorTexture.getName());
		gl.glGetTexImage(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
		gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
		
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		WritableRaster wr = bi.getRaster();
		int[] color = new int[3];
		for (int x = 0; x<width; x++){
			for (int y = 0; y<height; y++){
				int pos = (y*this.getWidth()+x)*4;
				color[0] = (int)bb.get(pos);
				color[1] = (int)bb.get(pos+1);
				color[2] = (int)bb.get(pos+2);
				wr.setPixel(x, height-y-1, color);
			}
		}
		return bi;
	}
	
	public void unbind(GL3 gl){
		super.unbind(gl);
		gl.glDrawBuffers(1, new int[]{GL3.GL_COLOR_ATTACHMENT0}, 0);
	}
	
	public void bind(GL3 gl, boolean filter){
		super.bind(gl);
		
		if (filter != filtered){		
			int state = filtered ? GL3.GL_LINEAR : GL3.GL_NEAREST;

			gl.glBindTexture(GL3.GL_TEXTURE_2D, colorTexture.getName());
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, state);
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, state);	
		
			if (deferredRendering){
				gl.glBindTexture(GL3.GL_TEXTURE_2D, normalTexture.getName());
				gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, state);
				gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, state);
				
				gl.glBindTexture(GL3.GL_TEXTURE_2D, positionTexture.getName());
				gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, state);
				gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, state);
						
			} 
			filter = filtered;
		}
		
		if (deferredRendering)
			gl.glDrawBuffers(3, new int[]{GL3.GL_COLOR_ATTACHMENT0, GL3.GL_COLOR_ATTACHMENT1, GL3.GL_COLOR_ATTACHMENT2}, 0);
		else 
			gl.glDrawBuffers(1, new int[]{GL3.GL_COLOR_ATTACHMENT0}, 0);
	}
	
	public int getColorTextureName(){
		return colorTexture.getName();
	}
	
	public int getNormalTextureName(){
		return normalTexture.getName();
	}
	
	public int getPositionTextureName(){
		return positionTexture.getName();
	}
}
