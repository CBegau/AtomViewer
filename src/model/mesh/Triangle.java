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

package model.mesh;
import common.Vec3;

public class Triangle implements MeshElement{
	HalfEdge neighborEdge;
	
	public Vertex[] getVertices(){
		Vertex[] v = new Vertex[3];
		v[0] = neighborEdge.vertexEnd;
		HalfEdge next = neighborEdge.next;
		v[1] = next.vertexEnd;
		next = next.next;
		v[2] = next.vertexEnd;
		return v;
	}
	
	public Vertex getVertex(){
		return neighborEdge.vertexEnd;
	}
	
	@Override
	public Vec3 getUnitNormalVector() {
		return getNormalVector().normalize();
	}
	
	@Override
	public Vec3 getNormalVector() {
		return neighborEdge.next.vertexEnd.subClone(neighborEdge.vertexEnd).cross(
				neighborEdge.next.next.vertexEnd.subClone(neighborEdge.vertexEnd));
	}
	
	@Override
	public boolean isPointInMesh(Vec3 p){
		Vec3 n = this.getNormalVector();
		Vec3 n2 = p.subClone(neighborEdge.vertexEnd);
		
		//If the point is opposite of the face, it cannot be inside
		float d = n.dot(n2); 
		if ( d > 0f) return false;
		return true;
	}
	
	public float getArea(){
		Vertex[] v = getVertices();
		Vec3 v1 = v[1].subClone(v[0]);
		Vec3 v2 = v[2].subClone(v[0]);
		return 0.5f*v1.cross(v2).getLength();
	}
	
	boolean isBadlyShaped(){
		//Test area vs. edge length, length of the basis should not be 10 times larger than the heigth
		float area = getArea();
		float l = neighborEdge.getLength(); 
		if (area/l < l*0.1f) return true;
		l = neighborEdge.next.getLength(); 
		if (area/l < l*0.1f) return true;
		l = neighborEdge.next.next.getLength(); 
		if (area/l < l*0.1f) return true;
		return false;
	}
}
