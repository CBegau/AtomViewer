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

package model.skeletonizer;

import java.awt.event.InputEvent;
import java.util.*;

import common.Vec3;
import crystalStructures.CrystalStructure;
import model.*;
import model.polygrain.Grain;

/**
 * A dislocation is extracted from the skeleton as a set of skeletonNodes stored in a array
 * The dislocation is a curve, composed from piecewise defined straight segments (polyline)  
 * @author begauc9f
 *
 */
public class Dislocation implements Pickable{
	private int id;
	
	private SkeletonNode[] polyline;
	private ArrayList<PlanarDefect> adjacentPlanarDefect = new ArrayList<PlanarDefect>();
	
	private Grain grain;
	private BurgersVectorInformation bvInfo;
	private Skeletonizer skel;
	
	/**
	 * Create a new dislocation from an array of SkeletonNodes
	 * @param polyline
	 */
	public Dislocation(SkeletonNode[] polyline, Skeletonizer skel){
		this.skel = skel;
		this.id = skel.getDislocationIDSource().getUniqueID();
		this.polyline = polyline;
		
		if (skel.getAtomData().isPolyCrystalline() && !skel.getAtomData().getCrystalStructure().skeletonizeOverMultipleGrains()){
			//as only atoms within the same grain as skeletonized into a dislocation
			//the first mapped atoms yields the correct grain
			int grainNumber = polyline[0].getMappedAtoms().get(0).getGrain();
			
			if (grainNumber != Atom.DEFAULT_GRAIN && grainNumber != Atom.IGNORED_GRAIN)
				grain = skel.getAtomData().getGrains(grainNumber);
		}
	}

	/**
	 * Adds a planar defect to the list of stacking faults, only used in direct import of
	 * skeletons (which is not implemented right now completely)
	 * @param sf
	 */
	protected void addAdjacentStackingFault(PlanarDefect sf){
		if (adjacentPlanarDefect==null) adjacentPlanarDefect = new ArrayList<PlanarDefect>();
		this.adjacentPlanarDefect.add(sf);
	}
	
	
	/**
	 * has to be called in order to determine the relationship between stacking faults and dislocations
	 * The nearest neighborhood graph between dislocation core atoms and stacking fault atoms has to be
	 * created before or no relationship will be detected
	 */
	protected void findAdjacentStackingFaults(Map<Atom, PlanarDefect> defectMap, NearestNeighborBuilder<Atom> nnb) {
		//Map which stacking fault is in contact with how many nodes
		HashMap<PlanarDefect, Integer> contactSurface = new HashMap<PlanarDefect, Integer>();
		
		for (int l = 0; l < polyline.length; l++) {
			// Count for the every node of a line which surfaces are in contact
			// (one of the dislocation core atoms is sharing a nearest neighbor with a stacking fault)
			// Each surface is counted once per each node
			ArrayList<PlanarDefect> inContact = new ArrayList<PlanarDefect>();
			for (int j = 0; j < polyline[l].getMappedAtoms().size(); j++) {
				Atom a = polyline[l].getMappedAtoms().get(j);
				ArrayList<Atom> stackingFaultNeigh = nnb.getNeigh(a);
				
				for (int i = 0; i < stackingFaultNeigh.size(); i++) {
					Atom b = stackingFaultNeigh.get(i);
					PlanarDefect pd = defectMap.get(b);
					if (pd != null) {
						if (!inContact.contains(pd)) inContact.add(pd);
					}
				}
			}

			for (PlanarDefect s : inContact) {
				if (contactSurface.containsKey(s)){
					contactSurface.put(s, contactSurface.get(s)+1);
				} else {
					contactSurface.put(s, 1);
				}		
			}
		}
		// if more than half of the nodes are in contact with a surface,
		// this surface is adjacent
		ArrayList<PlanarDefect> surfaces = new ArrayList<PlanarDefect>();
		if (contactSurface.size() > 0) {
			for (PlanarDefect s : contactSurface.keySet()) {
				if (contactSurface.get(s) > polyline.length / 2) surfaces.add(s);
			}
		}
		this.adjacentPlanarDefect = surfaces;
	}

	/**
	 * Test if a stacking fault is adjacent to this dislocation
	 * NOTE: if findAdjacentDislocations() has not been called or no nearest neighbors are created
	 * between atoms in dislocation cores and stacking fault, false will always be returned
	 * @param s
	 * @return
	 */
	public boolean isPlanarDefectAdjacent(PlanarDefect s) {
		for (int i = 0; i < this.adjacentPlanarDefect.size(); i++) {
			if (adjacentPlanarDefect.get(i) == s) return true;
		}
		return false;
	}

	/**
	 * This is the original dislocation line
	 * Be careful by editing it
	 * @return
	 */
	public SkeletonNode[] getLine() {
		return polyline;
	}
	
