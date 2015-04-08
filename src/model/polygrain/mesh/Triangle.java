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
import common.Tupel;
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
	
	public Tupel<Float, MeshElement> getMinSqrDistAndClosestObject(Vec3 p, Vec3 normal){
		MeshElement closest = this;
		Vec3 E0 = neighborEdge.next.vertexEnd.subClone(neighborEdge.vertexEnd);
		Vec3 E1 = neighborEdge.next.next.vertexEnd.subClone(neighborEdge.vertexEnd);
		Vec3 D = neighborEdge.vertexEnd.subClone(p);
		
		float a = E0.dot(E0);
		float b = E0.dot(E1);
		float c = E1.dot(E1);
		float d = E0.dot(D);
		float e = E1.dot(D);
		
		float det = a*c-b*b;
		float s = b*e-c*d;
		float t = b*d-a*e;
	
		if (s+t<=det){
			if (s < 0f){
				if (t < 0f){
					//closest to vertex
					return new Tupel<Float, MeshElement> (p.getSqrDistTo(neighborEdge.vertexEnd),
							neighborEdge.vertexEnd);
				} else {
					s = 0f;
					t = (e >= 0f ? 0f : (-e >= c ? 1f : -e/c));
					closest = this.neighborEdge;
				}
			} else if (t < 0f){
				t = 0f;
				s = (d >= 0f ? 0f : (-d >= a ? 1f : -d/a));
				closest = this.neighborEdge.next;
			} else {
				float invDet = 1f/det;
				s *= invDet;
				t *= invDet;
			}
		} else {
			if (s < 0){
				//closest to vertex 
				return new Tupel<Float, MeshElement> (p.getSqrDistTo(neighborEdge.next.next.vertexEnd),
						neighborEdge.next.next.vertexEnd);
				
			} else if (t < 0f){
				//closest to vertex
				return new Tupel<Float, MeshElement> (p.getSqrDistTo(neighborEdge.next.vertexEnd),
						neighborEdge.next.vertexEnd);
			} else {
				//region 1
				float numer = c+e-b-d;
				if (numer <= 0f){
					s = 0f;
				} else {
					float denom = a-2f*b+c;
					s = (numer >= denom ? 1f : numer/denom);
				}
				t = 1f-s;
				closest = this.neighborEdge.next.next;
			}
		}
		
		float f = D.dot(D);
		return new Tupel<Float, MeshElement> (a*s*s+2f*b*s*t+c*t*t+2f*d*s+2f*e*t+f, closest);
	}
	
	public float getMinSqrDist(Vec3 p, Vec3 normal){
		Vec3 E0 = neighborEdge.next.vertexEnd.subClone(neighborEdge.vertexEnd);
		Vec3 E1 = neighborEdge.next.next.vertexEnd.subClone(neighborEdge.vertexEnd);
		Vec3 D = neighborEdge.vertexEnd.subClone(p);
		
		float a = E0.dot(E0);
		float b = E0.dot(E1);
		float c = E1.dot(E1);
		float d = E0.dot(D);
		float e = E1.dot(D);
		
		float det = a*c-b*b;
		float s = b*e-c*d;
		float t = b*d-a*e;
	
		if (s+t<=det){
			if (s < 0f){
				if (t < 0f){
					//closest to vertex
					return p.getSqrDistTo(neighborEdge.vertexEnd);
				} else {
					s = 0f;
					t = (e >= 0f ? 0f : (-e >= c ? 1f : -e/c));
				}
			} else if (t < 0f){
				t = 0f;
				s = (d >= 0f ? 0f : (-d >= a ? 1f : -d/a));
			} else {
				float invDet = 1f/det;
				s *= invDet;
				t *= invDet;
			}
		} else {
			if (s < 0){
				//closest to vertex 
				return p.getSqrDistTo(neighborEdge.next.next.vertexEnd);
			} else if (t < 0f){
				//closest to vertex
				return p.getSqrDistTo(neighborEdge.next.vertexEnd);
			} else {
				//region 1
				float numer = c+e-b-d;
				if (numer <= 0f){
					s = 0f;
				} else {
					float denom = a-2f*b+c;
					s = (numer >= denom ? 1f : numer/denom);
				}
				t = 1f-s;
			}
		}
		
		float f = D.dot(D);
		return a*s*s+2f*b*s*t+c*t*t+2f*d*s+2f*e*t+f;
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
