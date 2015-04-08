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

public abstract class ClosestTriangleSearchAlgorithm {
	
	float threshold;
	
	protected ClosestTriangleSearchAlgorithm(float threshold){
		this.threshold = threshold*threshold;
	}
	
	public abstract void add(Triangle t);
	
	/**
	 * Provides a mesh element and the squared distance to it from the point p
	 * If no mesh element is closer to the than the threshold it will return the
	 * exact distance. Otherwise, the first mesh element found within the threshold
	 * distance is returned  
	 * @param p a point in space
	 * @return a pair of a mesh element and a distance that is either the true distance
	 * to the point p or the first found element thta is closer than the threshold
	 */
	public abstract Tupel<Float, MeshElement> sqrDistToMeshElement(Vec3 p);
}
