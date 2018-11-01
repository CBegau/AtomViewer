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
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import common.Tupel;
import common.Vec3;
import model.BoxParameter;
import model.NearestNeighborBuilder;

/**
 * A brute force approach to to test if a triangle exists to a given point within a certain distance
 */
public class ClosestTriangleSearchBruteForce<T extends Vec3> extends ClosestTriangleSearchAlgorithm<T>{
	private ArrayList<TriangleDataStruct> tri = new ArrayList<TriangleDataStruct>();
	private float nearestNeighborDistance;
	
	public ClosestTriangleSearchBruteForce(float threshold, BoxParameter box, float nearestNeighborDistance) {
		super(threshold, box);
		this.nearestNeighborDistance = nearestNeighborDistance;
	}
	
	public void add(FinalizedTriangle t){
		tri.add(new TriangleDataStruct(t));
	}
	
	@Override
	/**
	 * Provides the squared distance to the the mesh from point p
	 * If no mesh element is closer than the threshold, the exact distance is returned
	 * Otherwise, the first mesh element found within the threshold distance is returned  
	 * @param p a point in space
	 * @return the squared distance that is either the true distance
	 * to the point p or the first found element that is closer than the threshold
	 */
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

	@Override
	public List<T> getElementsWithinThreshold(List<T> sites) {
		final List<T> elementsWithinThreshold = Collections.synchronizedList(new ArrayList<>()); 
		
		final ArrayList<MeshDistanceHelper<T>> sitesToTest = new ArrayList<>();
		
		final NearestNeighborBuilder<MeshDistanceHelper<T>> nnb =
				new NearestNeighborBuilder<MeshDistanceHelper<T>>(box, nearestNeighborDistance);
		
		for (T a : sites){
			MeshDistanceHelper<T> amdh = new MeshDistanceHelper<T>(a);
			sitesToTest.add(amdh);
			nnb.add(amdh);
		}
		
		IntStream.range(0, sitesToTest.size()).parallel().forEach(i->{
			MeshDistanceHelper<T> a = sitesToTest.get(i);
			if (Thread.interrupted()) return;
			
			//Each element is associated with maximum and minimum value for the distance
			//to the mesh, initially 0 to infinity.
			
			//If the threshold distance is within the range of possible values
			//Compute the exact value
			if (a.minDist<threshold && a.maxDist>threshold){
				a.minDist = (float)Math.sqrt(sqrDistToMeshElement(a));
				if (a.minDist<threshold) //The exact distance is not known, it is only
					a.minDist = 0f;		// guaranteed that it is <threshold in this case
				a.maxDist = a.minDist;
			}
			
			//In the case the maximum possible distance is smaller than the threshold, accept 
			if (a.maxDist <= threshold){
				elementsWithinThreshold.add(a.v);
			}
			
			//Update the information of neighboring particles,
			//shrink their range of possible values for min/max, based on the min/max values of
			//the current point and the distance between the points.
			//There is no need to synchronize the updates between threads
			//Race conditions could result in a wider range than needed, but this does not change
			//the final result
			ArrayList<Tupel<MeshDistanceHelper<T>, Vec3>> nn = nnb.getNeighAndNeighVec(a);
			for (Tupel<MeshDistanceHelper<T>, Vec3> t : nn){
				MeshDistanceHelper<T> n = t.o1;
				float max = a.maxDist+t.o2.getLength();
				float min = Math.max(0f, a.minDist-t.o2.getLength());
				
				if (max < n.maxDist)
					n.maxDist = max;
				if (min > n.minDist)
					n.minDist = min;
			}
		});
		
		return elementsWithinThreshold;
	}
	
	private static class MeshDistanceHelper<T extends Vec3> extends Vec3 {
		T v;
		
		float minDist, maxDist;
		
		public MeshDistanceHelper(T v) {
			this.setTo(v);
			this.v = v;
			this.minDist = 0f;	//Unknown value
			this.maxDist = Float.POSITIVE_INFINITY;	//Unknown value
		}
	}
	
}