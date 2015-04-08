// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2014  ICAMS, Ruhr-Universität Bochum
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

package model.dataContainer;

import java.awt.event.InputEvent;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import javax.swing.JOptionPane;

import quickhull3d.Point3d;
import quickhull3d.QuickHull3D;
import common.*;
import crystalStructures.CrystalStructure;
import model.*;
import model.dataContainer.VacancyDataContainer.Vacancy;

public final class VacancyDataContainer extends ParticleDataContainer<Vacancy>{

	private static JParticleDataControlPanel<Vacancy> dataPanel;
	
	private float nnd_tolerance = 0.8f;
	
	@Override
	public boolean isTransparenceRenderingRequired() {
		return false;
	}

	@Override
	protected String getLabelForControlPanel() {
		return "Vacancies";
	}
	
	public void findVacancies(final AtomData data){
		findVacancies(data, this.nnd_tolerance);
	}
	
	public void findVacancies(final AtomData data, final float nnd_tolerance){
		CrystalStructure cs = Configuration.getCrystalStructure();
		final float nnd = cs.getDistanceToNearestNeighbor();
		final float nndSearch = cs.getNearestNeighborSearchRadius();
		
		//Data structure to get all first nearest neighbors
		final NearestNeighborBuilder<Vec3> firstNearestNeighbors = new NearestNeighborBuilder<Vec3>(nndSearch);
		
		//Data structure to get all neighbors up to twice the distance
		final NearestNeighborBuilder<Vec3> secondNearestNeighbors = new NearestNeighborBuilder<Vec3>(1.4f*nndSearch);
		
		//Data structure to get all vacancies in small neighborhood
		final NearestNeighborBuilder<Vacancy> realVacancies = new NearestNeighborBuilder<Vacancy>(0.707f*nnd);
		
		final List<Vec3> possibleVacancyList = Collections.synchronizedList(new ArrayList<Vec3>());
		
		//Define atom types
		final int defaultType = cs.getDefaultType();
		final int surfaceType = cs.getSurfaceType();
		
		final ArrayList<Atom> defectAtoms = new ArrayList<Atom>();
		
		//Prepare nearest neighbor builders
		for (Atom a : data.getAtoms()){
			if (a.getType() != defaultType) {
				
				if (a.getType() != surfaceType)
					defectAtoms.add(a);
				
				secondNearestNeighbors.add(a); //adding only defected atoms to second nearest neighbor builder
			}
			firstNearestNeighbors.add(a);  //adding all atoms to first nearest neighbor builder
		}
	
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		
		for (int i = 0; i < ThreadPool.availProcessors(); i++){
		
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
			
				@Override
				public Void call() throws Exception {
				
					final int start = ThreadPool.getSliceStart(defectAtoms.size(), j);
					final int end = ThreadPool.getSliceEnd(defectAtoms.size(), j);
		
					/*
					 * Identify possible vacancy sites for each defect atom in parallel.
					 * In this case, vacancy sites are typically identified multiple times.
					 * Here, the sites are identified and stored. The reduction of duplicates
					 * is done in a later step
					 */
					for(int k = start; k < end; k++) {
				
						Atom a = defectAtoms.get(k);
						
						//Datastructure to store second nearest neighbors of selected atom
						ArrayList<Vec3> nnb = secondNearestNeighbors.getNeighVec(a);
						
						if (nnb.size() < 4) continue;
						
						//Get Voronoi vertices for an atom a
						ArrayList<Vec3> allVoronoiVertices = getVoronoiVertices(nnb);
						
						if(allVoronoiVertices.size() == 0) continue;
						
						//Build a convex hull for the set of the second neighbours of atom a 
						ArrayList<Tupel<Vec3,Vec3>> convHullPlanes = getConvexHullBoundaryPlanes(nnb);
						if (convHullPlanes.size() == 0) continue;
						
						ArrayList<Vec3> voronoiVertices = new ArrayList<Vec3>();
						
						for(Vec3 point : allVoronoiVertices) {
						//	if (point.getLength()<1.4f*nndSearch)
							//Check if Voronoi vertex is inside the convex hull
							if(isInConvexHull(point,convHullPlanes) == true)
								voronoiVertices.add(point);	 //add Voronoi vertex to a list if its inside created convex hull
						}

						if(voronoiVertices.size() == 0) continue; 
							
						
						for(int i = 0; i < voronoiVertices.size(); i++) {
							Vec3 point = voronoiVertices.get(i);
							Vec3 marker = point.addClone(a);
							
							//Fix periodicity
							data.getBox().backInBox(marker);
							
							//Check size gap to closest atoms
							ArrayList<Vec3> nnbDists = firstNearestNeighbors.getNeighVec(marker);
							
							boolean isVacancy = true;
							
							if (nnbDists.isEmpty() == true) isVacancy = false;
							else { 
								for (Vec3 v : nnbDists)
									if ((v.getLength() <= nnd_tolerance*nnd))
										isVacancy = false; 
							}
							
							if (isVacancy) {
								//Add found Voronoi vertex to the list
								possibleVacancyList.add(marker);
							}
						}
					}
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);	
		
		/**
		 * Merge duplicates in the set of possible vacancy sites
		 * First sort the list to guarantee a same order independently of the way the
		 * list is created in parallel
		 */
		Collections.sort(possibleVacancyList, new Comparator<Vec3>(){
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
		

		//Data structure to get all possible vacancies in small neighborhood
		final NearestNeighborBuilder<Vec3> possibleVacancies = new NearestNeighborBuilder<Vec3>(0.6f*nnd);
		
		for (Vec3 v : possibleVacancyList)
			possibleVacancies.add(v);
		
		/**
		 * Find the unique vacancy sites by selecting a vacancy site, average it with its duplicates
		 * which may be minimally displaced by the numerical construction.
		 * If there has not been a vacancy been created in a small vicinity, 
		 * place a vacancy at the computed position.
		 */
		for(int i = 0; i < possibleVacancyList.size(); i++) {
			//Find an average position of a possible vacancies in a small neighborhood
			Vec3 marker = possibleVacancyList.get(i).clone();
			marker.add(getCenterOfMass(possibleVacancies.getNeighVec(possibleVacancyList.get(i))));
			
			//Find all identified vacancies in small neighborhood of potential vacancy
			ArrayList<Vacancy> nvnb = realVacancies.getNeigh(marker);
			
			if(nvnb.size() == 0) {
				//Add new found to data structures
				Vacancy v = new Vacancy(marker);
				realVacancies.add(v); 
				this.particles.add(v);
			}
		}
	}
	
	@Override
	public boolean processData(File dataFile, final AtomData data) throws IOException {
		this.findVacancies(data, this.nnd_tolerance);
		this.updateRenderData();
		return true;
	}

	
	@Override
	protected JParticleDataControlPanel<?> getParticleDataControlPanel() {
		if (dataPanel == null)
			dataPanel = new JParticleDataControlPanel<Vacancy>(this, new float[]{0.9f,0.9f,0.9f}, 1.5f);
		return dataPanel;
	}
	
	@Override
	public JDataPanel getDataControlPanel() {
		return getParticleDataControlPanel();
	}
	
	@Override
	public String[] getFileExtensions() {
		return null;
	}

	@Override
	public boolean isExternalFileRequired() {
		return false;
	}

	@Override
	public String getDescription() {
		return "Identify vacancies in crystals";
	}

	@Override
	public String getName() {
		return "Vacancy detector";
	}

	@Override
	public DataContainer deriveNewInstance() {
		return new VacancyDataContainer();
	}
	
	@Override
	public boolean showOptionsDialog(){
		String value = JOptionPane.showInputDialog(null, "Enter fraction of lattice constant of a gap to be considered as vacancy", nnd_tolerance);
		try {
			nnd_tolerance = Float.parseFloat(value);
		} catch (RuntimeException e){
			return false;
		}
		return true;
	}

	//Calculate the center of mass for given set of points
	private Vec3 getCenterOfMass(ArrayList<Vec3> list) {
		Vec3 centerOfMass = new Vec3();
		if (list.size() == 0) return centerOfMass;
		
		for (int i = 0; i < list.size(); i++)
			centerOfMass.add(list.get(i));
		
		centerOfMass.divide(list.size());
		
		return centerOfMass;

	}
	
	/**
	 * Computes the convex hull of the given points and returns its boundary planes
	 * @param list
	 * @return a list of information about the faces. For each face, the list contains
	 * a pair of Vec3. The first element is the normal of the face, the second one a vertex
	 */
	private ArrayList<Tupel<Vec3, Vec3>> getConvexHullBoundaryPlanes(ArrayList<Vec3> list) {
		ArrayList<Tupel<Vec3, Vec3>> convexHull = new ArrayList<Tupel<Vec3, Vec3>>();
		
		if (list.size() < 4) return convexHull;
		
		Point3d[] points = new Point3d[list.size()];
		
		//Convert input from Vec3 to Point3d for quickhull library
		for(int i = 0; i < points.length; i++){
			Vec3 v = list.get(i);
			points[i] = new Point3d(v.x, v.y, v.z);
		}
		
		QuickHull3D hull = new QuickHull3D();
		hull.build (points);
		
		
		int[][] faces = hull.getFaces();
		Point3d[] vertices = hull.getVertices();
		
		for (int i = 0; i < faces.length; i++) {
			int j = faces[i][0];
			int k = faces[i][1];
			int l = faces[i][2];
			//And convert back from Point3d to Vec3
			Vec3 x1 = new Vec3((float) vertices[j].x, (float) vertices[j].y, (float) vertices[j].z);
			Vec3 x2 = new Vec3((float) vertices[k].x, (float) vertices[k].y, (float) vertices[k].z);
			Vec3 x3 = new Vec3((float) vertices[l].x, (float) vertices[l].y, (float) vertices[l].z);
			
			Vec3 n = Vec3.makeNormal(x3, x2, x1);
			//Store for each face the normal and one vertex (here x3)
			Tupel<Vec3, Vec3> e = new Tupel <Vec3, Vec3> (n, x3);
		
			convexHull.add(e);
		}	
			
		return convexHull;
	}
	
	/**
	 * Computes the voronoi volume of a particle located at (0,0,0)
	 * @param points a list of points that should surround the origin at (0,0,0) 
	 * @return the volume of the voronoi cell
	 */
	@SuppressWarnings("unused")
	private float getVoronoiVolume(ArrayList<Vec3> points) {
		final float TOL = 1.0e-6f;
		final float TOL_DIST2 = 2.0e-11f;

		float atomic_volume;
		
		int vertexnum, facesnum, edgesnum;
		int[] vertexnumi = new int[points.size()];
		int[] ord = new int[points.size()];
		Vec3[] coord = new Vec3[points.size()];

		/* Allocate memory for vertices */
		Vec3[][] vertexloc = new Vec3[points.size()][points.size()];

		atomic_volume = 0.0f;

		
		//Get vertices first
		ArrayList<Vec3> vertex = getVoronoiVertices(points);
		vertexnum = vertex.size();

		// Number of vertices of Voronoi cell must be greater than 3
		if (vertexnum > 3) {
			int[] surfind = new int[vertexnum];
			/* Check whether faces exist */
			facesnum = 0;

			/*
			 * Each neighbour atom i corresponds to at most one surface * Sum
			 * over all surfaces
			 */
			for (int i = 0; i < points.size(); ++i) {
				/* Coordinates of center of surface i */
				coord[i] = points.get(i).multiplyClone(0.5f);

				/* Look for vertices that belong to surface i */
				vertexnumi[i] = 0;
				for (int j = 0; j < vertexnum; ++j) {
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
					++facesnum;

					/* Compute coordinates of vertices belonging to surface i */
					int k = 0;
					for (int j = 0; j < vertexnum; ++j)
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
			edgesnum = 0;

			for (int n = 0; n < points.size(); ++n)
				if (vertexnumi[n] > 2) edgesnum += vertexnumi[n];

			edgesnum /= 2;

			/* Check whether Euler relation holds */
			if ((vertexnum - edgesnum + facesnum) == 2) {
				/* Compute volume of Voronoi cell */
				int mink = 0;
				/* For all potential faces */
				for (int i = 0; i < points.size(); ++i)
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
											/ (float) (Math.sqrt(vertexloc[i][k].dot(vertexloc[i][k])) * Math
													.sqrt(vertexloc[i][ord[j]].dot(vertexloc[i][ord[j]])));
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
						float height = (float) Math.sqrt(coord[i].dot(coord[i]));
						float tmp = 1.0f / height;

						for (int j = 0; j < vertexnumi[i] - 1; ++j) {
							Vec3 tmpvek = vertexloc[i][ord[j + 1]].cross(vertexloc[i][ord[j]]);
							area_i += 0.5f * tmpvek.dot(coord[i]) * tmp;

						}

						Vec3 tmpvek = vertexloc[i][ord[0]].cross(vertexloc[i][ord[vertexnumi[i] - 1]]);
						area_i += 0.5f * tmpvek.dot(coord[i]) * tmp;

						/* Volume of Voronoi cell */
						atomic_volume += area_i * height / 3.0;

					} /* vertexnum[i] > 2 */

			} /* Euler relation holds */

		} /* Number of vertices > 3 */

		return atomic_volume;
	}
	
	/**
	 * Identifies the vertices of an voronoi cell around a fixed point at (0, 0, 0) 
	 * @param points a set of points around the center (0,0,0)
	 * @return the subset of the input points that are part of the voronoi cell
	 */
	private ArrayList<Vec3> getVoronoiVertices(ArrayList<Vec3> points) {
	    final float TOL2 = 1.0e-10f;
	    final float TOL_VERT2 = 1.0e-6f;

		ArrayList<Vec3> vertex = new ArrayList<Vec3>();
		
		/* Each possible vertex defined by the intersection of 3 planes is examined */
		for (int i = 0; i < points.size() - 2; ++i) {
		
			Vec3 icoord = points.get(i);
			float idist2 = -points.get(i).getLengthSqr();

			for (int j = i + 1; j < points.size() - 1; ++j) {
			
				Vec3 jcoord = points.get(j);
				float jdist2 = -points.get(j).getLengthSqr();

				float ab = icoord.x * jcoord.y - jcoord.x * icoord.y;
				float bc = icoord.y * jcoord.z - jcoord.y * icoord.z;
				float ca = icoord.z * jcoord.x - jcoord.z * icoord.x;
				float da = idist2   * jcoord.x - jdist2 * icoord.x;
				float db = idist2   * jcoord.y - jdist2 * icoord.y;
				float dc = idist2   * jcoord.z - jdist2 * icoord.z;

				for (int k = j + 1; k < points.size(); ++k) {
				
					Vec3 kcoord = points.get(k);
					float kdist2 = -points.get(k).getLengthSqr();
					
					float det = kcoord.x * bc + kcoord.y * ca + kcoord.z * ab;

					/* Check whether planes intersect */
					if (det*det > TOL2) {
						float detinv = 1.0f / det;
						Vec3 tmpvertex = new Vec3();
						tmpvertex.x = (-kdist2 * bc + kcoord.y * dc - kcoord.z * db) * detinv;
						tmpvertex.y = (-kcoord.x * dc - kdist2 * ca + kcoord.z * da) * detinv;
						tmpvertex.z = (kcoord.x * db - kcoord.y * da - kdist2 * ab)  * detinv;

						/* Check whether vertex belongs to the Voronoi cell */
						int l = 0;
						boolean ok = true;

						do {
							if (l != i && l != j && l != k)
								ok = ( points.get(l).dot(tmpvertex) <= points.get(l).getLengthSqr() + TOL_VERT2);
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

		ArrayList<Vec3> finalVertices = new ArrayList<Vec3>(vertexnum);
		
		for (int i = 0; i < vertexnum; i++)
			finalVertices.add(vertex.get(i));

		return finalVertices;
	}
	
	
	/**
	 * Checks whenever the given point is inside a convex hull
	 * @param point a given point
	 * @param convexHull the boundary planes of a convex hull
	 * @return true if point is inside the convex hull, false otherwise
	 */
	private boolean isInConvexHull (Vec3 point, ArrayList<Tupel<Vec3, Vec3>> convexHull) {
		for(int i = 0; i < convexHull.size(); i++) {	
			Vec3 n = convexHull.get(i).getO1();
			Vec3 v = point.subClone(convexHull.get(i).getO2());
			
			if(n.dot(v) < 0f) return false;
		}
		
		return true;
	}
	
	public static class Vacancy extends Vec3 implements Pickable{
		public Vacancy(Vec3 v) {
			super(v.x,v.y, v.z);
		}
		
		@Override
		public Collection<?> getHighlightedObjects() {
			return null;
		}

		@Override
		public boolean isHighlightable() {
			return false;
		}

		@Override
		public String printMessage(InputEvent ev) {
			return String.format("Vacancy position  ( %.6f, %.6f, %.6f )", x, y, z);
		}
		
		@Override
		public boolean equals(Object obj) {
			return this == obj;
		}
	}
	
}
