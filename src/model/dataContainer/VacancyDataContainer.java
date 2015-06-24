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

package model.dataContainer;

import gui.JPrimitiveVariablesPropertiesDialog;
import gui.ProgressMonitor;
import gui.JPrimitiveVariablesPropertiesDialog.BooleanProperty;
import gui.JPrimitiveVariablesPropertiesDialog.FloatProperty;

import java.awt.event.InputEvent;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import quickhull3d.Point3d;
import quickhull3d.QuickHull3D;
import common.*;
import crystalStructures.CrystalStructure;
import model.*;
import model.dataContainer.VacancyDataContainer.Vacancy;

public final class VacancyDataContainer extends ParticleDataContainer<Vacancy>{

	private static JParticleDataControlPanel<Vacancy> dataPanel;
	
	private float nnd_tolerance = 0.8f;
	private boolean testForSurfaces = true;
	
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
		CrystalStructure cs = data.getCrystalStructure();
		final float nnd = cs.getDistanceToNearestNeighbor();
		final float nndSearch = cs.getNearestNeighborSearchRadius();
		
		//Data structure to get all neighbors up to a selected distance
		final NearestNeighborBuilder<Vec3> defectedNearestNeighbors = 
				new NearestNeighborBuilder<Vec3>(data.getBox(), 1.5f*nndSearch, true);
		final NearestNeighborBuilder<Vec3> allNearestNeighbors = 
				new NearestNeighborBuilder<Vec3>(data.getBox(), 1.5f*nndSearch, true);
		
		final List<Vec3> possibleVacancyList = Collections.synchronizedList(new ArrayList<Vec3>());
		
		//Define atom types
		final int defaultType = cs.getDefaultType();
		final int surfaceType = cs.getSurfaceType();
		
		final ArrayList<Atom> nextToVancanyCandidateAtoms = new ArrayList<Atom>();
		
		final float minDistanceToAtom = nnd_tolerance*nnd;
		
		//Prepare nearest neighbor builders
		for (Atom a : data.getAtoms()){
			if (a.getType() != defaultType){
				defectedNearestNeighbors.add(a);
				
				if (a.getType() != surfaceType)
					nextToVancanyCandidateAtoms.add(a);
			}
		}
		
		allNearestNeighbors.addAll(data.getAtoms());
		
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		
		ProgressMonitor.getProgressMonitor().start(nextToVancanyCandidateAtoms.size());
		
