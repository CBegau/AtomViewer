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

package processingModules.skeletonizer.processors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import common.Vec3;
import crystalStructures.CrystalStructure;
import model.*;
import model.BurgersVector.BurgersVectorType;
import processingModules.skeletonizer.*;
import processingModules.skeletonizer.Dislocation.BurgersVectorInformation;

public class BurgersVectorAnalyzer {

	CrystalStructure cs;
	Skeletonizer skel;
	List<RBVToBVPattern> patterns;
	
	public BurgersVectorAnalyzer(CrystalStructure cs){
		this.cs = cs;
		this.patterns = cs.getBurgersVectorClassificationPattern();
	}
	
	public void analyse(final Skeletonizer skel) {
		this.skel = skel;
		calcAverages();
		
		boolean updated;
		
		for (Dislocation d : skel.getDislocations()){
			if (!d.getBurgersVectorInfo().isLineSenseKnown()) continue;
			
			BurgersVector bv;
			if (!skel.getAtomData().isPolyCrystalline() || skel.getAtomData().getCrystalStructure().skeletonizeOverMultipleGrains()){
				//Single crystal or poly-phase material with same orientations 
				 bv = skel.getAtomData().getCrystalRotation().rbvToBurgersVector(d.getBurgersVectorInfo().getAverageResultantBurgersVector());	
			} else {
				//Poly-crystal, check orientation first
				if (d.getGrain() == null) //for dislocations in default grain
					bv = skel.getAtomData().getCrystalRotation().rbvToBurgersVector(d.getBurgersVectorInfo().getAverageResultantBurgersVector());
				else
					bv = d.getGrain().getCystalRotationTools().rbvToBurgersVector(d.getBurgersVectorInfo().getAverageResultantBurgersVector());
			}
			
			BurgersVector trueBV = mapNumericalBurgersVectorToCrystalFromScratch(bv, d);
			if (trueBV != null) d.getBurgersVectorInfo().setBurgersVector(trueBV, false);
		}
			
			
		//Loop through all dislocation until there is no more progress in 
		//the identification of their burgers vectors
		do {
			updated = false;
			ArrayList<Dislocation> dislocationOrderedByLength = 
					new ArrayList<Dislocation>(skel.getDislocations());
			Collections.sort(dislocationOrderedByLength, new Comparator<Dislocation>(){
				@Override
				public int compare(Dislocation o1, Dislocation o2) {
					return (int)Math.signum(o1.getLength() - o2.getLength());
				}
			});
			
			for (Dislocation d : skel.getDislocations()) {
				if (identifyBurgersVectorFromNetwork(d))
					updated = true;
			}
		} while (updated);
		
		printStatusBurgersVectorAnalysis(skel);
	}

	protected BurgersVector mapNumericalBurgersVectorToCrystalFromScratch(BurgersVector bv, Dislocation d){
		for (RBVToBVPattern cp : patterns){
			if (cp.match(bv, d))
				return cp.replace(bv, d);
		}
		return null;
	}
	
	private void calcAverages(){
		//Create the average RBV for each dislocation
		for (Dislocation d : skel.getDislocations()){
			Vec3 averageResultantBurgersVector = new Vec3();
			d.setBurgersVectorInfo(new BurgersVectorInformation(cs, averageResultantBurgersVector));
			
			identifyLineSense(d);

			for (int i = 1; i < d.getLine().length-1; i++) {
				//Get the orientation of the segment before the current node
				//If the atoms lineSense is in the same direction add the burgers vector
				//Otherwise subtract it
				Vec3 localDirection = d.getLine()[i].subClone(d.getLine()[i-1]);
				
				SkeletonNode a = d.getLine()[i];
				Vec3 averageOnNode = new Vec3();
				
				for (int j = 0; j < a.getMappedAtoms().size(); j++) {
					RBV rbv = a.getMappedAtoms().get(j).getRBV();
					if (localDirection.dot(rbv.lineDirection)>0) averageOnNode.add(rbv.bv);
					else  averageOnNode.sub(rbv.bv);
				}
				averageOnNode.divide(a.getMappedAtoms().size());
				averageResultantBurgersVector.add(averageOnNode);
			}
			
			if (d.getLine().length>2) averageResultantBurgersVector.divide(d.getLine().length-2);
			else averageResultantBurgersVector.divide(d.getLine().length);
		}
	}
	
	
	private void identifyLineSense(Dislocation d) {
		//Count how many atoms have a lineSense in the orientation of the curve 
		int positiveLineSenseNodes = 0, negativeLineSenseNodes = 0;
		
		for (int i = 0; i < d.getLine().length; i++) {
			int positiveLineSense = 0, negativeLineSense = 0;
			Vec3 localDirection = new Vec3();
			//Orientation of the segment before the current node
			if (i != 0)
				localDirection = d.getLine()[i].subClone(d.getLine()[i-1]);
			if (i != d.getLine().length-1)
				localDirection.add(d.getLine()[i+1].subClone(d.getLine()[i]));
			
			SkeletonNode sn = d.getLine()[i];
			for (int j = 0; j < sn.getMappedAtoms().size(); j++) {
				Atom atom = sn.getMappedAtoms().get(j);
				if (atom.getRBV().lineDirection.dot(localDirection) > 0)
					positiveLineSense++;
				else negativeLineSense++;
			}
			
			//Check if the signal is clear enough
			//Can be a problem at stair rods
			if (negativeLineSense > positiveLineSense*3){
				negativeLineSenseNodes++;
			}
			
			if (positiveLineSense > negativeLineSense*3){
				positiveLineSenseNodes++;
			}
		}

		if (negativeLineSenseNodes > positiveLineSenseNodes)
			revertArray(d.getLine());
		d.getBurgersVectorInfo().setLineSenseKnown(true);
	}
	
