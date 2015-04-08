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

import javax.media.opengl.GL;
import javax.media.opengl.GL3;

public class FrameBufferObject {
	
	private int[] oldViewPort = new int[4];
	private int tex_height, tex_width, fbo, depthBuf, colorTexture = -1, normalTexture = -1, positionTexture = -1;
	private boolean destroyed;
	private boolean deferredRendering;
	
	
	public FrameBufferObject(int minWidth, int minHeight, GL3 gl){
		this(minWidth, minHeight, gl, false);
	}
	
	public FrameBufferObject(int minWidth, int minHeight, GL3 gl, boolean deferredRendering){
		this.deferredRendering = deferredRendering;
		
		tex_width = (int)Math.pow(2, Math.ceil(Math.log(minWidth)/Math.log(2)));
		tex_height = (int)Math.pow(2, Math.ceil(Math.log(minHeight)/Math.log(2)));
		
		//Adjust min-max size of texture
		int[] maxTextureSize = new int[1];
		gl.glGetIntegerv(GL3.GL_MAX_TEXTURE_SIZE, maxTextureSize, 0);
		if (tex_height > maxTextureSize[0]) tex_height = maxTextureSize[0];
		if (tex_width > maxTextureSize[0]) tex_width = maxTextureSize[0];
		if (tex_height<16) tex_height = 16;
		if (tex_width<16) tex_width = 16;
		
		int[] buf = new int[3];
		gl.glGenFramebuffers(1, buf, 0);
		fbo = buf[0];
		
		gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, fbo);
		gl.glGenRenderbuffers(1, buf, 0);
		depthBuf = buf[0];
		
		gl.glBindRenderbuffer(GL3.GL_RENDERBUFFER, depthBuf);
		gl.glRenderbufferStorage(GL3.GL_RENDERBUFFER, GL3.GL_DEPTH_COMPONENT, tex_width, tex_height);
		gl.glFramebufferRenderbuffer(GL3.GL_FRAMEBUFFER, GL3.GL_DEPTH_ATTACHMENT, GL3.GL_RENDERBUFFER, depthBuf);
		
		if (deferredRendering)
			gl.glGenTextures(3, buf, 0);
		else
			gl.glGenTextures(1, buf, 0);
		colorTexture = buf[0];
				
