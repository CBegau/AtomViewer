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

/**
 * A brute force approach to to test if a triangle exists to a given point within a certain distance
 */
public class ClosestTriangleSearch extends ClosestTriangleSearchAlgorithm{
	private ArrayList<TriangleDataStruct> tri = new ArrayList<TriangleDataStruct>();
	
	public ClosestTriangleSearch(float threshold) {
		super(threshold);
	}
	
	public void add(FinalizedTriangle t){
		tri.add(new TriangleDataStruct(t));
	}
	
	@Override
	public float sqrDistToMeshElement(Vec3 p) {
		float closest = Float.MAX_VALUE;
		
		for (int i=0; i<tri.size();i++){
			TriangleDataStruct n = tri.get(i);
			float distToPlane = p.dot(n.element.normal)-n.d;
			distToPlane *= distToPlane;
			if (distToPlane < closest){
				float d = n.element.getMinSqrDist(p);
				if (d < closest){
					if (d < threshold)
						return d;
					closest = d;
				}
			}
		}
		return closest;
	}
	
	private class TriangleDataStruct{
		public float d;
		public FinalizedTriangle element;	
		
		public TriangleDataStruct(FinalizedTriangle t){
			d = t.normal.dot(t.a);
			this.element = t;
		}
	}
}