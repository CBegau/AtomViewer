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
	
	private float nnd_tolerance = 0.75f;
	private boolean testForSurfaces = false;
	
	@Override
	public boolean isTransparenceRenderingRequired() {
		return false;
	}

	@Override
	protected String getLabelForControlPanel() {
		return "Vacancies";
	}
	
	private void findVacancies(final AtomData data, final float nnd_tolerance){
		CrystalStructure cs = data.getCrystalStructure();
		final float nnd = cs.getDistanceToNearestNeighbor();
		final float nndSearch = cs.getNearestNeighborSearchRadius();
		
		//Data structure to get all neighbors up to a selected distance
		final NearestNeighborBuilder<Vec3> defectedNearestNeighbors = 
				new NearestNeighborBuilder<Vec3>(data.getBox(), 1.5f*nndSearch, true);
		final NearestNeighborBuilder<Vec3> allNearestNeighbors = 
				new NearestNeighborBuilder<Vec3>(data.getBox(), 1f*nndSearch, true);
		
		//Define atom types
		final int defaultType = cs.getDefaultType();
		final int surfaceType = cs.getSurfaceType();
		
		final ArrayList<Atom> nextToVancanyCandidateAtoms = new ArrayList<Atom>();
		
		final float minDistanceToAtom = nnd_tolerance*nnd;
		
		allNearestNeighbors.addAll(data.getAtoms());
		
		for (Atom a : data.getAtoms()){
			if (a.getType() != defaultType){
				defectedNearestNeighbors.add(a);
				
				if (a.getType() != surfaceType)
					nextToVancanyCandidateAtoms.add(a);
			}
		}
		
		ProgressMonitor.getProgressMonitor().start(nextToVancanyCandidateAtoms.size());

		
		//Data structure to get all possible vacancies in small neighborhood
		final NearestNeighborBuilder<Vacancy> possibleVacancies = new NearestNeighborBuilder<Vacancy>(data.getBox(), nnd, true);
		
		final float squaredCutoff = defectedNearestNeighbors.getCutoff()*defectedNearestNeighbors.getCutoff();
		
		Vector<Callable<Void>> parallelTasks = new Vector<Callable<Void>>();
		
		ProgressMonitor.getProgressMonitor().start(nextToVancanyCandidateAtoms.size());
		
		/*
		 * Identify possible vacancy sites for each defect atom in parallel.
		 * In this case, vacancy sites are typically identified multiple times.
		 * Here, the sites are identified and stored. The reduction of duplicates
		 * is done in a later step
		 */
		for (int i = 0; i < ThreadPool.availProcessors(); i++){
		
			final int j = i;
			parallelTasks.add(new Callable<Void>() {
			
				@Override
				public Void call() throws Exception {
				
					final int start = ThreadPool.getSliceStart(nextToVancanyCandidateAtoms.size(), j);
					final int end = ThreadPool.getSliceEnd(nextToVancanyCandidateAtoms.size(), j);
					
					for (int k = start; k < end; k++) {
						if (k % 1000 == 0) ProgressMonitor.getProgressMonitor().addToCounter(1000);

						Atom a = nextToVancanyCandidateAtoms.get(k);

						// Datastructure to store second nearest neighbors of selected atom
						ArrayList<Vec3> nnb = defectedNearestNeighbors.getNeighVec(a);

						
						ArrayList<Tupel<Vec3,Vec3>> convHullPlanes = null;
						if (nnb.size() < 4) continue;			
						
						//get the voronoi cell
						List<Vec3> voronoi = VoronoiVolume.getVoronoiVertices(nnb);
						for (Vec3 point : voronoi){
							//Exclude points that are very close to an atom
							if (point.getLength() < minDistanceToAtom || point.getLengthSqr() > squaredCutoff)
								continue;
							
							//Move point into the global coordinate system
							Vec3 absolutePoint = point.addClone(a);
							data.getBox().backInBox(absolutePoint);
							
							float minDist = nndSearch;
							// Test validity
							final List<Vec3> neigh = allNearestNeighbors.getNeighVec(absolutePoint);
							boolean valid = true;
							for (Vec3 p : neigh) {
								float d = p.getLength();
								minDist = Math.min(d, minDist);
								if (minDist < minDistanceToAtom) {
									valid = false;
									break;
								}
							}
							
							//Test if point is inside a convex hull if requested
							if (testForSurfaces && valid){
								if (convHullPlanes == null) {
									//Compute the convex hull if needed
									nnb.add(new Vec3(0f, 0f, 0f)); //Add the central atom
									convHullPlanes = getConvexHullBoundaryPlanes(nnb);
								}
								valid = isInConvexHull(point, convHullPlanes);
							}
							    
							
							if (valid) {
								Vec3NoEqual tmp = new Vec3NoEqual();
								tmp.setTo(absolutePoint);
								Vacancy v = new Vacancy(tmp, minDist);
								
								synchronized (possibleVacancies) {
									List<Tupel<Vacancy,Vec3>> nearOnes = possibleVacancies.getNeighAndNeighVec(tmp);
									Vacancy bestFit = v;
									
									for (Tupel<Vacancy,Vec3> t : nearOnes){
										if (t.o2.getLength()<0.1f*minDistanceToAtom){
											if (bestFit.dist<t.o1.dist)
												bestFit = t.o1;
											possibleVacancies.remove(t.o1);
										}
									}

									possibleVacancies.add(bestFit);
										
									
								}
							}
						}
					}
					ProgressMonitor.getProgressMonitor().addToCounter(end-start%1000);
					return null;
				}
			});
		}
		ThreadPool.executeParallel(parallelTasks);
		
		List<Vacancy> possibleVacancyList = possibleVacancies.getAllElements();
		possibleVacancies.removeAll();
		
		Collections.sort(possibleVacancyList, new Comparator<Vacancy>() {
			@Override
			public int compare(Vacancy o1, Vacancy o2) {
				if (o1.dist>o2.dist) return 1;
				else if (o1.dist<o2.dist) return -1;
				else return 0;
			}
		});
		
		
		for (Vacancy v : possibleVacancyList){
			List<Tupel<Vacancy,Vec3>> nearOnes = possibleVacancies.getNeighAndNeighVec(v);
			Vacancy bestFit = v;
			
			for (Tupel<Vacancy,Vec3> t : nearOnes){
				if (t.o2.getLength()<minDistanceToAtom){
					if (bestFit.dist<t.o1.dist)
						bestFit = t.o1;
					possibleVacancies.remove(t.o1);
				}
			}

			possibleVacancies.add(bestFit);
		}
		
			
		possibleVacancyList = possibleVacancies.getAllElements();
		/**
		 * Find the unique vacancy sites by selecting a vacancy site, average it with its duplicates
		 * which may be minimally displaced by the numerical construction.
		 * If there has not been a vacancy been created in a small vicinity, 
		 * place a vacancy at the computed position.
		 */
		for(int i = 0; i < possibleVacancyList.size(); i++) {
			//Add new found to data structures
			Vacancy v = possibleVacancyList.get(i);
			this.particles.add(v);
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
			dataPanel = new JParticleDataControlPanel<Vacancy>(this, new float[]{0.7f,0.9f,0.9f}, 1.5f);
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
		clone.testForSurfaces = this.testForSurfaces;
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
	private Vec3 getCenterOfMass(List<Vec3> list) {
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
	private ArrayList<Tupel<Vec3, Vec3>> getConvexHullBoundaryPlanes(List<Vec3> list) {
		ArrayList<Tupel<Vec3, Vec3>> convexHull = new ArrayList<Tupel<Vec3, Vec3>>();
		
		if (list.size() < 4) return convexHull;
		
		Vec3 com = getCenterOfMass(list);
		
		Point3d[] points = new Point3d[list.size()];
		
		//Convert input from Vec3 to Point3d for quickhull library
		for(int i = 0; i < points.length; i++){
			Vec3 v = list.get(i);
			points[i] = new Point3d(v.x-com.x, v.y-com.y, v.z-com.z);
		}
		
		QuickHull3D hull = new QuickHull3D();
		hull.build(points);
		
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
			Tupel<Vec3, Vec3> e = new Tupel <Vec3, Vec3> (n, x3.add(com));
		
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
		private float dist;
		
		public Vacancy(Vec3 v, float d) {
			super(v.x,v.y, v.z);
			dist = d;
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
			return String.format("Vacancy position  ( %.6f, %.6f, %.6f) Dist: %6f", x, y, z, dist);
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
	
	
	private class Vec3NoEqual extends Vec3{
		@Override
		public boolean equals(Object obj) {
			return obj==this;
		}
	}
}
