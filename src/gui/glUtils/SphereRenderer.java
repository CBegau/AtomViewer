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

import gui.ViewerGLJPanel;
import gui.glUtils.CellRenderBufferRing.CellRenderBuffer;
import gui.glUtils.Shader.BuiltInShader;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;

import model.AtomData;
import model.RenderingConfiguration;
import common.*;

public class SphereRenderer {
	private static final int FLOATS_PER_SPHERE = 8;
	
	private ViewerGLJPanel viewer;
	
	//Sphere render data
	private int[] sphereVboIndices;
	private int sphereVBOIndexCount = 0;
	private int sphereVBOPrimitive = GL.GL_TRIANGLE_FAN;
	
	public SphereRenderer(ViewerGLJPanel viewer, GL3 gl) {
		this.viewer = viewer;
	}
	
	public void dispose(GL3 gl){
		CellRenderBufferRing.dispose(gl);
		
		if (sphereVboIndices != null)
			gl.glDeleteBuffers(3, sphereVboIndices, 0);
		sphereVboIndices = null;
	}
		
	public void drawSpheres(GL3 gl, ObjectRenderData<?> ard, boolean picking){
		VertexDataStorage.unbindAll(gl);
		
		if (ViewerGLJPanel.openGLVersion>=3.3 && ard.isSubdivided()){
			drawSpheresInstanced(gl.getGL3(), ard, picking);
			return;
		}
		
		gl.glDisable(GL.GL_BLEND); //Transparency can cause troubles and should be avoided, disabling blending might be faster then
		gl.glDisable(GL.GL_CULL_FACE); // The billboard is always correctly oriented, do not bother testing
		
		//Select the rendering shader
		Shader shader;
		if (RenderingConfiguration.Options.PERFECT_SPHERES.isEnabled())
			shader = BuiltInShader.SPHERE_DEFERRED_PERFECT.getShader();
		else shader = BuiltInShader.SPHERE_DEFERRED.getShader();
		
		//Set all uniforms, depending on the active shader, some may not be used
		shader.enable(gl);
		//Pass transpose (=inverse) of rotation matrix to rotate billboard normals
		gl.glUniformMatrix3fv(gl.glGetUniformLocation(shader.getProgram(), "inv_rot"), 1, true, 
				viewer.getRotationMatrix().getUpper3x3Matrix());
		

		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, sphereVboIndices[0]);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[1]);
		gl.glVertexAttribPointer(Shader.ATTRIB_VERTEX, 3, GL.GL_FLOAT, false, 0, 0);

		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[2]);
		gl.glVertexAttribPointer(Shader.ATTRIB_TEX0, 2, GL.GL_FLOAT, false, 0, 0);
		
		if (!picking){
			if (RenderingConfiguration.Options.NO_SHADING.isEnabled())
				gl.glUniform1i(gl.glGetUniformLocation(shader.getProgram(), "picking"), 1);
			else gl.glUniform1i(gl.glGetUniformLocation(shader.getProgram(), "picking"), 0);
		}

		int sphereColorUniform = gl.glGetUniformLocation(shader.getProgram(), "Color");
		int sphereTranslateUniform = gl.glGetUniformLocation(shader.getProgram(), "Move");

		for (int i=0; i<ard.getRenderableCells().size(); ++i){
			ObjectRenderData<?>.Cell c = ard.getRenderableCells().get(i);
			if (c.getNumVisibleObjects() == 0) continue;
			float[] colors = c.getColorArray();
			float[] sizes = c.getSizeArray();
			List<? extends Vec3> objects = c.getObjects();
			boolean[] visible = c.getVisibiltyArray();
			
			for (int j=0; j<c.getNumObjects(); j++){
				if (visible[j]){
					Vec3 a = objects.get(j);
					 
					if (picking){
						float[] col = viewer.getNextPickingColor(c.getObjects().get(j));
						gl.glUniform4f(sphereColorUniform, col[0], col[1], col[2], 1f);
					} else {
						gl.glUniform4f(sphereColorUniform, colors[3*j], colors[3*j+1], colors[3*j+2], 1f);
					}
					
					gl.glUniform4f(sphereTranslateUniform, a.x, a.y, a.z, sizes[j]);
	
					gl.glDrawElements(sphereVBOPrimitive, sphereVBOIndexCount, GL.GL_UNSIGNED_INT, 0);
				}
			}
		}

		//Disable vbos and switch active shader
		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		Shader.disableLastUsedShader(gl);
		
		if (!picking) gl.glEnable(GL.GL_BLEND);
		gl.glEnable(GL.GL_CULL_FACE);
	}

	private void drawSpheresInstanced(GL3 gl, ObjectRenderData<?> ard, boolean picking){
		gl.glDisable(GL.GL_BLEND); //Transparency can cause troubles and should be avoided, disabling blending might be faster then
		gl.glDisable(GL.GL_CULL_FACE); // The billboard is always correctly oriented, do not bother testing
		
		//Select the rendering shader
		Shader shader;
		if (RenderingConfiguration.Options.PERFECT_SPHERES.isEnabled())
			shader = BuiltInShader.SPHERE_INSTANCED_DEFERRED_PERFECT.getShader();
		else shader = BuiltInShader.SPHERE_INSTANCED_DEFERRED.getShader();

		// Init the shader for drawing test bounding boxes
		Shader visTestShader = BuiltInShader.VERTEX_ARRAY_COLOR_UNIFORM.getShader();
		visTestShader.enable(gl);
		int visMvmpUniform = gl.glGetUniformLocation(visTestShader.getProgram(), "mvpm");
		GLMatrix mvmp = viewer.getProjectionMatrix().clone();
		mvmp.mult(viewer.getModelViewMatrix());
		gl.glUniform4f(
				gl.glGetUniformLocation(visTestShader.getProgram(), "Color"),1f, 0f, 0f, 1f);

		shader.enable(gl);
		//Pass transpose (=inverse) of rotation matrix to rotate billboard normals
		gl.glUniformMatrix3fv(gl.glGetUniformLocation(shader.getProgram(), "inv_rot"), 1, true, 
				viewer.getRotationMatrix().getUpper3x3Matrix());
		
		RingBuffer<CellRenderBuffer> cellRenderRingBuffer = CellRenderBufferRing.getCellBufferRing(gl, FLOATS_PER_SPHERE);
		//Initialize all vertex attrib arrays
		CellRenderBuffer cbr = cellRenderRingBuffer.getCurrent();
		for (int i=0; i<cellRenderRingBuffer.size(); i++){
			cbr.resetVAO(gl);
			gl.glBindVertexArray(cbr.vertexArrayObject);
			
			Shader.disableLastUsedShader(gl);
			shader.enable(gl);
			
			gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, sphereVboIndices[0]);
			
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[1]);
			gl.glVertexAttribPointer(Shader.ATTRIB_VERTEX, 3, GL.GL_FLOAT, false, 0, 0);
			gl.glVertexAttribDivisor(Shader.ATTRIB_VERTEX, 0);
	
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[2]);
			gl.glVertexAttribPointer(Shader.ATTRIB_TEX0, 2, GL.GL_FLOAT, false, 0, 0);
			gl.glVertexAttribDivisor(Shader.ATTRIB_TEX0, 0);
			
			gl.glBindBuffer(cbr.bufferType, cbr.buffer);
			gl.glVertexAttribPointer(Shader.ATTRIB_COLOR, 4, GL.GL_FLOAT, false, 8*Float.SIZE/8, 0);
			gl.glVertexAttribDivisor(Shader.ATTRIB_COLOR, 1);
			gl.glVertexAttribPointer(Shader.ATTRIB_VERTEX_OFFSET, 4, GL.GL_FLOAT, false, 8*Float.SIZE/8, 4*Float.SIZE/8);
			gl.glVertexAttribDivisor(Shader.ATTRIB_VERTEX_OFFSET, 1);
			
			cbr = cellRenderRingBuffer.getNext();
		}
		gl.glBindVertexArray(0);
		
		ard.sortCells(viewer.getModelViewMatrix());

		boolean hasRenderedCell = true;	//Set to true if spheres have been rendered and to false in occlusion test
		
		int[] queries = new int[ard.getRenderableCells().size()];
		gl.glGenQueries(queries.length, queries, 0);
		
