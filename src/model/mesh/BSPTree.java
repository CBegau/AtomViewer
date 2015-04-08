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

import common.*;

public class BSPTree extends ClosestTriangleSearchAlgorithm{	
	private BSPNode root;
	
	public BSPTree(float threshold) {
		super(threshold);
	}
	
	@Override
	public void add(FinalizedTriangle t){
		if (root == null){
			root = new BSPNode(t);
		} else {
			root.add(t);
		}
	}
	
	@Override
	public float sqrDistToMeshElement(Vec3 eye){
		if (root == null) return Float.MAX_VALUE;
		float best = root.distToClosestMeshElement(eye, Float.MAX_VALUE);
		return best;
	}
	
	private class BSPNode{
		public float d;
		public FinalizedTriangle element;
		
		public BSPNode front = null;
		public BSPNode back = null;
		
		
		public BSPNode(FinalizedTriangle t){
			this.d = element.getUnitNormalVector().dot(t.getVertex());
			this.element = t;
		}
	
		public void add(FinalizedTriangle t){
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
		
		private float distToClosestMeshElement(Vec3 eye, float best){			
			float distToPlane = eye.dot(element.normal) - d;
			float distToPlaneSqr = distToPlane* distToPlane; //Squared distance to compare with the rest
			
			if (distToPlaneSqr < best){
				float distToTriangle = element.getMinSqrDist(eye);
				if (distToTriangle < best){
					best = distToTriangle;
					if (best < threshold) return best;
				}
			}
			
			if (distToPlaneSqr > best){
				if (distToPlane < 0f){
					if (back!=null) return back.distToClosestMeshElement(eye, best);
				} else {
					if (front!=null) return front.distToClosestMeshElement(eye, best);
				}
				return best;
			}
			
			if (back!=null) best = back.distToClosestMeshElement(eye, best);
			if (best < threshold) return best;
			if (front!=null) best = front.distToClosestMeshElement(eye, best);
						
			return best; 
		}
		
		/**
		 * 
		 * @param t
		 * @return 0: equal plane, 1 front, -1 back, 2 both
		 */
		public int compareTo(FinalizedTriangle t) {
			Vec3 normal = t.normal;
			
			float p0 = t.a.dot(normal)-d;
			float p1 = t.b.dot(normal)-d;
			float p2 = t.c.dot(normal)-d;
			
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
