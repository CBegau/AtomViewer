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
package common;

import java.util.List;
import java.util.ArrayList;

public class VoronoiVolume {

	/**
	 * Identifies the vertices of an voronoi cell around a fixed point at (0, 0, 0) 
	 * @param points a set of points around the center (0,0,0)
	 * @return the subset of the input points that are part of the voronoi cell
	 */
	public static List<Vec3> getVoronoiVertices(List<Vec3> points) {
	    final float TOL2 = 1.0e-10f;
	    final float TOL_VERT2 = 1.0e-6f;

		List<Vec3> vertex = new ArrayList<Vec3>();
		
		float[] distSqr = new float[points.size()];
		for (int i=0; i<points.size(); i++)
			distSqr[i] = points.get(i).getLengthSqr();
		
		/* Each possible vertex defined by the intersection of 3 planes is examined */
		for (int i = 0; i < points.size() - 2; ++i) {
			Vec3 icoord = points.get(i);

			for (int j = i + 1; j < points.size() - 1; ++j) {
			
				Vec3 jcoord = points.get(j);

				float ab = icoord.x * jcoord.y - jcoord.x * icoord.y;
				float bc = icoord.y * jcoord.z - jcoord.y * icoord.z;
				float ca = icoord.z * jcoord.x - jcoord.z * icoord.x;
				float da = distSqr[j] * icoord.x - distSqr[i] * jcoord.x;
				float db = distSqr[j] * icoord.y - distSqr[i] * jcoord.y;
				float dc = distSqr[j] * icoord.z - distSqr[i] * jcoord.z;

				for (int k = j + 1; k < points.size(); ++k) {
					Vec3 kcoord = points.get(k);
					
					float det = kcoord.x * bc + kcoord.y * ca + kcoord.z * ab;

					/* Check whether planes intersect */
					if (det*det > TOL2) {
						float detinv = 1.0f / det;
						Vec3 tmpvertex = new Vec3();
						tmpvertex.x = (distSqr[k] * bc + kcoord.y * dc - kcoord.z * db) * detinv;
						tmpvertex.y = (-kcoord.x * dc + distSqr[k] * ca + kcoord.z * da) * detinv;
						tmpvertex.z = (kcoord.x * db - kcoord.y * da + distSqr[k] * ab)  * detinv;

						/* Check whether vertex belongs to the Voronoi cell */
						int l = 0;
						boolean ok = true;

						do {
							if (l != i && l != j && l != k)
								ok = ( points.get(l).dot(tmpvertex) <= distSqr[l] + TOL_VERT2);
							l++;
						} while (ok && (l < points.size()));

						if (ok)
							vertex.add(tmpvertex.multiply(0.5f));
					} /* Planes intersect */

				} /* k */
			} /* j */
		} /* i */

		int vertexnum = vertex.size();

		int[] index = new int[vertexnum];
		/* Check whether some vertices coincide */
		for (int i = 0; i < vertexnum; ++i) {
			index[i] = 1;
			for (int j = i + 1; j < vertexnum; ++j) {
				if (vertex.get(i).getSqrDistTo(vertex.get(j)) < TOL2)
					index[i] = 0;
			}
		}

		/* Remove coincident vertices */
		int j = 0;
		for (int i = 0; i < vertexnum; ++i){
			if (index[i] != 0) {
				vertex.get(j).setTo(vertex.get(i));
				++j;
			}
		}
		vertexnum = j;

		if (vertexnum == vertex.size())
			return vertex;
		
		ArrayList<Vec3> finalVertices = new ArrayList<Vec3>(vertexnum);
		
		for (int i = 0; i < vertexnum; i++)
			finalVertices.add(vertex.get(i));

		return finalVertices;
	}
	
