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

import gui.ViewerGLJPanel;
import gui.glUtils.CellRenderBufferRing.CellRenderBuffer;
import gui.glUtils.Shader.BuiltInShader;

import java.nio.FloatBuffer;
import java.util.List;

import common.RingBuffer;
import common.Vec3;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

import model.Atom;
import model.DataColumnInfo;

/**
 * Rendering arrows in a Gl context.
 */
public class ArrowRenderer {
	private final static float H_SQRT2 = 0.7071067811865475727f;
	private final static int FLOATS_PER_VECTOR = 14;
	
	private static int dimensionsUniform;
	private static int colorUniform;
	private static int directionUniform;
	private static int originUniform;
	
	private static VertexDataStorageDirect vds = null;
	private static Shader lastUsedShader = null;
	
	private static float[] vertices = new float[]{
		//Bottom fan
		0f,0f,
		 1f, 0f,  H_SQRT2,-H_SQRT2,  0f,-1f, -H_SQRT2, -H_SQRT2,
		-1f, 0f, -H_SQRT2, H_SQRT2,  0f, 1f,  H_SQRT2,  H_SQRT2,
		//fan at head bottom
		0f,0f,
		 1f, 0f,  H_SQRT2,-H_SQRT2, 0f,-1f,-H_SQRT2,-H_SQRT2,
		-1f, 0f, -H_SQRT2, H_SQRT2, 0f, 1f, H_SQRT2, H_SQRT2,
		//Head fan
		0f,0f,		
		 H_SQRT2, H_SQRT2, 0f, 1f, -H_SQRT2, H_SQRT2,-1f, 0f,   
		-H_SQRT2,-H_SQRT2, 0f,-1f,  H_SQRT2,-H_SQRT2, 1f, 0f,
		//Ring on shaft
		
		 H_SQRT2, H_SQRT2, 0f, 1f, -H_SQRT2, H_SQRT2, -1f, 0f,
		-H_SQRT2,-H_SQRT2, 0f,-1f,  H_SQRT2,-H_SQRT2,  1f, 0f,
		 H_SQRT2, H_SQRT2, 0f, 1f, -H_SQRT2, H_SQRT2, -1f, 0f,
		-H_SQRT2,-H_SQRT2, 0f,-1f,  H_SQRT2,-H_SQRT2,  1f, 0f,
	};
	
	private static float[] normal = new float[]{
		//Bottom fan
		-1f,0f,0f,
		-1f,0f,0f, -1f,0f,0f, -1f,0f,0f, -1f,0f,0f,
		-1f,0f,0f, -1f,0f,0f, -1f,0f,0f, -1f,0f,0f,
		//fan at head bottom
		-1f,0f,0f,
		-1f,0f,0f, -1f,0f,0f, -1f,0f,0f, -1f,0f,0f,
		-1f,0f,0f, -1f,0f,0f, -1f,0f,0f, -1f,0f,0f,
		//Head fan
		1f,0f,0f,
		0f,1f,0f, 0f,1f,0f, 0f,1f,0f, 0f,1f,0f,   
		0f,1f,0f, 0f,1f,0f, 0f,1f,0f, 0f,1f,0f, 
		//shaft
		0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,
		0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,
		0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,
		0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,
	};
	
	//thickness, headThickness, shift, length
	private static float[] multipliers = new float[]{
		//Bottom fan
		0f,0f,0f,0f,		//0-8
		1f, 0f, 0f, 0f,  1f, 0f, 0f, 0f,   1f, 0f, 0f, 0f,   1f, 0f, 0f, 0f,
		1f, 0f, 0f, 0f,  1f, 0f, 0f, 0f,   1f, 0f, 0f, 0f,   1f, 0f, 0f, 0f,
		//fan at head bottom
		0f, 0f, 1f, 0f,		//9-17
		0f, 1f, 1f, 0f,  0f, 1f, 1f, 0f,  0f, 1f, 1f, 0f,  0f, 1f, 1f, 0f,
		0f, 1f, 1f, 0f,  0f, 1f, 1f, 0f,  0f, 1f, 1f, 0f,  0f, 1f, 1f, 0f,
		//Head fan
		0f, 0f, 0f, 1f,		//10-26
		0f, 1f, 1f, 0f,  0f, 1f, 1f, 0f,  0f, 1f, 1f, 0f,  0f, 1f, 1f, 0f,
		0f, 1f, 1f, 0f,  0f, 1f, 1f, 0f,  0f, 1f, 1f, 0f,  0f, 1f, 1f, 0f,
		//Ring on shaft		
		1f, 0f, 0f, 0f,  1f, 0f, 0f, 0f,  1f, 0f, 0f, 0f,  1f, 0f, 0f, 0f, //27-34
		1f, 0f, 0f, 0f,  1f, 0f, 0f, 0f,  1f, 0f, 0f, 0f,  1f, 0f, 0f, 0f,
		1f, 0f, 1f, 0f,  1f, 0f, 1f, 0f,  1f, 0f, 1f, 0f,  1f, 0f, 1f, 0f, //35-42
		1f, 0f, 1f, 0f,  1f, 0f, 1f, 0f,  1f, 0f, 1f, 0f,  1f, 0f, 1f, 0f,
	};
	
