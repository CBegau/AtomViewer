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

package model.mesh;

import common.Vec3;

public class FinalizedTriangle implements MeshElement{
	Vec3 a, b, c;
	Vec3 normal;
	
	public FinalizedTriangle(Vec3 a, Vec3 b, Vec3 c){
		this.a = a;
		this.b = b;
		this.c = c;
		this.normal = Vec3.makeNormal(a, b, c);
	}
	
	@Override
	public Vec3 getUnitNormalVector() {
		return normal;
	}

	@Override
	public Vec3 getNormalVector() {
		return normal;
	}
	
	public Vec3 getVertex() {
		return a;
	}

	@Override
	public boolean isPointInMesh(Vec3 p) {
		Vec3 n = a.subClone(p);
		//If the point is opposite of the face, it cannot be inside
		float d = normal.dot(n); 
		if ( d > 0f) return false;
		return true;
	}
	
	public float getMinSqrDist(Vec3 p){
		Vec3 E0 = b.subClone(a);
		Vec3 E1 = c.subClone(a);
		Vec3 D = a.subClone(p);
		
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
					return p.getSqrDistTo(this.a);
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
				return p.getSqrDistTo(this.c);
			} else if (t < 0f){
				//closest to vertex
				return p.getSqrDistTo(this.b);
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

}
