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

package model.polygrain.mesh;

import gui.glUtils.VertexDataStorageLocalArrays;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;

import javax.media.opengl.GL;
import javax.media.opengl.GL3;

import common.Vec3;

public class FinalMesh {

	private int[] triangles;
	private int[] renderIndices;
	private float[] vertices;
	private double volume = -1.;
	private double area = -1.;
	private float[] normals;
	
	public FinalMesh(Collection<Vertex> vertices, Collection<Triangle> triangles){
		this.triangles = new int[triangles.size()*3];
		this.vertices = new float[vertices.size()*3];
		
		HashMap<Vertex, Integer> vertexMap = new HashMap<Vertex, Integer>();
		
		int i=0;
		for (Vertex v : vertices){
			vertexMap.put(v,i);
			this.vertices[i+0] = v.x;
			this.vertices[i+1] = v.y;
			this.vertices[i+2] = v.z;
			i+=3;
		}
		
		i=0;
		for (Triangle t : triangles){
			this.triangles[i+0] = vertexMap.get(t.neighborEdge.vertexEnd);
			HalfEdge next = t.neighborEdge.next;
			this.triangles[i+1] = vertexMap.get(next.vertexEnd);
			next = next.next;
			this.triangles[i+2] = vertexMap.get(next.vertexEnd);
			i+=3;
		}
		
		this.renderIndices = new int[this.triangles.length];
		for (i = 0; i< renderIndices.length; i++)
			renderIndices[i] = this.triangles[i]/3;
		
		makeNormals();
	}
	
	public FinalMesh(int[] triangles, float[] vertices){
		this.triangles = triangles;
		this.vertices = vertices;
		
		this.renderIndices = new int[triangles.length];
		for (int i = 0; i< renderIndices.length; i++)
			renderIndices[i] = triangles[i]/3;
		
		makeNormals();
	}
	
	private void makeNormals(){
		this.normals = new float[vertices.length];
		
		for (int i=0; i<triangles.length; i+=3){
			Vec3 n = getTriangleUnitNormal(i);
			int k = triangles[i+0];
			normals[k] += n.x; normals[k+1] += n.y; normals[k+2] += n.z;
			k = triangles[i+1];
			normals[k] += n.x; normals[k+1] += n.y; normals[k+2] += n.z;
			k = triangles[i+2];
			normals[k] += n.x; normals[k+1] += n.y; normals[k+2] += n.z;
		}
		
		for (int i=0; i<this.normals.length; i+=3){
			float l = (float)(1./Math.sqrt(this.normals[i+0]*this.normals[i+0] + this.normals[i+1]*this.normals[i+1] + 
					this.normals[i+2]*this.normals[i+2]));
			this.normals[i+0] *= l;
			this.normals[i+1] *= l;
			this.normals[i+2] *= l;
		}
	}
	
	public void renderMesh(GL3 gl){
		VertexDataStorageLocalArrays vdsa = new VertexDataStorageLocalArrays(gl, 3, 3, 0, 0, 0, 0, 0, 0);
		vdsa.beginFillBuffer(gl);
		vdsa.setNormal(normals);
		vdsa.setVertex(vertices);
		vdsa.endFillBuffer(gl);
		vdsa.setIndices(gl, renderIndices);
		vdsa.draw(gl, GL.GL_TRIANGLES);
		vdsa.dispose(gl);
	}
	
	private Vec3 getTriangleUnitNormal(int index){
		float v2_v1_x = vertices[triangles[index+1]+0] - vertices[triangles[index]+0];
		float v2_v1_y = vertices[triangles[index+1]+1] - vertices[triangles[index]+1];
		float v2_v1_z = vertices[triangles[index+1]+2] - vertices[triangles[index]+2];
		
		float v3_v1_x = vertices[triangles[index+2]+0] - vertices[triangles[index]+0];
		float v3_v1_y = vertices[triangles[index+2]+1] - vertices[triangles[index]+1];
		float v3_v1_z = vertices[triangles[index+2]+2] - vertices[triangles[index]+2];
		
		Vec3 norm = new Vec3(v2_v1_y*v3_v1_z-v2_v1_z*v3_v1_y,
				v2_v1_z*v3_v1_x-v2_v1_x*v3_v1_z,
				v2_v1_x*v3_v1_y-v2_v1_y*v3_v1_x);
		
		norm.normalize();
		return norm;
	}
	
	public float getTriangleArea(int index){
		float v2_v1_x = vertices[triangles[index+1]+0] - vertices[triangles[index]+0];
		float v2_v1_y = vertices[triangles[index+1]+1] - vertices[triangles[index]+1];
		float v2_v1_z = vertices[triangles[index+1]+2] - vertices[triangles[index]+2];
		
		float v3_v1_x = vertices[triangles[index+2]+0] - vertices[triangles[index]+0];
		float v3_v1_y = vertices[triangles[index+2]+1] - vertices[triangles[index]+1];
		float v3_v1_z = vertices[triangles[index+2]+2] - vertices[triangles[index]+2];
		
		Vec3 norm = new Vec3(v2_v1_y*v3_v1_z-v2_v1_z*v3_v1_y,
				v2_v1_z*v3_v1_x-v2_v1_x*v3_v1_z,
				v2_v1_x*v3_v1_y-v2_v1_y*v3_v1_x);
		
		return 0.5f*norm.getLength();
	}
	
	public int getTriangleCount(){
		return triangles.length/3;
	}
	
	public double getVolume(){
		if (volume!=-1) return volume;
		
		this.volume = 0.;
		for (int i=0; i<triangles.length; i+=3){
			Vec3 c = new Vec3(vertices[triangles[i+0]+0], vertices[triangles[i+0]+1], vertices[triangles[i+0]+2]);
			this.volume += getTriangleArea(i)*getTriangleUnitNormal(i).dot(c);
		}		
		this.volume /= 3.;
		
		return this.volume;
	}
	
	public double getArea(){
		if (area!=-1) return area;
		
		this.area = 0.;
		for (int i=0; i<triangles.length; i+=3)
			this.area += getTriangleArea(i);
		
		return this.area;
	}
	
	public void printMetaData(DataOutputStream dos, int number) throws IOException{
		dos.writeBytes("##mesh "+number+"\n");
		dos.writeBytes("###triangles "+triangles.length+" ");
		for (int i=0; i<triangles.length; i++){
			dos.writeBytes(Integer.toString(triangles[i]));
			dos.writeBytes(" ");
		}
		dos.writeBytes("\n");
		
		dos.writeBytes("###vertices "+vertices.length+" ");
		for (int i=0; i<vertices.length; i++){
			dos.writeBytes(Float.toString(vertices[i]));
			dos.writeBytes(" ");
		}
		dos.writeBytes("\n");
	}
}
