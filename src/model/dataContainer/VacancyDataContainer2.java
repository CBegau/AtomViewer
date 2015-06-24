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
import model.dataContainer.VacancyDataContainer2.Vacancy;

public final class VacancyDataContainer2 extends ParticleDataContainer<Vacancy>{

	private static JParticleDataControlPanel<Vacancy> dataPanel;
	
	private float nnd_tolerance = 0.75f;
	private boolean testForSurfaces = true;
	
	@Override
	public boolean isTransparenceRenderingRequired() {
		return false;
	}

	@Override
	protected String getLabelForControlPanel() {
		return "Vacancies2";
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
		
		final List<Vec3> possibleVacancyList = Collections.synchronizedList(new ArrayList<Vec3>());
		
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
		final NearestNeighborBuilder<Vec3> possibleVacancies = new NearestNeighborBuilder<Vec3>(data.getBox(), nnd, true);
		
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

						if (nnb.size() < 4) continue;			
						
						//get the voronoi cell
						List<Vec3> voronoi = VoronoiVolume.getVoronoiVertices(nnb);
						for (Vec3 point : voronoi){
							//Exclude points that are very close to an atom
							if (point.getLength() < minDistanceToAtom || point.getLengthSqr() > squaredCutoff)
								continue;
							
							point.add(a);
							
							// Test validity
							final List<Vec3> neigh = allNearestNeighbors.getNeighVec(point);
							boolean valid = true;
							for (Vec3 p : neigh) {
								float d = p.getLength();
								if (d < minDistanceToAtom) {
									valid = false;
									break;
								}
							}

							if (valid) {
								Vec3NoEqual tmp = new Vec3NoEqual();
								tmp.setTo(point);

								synchronized (possibleVacancies) {
									List<Vec3> nearest = possibleVacancies.getNeighVec(tmp, 1);
									if (nearest.size() == 0 || nearest.get(0).getLength() > 0.1f*minDistanceToAtom) {
										possibleVacancies.add(tmp);
										data.getBox().backInBox(tmp);
										possibleVacancyList.add(tmp);
									}	
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
		
		/**
		 * Find the unique vacancy sites by selecting a vacancy site, average it with its duplicates
		 * which may be minimally displaced by the numerical construction.
		 * If there has not been a vacancy been created in a small vicinity, 
		 * place a vacancy at the computed position.
		 */
		for(int i = 0; i < possibleVacancyList.size(); i++) {
			//Add new found to data structures
			Vec3 marker = possibleVacancyList.get(i);
			List<Vec3> nnb = allNearestNeighbors.getNeighVec(marker, 1);
			Vacancy v;
			if (nnb.isEmpty())
				v = new Vacancy(marker, allNearestNeighbors.getCutoff());
			else v = new Vacancy(marker, nnb.get(0).getLength());
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
		return "Vacancy detector2";
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
		VacancyDataContainer2 clone = new VacancyDataContainer2();
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

	
	private Vec3 getChebyshevCenter(List<Vec3> list) {
		Vec3 c = new Vec3(0,0,0);
		
		for (int i = 0; i < list.size(); i++) {
			c.add(list.get(i));
		}
		
		c.divide(list.size());
		
		Vec3 res;
		Vec3 xk = c;
		Vec3 yk;
		
		Vec3 temp;
		Vec3 temp1;
		Vec3 temp2;
		
		float alpha;
		float alphaTemp;
		float d;
		float epsilon = 1e-4f;
		
		while(true) {
			
			ArrayList<Vec3> ek = new ArrayList<Vec3>();
			ArrayList<Vec3> ik =  new ArrayList<Vec3>();
			ArrayList<Vec3> ikminus = new ArrayList<Vec3>();
			
			
			//calculating the largest distance from xk
			
			float maxDist = list.get(0).getDistTo(xk);
			
			for (int i = 1; i < list.size(); i++) {
				
				d = list.get(i).getDistTo(xk);
				
				if (d < maxDist) {
					
					maxDist = d;
				}
			}
			
			for (int i = 0; i < list.size(); i++) {
				
				d = list.get(i).getDistTo(xk);
				
				if (d >= (maxDist - epsilon)) {
				
					ek.add(list.get(i));
				
				} else {
					
					ik.add(list.get(i));
				}
			}
			
			if(ek.size() == list.size()) {
				
				res = xk;
				return res;
				
			} 
			
			yk = getChebyshevCenter(ek);
			
			if(yk.getDistTo(xk) < epsilon) {
				
				res = xk;
				return res;
				
			}
			
			for(int k = 0; k < ik.size(); k++) {
				
				temp1 = yk.subClone(xk);
				temp2 = ik.get(k).subClone(yk);
				
				if((temp1.dot(temp2)) < 0) ikminus.add(ik.get(k));
			
			}
			
			
			if (ikminus.size() == 0) {
				
				alpha = Float.POSITIVE_INFINITY;
			
			} else {
				
				temp = ikminus.get(0).subClone(xk);
				temp1 = yk.subClone(xk);
				temp2 = ikminus.get(0).subClone(yk);
				
				alpha = (temp.getLengthSqr() - maxDist*maxDist)/(2.f*(temp1.dot(temp2)));
				
				if (ikminus.size() > 1) { 
					
					for(int k = 1; k < ikminus.size(); k++) {
						
						temp = ikminus.get(k).subClone(xk);
						temp1 = yk.subClone(xk);
						temp2 = ikminus.get(k).subClone(yk);
						
						alphaTemp = (temp.getLengthSqr() - maxDist*maxDist)/(2.f*(temp1.dot(temp2)));
						
						if(alphaTemp < alpha) alpha = alphaTemp;
	
					}
				}
			}
			
			if(alpha >= 1) {
				
				res = yk;
				return res;
			
			}
			
			temp = yk.subClone(xk);
			xk = xk.addClone(temp.multiplyClone(alpha));
			
			
		}	
		
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
