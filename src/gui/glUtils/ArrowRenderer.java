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

import common.Vec3;
import javax.media.opengl.GL;
import javax.media.opengl.GL3;

/**
 * Rendering arrows in a Gl context.
 * The class is not thread safe!
 */
public class ArrowRenderer {
	private final static float H_SQRT2 = 0.7071067811865475727f;
	
	private static int dimensionsUniform;
	private static int colorUniform;
	private static int directionUniform;
	private static int originUniform;
	
	private static VertexDataStorage vds = null;
	
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
		if (vds == null){
			vds = new VertexDataStorageDirect(gl, vertices.length/2, 2, 0, 0, 0, 4, 3, 0, 0);
			vds.beginFillBuffer(gl);
			for (int i = 0; i < vertices.length/2; i++){
				vds.setCustom1(new float[]{normal[i*3+0], normal[i*3+1], normal[i*3+2]});
				vds.setCustom0(new float[]{multipliers[i*4+0], multipliers[i*4+1], multipliers[i*4+2], multipliers[i*4+3]});
				vds.setVertex(new float[]{vertices[i*2+0], vertices[i*2+1]});
			}
			vds.endFillBuffer(gl);
			vds.setIndices(gl, indices);
			
			dimensionsUniform = gl.glGetUniformLocation(s.getProgram(), "Dimensions");
			colorUniform = gl.glGetUniformLocation(s.getProgram(), "Color");
			originUniform = gl.glGetUniformLocation(s.getProgram(), "Origin");
			directionUniform = gl.glGetUniformLocation(s.getProgram(), "Direction");
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
	
	public static void dispose(GL3 gl){
		if (vds != null) vds.dispose(gl);
		vds = null;
	}
}