	private static int[] indices = new int[]{
		0, 1, 2,  0, 2, 3,  0, 3, 4,  0, 4, 5,  0, 5, 6,  0, 6, 7,  0, 7, 8,  0, 8, 1, //Bottom fan 
		9, 10, 11, 9, 11, 12, 9, 12, 13, 9, 13, 14, 9, 14, 15, 9, 15, 16, 9, 16, 17, 9, 17, 10, //fan at head bottom
		18, 19, 20, 18, 20, 21, 18, 21, 22, 18, 22, 23, 18, 23, 24, 18, 24, 25, 18, 25, 26, 18, 26, 19, //Head fan
		
		27,28,36, 36,28,37,
		28,29,37, 37,29,38,
		29,30,38, 38,30,39,
		30,31,39, 39,31,40,
		31,32,40, 40,32,41,
		32,33,41, 41,33,42,
		33,34,42, 42,34,35,
		34,27,35, 35,27,36,
	};
	
	private ViewerGLJPanel viewer;
	
	public ArrowRenderer(ViewerGLJPanel viewer, GL3 gl){
		this.viewer = viewer;
	}
	
	/**
	 * Draws data as vectors
	 * Only functional for deferred rendering
	 * @param gl
	 * @param ord
	 * @param picking
	 * @param dciVector A vector based DataColumnInfo object
	 * @param scalingFactor
	 * @param normalize
	 */
	public void drawVectors(GL3 gl, ObjectRenderData<Atom> ord, boolean picking, DataColumnInfo dciVector,
	        float scalingFactor, float thickness, boolean normalize){
		assert(dciVector.isVectorComponent());
	    
		if (ViewerGLJPanel.openGLVersion>=3.3 && ord.isSubdivided()){
			drawVectorsInstanced(gl, ord, picking, dciVector, scalingFactor, normalize, thickness, 2f);
			return;
		}
		int xIndex = ord.getData().getDataColumnIndex(dciVector.getVectorComponents()[0]);
        int yIndex = ord.getData().getDataColumnIndex(dciVector.getVectorComponents()[1]);
        int zIndex = ord.getData().getDataColumnIndex(dciVector.getVectorComponents()[2]);
		float[] xArray = ord.getData().getDataArray(xIndex).getData();
		float[] yArray = ord.getData().getDataArray(yIndex).getData();
		float[] zArray = ord.getData().getDataArray(zIndex).getData();
		
		for (int i=0; i<ord.getRenderableCells().size(); ++i){
			ObjectRenderData<Atom>.Cell c = ord.getRenderableCells().get(i);
			
			if (c.getNumVisibleObjects() == 0) continue;
			float[] colors = c.getColorArray();
			List<Atom> objects = c.getObjects();
			boolean[] visible = c.getVisibiltyArray();
			
			for (int j=0; j<c.getNumObjects(); j++){
				if (visible[j]){
					Atom a = objects.get(j);
					int id = a.getID();
					Vec3 dir = new Vec3(xArray[id], yArray[id], zArray[id]);
					if (normalize && dir.getLengthSqr()>1e-10) dir.normalize();
					dir.multiply(scalingFactor);
					
					float[] col; 
					if (picking) col = viewer.getNextPickingColor(a);
					else col = new float[]{colors[3*j], colors[3*j+1], colors[3*j+2], 1f};
					
					ArrowRenderer.renderArrow(gl, a, dir, thickness, 2f, col, true);
				}
			}
		}
	}
	
