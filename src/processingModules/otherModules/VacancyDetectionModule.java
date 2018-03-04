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

package processingModules.otherModules;

import gui.JPrimitiveVariablesPropertiesDialog;
import gui.PrimitiveProperty.*;

import java.awt.event.InputEvent;
import java.util.*;

import javax.swing.JFrame;

import quickhull3d.Point3d;
import quickhull3d.QuickHull3D;
import common.*;
import crystalStructures.CrystalStructure;
import model.*;
import processingModules.ClonableProcessingModule;
import processingModules.DataContainer;
import processingModules.JDataPanel;
import processingModules.ProcessingResult;
import processingModules.otherModules.ParticleDataContainer.JParticleDataControlPanel;
import processingModules.toolchain.Toolchainable.ExportableValue;
import processingModules.toolchain.Toolchainable.ToolchainSupport;

@ToolchainSupport
public final class VacancyDetectionModule extends ClonableProcessingModule {

	private static JParticleDataControlPanel<Vacancy> dataPanel;
	
	@ExportableValue
	private float nnd_tolerance = 0.75f;
	@ExportableValue
	private boolean testForSurfaces = false;
	

	@Override
	public String getFunctionDescription() {
		return "Identify vacancies in crystals";
	}

	@Override
	public String getShortName() {
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
	public DataColumnInfo[] getDataColumnsInfo() {
		return null;
	}
	
	@Override
	public boolean showConfigurationDialog(JFrame frame, AtomData data) {
		JPrimitiveVariablesPropertiesDialog dialog = new JPrimitiveVariablesPropertiesDialog(null, "Detect vacancies");
		
		FloatProperty tolerance = dialog.addFloat("nnd_tolerance", 
				"Fraction of distance to nearest neghbor of a gap to be considered as vacancy"
				, "", nnd_tolerance, 0.01f, 1.1f);
		BooleanProperty surface = dialog.addBoolean("testSurface", "Test for surfaces", "", testForSurfaces);
		
		boolean ok = dialog.showDialog();
		if (ok){
			
			this.nnd_tolerance = tolerance.getValue();
			this.testForSurfaces = surface.getValue();
		}
		return ok;
	}

	@Override
	public boolean canBeAppliedToMultipleFilesAtOnce() {
		return true;
	}

	@Override
	public ProcessingResult process(AtomData data) throws Exception {
		VacancyDataContainer dc = new VacancyDataContainer();
		dc.findVacancies(data, this.nnd_tolerance);
		dc.updateRenderData(data);
		return new DataContainer.DefaultDataContainerProcessingResult(dc, "");
	}

	
	public class VacancyDataContainer extends ParticleDataContainer<Vacancy>{
		@Override
		public boolean isTransparenceRenderingRequired() {
			return false;
		}
		
		@Override
		protected String getLabelForControlPanel() {
			return "Vacancies";
		}
		
		@Override
		public JDataPanel getDataControlPanel() {
			return getParticleDataControlPanel();
		}
		
		@Override
		protected JParticleDataControlPanel<?> getParticleDataControlPanel() {
			if (dataPanel == null)
				dataPanel = new JParticleDataControlPanel<Vacancy>(this, new float[]{0.9f,0.9f,0.9f}, 1.5f);
			return dataPanel;
		}
		
		private void findVacancies(final AtomData data, final float nnd_tolerance){
			final CrystalStructure cs = data.getCrystalStructure();
			
			//Cutoff-radii
			final float nnd = cs.getDistanceToNearestNeighbor();
			final float nndSearch = cs.getNearestNeighborSearchRadius();
			final float minDistanceToAtom = nnd_tolerance*nnd;
			
			//Define atom types
			final int defaultType = cs.getDefaultType();
			final int surfaceType = cs.getSurfaceType();
			
			//Data structure to get all neighbors up to a selected distance
			//Defects are considered up to twice the minimum distance to fit a vacancy into it
			final NearestNeighborBuilder<Vec3> defectedNearestNeighbors = 
					new NearestNeighborBuilder<Vec3>(data.getBox(), 2f*minDistanceToAtom, false);
			final float defectCutoff = defectedNearestNeighbors.getCutoff();
			
			//The total set of atoms is only considered for the typically search radius
			final NearestNeighborBuilder<Vec3> allNearestNeighbors = 
					new NearestNeighborBuilder<Vec3>(data.getBox(), nndSearch, true);
			//Data structure to get all possible vacancies in small neighborhood
			final NearestNeighborBuilder<Vacancy> possibleVacancies = new NearestNeighborBuilder<Vacancy>(data.getBox(), minDistanceToAtom, false);
			
			//This list is holding candidates that need to be tested for vacancy positions
			final ArrayList<Atom> nextToVancanyCandidateAtoms = new ArrayList<>();
			

			//Insert all atoms in the different lists
			allNearestNeighbors.addAll(data.getAtoms());
			
			for (Atom a : data.getAtoms()){
				if (a.getType() != defaultType){
					defectedNearestNeighbors.add(a);	//<-All defects
					if (a.getType() != surfaceType)
						nextToVancanyCandidateAtoms.add(a); //<-Defects that are not surface atoms
				}
			}
			
			ThreadPool.executeAsParallelStream(nextToVancanyCandidateAtoms.size(), k->{
				//First get the nearest defect atoms for an atom possibly next to a vacancy
				Atom a = nextToVancanyCandidateAtoms.get(k);
				ArrayList<Vec3> nnb = defectedNearestNeighbors.getNeighVec(a);
				if (nnb.size() < 4) return;
				
				
				//A convex hull may be required, but is only computed on demand
				//thus here initialized with null
				ArrayList<Tupel<Vec3,Vec3>> convexHull = null;
				
				//Compute the voronoi diagram around the atom and its neighbors
				//Vacancies can be positions that are far enough away from any defect atom
				List<Vec3> voronoiVertices = VoronoiVolume.getVoronoiVertices(nnb);
				
				for (Vec3 voro : voronoiVertices){
					//Exclude points that are either too close to be a vacancy position
					//or that are too far away and actually are boundary points in the voronoi diagram
					if (voro.getLength() < minDistanceToAtom || voro.getLength() > defectCutoff)
						return;
					
					//The voronoi vertex is in the local coordinate system of atom a
					//Move it back into the global coordinate system and correct periodicity if needed
					Vec3 absolutePoint = voro.addClone(a);
					data.getBox().backInBox(absolutePoint);
					
					//Compute the distance from the voronoi vertex to all atoms
					//Test if all of them are further away than the minimal distance
					final List<Vec3> neigh = allNearestNeighbors.getNeighVec(absolutePoint);
					float minDist = nndSearch;
					boolean valid = true;
					for (Vec3 p : neigh) {
						float d = p.getLength();
						minDist = Math.min(d, minDist);
						if (minDist < minDistanceToAtom) {
							valid = false;
							break;
						}
					}
					
					//Test if vertex is inside a convex hull if requested
					if (testForSurfaces && valid){
						if (convexHull == null) {
							//Compute the convex hull if needed
							nnb.add(new Vec3(0f, 0f, 0f)); //Add the central atom
							convexHull = getConvexHullBoundaryPlanes(nnb);
						}
						valid = isInConvexHull(voro, convexHull);
					}
					    
					//The vertex is far enough away from any atom
					if (valid) {
						Vacancy v = new Vacancy(absolutePoint, minDist);
						//Start with the first step in the reduction of points that are found multiple times
						synchronized (possibleVacancies) {
							//Get the already identified vacancies close to the new one
							List<Tupel<Vacancy,Vec3>> nearOnes = possibleVacancies.getNeighAndNeighVec(v);
							Vacancy bestFit = v;
							//Delete those points that are close to the new vacancy
							//The one that has the largest distance to an atom will survive
							for (Tupel<Vacancy,Vec3> t : nearOnes){
								if (t.o2.getLength()<0.01f*minDistanceToAtom){
									if (bestFit.dist<t.o1.dist)
										bestFit = t.o1;
									possibleVacancies.remove(t.o1);
								}
							}

							possibleVacancies.add(bestFit);
						}
					}
				}
			});
			
			
			//Start the second part of the reduction part
			//First get all possible vacancies and sort the with ascending distance to
			//neighboring atoms
			List<Vacancy> possibleVacancyList = possibleVacancies.getAllElements();
			possibleVacancies.removeAll();
			
			Collections.sort(possibleVacancyList, (Vacancy o1, Vacancy o2)->{
				if (o1.dist>o2.dist) return 1;
				else if (o1.dist<o2.dist) return -1;
				else return 0;
			});
			
			//Again, insert vacancies one by one
			for (Vacancy v : possibleVacancyList){
				List<Tupel<Vacancy,Vec3>> nearOnes = possibleVacancies.getNeighAndNeighVec(v);
				Vacancy bestFit = v;
				//And delete all neighboring ones and keep only that one that is
				//furthest away from an atom
				for (Tupel<Vacancy,Vec3> t : nearOnes){
					if (t.o2.getLength()<minDistanceToAtom){
//						if (bestFit.dist<t.o1.dist)		Vacancies are ordered ascendingly in dist, no need to test here
//							bestFit = t.o1;
						possibleVacancies.remove(t.o1);
					}
				}

				possibleVacancies.add(bestFit);
			}
			
			//Copy data into the final results container
			this.particles.addAll(possibleVacancies.getAllElements());
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
			ArrayList<Tupel<Vec3, Vec3>> convexHull = new ArrayList<>();
			
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
	}
	
	static class Vacancy extends Vec3 implements Pickable{
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
		public Tupel<String,String> printMessage(InputEvent ev, AtomData data) {
			return new Tupel<String,String>("Vacancy site", 
					CommonUtils.buildHTMLTableForKeyValue(
							new String[]{"Position", "Distance to atom"}, new Object[]{this, dist}));
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