	protected int getNumberOfNotVerifiedDislocations(SkeletonNode n){
		if (n.getJoiningDislocations() == null) return 0;
		int nonVerified = 0;
		for (int i=0; i<n.getJoiningDislocations().size(); i++){
			if (n.getJoiningDislocations().get(i).getBurgersVectorInfo().getBurgersVector().getType() == BurgersVectorType.UNDEFINED)
				nonVerified++;
		}
		return nonVerified;
	}
	
	public void printStatusBurgersVectorAnalysis(Skeletonizer skel){
		int verfiedDislocations = 0;
		int totalDislocations = skel.getDislocations().size();
		float totalLength = 0f;
		float verifiedLength = 0f;
		for (Dislocation d : skel.getDislocations()){
			if (d.getBurgersVectorInfo().getBurgersVector().isFullyDefined()) {
				verfiedDislocations++;
				verifiedLength += d.getLength();
			}
			totalLength += d.getLength();
		}
		System.out.println(String.format("Verified burgers vectors (number): %d/%d (%.2f",verfiedDislocations, totalDislocations
				,(verfiedDislocations/(float)totalDislocations)*100) + " %)");
		System.out.println(String.format("Verified burgers vectors (length): %.2f/%.2f (%.2f", 
				verifiedLength, totalLength, (verifiedLength/totalLength)*100f) + " %)");
	}