	private void drawVectorsInstanced(GL3 gl, ObjectRenderData<Atom> ord, boolean picking, DataColumnInfo dciVector,
	        float scalingFactor, boolean normalize, float thickness, float headThickScale){
		VertexDataStorage.unbindAll(gl);
		
		//Select the rendering shader
		Shader shader = BuiltInShader.ARROW_INSTANCED_DEFERRED.getShader();
		
		if (vds == null) ArrowRenderer.initVDS(gl, shader);
		
		shader.enable(gl);
		
		RingBuffer<CellRenderBuffer> cellRenderRingBuffer = CellRenderBufferRing.getCellBufferRing(gl, FLOATS_PER_VECTOR);
		//Initialize all vertex attrib arrays
		CellRenderBuffer cbr = cellRenderRingBuffer.getCurrent();
		for (int i=0; i<cellRenderRingBuffer.size(); i++){
			cbr.resetVAO(gl);
			gl.glBindVertexArray(cbr.vertexArrayObject);
			
			Shader.disableLastUsedShader(gl);
			shader.enable(gl);
			//Bind fixed arrow geometry
			vds.bind(gl);
			
			gl.glBindBuffer(cbr.bufferType, cbr.buffer);
			gl.glVertexAttribPointer(Shader.ATTRIB_COLOR, 4, GL.GL_FLOAT, false, FLOATS_PER_VECTOR*Float.SIZE/8, 0);
			gl.glVertexAttribDivisor(Shader.ATTRIB_COLOR, 1);
			gl.glVertexAttribPointer(Shader.ATTRIB_VERTEX_OFFSET, 3, GL.GL_FLOAT, false, FLOATS_PER_VECTOR*Float.SIZE/8, 4*Float.SIZE/8);
			gl.glVertexAttribDivisor(Shader.ATTRIB_VERTEX_OFFSET, 1);
			gl.glVertexAttribPointer(Shader.ATTRIB_CUSTOM2, 3, GL.GL_FLOAT, false, FLOATS_PER_VECTOR*Float.SIZE/8, 7*Float.SIZE/8);
			gl.glVertexAttribDivisor(Shader.ATTRIB_CUSTOM2, 1);
			gl.glVertexAttribPointer(Shader.ATTRIB_CUSTOM3, 4, GL.GL_FLOAT, false, FLOATS_PER_VECTOR*Float.SIZE/8, 10*Float.SIZE/8);
			gl.glVertexAttribDivisor(Shader.ATTRIB_CUSTOM3, 1);
			
			gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, vds.getElementArrayBufferIndex());
			
			cbr = cellRenderRingBuffer.getNext();
		}
		gl.glBindVertexArray(viewer.getDefaultVAO());
		
		viewer.updateModelViewInShader(gl, shader, viewer.getModelViewMatrix(), viewer.getProjectionMatrix());
		
		ord.sortCells(viewer.getModelViewMatrix());

        int xIndex = ord.getData().getDataColumnIndex(dciVector.getVectorComponents()[0]);
        int yIndex = ord.getData().getDataColumnIndex(dciVector.getVectorComponents()[1]);
        int zIndex = ord.getData().getDataColumnIndex(dciVector.getVectorComponents()[2]);
        float[] xArray = ord.getData().getDataArray(xIndex).getData();
        float[] yArray = ord.getData().getDataArray(yIndex).getData();
        float[] zArray = ord.getData().getDataArray(zIndex).getData();
		
		for (int j=0; j<ord.getRenderableCells().size(); j++){
			ObjectRenderData<Atom>.Cell c = ord.getRenderableCells().get(j);

			//Cells are order by visibility, with empty cells at the end of the list
			//Stop at the first empty block
			if (c.getNumVisibleObjects() == 0) break; 
									
			gl.glBindVertexArray(cbr.vertexArrayObject);
			gl.glBindBuffer(cbr.bufferType, cbr.buffer);
			
			FloatBuffer buf = gl.glMapBufferRange(cbr.bufferType,0, cbr.bufferSize, 
					 GL3.GL_MAP_WRITE_BIT | GL3.GL_MAP_INVALIDATE_BUFFER_BIT).asFloatBuffer();
			
			float[] colors = c.getColorArray();
			List<Atom> objects = c.getObjects();
			boolean[] visible = c.getVisibiltyArray();
			int renderElements = 0;
			//Fill render buffer, color values is either the given value or a picking color
			try{
			
			for (int i=0; i<c.getNumObjects(); i++){
				if (visible[i]){
					Atom a = objects.get(i);
					int id = a.getID();
					Vec3 dir = new Vec3(xArray[id], yArray[id], zArray[id]);
					
					if (normalize && dir.getLengthSqr()>1e-10) dir.normalize();
					dir.multiply(scalingFactor);
					
					float length = dir.getLength();
					float t = thickness;
					if (t > length * 2) t = length * .1f;
					float headThickness = headThickScale*t;
					
					//Color
					if (picking){
						float[] col = viewer.getNextPickingColor(a);
						buf.put(col[0]); buf.put(col[1]); buf.put(col[2]); buf.put(col[3]);
					} else {
						buf.put(colors[3*i]); buf.put(colors[3*i+1]); buf.put(colors[3*i+2]); buf.put(1f);
					}
					//Origin
					buf.put(a.x); buf.put(a.y); buf.put(a.z);
					//Direction
					buf.put(dir.x); buf.put(dir.y); buf.put(dir.z);
					//Scalings
					buf.put(thickness); buf.put(headThickness); buf.put(length-2f*headThickness);  buf.put(length);
					renderElements++;
				}
			}
			
			}catch(Exception e){
				e.printStackTrace();
			}
			
			gl.glUnmapBuffer(cbr.bufferType);
			gl.glDrawElementsInstanced(GL.GL_TRIANGLES, indices.length, GL.GL_UNSIGNED_INT, 0, renderElements);

			cbr = cellRenderRingBuffer.getNext();
		}
		