	/**
	 * Access for postprocessors to edit the dislocation's polyline
	 * @param polyline a new array to SkeletonNodes to replace the current polyline-array
	 */
	public void replaceLine(SkeletonNode[] polyline){
		this.polyline = polyline;
	}

	/**
	 * A list of all adjacent surfaces
	 * @return the list of adjacent surfaces
	 */
	public List<PlanarDefect> getAdjacentSurfaces() {
		if (adjacentPlanarDefect == null) adjacentPlanarDefect = new ArrayList<PlanarDefect>(); 
		return adjacentPlanarDefect;
	}

	/**
	 * The first node in the polyline array
	 * @return
	 */
	public SkeletonNode getStartNode() {
		return polyline[0];
	}

	/**
	 * The last node in the polyline array
	 * @return
	 */
	public SkeletonNode getEndNode() {
		return polyline[polyline.length - 1];
	}

	/**
	 * The unique ID to identify this dislocation
	 * @return
	 */
	public int getID() {
		return id;
	}
	
	@Override
	public String toString() {
		String s = "Dislocation(" + id + ") Length " + getLength();
		if (this.bvInfo!=null){
			Vec3 bv;
			if (grain!=null){
				int grainNumber = polyline[0].getMappedAtoms().get(0).getGrain();
				s += String.format(" Grain(%d)", grainNumber);
				bv = grain.getCystalRotationTools().getInCrystalCoordinates(bvInfo.averageResultantBurgersVector);
			} else bv = skel.getAtomData().getCrystalRotation().getInCrystalCoordinates(bvInfo.averageResultantBurgersVector);
			s += String.format(" Avg. RBV ( %.3f | %.3f | %.3f )", bv.x, bv.y, bv.z);
			s += " (Magnitude=" + bvInfo.averageResultantBurgersVector.getLength() +")";
			if (bvInfo.burgersVector.isFullyDefined()) {
				s += " Burgers Vector: " + bvInfo.burgersVector.toString();
			} else {
				BurgersVector tbv;
				if (grain != null){
					tbv = grain.getCystalRotationTools().rbvToBurgersVector(bvInfo.averageResultantBurgersVector);
				} else tbv = skel.getAtomData().getCrystalRotation().rbvToBurgersVector(bvInfo.averageResultantBurgersVector);
				s += " Approximate Burgers Vector: " + tbv.toString();
			}
			if (bvInfo.isComputed())
				s += " (computed)";
		}
		
		return s;
	}
	
	public float getLength() {
		double length = 0.;
		for (int i = 0; i < polyline.length - 1; i++) {
			length += skel.getAtomData().getBox().getPbcCorrectedDirection(polyline[i], polyline[i + 1]).getLength();
		}
		return (float)length;
	}
	
	public Grain getGrain() {
		return grain;
	}
	
	public BurgersVectorInformation getBurgersVectorInfo() {
		return bvInfo;
	}
	
	public void setBurgersVectorInfo(BurgersVectorInformation bvInfo) {
		this.bvInfo = bvInfo;
	}

	@Override
	public Collection<?> getHighlightedObjects() {
		return null;
	}
	
	@Override
	public boolean isHighlightable() {
		return true;
	}
	
	public static class BurgersVectorInformation{
		private Vec3 averageResultantBurgersVector;
		private boolean lineSenseKnown = false;
		private BurgersVector burgersVector = null;
		private boolean computed = false;
		
		public BurgersVectorInformation(CrystalStructure cs, Vec3 averageResultantBurgersVector) {
			this.averageResultantBurgersVector = averageResultantBurgersVector;
			this.burgersVector = new BurgersVector(cs);
		}
		
		public boolean isLineSenseKnown() {
			return lineSenseKnown;
		}
		
		public void setLineSenseKnown(boolean lineSenseKnown) {
			this.lineSenseKnown = lineSenseKnown;
		}
		
		public BurgersVector getBurgersVector() {
			return burgersVector;
		}
		
		/**
		 * Assign a Burgers vector
		 * @param burgersVector the Burgers vector
		 * @param computed set to false if the Burgers vector is derived directly from the numerical
		 * averaged rbv, if computed from the sum at an dislocation junction, set to true
		 */
		public void setBurgersVector(BurgersVector burgersVector, boolean computed) {
			this.burgersVector = burgersVector;
			this.computed = computed;
		}
		
		public Vec3 getAverageResultantBurgersVector() {
			return averageResultantBurgersVector.clone();
		}
		
		public boolean isComputed(){
			return computed;
		}
	}
	
	@Override
	public String printMessage(InputEvent ev, AtomData data) {
		return toString();
	}
	
	@Override
	public Vec3 getCenterOfObject() {
		return polyline[polyline.length/2].clone();
	}
}