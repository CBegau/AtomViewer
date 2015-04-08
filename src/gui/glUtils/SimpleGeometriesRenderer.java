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
import common.Tupel;

public class SimpleGeometriesRenderer {
	private static VertexDataStorageDirect cube;
	private static VertexDataStorageDirect cubeIndexed;
	private static VertexDataStorageDirect sphere;
	private static VertexDataStorageDirect cylinderCap1;
	private static VertexDataStorageDirect cylinderCap2;
	private static VertexDataStorageDirect cylinderBody;
	
	private static void createCube(GL3 gl) {
		cube = new VertexDataStorageDirect(gl, 36, 3, 3, 0, 0, 0, 0, 0, 0);
		cube.beginFillBuffer(gl);
		
		cube.setNormal(0, 0, -1);
		cube.setVertex(0f, 0f, 0f); cube.setVertex(0f, 1f, 0f); cube.setVertex(1f, 1f, 0f);
		cube.setVertex(1f, 1f, 0f); cube.setVertex(1f, 0f, 0f); cube.setVertex(0f, 0f, 0f); 
		
		cube.setNormal(0, 0, 1);
		cube.setVertex(0f, 0f, 1f); cube.setVertex(1f, 0f, 1f); cube.setVertex(1f, 1f, 1f);
		cube.setVertex(1f, 1f, 1f); cube.setVertex(0f, 1f, 1f); cube.setVertex(0f, 0f, 1f);
		
		cube.setNormal(0, -1, 0);
		cube.setVertex(0f, 0f, 0f); cube.setVertex(1f, 0f, 0f); cube.setVertex(1f, 0f, 1f);
		cube.setVertex(1f, 0f, 1f); cube.setVertex(0f, 0f, 1f); cube.setVertex(0f, 0f, 0f);
		
		cube.setNormal(0, 1, 0);
		cube.setVertex(0f, 1f, 0f); cube.setVertex(0f, 1f, 1f); cube.setVertex(1f, 1f, 1f);
		cube.setVertex(1f, 1f, 1f); cube.setVertex(1f, 1f, 0f); cube.setVertex(0f, 1f, 0f);
		
		cube.setNormal(-1, 0, 0);
		cube.setVertex(0f, 0f, 0f); cube.setVertex(0f, 0f, 1f); cube.setVertex(0f, 1f, 1f);
		cube.setVertex(0f, 1f, 1f); cube.setVertex(0f, 1f, 0f); cube.setVertex(0f, 0f, 0f);
		
		cube.setNormal(1, 0, 0);
		cube.setVertex(1f, 0f, 0f); cube.setVertex(1f, 1f, 0f); cube.setVertex(1f, 1f, 1f);
		cube.setVertex(1f, 1f, 1f); cube.setVertex(1f, 0f, 1f); cube.setVertex(1f, 0f, 0f);
		
		cube.endFillBuffer(gl);
	}
	
	private static void createCubeIndexed(GL3 gl) {
		int[] t = {0,1,5, 0,5,4, 1,3,7, 1,7,5, 3,2,7, 2,6,7, 2,0,6, 0,4,6, 4,5,7, 4,7,6, 2,3,0, 3,1,0};
		float[] v = {0f, 0f, 0f,   1f, 0f, 0f,   0f, 1f, 0f,   1f, 1f, 0f,
					 0f, 0f, 1f,   1f, 0f, 1f,   0f, 1f, 1f,   1f, 1f, 1f};
		
		cubeIndexed = new VertexDataStorageDirect(gl, 8, 3, 0, 0, 0, 0, 0, 0, 0);
		cubeIndexed.beginFillBuffer(gl);
		for (int i=0; i<v.length; i+=3){
			cubeIndexed.setVertex(v[i+0], v[i+1], v[i+2]);
		}
		cubeIndexed.endFillBuffer(gl);
		cubeIndexed.setIndices(gl, t);
	}
	