		gl.glBindVertexArray(viewer.getDefaultVAO());
		VertexDataStorage.unbindAll(gl);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
		
		Shader.disableLastUsedShader(gl);
	}
	
	private static void initVDS(GL3 gl,Shader s){
		vds = new VertexDataStorageDirect(gl, vertices.length/2, 2, 0, 0, 0, 4, 3, 0, 0);
		vds.beginFillBuffer(gl);
		for (int i = 0; i < vertices.length/2; i++){
			vds.setCustom1(new float[]{normal[i*3+0], normal[i*3+1], normal[i*3+2]});
			vds.setCustom0(new float[]{multipliers[i*4+0], multipliers[i*4+1], multipliers[i*4+2], multipliers[i*4+3]});
			vds.setVertex(new float[]{vertices[i*2+0], vertices[i*2+1]});
		}
		vds.endFillBuffer(gl);
		vds.setIndices(gl, indices);
	}
	
	/**
	 * Renders an arrow with a standard head size of 3 
	 * For very short arrows, the thickness is automatically adjusted if too large
	 * @param gl 
	 * @param origin Coordinate to start the arrow from 
	 * @param direction vector that defines direction and 
	 * @param thickness arrow shaft thickness
	 * @param deferred set to true for using a deferred renderer
	 */
	public static void renderArrow(GL3 gl, Vec3 origin, Vec3 direction, float thickness, float[] color, boolean deferred){
		renderArrow(gl, origin, direction, thickness, 3f, color, deferred);
	}
	
	/**
	 * Renders an arrow with a variable scaling of head to shaft thickness
	 * For very short arrows, the thickness is automatically adjusted if too large
	 * @param gl 
	 * @param origin Coordinate to start the arrow from 
	 * @param direction vector that defines direction and 
	 * @param thickness arrow shaft thickness
	 * @param headThickScale scaling factor between head an shaft thickness
	 * @param color color (RGB) of the array as a float[3] or float[4] (with alpha)
	 * @param deferred set to true for using a deferred renderer
	 */
	public static void renderArrow(GL3 gl, Vec3 origin, Vec3 direction, 
			float thickness, float headThickScale, float[] color, boolean deferred){
		Shader s; 
		if (deferred)
			s= Shader.BuiltInShader.ARROW_DEFERRED.getShader();
		else 
			s= Shader.BuiltInShader.ARROW.getShader();
		s.enable(gl);
		
		if (vds == null) ArrowRenderer.initVDS(gl, s);
		if (lastUsedShader != s){ // Get shader uniforms
			dimensionsUniform = gl.glGetUniformLocation(s.getProgram(), "Dimensions");
			colorUniform = gl.glGetUniformLocation(s.getProgram(), "Color");
			originUniform = gl.glGetUniformLocation(s.getProgram(), "Origin");
			directionUniform = gl.glGetUniformLocation(s.getProgram(), "Direction");
			lastUsedShader = s;
		}
		
		float lenght = direction.getLength();
		if (thickness > lenght * 2) thickness = lenght * .1f;
		if (lenght < 0.001f) return;
		float headThickness = headThickScale*thickness;
			
		gl.glUniform4f(dimensionsUniform, thickness, headThickness, lenght-2*headThickness, lenght);
		gl.glUniform3f(directionUniform, direction.x, direction.y, direction.z);
		gl.glUniform3f(originUniform, origin.x, origin.y, origin.z);
		if (color.length == 3) gl.glUniform4f(colorUniform, color[0], color[1], color[2], 1f);
		else gl.glUniform4f(colorUniform, color[0], color[1], color[2], color[3]);
		
		vds.draw(gl, GL.GL_TRIANGLES);
	}
	
	public void dispose(GL3 gl){
		if (vds != null) vds.dispose(gl);
		vds = null;
		CellRenderBufferRing.dispose(gl);
		lastUsedShader = null;
	}
}