	protected boolean identifyBurgersVectorFromNetwork(Dislocation d) {
		if (!d.getBurgersVectorInfo().isLineSenseKnown()) return false;
		if (d.getBurgersVectorInfo().getBurgersVector().isFullyDefined()) return false;
		
		//start the calculation of a missing burgers vector at the junction where
		//the sum of all joining dislocation lengths in maximal
		//The longer the dislocation, the more reliable are their results
		float lengthAtStart = 0, lengthAtEnd = 0;
		for (Dislocation dis : d.getStartNode().getJoiningDislocations()){
			lengthAtStart += dis.getLength();
		}
		for (Dislocation dis : d.getEndNode().getJoiningDislocations()){
			lengthAtEnd += dis.getLength();
		}
		
		if (lengthAtStart > lengthAtEnd){
			if (calculateBurgersVector(d.getStartNode())) {
				return true;
			}
			if (calculateBurgersVector(d.getEndNode())){
				return true;
			}
		} else {
			if (calculateBurgersVector(d.getEndNode())){
				return true;
			}
			if (calculateBurgersVector(d.getStartNode())) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * @param n
	 * @return
	 */
	protected boolean calculateBurgersVector(SkeletonNode n) {
		if (n.getJoiningDislocations() == null || 
				n.getJoiningDislocations().size() == 1 || getNumberOfNotVerifiedDislocations(n) != 1) return false;
		List<Dislocation> joiningDis = n.getJoiningDislocations();
		Dislocation unknownDislocation = null;
		for (int i = 0; i < joiningDis.size(); i++)
			if (joiningDis.get(i).getBurgersVectorInfo().getBurgersVector().getType() == BurgersVectorType.UNDEFINED) 
				unknownDislocation = joiningDis.get(i);

		for (int i = 0; i < n.getJoiningDislocations().size(); i++) {
			Dislocation d = joiningDis.get(i);
			if (d != unknownDislocation) {
				if (d.getBurgersVectorInfo().getBurgersVector().getType() == BurgersVectorType.UNDEFINED 
						|| !d.getBurgersVectorInfo().isLineSenseKnown())
					return false;
			}
		}

		BurgersVector bv = new BurgersVector(cs);
		for (int i = 0; i < joiningDis.size(); i++) {
			// add/sub the Burgers vectors according to their line direction
			Dislocation d = n.getJoiningDislocations().get(i);
			if (d != unknownDislocation) {
				if (d.getEndNode() == n) bv.add(d.getBurgersVectorInfo().getBurgersVector());
				else bv.sub(d.getBurgersVectorInfo().getBurgersVector());
			}
		}
		
		if (n != unknownDislocation.getStartNode()) revertArray(unknownDislocation.getLine());
		unknownDislocation.getBurgersVectorInfo().setBurgersVector(bv, true);
		unknownDislocation.getBurgersVectorInfo().setLineSenseKnown(true);
		return true;
	}
	
	protected static void revertArray(Object[] o) {
		for (int left = 0, right = o.length - 1; left < right; left++, right--) {
			Object temp = o[left];
			o[left] = o[right];
			o[right] = temp;
		}
	}
	
	/**
	 * Numerical resultant Burgers vectors need to be mapped to crystallographic ones.
	 * Since the numerical results is to be expected to be slightly biased, especially in lenght,
	 * less strict patterns are required to identify the correct Burgers vector.
	 * 
	 * Each pattern consists of a pattern of Burgersvector components to match and one which is the replacement
	 * Intervals for min and max fraction can be set and the number of adjacent surfaces 
	 */
	public static class RBVToBVPattern{
		private int pattern, replacementFrac;
		private int minFrac, maxFrac, minSurfaces, maxSurfaces;
		private int[] patternComp, replacementComp;
		private BurgersVectorType type;
		
		/**
		 * Creates a pattern to classify a Burgers vector
		 * @param pattern Defining the numerical Burgers vector that is map to another one
		 * E.g. 211, or 632 
		 * If pattern is -1, the pattern will match on anything
		 * @param minFrac the minimum of a burgers vector fraction to map eg. 1/8<211>  
		 * @param maxFrac the minimum of a burgers vector fraction to map eg. 1/5<211>
		 * @param replacementPattern replaces the pattern, if pattern=632 if can be replaced by 211 for example
		 * @param replacementFrac the replacing fraction, Vectors of type 1/19<632> can be mapped to 1/6<211>
		 * @param type The Burgers vector type
		 */
		public RBVToBVPattern(int pattern, int minFrac, int maxFrac, 
				int replacementPattern, int replacementFrac, BurgersVectorType type) {
			this(pattern, minFrac, maxFrac, replacementPattern, replacementFrac, 0, 0, type);
		}
		
		public RBVToBVPattern(int pattern, int minFrac, int maxFrac, int replacement, int replacementFrac, 
				 int minSurfaces, int maxSurfaces, BurgersVectorType type) {
			this.pattern = orderPattern(pattern);
			this.patternComp = decompPattern(this.pattern);
			this.replacementFrac = replacementFrac;
			this.replacementComp = decompPattern(orderPattern(replacement));
			
			this.maxFrac = maxFrac;
			this.minFrac = minFrac;
			this.maxSurfaces = maxSurfaces;
			this.minSurfaces = minSurfaces;
			this.type = type;
		}
		
		/**
		 * Test if the given Burgers vector matches the pattern
		 * @param bv
		 * @param d
		 * @return
		 */
		public boolean match(BurgersVector bv, Dislocation d){
			if (pattern == -1) return true; 
			
			int p = Math.abs(bv.getDirection()[0])*100;
			p += Math.abs(bv.getDirection()[1])*10;
			p += Math.abs(bv.getDirection()[2]);
			p = orderPattern(p);
			return (p == pattern && bv.getFraction()>=minFrac && bv.getFraction()<=maxFrac &&
					d.getAdjacentSurfaces().size() >= minSurfaces && d.getAdjacentSurfaces().size() <= maxSurfaces);
		}
		
		/**
		 * Test if Burgers vector is matching the replacement pattern, to check if they belong to the same type
		 * @param bv
		 * @return
		 */
		public boolean typeMatch(BurgersVector bv){
			int p = Math.abs(bv.getDirection()[0])*100;
			p += Math.abs(bv.getDirection()[1])*10;
			p += Math.abs(bv.getDirection()[2]);
			p = orderPattern(p);
			return p == pattern && bv.getFraction() == replacementFrac;
		}
		
		public BurgersVectorType getType() {
			return type;
		}
		
		/**
		 * Creates a new Burgers vector by applying the replacement pattern
		 * @param bv
		 * @param d
		 * @return
		 */
		public BurgersVector replace(BurgersVector bv, Dislocation d){
			if (!match(bv,d)) return null;
			int[] p = new int[3];
			
			for (int i=0; i<3; i++){
				for (int j=0; j<3; j++){
					if (Math.abs(bv.getDirection()[i]) == patternComp[j]){
						p[i] = (bv.getDirection()[i]>0?1:-1) * replacementComp[j];
						break;
					}
				}	
			}
			return new BurgersVector(replacementFrac, p[0], p[1], p[2], bv.getCrystalStructure(), type);
		}
		
		private int[] decompPattern(int pattern){
			int[] decomp = new int[3];
			decomp[2] = pattern/100;
			pattern -= decomp[2]*100;
			decomp[1] = pattern/10;
			pattern -= decomp[1]*10;
			decomp[0] = pattern;
			return decomp;
		}
		
		private int orderPattern(int pattern){
			int b1 = pattern/100;
			pattern -= b1*100;
			int b2 = pattern/10;
			pattern -= b2*10;
			int b3 = pattern;
			//Bubble sorting the three values
			if (b3>b2){
				int tmp = b3; b3 = b2; b2 = tmp; 
			}
			if (b2>b1){
				int tmp = b1; b1 = b2; b2 = tmp; 
			}
			if (b3>b2){
				int tmp = b3; b3 = b2; b2 = tmp; 
			}
			
			return b1*100+b2*10+b3;
		}
	}
}