		for (int i = 0; i < ThreadPool.availProcessors(); i++){
		
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
			
				@Override
				public Void call() throws Exception {
				
					final int start = ThreadPool.getSliceStart(nextToVancanyCandidateAtoms.size(), j);
					final int end = ThreadPool.getSliceEnd(nextToVancanyCandidateAtoms.size(), j);
		
					/*
					 * Identify possible vacancy sites for each defect atom in parallel.
					 * In this case, vacancy sites are typically identified multiple times.
					 * Here, the sites are identified and stored. The reduction of duplicates
					 * is done in a later step
					 */
					for(int k = start; k < end; k++) {
						if ((k-start)%1000 == 0)
							ProgressMonitor.getProgressMonitor().addToCounter(1000);
				
						Atom a = nextToVancanyCandidateAtoms.get(k);
						
						//Datastructure to store second nearest neighbors of selected atom
						ArrayList<Vec3> nnb = defectedNearestNeighbors.getNeighVec(a);
						
						if (nnb.size() < 4) continue;
						
						//Get Voronoi vertices for an atom a
						List<Vec3> allVoronoiVertices = VoronoiVolume.getVoronoiVertices(nnb);
						
						if(allVoronoiVertices.size() == 0) continue;
												
						ArrayList<Vec3> acceptedVoronoiVertices = new ArrayList<Vec3>();
						ArrayList<Vec3> first = allNearestNeighbors.getNeighVec(a);
						
						
						nnb.add(new Vec3(0f, 0f, 0f)); //Add the central atom for the next steps
						ArrayList<Tupel<Vec3,Vec3>> convHullPlanes = null;
						if (testForSurfaces) convHullPlanes = getConvexHullBoundaryPlanes(nnb);
						
						for(Vec3 point : allVoronoiVertices) {
							boolean aboveMinDist = true;
							boolean belowMaxDist = true;
							for (Vec3 n : first){
								float d = n.getDistTo(point);
								if (d < minDistanceToAtom) {
									//Too close to an atom to be a vacancy
									aboveMinDist = false;
									break;
								}
								if (d > 2*allNearestNeighbors.getCutoff()) {
									//Too far away, won't be in convex hull
									belowMaxDist = false;
									break;
								}
							}
							
							if (aboveMinDist && belowMaxDist){
								if (testForSurfaces){
									//Test more precisely if the site detected is inside the convex hull of points
								    if (isInConvexHull(point, convHullPlanes)) 
								    	acceptedVoronoiVertices.add(point);
								} else
									acceptedVoronoiVertices.add(point);								
							}
						}
						if (acceptedVoronoiVertices.size() == 0) continue;
						
						ArrayList<Vec3> clusterCenter = clusteringAnalysis(acceptedVoronoiVertices, minDistanceToAtom*0.5f);
						
						for (Vec3 point : clusterCenter){
							point.add(a);
							//Fix periodicity
							data.getBox().backInBox(point);
								
							//Add Voronoi vertex to the list
							possibleVacancyList.add(point);
						}
					}
					ProgressMonitor.getProgressMonitor().addToCounter(end-start%1000);
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
		final NearestNeighborBuilder<Vec3> possibleVacancies = new NearestNeighborBuilder<Vec3>(data.getBox(), 0.5f*nnd, true);
		possibleVacancies.addAll(possibleVacancyList);
		final NearestNeighborBuilder<Vacancy> realVacancies = new NearestNeighborBuilder<Vacancy>(data.getBox(), 0.707f*nnd);
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
		
		ProgressMonitor.getProgressMonitor().stop();
	}
	
	@Override
	public boolean processData(final AtomData data) throws IOException {
		this.findVacancies(data, this.nnd_tolerance);
		this.updateRenderData(data.getBox());
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
	public String getDescription() {
		return "Identify vacancies in crystals";
	}

	@Override
	public String getName() {
		return "Vacancy detector";
	}
	
	@Override
	public boolean isApplicable(AtomData data) {
		return true;
	}

	@Override
	public String getRequirementDescription() {
		return "";
	}

	@Override
	public DataContainer deriveNewInstance() {
		VacancyDataContainer clone = new VacancyDataContainer();
		clone.nnd_tolerance = this.nnd_tolerance;
		return clone;
	}
	
	@Override
	public boolean showOptionsDialog(){
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(null, "Detect vacancies");
		
		FloatProperty tolerance = dialog.addFloat("nnd_tolerance", 
				"Fraction of distance to nearest neghbor of a gap to be considered as vacancy"
				, "", nnd_tolerance, 0.01f, 1000f);
		BooleanProperty surface = dialog.addBoolean("testSurface", "Test for surfaces", "", testForSurfaces);
		
		boolean ok = dialog.showDialog();
		if (ok){
			
			this.nnd_tolerance = tolerance.getValue();
			this.testForSurfaces = surface.getValue();
		}
		return ok;
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
	
	
	private ArrayList<Vec3> clusteringAnalysis(ArrayList<Vec3> inputSites, float clusterRadius){
		ArrayList<Vec3> clusterCenter = new ArrayList<Vec3>();
		
		int[] cluster = new int[inputSites.size()];
		for (int i=0; i<cluster.length; i++)
			cluster[i] = -1;
		
		int numCluster = 0;
		
		for (int i=0; i<cluster.length; i++){
			if (cluster[i] == -1)
				cluster[i] = numCluster++;
			
			for (int j=i+1; j<cluster.length; j++){
				if (inputSites.get(i).getDistTo(inputSites.get(j))<clusterRadius){
					cluster[j] = cluster[i];
					break;
				}
			}
		}
		
		for (int i = 0; i<numCluster; i++){
			ArrayList<Vec3> c = new ArrayList<Vec3>();
			for (int j=0; j<cluster.length; j++)
				if (cluster[j] == 0)
					c.add(inputSites.get(j));
			
			clusterCenter.add(getCenterOfMass(c));
		}
		
		return clusterCenter;
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
		public String printMessage(InputEvent ev, AtomData data) {
			return String.format("Vacancy position  ( %.6f, %.6f, %.6f )", x, y, z);
		}
		
		@Override
		public boolean equals(Object obj) {
			return this == obj;
		}
		
		@Override
		public Vec3 getCenterOfObject() {
			return this.clone();
		}
	}
	
}
