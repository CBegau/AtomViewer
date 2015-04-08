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

import java.util.ArrayList;

import model.polygrain.kdTree.*;
import common.UniqueID;
import common.Vec3;

public class Vertex extends Vec3 implements Comparable<Vertex>, MeshElement, UniqueID{	
	private final int id;
	HalfEdge neighborEdge;
	
	public Vertex(Vec3 c, int id){
		this.setTo(c);
		this.id = id;
	}
	
	public ArrayList<Vertex> getAdjacentVertices(){
		ArrayList<Vertex> v = new ArrayList<Vertex>();
		HalfEdge n = neighborEdge;
		do {
			v.add(n.vertexEnd);
			n = n.pair.next;
		} while (n!=neighborEdge);
		return v;
	}
	
	public ArrayList<Triangle> getAdjacentFaces(){
		ArrayList<Triangle> t = new ArrayList<Triangle>();
		HalfEdge n = neighborEdge;
		do {
			t.add(n.triangle);
			n = n.pair.next;
		} while (n!=neighborEdge);
		return t;
	}
	
	public Vec3 getLaplacianSmoother(){
		int size = 0;
		Vec3 s = new Vec3();
		
		HalfEdge n = neighborEdge;
		do {
			s.add(n.vertexEnd);

			n = n.pair.next;
			size++;
		} while (n!=neighborEdge);
		
		s.divide(size);
		s.sub(this);
		
		Vec3 normal = getUnitNormalVector();
		float sn = s.dot(normal); 
		
		s.sub(normal.multiply(sn));
		return s;
	}
	
	@Override
	public Vec3 getUnitNormalVector() {
		return getNormalVector().normalize();
	}
	
	@Override
	public Vec3 getNormalVector() {
		Vec3 normal = new Vec3(); 
		HalfEdge n = neighborEdge;
		do {
			Vec3 no = n.triangle.getUnitNormalVector();
			normal.add(no);
			n = n.pair.next;
		} while (n!=neighborEdge);
		return normal;
	}
	
	@Override
	public boolean isPointInMesh(Vec3 p){
		HalfEdge n = neighborEdge;
		do {
			if (!n.triangle.isPointInMesh(p)) return false;
			n = n.pair.next;
		} while (n!=neighborEdge);
		return true;
	}
	
	public void shrink(KDTree<? extends Vec3> tree){
		Vec3 p1 = tree.getNearest(this);
		this.add(p1.subClone(this).multiply(0.4f));
	}
	
	public HalfEdge getNeighborEdge() {
		return neighborEdge;
	}
	
	public int getID() {
		return id;
	}
	
	@Override
	public int compareTo(Vertex o) {
		return this.id-o.id;
	}
	
	@Override
	public boolean equals(Object obj) {
		return this == obj;
	}
}