	private static void createCylinder(GL3 gl) {
		final int CYLINDER_RES = 32;
		cylinderCap1 = new VertexDataStorageDirect(gl, CYLINDER_RES+2, 3, 3, 0, 0, 0, 0, 0, 0);
		cylinderCap2 = new VertexDataStorageDirect(gl, CYLINDER_RES+2, 3, 3, 0, 0, 0, 0, 0, 0);
		cylinderBody = new VertexDataStorageDirect(gl, (CYLINDER_RES+1)*2, 3, 3, 0, 0, 0, 0, 0, 0);
		
		cylinderCap1.beginFillBuffer(gl);
		cylinderCap1.setNormal(0f, 0f, -1f);
		cylinderCap1.setVertex(0f, 0f, 0f);
		for (int i=0; i<CYLINDER_RES; i++){
			float s = (float)Math.sin(i*2.*Math.PI/CYLINDER_RES);
			float c = (float)Math.cos(i*2.*Math.PI/CYLINDER_RES);
			cylinderCap1.setVertex(s, c, 0f);
		}
		cylinderCap1.setVertex(0f, 1f, 0f);
		cylinderCap1.endFillBuffer(gl);
		
		cylinderCap2.beginFillBuffer(gl);
		cylinderCap2.setNormal(0f, 0f, 1f);
		cylinderCap2.setVertex(0f, 0f, 1f);
		for (int i=CYLINDER_RES; i>0; i--){
			float s = (float)Math.sin(i*2.*Math.PI/CYLINDER_RES);
			float c = (float)Math.cos(i*2.*Math.PI/CYLINDER_RES);
			cylinderCap2.setVertex(s, c, 1f);
		}
		cylinderCap2.setVertex(0f, 1f, 1f);
		cylinderCap2.endFillBuffer(gl);
		
		cylinderBody.beginFillBuffer(gl);
		for (int i=0; i<CYLINDER_RES; i++){
			float s = (float)Math.sin(i*2.*Math.PI/CYLINDER_RES);
			float c = (float)Math.cos(i*2.*Math.PI/CYLINDER_RES);
			cylinderBody.setNormal(s, c, 0f);
			cylinderBody.setVertex(s, c, 0f);
			cylinderBody.setVertex(s, c, 1f);
		}
		cylinderBody.setNormal(0f, 1f, 0f);
		cylinderBody.setVertex(0f, 1f, 0f);
		cylinderBody.setVertex(0f, 1f, 1f);
		cylinderBody.endFillBuffer(gl);
	}
	
	private static void createSphere(GL3 gl) {
		Tupel<int[], float[]> s = SphereGenerator.getSphericalVBOData(3);
		
		float[] v = s.o2;
		sphere = new VertexDataStorageDirect(gl, v.length/3, 3, 3, 0, 0, 0, 0, 0, 0);
			
		sphere.beginFillBuffer(gl);
		for (int i=0; i<v.length; i+=3){
			sphere.setNormal(v[i+0], v[i+1], v[i+2]);
			sphere.setVertex(v[i+0], v[i+1], v[i+2]);
		}
		sphere.endFillBuffer(gl);
		sphere.setIndices(gl, s.o1);
	}
	
	/**
	 * Draws a unit-sized cube at the coordinate system's origin
	 * @param gl
	 */
	public static void drawCube(GL3 gl){
		if (cube == null) createCube(gl);
		cube.draw(gl, GL.GL_TRIANGLES);
	}
	
	/**
	 * Draws a unit-sized cube at the coordinate system's origin
	 * cube has no normals specified
	 * @param gl
	 */
	public static void drawCubeWithoutNormals(GL3 gl){
		if (cubeIndexed == null) createCubeIndexed(gl);
		cubeIndexed.draw(gl, GL.GL_TRIANGLES);
	}
	
	/**
	 * Draws a unit-sized sphere at the coordinate system's origin
	 * @param gl
	 */
	public static void drawSphere(GL3 gl){
		if (sphere == null) createSphere(gl);
		sphere.draw(gl, GL.GL_TRIANGLES);
	}
	
	public static void drawCylinder(GL3 gl){
		if (cylinderCap1 == null) createCylinder(gl);
		cylinderBody.draw(gl, GL.GL_TRIANGLE_STRIP);
		cylinderCap1.draw(gl, GL.GL_TRIANGLE_FAN);
		cylinderCap2.draw(gl, GL.GL_TRIANGLE_FAN);
	}
	
	public static void dispose(GL3 gl){
		if (cube != null){
			cube.dispose(gl);
			cube = null;
		}
		if (cubeIndexed != null){
			cubeIndexed.dispose(gl);
			cubeIndexed = null;
		}
		if (sphere != null) {
			sphere.dispose(gl);
			sphere = null;
		}
		
		if (cylinderCap1 != null) {
			cylinderCap1.dispose(gl);
			cylinderCap2.dispose(gl);
			cylinderBody.dispose(gl);
			cylinderCap1 = null;
			cylinderCap2 = null;
			cylinderBody = null;
		}
	}
}
