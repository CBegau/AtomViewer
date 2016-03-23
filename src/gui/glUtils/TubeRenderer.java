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
import java.util.ArrayList;
import java.util.List;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

public class TubeRenderer {
	
	private static int maxPathLength = 0;
	private static VertexDataStorage vds_tube = null;
	private static VertexDataStorage vds_cap = null;
	
	private static final int[] tubeIndizes = {7,15, 6,14, 5,13, 4,12, 3,11, 2,10, 1,9, 0,8, 7,15};
	
	
	public static void drawTube(GL3 gl, List<Vec3> path, float thickness){
		//Check if the direction changes between two consecutive points by more than 90°
		// If this is the case, draw the tube with multiple segments
		List<Vec3> partialPath = new ArrayList<Vec3>(path.size());
		
		partialPath.add(path.get(0));
		Vec3 dir = getDirectionInPath(path, 0);
		for (int i=1; i<path.size(); i++){
			Vec3 dir2 = getDirectionInPath(path, i);
			if (dir.dot(dir2) < 0f){ //Direction changes more than 90°
				partialPath.add(path.get(i));
				renderTube(gl, partialPath, thickness);
				partialPath.clear();
			}
			dir = dir2;
			partialPath.add(path.get(i));
		}
		
		if (partialPath.size()>1)
			renderTube(gl, partialPath, thickness);
	}
	
	public static void dispose(GL3 gl){
		if (vds_cap!=null) vds_cap.dispose(gl);
		if (vds_tube!=null) vds_tube.dispose(gl);
		vds_cap = null;
		vds_tube = null;
		maxPathLength = 0;
	}
	
	private static void renderTube(GL3 gl, List<Vec3> path, float thickness){
		Vec3[] directions = new Vec3[path.size()];
		for (int i=0; i<path.size(); i++)
			directions[i] = getDirectionInPath(path, i);
		
		if (path.size()>maxPathLength){
			if (vds_tube != null) vds_tube.dispose(gl); 
			vds_tube = new VertexDataStorageDirect(gl, 8*path.size(), 3, 3, 0, 0, 0, 0, 0, 0);
			vds_tube.setIndices(gl, tubeIndizes);
			maxPathLength = path.size();
		}
		if (vds_cap == null){
			vds_cap = new VertexDataStorageLocal(gl, 20, 3, 3, 0, 0, 0, 0, 0, 0);
		}
		
		if (path.size()<2) throw new IllegalArgumentException("Cannot render tube");
		Vec3 center = path.get(0);
		Vec3 normal1 = directions[0];
		Vec3[] normal = new Vec3[8];
		Vec3[] points = new Vec3[8];
		Vec3 u;
		
		if (Math.abs(normal1.x) >= Math.abs(normal1.y)) u = new Vec3(-normal1.z, 0f, normal1.x);
		else u = new Vec3(0f, normal1.z, -normal1.y);
		
		u.normalize().multiply(thickness*0.5f);
		Vec3 v = normal1.cross(u);
		v.normalize().multiply(thickness*0.5f);
		
		Vec3 uMul = u.multiplyClone(0.7071067811865475727f);
		Vec3 vMul = v.multiplyClone(0.7071067811865475727f);
		
		normal[0] = u;
		normal[1] = uMul.addClone(vMul);
		normal[2] = v;
		normal[3] = vMul.subClone(uMul);
		normal[4] = u.multiplyClone(-1f);
		normal[5] = uMul.addClone(vMul).multiply(-1f);
		normal[6] = v.multiplyClone(-1f);
		normal[7] = uMul.subClone(vMul);
		
		for (int i=0; i<8; i++)
			points[i] = center.addClone(normal[i]);
		
		vds_cap.beginFillBuffer(gl);
		makeCap(gl, path.get(0), points, normal1, true);
		
		Vec3[] points2 = points;
		
		vds_tube.beginFillBuffer(gl);
		
		for (int i=0; i<8; i++){
			vds_tube.setNormal(normal[i].x, normal[i].y, normal[i].z);
			vds_tube.setVertex(points[i].x, points[i].y, points[i].z);
		}
		
		for (int j=1; j<path.size();j++){
			points2 = points;
			points = new Vec3[8];
			normal = new Vec3[8];
			
			Vec3 dir = directions[j-1];
			Vec3 n = directions[j].addClone(dir).normalize();
			
			float d = path.get(j).dot(n);
			float il = 1f / (dir.dot(n));
			if (il<0.) il=1;
			
			for (int i=0; i<8; i++){
				float lambda = (d-points2[i].dot(n)) * il;
				points[i] = points2[i].addClone(dir.multiplyClone(lambda));
				
				//Do not normalize normals, this is done in the shader
				normal[i] = points[i].subClone(path.get(j));
				
				vds_tube.setNormal(normal[i].x, normal[i].y, normal[i].z);
				vds_tube.setVertex(points[i].x, points[i].y, points[i].z);
			}
		}
		
		vds_tube.endFillBuffer(gl);
		vds_tube.multiDraw(gl, GL.GL_TRIANGLE_STRIP, path.size()-1, 8);
		
		makeCap(gl, path.get(path.size()-1), points, directions[directions.length-1], false);
		vds_cap.endFillBuffer(gl);
		
		vds_cap.multiDraw(gl, GL.GL_TRIANGLE_FAN, 2, 10);
	}
	
	private static void makeCap(GL3 gl, Vec3 center, Vec3[] points, Vec3 normal, boolean inverseOrder){		
		if (!inverseOrder) {
			vds_cap.setNormal(normal.x, normal.y, normal.z);
			vds_cap.setVertex(center.x, center.y, center.z);
			
			vds_cap.setVertex(points[0].x, points[0].y, points[0].z);
			vds_cap.setVertex(points[1].x, points[1].y, points[1].z);
			vds_cap.setVertex(points[2].x, points[2].y, points[2].z);
			vds_cap.setVertex(points[3].x, points[3].y, points[3].z);
			vds_cap.setVertex(points[4].x, points[4].y, points[4].z);
			vds_cap.setVertex(points[5].x, points[5].y, points[5].z);
			vds_cap.setVertex(points[6].x, points[6].y, points[6].z);
			vds_cap.setVertex(points[7].x, points[7].y, points[7].z);
			vds_cap.setVertex(points[0].x, points[0].y, points[0].z);
		} else {
			vds_cap.setNormal(-normal.x, -normal.y, -normal.z);
			vds_cap.setVertex(center.x, center.y, center.z);
			
			vds_cap.setVertex(points[0].x, points[0].y, points[0].z);
			vds_cap.setVertex(points[7].x, points[7].y, points[7].z);
			vds_cap.setVertex(points[6].x, points[6].y, points[6].z);
			vds_cap.setVertex(points[5].x, points[5].y, points[5].z);
			vds_cap.setVertex(points[4].x, points[4].y, points[4].z);
			vds_cap.setVertex(points[3].x, points[3].y, points[3].z);
			vds_cap.setVertex(points[2].x, points[2].y, points[2].z);
			vds_cap.setVertex(points[1].x, points[1].y, points[1].z);
			vds_cap.setVertex(points[0].x, points[0].y, points[0].z);
		}
	}
	
	
	public static Vec3 getDirectionInPath(List<Vec3> path, int i){
		assert (i>0 || i<path.size()-1);
		if (i==path.size()-1)
			return path.get(i).subClone(path.get(i-1));
		else if (i==0)
			return path.get(1).subClone(path.get(0));
		else {
			return path.get(i+1).subClone(path.get(i));
		}
	}
}