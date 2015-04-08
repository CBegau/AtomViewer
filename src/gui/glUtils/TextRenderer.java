// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2014  ICAMS, Ruhr-Universität Bochum
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

import gui.glUtils.Shader.BuiltInShader;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedList;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;
import javax.media.opengl.GLProfile;

import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

public class TextRenderer {

	private static final int CACHE_SIZE = 20; 
	
	private Font font;
	private Color color;
	private GLProfile glp;
	private FontMetrics fm;
	
	private LinkedList<String> textureCacheNames = new LinkedList<String>();
	private LinkedList<Texture> textureCacheTextures = new LinkedList<Texture>();
	
	public TextRenderer(Font font, GLProfile glp) {
		this.font = font;
		this.glp = glp;
		
		BufferedImage bi = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = (Graphics2D)bi.getGraphics();
		fm = g.getFontMetrics(font);
	}
	
	public void dispose(GL3 gl){
		for (Texture t : textureCacheTextures)
			t.destroy(gl);
		textureCacheTextures.clear();
	}
	
	public void setColor(GL3 gl, float r, float g, float b, float a){
		Color c = new Color(r, g, b, a);
		if (color == null || !c.equals(color)){
			this.dispose(gl);
			this.color = c;
		}
	}
	
	public void beginRendering(GL3 gl){
		Shader.pushShader();
		gl.glEnable(GL3.GL_BLEND);
		gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
	    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
		Shader.BuiltInShader.PLAIN_TEXTURED.getShader().enable(gl);
		gl.glActiveTexture(GL3.GL_TEXTURE0);
	}
	
	public void endRendering(GL3 gl){
		gl.glActiveTexture(GL3.GL_TEXTURE0);
		Shader.popShader();
		
	}
	
	public void draw(GL3 gl, String s, float x, float y, float z, float size){
		if (s.trim().isEmpty()) return;
		VertexDataStorage vds = new VertexDataStorageLocal(gl, 4, 3, 0, 2, 0, 0, 0, 0, 0);
		
		Texture t = generateTexture(gl, s);
		
		int h = t.getImageHeight();
		int w = t.getImageWidth();
		
		float ht = h/t.getHeight();
		float wt = w/t.getWidth();
		
		vds.beginFillBuffer(gl);
		
		vds.setTexCoord(wt, ht); vds.setVertex(x+w*size,y-h*size*0.25f,z);
		vds.setTexCoord(wt, 0f); vds.setVertex(x+w*size,y+h*size*0.75f,z);
		vds.setTexCoord(0f, ht); vds.setVertex(x,y-h*size*0.25f,z);
		vds.setTexCoord(0f, 0f); vds.setVertex(x,y+h*size*0.75f,z);
		
		vds.endFillBuffer(gl);
		
		gl.glBindTexture(t.getTarget(), t.getTextureObject(gl));
		vds.bind(gl);
		
		gl.glUniform1i(gl.glGetUniformLocation(BuiltInShader.PLAIN_TEXTURED.getShader().getProgram(), "Texture0"), 0);
		
		vds.draw(gl, GL3.GL_TRIANGLE_STRIP);
		
		vds.dispose(gl);
	}
	
	public int getStringWidth(CharSequence s){
		String str = s.toString();
		return fm.stringWidth(str);
	}
	
	public int getStringHeigh(){
		return font.getSize();
	}
	
	private Texture generateTexture(GL3 gl, CharSequence s){
		String str = s.toString();
		
		//Recycle textures in a cache
		if (textureCacheNames.contains(str)){
			int index = 0;
			for (int i = 0; i<textureCacheNames.size(); i++){
				if (textureCacheNames.get(i).equals(str)){
					index = i;
					break;
				}
			}
			//Move the texture to the front
			Texture t = textureCacheTextures.remove(index);
			textureCacheTextures.addFirst(t);
			textureCacheNames.addFirst(textureCacheNames.remove(index));
			
			return t;
		} else {
			int w = fm.stringWidth(str);
			int h = font.getSize()*2;
			
			BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = (Graphics2D)bi.getGraphics();
	
			g.setColor(color);
			g.setFont(font);
			g.drawString(str, 0, h-font.getSize()/2);
			
			Texture t = AWTTextureIO.newTexture(glp, bi, false);
			
			textureCacheNames.addFirst(str);
			textureCacheTextures.addFirst(t);
			if (textureCacheNames.size() > CACHE_SIZE){
				textureCacheNames.removeLast();
				textureCacheTextures.removeLast().destroy(gl);
			}
			
			return t;
		
		}
	}
}