	/**
	 * Computes the voronoi volume of a particle located at (0,0,0)
	 * @param points a list of points that should surround the origin at (0,0,0) 
	 * @return the volume of the voronoi cell
	 */
	public static float getVoronoiVolume(List<Vec3> points) {
		final float TOL = 1.0e-6f;
		final float TOL_DIST2 = 1.0e-10f;

		float atomic_volume = 0.0f;
		
		int numfaces = 0;
		int[] vertexnumi = new int[points.size()];
		int[] ord = new int[points.size()];
		Vec3[] coord = new Vec3[points.size()];
		
		//Get vertices first
		List<Vec3> vertex = getVoronoiVertices(points);
		int numVertex = vertex.size();
		/* Allocate memory for vertices */
		Vec3[][] vertexloc = new Vec3[points.size()][numVertex];

		// Number of vertices of Voronoi cell must be greater than 3
		if (numVertex <= 3) return 0f; 
	
		int[] surfind = new int[numVertex];
		/* Check whether faces exist */
		/*
		 * Each neighbour atom i corresponds to at most one surface * Sum
		 * over all surfaces
		 */
		for (int i = 0; i < points.size(); ++i) {
			/* Coordinates of center of surface i */
			coord[i] = points.get(i).multiplyClone(0.5f);

			/* Look for vertices that belong to surface i */
			for (int j = 0; j < numVertex; ++j) {
				surfind[j] = 0;
				vertexloc[i][j] = vertex.get(j).subClone(coord[i]);
				float tmp = coord[i].dot(vertexloc[i][j]);

				if (tmp * tmp < TOL_DIST2) {
					/* vertex j belongs to surface i */
					surfind[j] = 1;
					++vertexnumi[i];
				}
			}

			/* Surface i exists */
			if (vertexnumi[i] > 2) {
				++numfaces;

				/* Compute coordinates of vertices belonging to surface i */
				int k = 0;
				for (int j = 0; j < numVertex; ++j)
					if (surfind[j] == 1) {
						vertexloc[i][k].setTo(vertexloc[i][j]);
						++k;
					}
			}
			/* Transform into center of mass system */
			if (vertexnumi[i] > 2) {
				Vec3 center = new Vec3();
				for (int j = 0; j < vertexnumi[i]; ++j)
					center.add(vertexloc[i][j]);
				
				center.multiply(1.0f / vertexnumi[i]);
				
				for (int j = 0; j < vertexnumi[i]; ++j)
					vertexloc[i][j].sub(center);
			}

		} /* i */

		/* Number of edges of Voronoi cell */
		int edgesnum = 0;

		for (int n = 0; n < points.size(); ++n)
			if (vertexnumi[n] > 2) edgesnum += vertexnumi[n];

		edgesnum /= 2;

		/* Check whether Euler relation holds */
		if ((numVertex - edgesnum + numfaces) != 2) return 0f;
		
		
		/* Compute volume of Voronoi cell */
		int mink = 0;
		/* For all potential faces */
		for (int i = 0; i < points.size(); ++i){
			/* Surface i exists */
			if (vertexnumi[i] > 2) {
				/* Sort vertices of face i */
				ord[0] = 0;
				for (int j = 0; j < vertexnumi[i] - 1; ++j) {
					float maxcos = -1.0f;
					for (int k = 0; k < vertexnumi[i]; ++k) {
						Vec3 tmpvek = vertexloc[i][k].cross(vertexloc[i][ord[j]]);
						float sin = tmpvek.dot(coord[i]);

						if (sin > TOL) {
							float cos = vertexloc[i][k].dot(vertexloc[i][ord[j]])
									/ (vertexloc[i][k].getLength() * vertexloc[i][ord[j]].getLength());
							if (cos > maxcos) {
								maxcos = cos;
								mink = k;
							}
						}
					}

					ord[j + 1] = mink;

				}

				/* Compute area of surface i */
				float area_i = 0.0f;
				float height = coord[i].getLength();
				float tmp = 1.0f / height;

				for (int j = 0; j < vertexnumi[i] - 1; ++j) {
					Vec3 tmpvek = vertexloc[i][ord[j + 1]].cross(vertexloc[i][ord[j]]);
					area_i += 0.5f * tmpvek.dot(coord[i]) * tmp;
				}

				Vec3 tmpvek = vertexloc[i][ord[0]].cross(vertexloc[i][ord[vertexnumi[i] - 1]]);
				area_i += 0.5f * tmpvek.dot(coord[i]) * tmp;

				/* Volume of Voronoi cell */
				atomic_volume += area_i * height / 3.0f;

			} /* vertexnum[i] > 2 */
		}

		return atomic_volume;
	}
	
}
