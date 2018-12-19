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

import model.BoxParameter;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import common.Vec3;
import model.NearestNeighborBuilder;

/**
 * An implementation in which triangles are rasterized to detect the closest triangle.
 * This provides only an approximate distance, but is usually much faster than an
 * exact geometrical computation
 */
public class GridRasterTriangleSearch<T extends Vec3> extends ClosestTriangleSearchAlgorithm<T>{
	private NearestNeighborBuilder<Vec3> triangleSurfacePoints;
	private TreeSet<Vec3> nodes = new TreeSet<Vec3>(new Comparator<Vec3>() {
		@Override
		public int compare(Vec3 o1, Vec3 o2) {
			if (o1.x > o2.x) return 1;
			if (o1.x < o2.x) return -1;
			if (o1.y > o2.y) return 1;
			if (o1.y < o2.y) return -1;
			if (o1.z > o2.z) return 1;
			if (o1.z < o2.z) return -1;
			return 0;
		}
	});
	
	public GridRasterTriangleSearch(float threshold, BoxParameter box) {
		super(threshold, box);
		triangleSurfacePoints = new NearestNeighborBuilder<Vec3>(box, threshold);
	}
	
	public void add(FinalizedTriangle t){
		this.add(t.a, t.b, t.c);
	}
	
	private void add(Vec3 a, Vec3 b, Vec3 c){
		//Insert the three vertices, if not already added
		if (nodes.add(a)) triangleSurfacePoints.add(a);
		if (nodes.add(b)) triangleSurfacePoints.add(b);
		if (nodes.add(c)) triangleSurfacePoints.add(c);
		
		//Test if any edge is larger than half the threshold
		//If so insert recursively subtriangles
		
		Vec3 ba = b.subClone(a);
		Vec3 cb = c.subClone(b);
		Vec3 ac = a.subClone(c);
		
		float la = ba.getLengthSqr();
		float lb = cb.getLengthSqr();
		float lc = ac.getLengthSqr();
	
		float max = Math.max(la, Math.max(lb, lc));
		
		if (max > 0.25f*threshold){
			Vec3 t_ba = a.addClone(ba.multiply(0.5f));
			Vec3 t_cb = b.addClone(cb.multiply(0.5f));
			Vec3 t_ac = c.addClone(ac.multiply(0.5f));
			
			this.add(t_ba, t_cb, t_ac);
			this.add(t_ba, t_ac, a);
			this.add(t_ba, b, t_cb);
			this.add(t_ac, t_cb, c);
		}
	}
	
	@Override
	public float sqrDistToMeshElement(Vec3 p) {
		Vec3 d =triangleSurfacePoints.getVectorToNearest(p);
		if (d == null) return Float.MAX_VALUE; 
		else return d.getLengthSqr();
	}

	@Override
	public List<T> getElementsWithinThreshold(final List<T> sites) {		
		return sites.stream().parallel().filter(a->{
			Vec3 v = triangleSurfacePoints.getVectorToNearest(a);
			return (v != null && v.getLength() < threshold);
		}).collect(Collectors.toList());
	}
}