//		int cellsDrawn = 0;

		for (int j=0; j<ard.getRenderableCells().size(); j++){
			ObjectRenderData<?>.Cell c = ard.getRenderableCells().get(j);

			//Cells are order by visibility, with empty cells at the end of the list
			//Stop at the first empty block
			if (c.getNumVisibleObjects() == 0) break; 
			
			boolean renderCell = true;
			//Test if the cell needs to be rendered, skip the first elements
			//for which occlusion culling is not used
			if (j>cellRenderRingBuffer.size()){
				int[] result = new int[1];
				int q = j;
				gl.glGetQueryObjectuiv(queries[q], GL3.GL_QUERY_RESULT, result, 0);
				if (result[0] == GL3.GL_FALSE) renderCell=false;
			}
			
			//Full renderer
			if (renderCell){
				//Currently fencing and unsynchronized access to maps is slower than
				//synchronized maps without fences, this might change using BufferStorage in GL4.4
				if (cbr.fence != -1){
					// Wait until last call using the buffer is finished
					gl.glClientWaitSync(cbr.fence, GL3.GL_SYNC_FLUSH_COMMANDS_BIT, GL3.GL_TIMEOUT_IGNORED);
					gl.glDeleteSync(cbr.fence);
					cbr.fence = -1;
				}
				
//				cellsDrawn++;
				//Reenable masks that have been disable in the occlusion test
				if (!hasRenderedCell){
					gl.glColorMask(true, true, true, true);
					gl.glDepthMask(true);
					shader.enable(gl);
				}
								
				gl.glBindVertexArray(cbr.vertexArrayObject);
				gl.glBindBuffer(cbr.bufferType, cbr.buffer);
				
				//Unsynchronized access is useful with fencing
//				FloatBuffer buf = gl.glMapBufferRange(cbr.bufferType,0, cbr.bufferSize, 
//						 GL3.GL_MAP_WRITE_BIT | GL3.GL_MAP_INVALIDATE_BUFFER_BIT| GL3.GL_MAP_UNSYNCHRONIZED_BIT).asFloatBuffer();
				
				FloatBuffer buf = gl.glMapBufferRange(cbr.bufferType,0, cbr.bufferSize, 
						 GL3.GL_MAP_WRITE_BIT | GL3.GL_MAP_INVALIDATE_BUFFER_BIT).asFloatBuffer();
				
				float[] colors = c.getColorArray();
				float[] sizes = c.getSizeArray();
				List<? extends Vec3> objects = c.getObjects();
				boolean[] visible = c.getVisibiltyArray();
				//Fill render buffer, color values is either the given value or a picking color
				if (picking){
					for (int i=0; i<c.getNumObjects(); i++){
						if (visible[i]){
							Vec3 ra = objects.get(i);
							float[] col = viewer.getNextPickingColor(c.getObjects().get(i));
							buf.put(col[0]); buf.put(col[1]); buf.put(col[2]); buf.put(1f);
							buf.put(ra.x); buf.put(ra.y); buf.put(ra.z); buf.put(sizes[i]);
						}
					}	
				} else {
					for (int i=0; i<c.getNumObjects(); i++){
						if (visible[i]){
							Vec3 ra = objects.get(i);
							buf.put(colors[3*i]); buf.put(colors[3*i+1]); buf.put(colors[3*i+2]); buf.put(1f);
							buf.put(ra.x); buf.put(ra.y); buf.put(ra.z); buf.put(sizes[i]);
						}
					}
				}
				gl.glUnmapBuffer(cbr.bufferType);
				gl.glDrawElementsInstanced(sphereVBOPrimitive, sphereVBOIndexCount, GL.GL_UNSIGNED_INT, 0, c.getNumVisibleObjects());
				
				//Fencing currently disable
//				cbr.fence = gl.glFenceSync(GL3.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
				hasRenderedCell = true;
				cbr = cellRenderRingBuffer.getNext();
			}
			
			//Occlusion test
			if (j+cellRenderRingBuffer.size()<ard.getRenderableCells().size()){
				ObjectRenderData<?>.Cell futureCell = ard.getRenderableCells().get(j+cellRenderRingBuffer.size());
	
				if (hasRenderedCell){
					gl.glBindVertexArray(viewer.getDefaultVAO());
					gl.glColorMask(false, false, false, false);
					gl.glDepthMask(false);
					visTestShader.enable(gl);
					hasRenderedCell = false;
				}
				
				Vec3 trans = futureCell.getOffset();
				Vec3 scale = futureCell.getSize();
				GLMatrix m = mvmp.clone();
				m.translate(trans.x, trans.y, trans.z);
				m.scale(scale.x, scale.y, scale.z);

//				gl.glUniform4f(gl.glGetUniformLocation(visTestShader.getProgram(), "Color"), 1f,1f, 0f, 1f);
//				gl.glColorMask(true, true, true, true);
//				gl.glDepthMask(true);
//					
//				gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL3.GL_LINE);
				gl.glUniformMatrix4fv(visMvmpUniform, 1, false, m.getMatrix());
				gl.glBeginQuery(GL3.GL_ANY_SAMPLES_PASSED, queries[j+cellRenderRingBuffer.size()]);
				SimpleGeometriesRenderer.drawCubeWithoutNormals(gl);	//Bounding box
				gl.glEndQuery(GL3.GL_ANY_SAMPLES_PASSED);
//				gl.glPolygonMode(GL.GL_FRONT_AND_BACK, GL3.GL_FILL);
			}
		}

		//System.out.println("Drawn "+cellsDrawn+" of "+ard.getRenderableCells().size());

		gl.glDeleteQueries(queries.length, queries, 0);
		
		gl.glBindVertexArray(viewer.getDefaultVAO());
		VertexDataStorage.unbindAll(gl);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
		gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, 0);
		
		gl.glColorMask(true, true, true, true);
		gl.glDepthMask(true);
		
		Shader.disableLastUsedShader(gl);

		viewer.updateModelViewInShader(gl, visTestShader, viewer.getModelViewMatrix(), viewer.getProjectionMatrix());
		if (!picking) gl.glEnable(GL.GL_BLEND);
		gl.glEnable(GL.GL_CULL_FACE);
	}

	/**
	 * Creates oriented billboards.
	 * Depending on the visible size of atoms on screen, the detail level of billboards is adjusted,
	 * ranging from a simple flat quad, to close approximations of a hemisphere.
	 * This way, atoms do visibly correctly overlap each other on screen
	 * @param gl
	 */
	public void updateSphereRenderData(GL3 gl, AtomData atomData){
		int[] t;
		float[] v = null;
		
		float maxPixelSize = estimatePixelSizeOfAtom(atomData);
		
		float[] normal = new float[]{0.0f,0.0f,  -1f, 1f,   1f, -1f,  1f, 1f,  -1f, -1f};
		float[] v_orig = new float[]{0f,0f,1f, -1f,1f,0f,  1f,-1f,0f, 1f,1f,0f, -1f,-1f,0f};
		if (RenderingConfiguration.Options.PERFECT_SPHERES.isEnabled())
			v_orig = new float[]{0f,0f,0f, -1f,1f,0f,  1f,-1f,0f, 1f,1f,0f, -1f,-1f,0f};
		
		//A simple flat quad
		t = new int[]{4,2,1,3};
		sphereVBOPrimitive = GL.GL_TRIANGLE_STRIP;
		
		if (maxPixelSize>10 && !RenderingConfiguration.Options.PERFECT_SPHERES.isEnabled()){
			//upgrade to a pyramid of 4 triangles
			t = new int[]{0,1,4,2,3,1};
			sphereVBOPrimitive = GL.GL_TRIANGLE_FAN;
			
			if (maxPixelSize>40){ 
				//Tesselate two or three times for improved rendering
				float s = (float)(1./Math.sqrt(2.))*1f;
				v_orig = new float[]{0f,0f,1f, -s,s,0f,  s,-s,0f, s,s,0f, -s,-s,0f};
				t = new int[]{0,1,4, 0,4,2, 0,2,3, 0,3,1};
				
				Tupel<int[],float[]> data;
				int iter = maxPixelSize > 200 ? 3 : 2;
				normal = new float[v_orig.length/3*2];
				
				for (int j=0; j<iter;j++){
					data = SphereTesselator.tesselate(t, v_orig);
					t = data.o1;
					v_orig=data.o2;
					normal = new float[v_orig.length/3*2];
					
					for (int i = 0; i<v_orig.length/3; i++){
						normal[i*2]   = v_orig[i*3];
						normal[i*2+1] = v_orig[i*3+1];
						float a = 1f-(v_orig[i*3]*v_orig[i*3] + v_orig[i*3+1]*v_orig[i*3+1]);
						if (a<0f) a = 0.0f; 
						v_orig[i*3+2] = (float)Math.sqrt(a);
					}
				}
				
				sphereVBOPrimitive = GL.GL_TRIANGLES;
			}
		}
		
		GLMatrix rotInverse = viewer.getRotationMatrix().clone();
		rotInverse.inverse();
		float[] tmp = new float[16];
		rotInverse.getMatrix().get(tmp, 0, 16);
		
		v = new float[v_orig.length];
		for (int i = 0; i<v.length; i+=3){
			v[i+0] = v_orig[i] * tmp[0] + v_orig[i+1] * tmp[4] + v_orig[i+2] * tmp[8]; 
			v[i+1] = v_orig[i] * tmp[1] + v_orig[i+1] * tmp[5] + v_orig[i+2] * tmp[9];
			v[i+2] = v_orig[i] * tmp[2] + v_orig[i+1] * tmp[6] + v_orig[i+2] * tmp[10];
		}
		
		sphereVBOIndexCount = t.length;
		if (sphereVboIndices != null)
			gl.glDeleteBuffers(3, sphereVboIndices, 0);
		else  sphereVboIndices = new int[3];
		
		gl.glGenBuffers(3, sphereVboIndices, 0);
		
		IntBuffer tb = IntBuffer.wrap(t);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[0]);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, tb.capacity()*Integer.SIZE/8, tb, GL.GL_STATIC_DRAW);
		
		FloatBuffer vb = FloatBuffer.wrap(v);
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[1]);
		gl.glBufferData(GL.GL_ARRAY_BUFFER, vb.capacity()*Float.SIZE/8, vb, GL.GL_STATIC_DRAW);
		
		if (normal != null){
			FloatBuffer texb = FloatBuffer.wrap(normal);
			gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sphereVboIndices[2]);
			gl.glBufferData(GL.GL_ARRAY_BUFFER, texb.capacity()*Float.SIZE/8, texb, GL.GL_STATIC_DRAW);
		}
		
		gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
	}
	
	/**
	 * Computes the size of the largest atom on the screen in pixel.
	 * Using orthogonal projection, the value is exact, in perspective projection
	 * an approximation
	 * @return
	 */
	private float estimatePixelSizeOfAtom(AtomData data){
		if (data == null) return 1f;
		float[] sphereSize = data.getCrystalStructure().getSphereSizeScalings();
		float maxSphereSize = 0f;
		for (int i=0; i<sphereSize.length; i++){
			sphereSize[i] *= viewer.getSphereSize();
			if (maxSphereSize < sphereSize[i]) maxSphereSize = sphereSize[i];
		}
		return viewer.estimateUnitLengthInPixels()*maxSphereSize*2;
	}	
}