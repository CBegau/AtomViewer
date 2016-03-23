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

import java.util.ArrayList;

import common.Vec3;

public class HalfEdge implements MeshElement{
	Vertex vertexEnd;
	HalfEdge pair;
	Triangle triangle;
	HalfEdge next;
	
	public boolean isContractable(){
		ArrayList<Vertex> adjacentV2 = pair.vertexEnd.getAdjacentVertices();
		int num = 0;
		
		HalfEdge n = vertexEnd.neighborEdge;
		do {
			if (this.next.vertexEnd != n.vertexEnd && this.pair.next.vertexEnd != n.vertexEnd 
					&& adjacentV2.contains(n.vertexEnd)) {
				return false;
			}
			n = n.pair.next;
			num++;
		} while (n!=vertexEnd.neighborEdge);
		
		if (num<=3) return false;
		return true;
	}
	
	public boolean isContractableForGivenPoint(Vec3 newCoord){
//		if (!isContractable()) return false;
		
		//Test if contraction does not flip face normals
		//Always contract to the node with the higher order
		Vertex v1 = this.vertexEnd;
		Vertex v2 = this.pair.vertexEnd;
		
		//Always contract to the node with the higher order
		if (v2.compareTo(v1)>0){
			v2 = this.pair.vertexEnd;
			v1 = this.vertexEnd;
		}
		
		Vec3 c = v1.clone();
		
		HalfEdge n = v1.neighborEdge;
		do {
			Triangle t1 = n.triangle;
			//Test only triangles, that are not deleted during edge collapse
			if (t1 != this.triangle  && t1 != this.pair.triangle){
				Vec3 normal1 = t1.getNormalVector();
				//Move node as in an merging process
				v1.setTo(newCoord);
				
				boolean badlyShaped = t1.isBadlyShaped();
				
				Vec3 normal2 = t1.getNormalVector();
				//Move node back
				v1.setTo(c);
				//Test if the face normal has flipped
				if (normal1.dot(normal2)<0 || badlyShaped) 
					return false;
			}
			n = n.pair.next;
		} while (n!=v1.neighborEdge);
		
		
		n = v2.neighborEdge;
		c = v2.clone();
		
		do {
			Triangle t1 = n.triangle;
			//Test only triangles, that are not deleted during edge collapse
			if (t1 != this.triangle  && t1 != this.pair.triangle){
			
				Vec3 normal1 = t1.getNormalVector();
				//Move node as in an merging process
				v2.setTo(newCoord);
				Vec3 normal2 = t1.getNormalVector();
				
				boolean badlyShaped = t1.isBadlyShaped();
				
				//Move node back
				v2.setTo(c);
				//Test if the face normal has flipped
				if (normal1.dot(normal2)<0 || badlyShaped) 
					return false;
			}
			n = n.pair.next;
		} while (n!=v2.neighborEdge);
		
		return true;
	}
	
	float getLength(){
		return vertexEnd.getDistTo(this.pair.vertexEnd);
	}
	
	@Override
	public Vec3 getUnitNormalVector() {
		return getNormalVector().normalize();
	}
	
	@Override
	public Vec3 getNormalVector() {
		Vec3 t1n = triangle.getUnitNormalVector();
		Vec3 t2n = pair.triangle.getUnitNormalVector();
		t1n.add(t2n);
		return t1n;
	}
	
	@Override
	public boolean isPointInMesh(Vec3 p){
		//Decide if the local triangles are convex or concave
		Vec3 norm = triangle.getNormalVector();
		Vec3 dir = pair.next.vertexEnd.subClone(pair.vertexEnd);
		
		float d = norm.dot(dir); 
		
		if (d < 0f){
			//convex
			if (triangle.isPointInMesh(p) && pair.triangle.isPointInMesh(p)) return true;
			else return false;
		} else {
			//concave
			if (triangle.isPointInMesh(p) || pair.triangle.isPointInMesh(p)) return true;
			else return false;
		}
	}
}
