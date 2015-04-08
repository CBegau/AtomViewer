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

import common.*;

public class BSPTree extends ClosestTriangleSearchAlgorithm{	
	private BSPNode root;
	
	public BSPTree(float threshold) {
		super(threshold);
	}
	
	
	public void add(Triangle t){
		if (root == null){
			root = new BSPNode(t);
		} else {
			root.add(t);
		}
	}
	
	@Override
	public Tupel<Float, MeshElement> sqrDistToMeshElement(Vec3 eye){
		if (root == null) return new Tupel<Float, MeshElement>(Float.MAX_VALUE, null);
		Tupel<Float, MeshElement> best = root.distToClosestMeshElement(eye, new Tupel<Float, MeshElement>(Float.MAX_VALUE, null));
		best.o1 = Math.abs(best.o1);
		return best;
	}
	
	private class BSPNode{
		public Vec3 normal;
		public float d;
		public Triangle element;
		
		public BSPNode front = null;
		public BSPNode back = null;
		
		
		public BSPNode(Triangle t){
			this.normal = t.getUnitNormalVector();
			this.d = normal.dot(t.getVertex());
			this.element = t;
		}
	
		public void add(Triangle t){
			int c = compareTo(t);
			//front plane
			if (c == 1){
				if (front == null){
					front = new BSPNode(t);
				} else front.add(t);
			}
			//back plane
			else if (c == -1){
				if (back == null){
					back = new BSPNode(t);
				} else back.add(t);
			}
			else {
				if (front == null){
					front = new BSPNode(t);
				} else front.add(t);
				if (back == null){
					back = new BSPNode(t);
				} else back.add(t);
			}
		}
		
		private Tupel<Float, MeshElement> distToClosestMeshElement(Vec3 eye, Tupel<Float, MeshElement> best){			
			float distToPlane = eye.dot(normal) - d;
			float distToPlaneSqr = distToPlane* distToPlane; //Squared distance to compare with the rest
			
			if (distToPlaneSqr < best.o1){
				Tupel<Float, MeshElement> distToTriangle = element.getMinSqrDistAndClosestObject(eye, normal);
				if (distToTriangle.o1 < best.o1){
					best.o1 = distToTriangle.o1;
					best.o2 = distToTriangle.o2;
					if (best.o1 < threshold) return best;
				}
			}
			
			if (distToPlaneSqr > best.o1){
				if (distToPlane < 0f){
					if (back!=null) return back.distToClosestMeshElement(eye, best);
				} else {
					if (front!=null) return front.distToClosestMeshElement(eye, best);
				}
				return best;
			}
			
			if (back!=null) best = back.distToClosestMeshElement(eye, best);
			if (best.o1 < threshold) return best;
			if (front!=null) best = front.distToClosestMeshElement(eye, best);
						
			return best; 
		}
		
		/**
		 * 
		 * @param t
		 * @return 0: equal plane, 1 front, -1 back, 2 both
		 */
		public int compareTo(Triangle t) {
			Vertex[] vert = t.getVertices();
			
			float p0 = vert[0].dot(normal)-d;
			float p1 = vert[1].dot(normal)-d;
			float p2 = vert[2].dot(normal)-d;
			
			int pos = 0;
			int neg = 0;
			int eq = 0;
			
			if (Math.abs(p0)<0.01f){
				p0 = 0f;
				++eq;
			}
			if (Math.abs(p1)<0.01f){
				p1 = 0f;
				++eq;
			}
			if (Math.abs(p2)<0.01f){
				p2 = 0f;
				++eq;
			}
			
			if (eq == 3) return 0;
			
			if (p0 < 0f) ++neg;
			else if (p0 > 0f) ++pos;
			
			if (p1 < 0f) ++neg;
			else if (p1 > 0f) ++pos;
			
			if (p2 < 0f) ++neg;
			else if (p2 > 0f) ++pos;
		
			if (pos == 0) return -1;
			if (neg == 0) return 1;
		
			return 2;
		}
	}
}
