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

import java.util.List;

import common.Vec3;
import model.BoxParameter;

public abstract class ClosestTriangleSearchAlgorithm<T extends Vec3> {
	
	float threshold;
	BoxParameter box;
	
	protected ClosestTriangleSearchAlgorithm(float threshold, BoxParameter box){
		this.threshold = threshold;
		this.box = box;
	}
	
	public abstract void add(FinalizedTriangle t);
	
	/**
	 * Provides a squared distance to the mesh from point p
	 * Depending on the implementation, this can be an exact value or an 
	 * approximation. The method may return infinity if no element of
	 * the mesh is found in the vicinity. 
	 * @param p a point in space
	 * @return the (approximated) squared distance
	 */
	public abstract float sqrDistToMeshElement(Vec3 p);
	
	
	public abstract List<T> getElementsWithinThreshold(List<T> sites);
}