		gl.glBindTexture(GL3.GL_TEXTURE_2D, colorTexture);
		gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_NEAREST);
		gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_NEAREST);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
		
		
		gl.glTexImage2D(GL3.GL_TEXTURE_2D, 0, GL3.GL_RGBA,  tex_width, tex_height, 0, 
				GL3.GL_RGBA, GL3.GL_UNSIGNED_BYTE, null);
		gl.glFramebufferTexture2D(GL3.GL_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT0, GL3.GL_TEXTURE_2D, colorTexture, 0);
		
		if (deferredRendering){
			normalTexture = buf[1];
			gl.glBindTexture(GL3.GL_TEXTURE_2D, normalTexture);
			
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_NEAREST);
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_NEAREST);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
			
			gl.glTexImage2D(GL3.GL_TEXTURE_2D, 0, GL3.GL_RGBA32F,  tex_width, tex_height, 0, 
					GL3.GL_RGBA, GL3.GL_FLOAT, null);
			gl.glFramebufferTexture2D(GL3.GL_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT1, GL3.GL_TEXTURE_2D, normalTexture, 0);
		
			positionTexture = buf[2];
			gl.glBindTexture(GL3.GL_TEXTURE_2D, positionTexture);
			
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_NEAREST);
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_NEAREST);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
			gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
			
			gl.glTexImage2D(GL3.GL_TEXTURE_2D, 0, GL3.GL_RGBA32F,  tex_width, tex_height, 0, 
					GL3.GL_RGBA, GL3.GL_FLOAT, null);
			gl.glFramebufferTexture2D(GL3.GL_FRAMEBUFFER, GL3.GL_COLOR_ATTACHMENT2, GL3.GL_TEXTURE_2D, positionTexture, 0);
		}
		
		checkFBO(gl);
		destroyed = false;
	}
	
	public BufferedImage textureToBufferedImage(int width, int height, GL3 gl){
		if (width > tex_width) width = tex_width;
		if (height > tex_height) height = tex_height;
		
		ByteBuffer bb = ByteBuffer.allocate(tex_height*tex_width*4);
		gl.glBindTexture(GL.GL_TEXTURE_2D, colorTexture);
		gl.glGetTexImage(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
		gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
		
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		WritableRaster wr = bi.getRaster();
		int[] color = new int[3];
		for (int x = 0; x<width; x++){
			for (int y = 0; y<height; y++){
				int pos = (y*tex_width+x)*4;
				color[0] = (int)bb.get(pos);
				color[1] = (int)bb.get(pos+1);
				color[2] = (int)bb.get(pos+2);
				wr.setPixel(x, height-y-1, color);
			}
		}
		return bi;
	}
	
	private void checkFBO(GL3 gl) {
		int error = gl.glCheckFramebufferStatus(GL3.GL_FRAMEBUFFER);
		switch (error) {
		case GL3.GL_FRAMEBUFFER_COMPLETE:
			break;
		case GL3.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT:
			throw new RuntimeException(" FBO: Incomplete attachment");
		case GL3.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT:
			throw new RuntimeException(" FBO: Missing attachment");
		case GL3.GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS:
			throw new RuntimeException(" FBO: Incomplete dimensions");
		case GL3.GL_FRAMEBUFFER_INCOMPLETE_FORMATS:
			throw new RuntimeException(" FBO: Incomplete formats");
		case GL3.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER:
			throw new RuntimeException(" FBO: Incomplete draw buffer");
		case GL3.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER:
			throw new RuntimeException(" FBO: Incomplete read buffer");
		case GL3.GL_FRAMEBUFFER_UNSUPPORTED:
			throw new RuntimeException(" FBO: Framebufferobjects unsupported");
		}
	}
	
	public void bind(GL3 gl, boolean filter){
		gl.glBindFramebuffer(GL3.GL_DRAW_FRAMEBUFFER, fbo);
		gl.glBindTexture(GL3.GL_TEXTURE_2D, colorTexture);
		if (filter){
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_LINEAR);
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_LINEAR);	
		} else {
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_NEAREST);
			gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_NEAREST);
		}
		
		if (deferredRendering){
			gl.glBindTexture(GL3.GL_TEXTURE_2D, normalTexture);
			if (filter){
				gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_LINEAR);
				gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_LINEAR);	
			} else {
				gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_NEAREST);
				gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_NEAREST);
			}
			
			gl.glBindTexture(GL3.GL_TEXTURE_2D, positionTexture);
			if (filter){
				gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_LINEAR);
				gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_LINEAR);	
			} else {
				gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MAG_FILTER, GL3.GL_NEAREST);
				gl.glTexParameteri(GL3.GL_TEXTURE_2D, GL3.GL_TEXTURE_MIN_FILTER, GL3.GL_NEAREST);
			}
			
			
			gl.glDrawBuffers(3, new int[]{GL3.GL_COLOR_ATTACHMENT0, GL3.GL_COLOR_ATTACHMENT1, GL3.GL_COLOR_ATTACHMENT2}, 0);
		}else 
			gl.glDrawBuffers(1, new int[]{GL3.GL_COLOR_ATTACHMENT0}, 0);
		
		gl.glGetIntegerv(GL3.GL_VIEWPORT, oldViewPort, 0);
		gl.glViewport(0, 0, tex_width, tex_height);
	}
	
	public void unbind(GL3 gl){
		gl.glBindFramebuffer(GL3.GL_FRAMEBUFFER, 0);
		gl.glDrawBuffers(1, new int[]{GL3.GL_COLOR_ATTACHMENT0}, 0);
		gl.glViewport(oldViewPort[0], oldViewPort[1], oldViewPort[2], oldViewPort[3]);
	}
	
	public void destroy(GL3 gl){
		if (destroyed) return;
		gl.glDeleteFramebuffers(1, new int[] { fbo }, 0);
		gl.glDeleteRenderbuffers(1, new int[] { depthBuf }, 0);
		
		if (deferredRendering){
			gl.glDeleteTextures(3, new int[] { colorTexture, normalTexture, positionTexture }, 0);
		} else {
			gl.glDeleteTextures(1, new int[] { colorTexture }, 0);
		}
		destroyed = true;
	}
	
	public int getColorTextureName(){
		if (destroyed) throw new RuntimeException("FBO is not valid anymore");
		return colorTexture;
	}
	
	public int getNormalTextureName(){
		if (destroyed) throw new RuntimeException("FBO is not valid anymore");
		return normalTexture;
	}
	
	public int getPositionTextureName(){
		if (destroyed) throw new RuntimeException("FBO is not valid anymore");
		return positionTexture;
	}
}
