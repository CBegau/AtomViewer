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

package crystalStructures;

import java.util.*;

import common.Vec3;
import gui.PrimitiveProperty.BooleanProperty;
import model.*;
import model.BurgersVector.BurgersVectorType;
import model.polygrain.grainDetection.AtomToGrainObject;
import model.polygrain.grainDetection.GrainDetectionCriteria;
import processingModules.skeletonizer.processors.*;
import processingModules.skeletonizer.processors.BurgersVectorAnalyzer.RBVToBVPattern;

public class FCCStructure extends CrystalStructure {

	private final static int FCC = 1;
	private final static int HCP = 2;
	
	private static Vec3[] neighPerfFCC = new Vec3[]{
		new Vec3(0f, 0.5f, 0.5f),
		new Vec3(0f,-0.5f,-0.5f),
		new Vec3(0f,-0.5f, 0.5f),
		new Vec3(0f, 0.5f,-0.5f),
		
		new Vec3(0.5f , 0f, 0.5f),
		new Vec3(-0.5f, 0f,-0.5f),
		new Vec3(-0.5f, 0f, 0.5f),
		new Vec3(0.5f , 0f,-0.5f),
		
		new Vec3(0.5f , 0.5f , 0f),
		new Vec3(-0.5f, -0.5f, 0f),
		new Vec3(-0.5f, 0.5f , 0f),
		new Vec3(0.5f , -0.5f, 0f)
	};
	
	private static final ArrayList<RBVToBVPattern> bvClassifcationPattern = new ArrayList<RBVToBVPattern>();
	static{
		//Shockley partial <211> with two adjacent stacking fault planes
		bvClassifcationPattern.add(new RBVToBVPattern(211, 4, 9, 211, 6, 2, 2, BurgersVectorType.PARTIAL));
		//Shockley partial <211> with more than two adjacent stacking fault planes e.g. thin separated cores
		bvClassifcationPattern.add(new RBVToBVPattern(211, 5, 7, 211, 6, 0, 100, BurgersVectorType.PARTIAL));
		//Perfect <110> with no adjacent stacking fault planes
		bvClassifcationPattern.add(new RBVToBVPattern(110, 1, 3, 110, 2, 0, 0, BurgersVectorType.PERFECT));
		//Ideal Shockley partials, where the adjacent stacking faults are not precisely identified 
		bvClassifcationPattern.add(new RBVToBVPattern(211, 6, 6, 211, 6, 0, 10, BurgersVectorType.PARTIAL));
		//Slightly disordered partial <211> recognized as <632>
		bvClassifcationPattern.add(new RBVToBVPattern(632, 10, 30, 211, 6, 2, 3, BurgersVectorType.PARTIAL));
//		//Slightly disordered partial <211> recognized as 1/5<211> or 1/8<211> 
//		bvClassifcationPattern.add(new ClassificationPattern(211, 5, 8, 211, 6, 2, 2, BurgersVectorType.PARTIAL));
		//More severed disordered partial <211> recognized as 1/5<211> or 1/11<211> but with correct number of adjacent stacking faults
		bvClassifcationPattern.add(new RBVToBVPattern(211, 5, 11, 211, 6, 2, 2, BurgersVectorType.PARTIAL));
		//Stair Rod <100> with extremely clear signals
		bvClassifcationPattern.add(new RBVToBVPattern(100, 3, 6, 100, 3, 4, 4, BurgersVectorType.STAIR_ROD));
		//Stair Rod 1/3<101> with perfect signals
		bvClassifcationPattern.add(new RBVToBVPattern(110, 3, 3, 110, 3, 4, 10, BurgersVectorType.STAIR_ROD));
		//Stair Rod 1/6<101> with perfect signals
		bvClassifcationPattern.add(new RBVToBVPattern(110, 6, 8, 110, 6, 4, 10, BurgersVectorType.STAIR_ROD));
		//Stair Rod 1/6<101> with not so close signals, but correct number of adjacent surfaces
		bvClassifcationPattern.add(new RBVToBVPattern(110, 6, 11, 110, 6, 4, 4, BurgersVectorType.STAIR_ROD));
		//Stair Rod 1/6<310> with perfect signals
		bvClassifcationPattern.add(new RBVToBVPattern(310, 6, 8, 310, 6, 4, 4, BurgersVectorType.STAIR_ROD));
		//Stair Rod 1/6<123> with perfect signals
		bvClassifcationPattern.add(new RBVToBVPattern(123, 6, 8, 123, 6, 4, 4, BurgersVectorType.STAIR_ROD));
		//Frank partial 1/3<111> with perfect signals
		bvClassifcationPattern.add(new RBVToBVPattern(111, 3, 3, 111, 3, 0, 4, BurgersVectorType.FRANK_PARTIAL));
	}
	
	protected BooleanProperty highTempProperty = 
			new BooleanProperty("highTempADA", "optimize defect classification for >150K",
					"<html>Modifies the thresholds to classify atoms.<br>"
					+ "Typically reduces the number of false classifications.</html>",
					false);
	
	public FCCStructure() {
		super();
		crystalProperties.add(highTempProperty);
	}
	
	@Override
	protected CrystalStructure deriveNewInstance() {
		return new FCCStructure();
	}
	
	@Override
	protected String getIDName() {
		return "FCC";
	}
	
	@Override
	public float getDefaultNearestNeighborSearchScaleFactor(){
		return 0.848f;
	}
	
	@Override
	public boolean hasStackingFaults(){
		return true;
	}
	
	@Override
	public Vec3[] getStackingFaultNormals(CrystalRotationTools crt){
		Vec3[] stackingFaultNormals = new Vec3[4];
		
		stackingFaultNormals[0] = crt.getInXYZ(new Vec3( 1f, 1f, 1f)).normalize(); 
		stackingFaultNormals[1] = crt.getInXYZ(new Vec3(-1f, 1f,-1f)).normalize();
		stackingFaultNormals[2] = crt.getInXYZ(new Vec3(-1f,-1f, 1f)).normalize();
		stackingFaultNormals[3] = crt.getInXYZ(new Vec3( 1f,-1f,-1f)).normalize();
		
		return stackingFaultNormals;
	}
	
	@Override
	public int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb) {
		float threshold = highTempProperty.getValue() ? -0.945f : -0.965f;
		
		ArrayList<Vec3> neigh = nnb.getNeighVec(atom);
		/*
		 * type=0: bcc
		 * type=1: fcc
		 * type=2: hcp
		 * type=3: unassigned
		 * type=4: less than 12 neighbors
		 * type=5: more than 12 neighbors
		 * type=6: less than 10 neighbors
		 */
		if (neigh.size() < 10) return 6;
		else if (neigh.size() < 12) return 4;
		else if (neigh.size() > 14) return 7;
		else {
			int co_x0 = 0;
			int co_x1 = 0;
			int co_x2 = 0;
			for (int i = 0; i < neigh.size(); i++) {
				Vec3 v = neigh.get(i);
				float v_length = v.getLength();
				for (int j = 0; j < i; j++) {
					Vec3 u = neigh.get(j);
					float u_length = u.getLength();
					float a = v.dot(u) / (v_length*u_length);
					
					if (a < threshold) co_x0++; // 0.945
					else if (a < -.915f) co_x1++;
					else if (a < -.775f) co_x2++; // 0.755
				}
			}

			if (co_x0 == 7 && neigh.size() == 14) return 0;
			else if (co_x0 == 6 && neigh.size() == 12) return FCC;
			else if (co_x0 == 3 && co_x1 <= 1 && co_x2 > 2 && neigh.size() == 12) return HCP;
			else if (neigh.size() > 12) return 5;
			else if (neigh.size() == 12) return 3;
			else return 4;
		}
	}
	
	@Override
	public List<Atom> getStackingFaultAtoms(AtomData data){
		ArrayList<Atom> sfAtoms = new ArrayList<Atom>();
		for (int i=0; i<data.getAtoms().size(); i++){
			Atom a = data.getAtoms().get(i);
			if (a.getType() == HCP && a.getGrain() != Atom.IGNORED_GRAIN)
				sfAtoms.add(a);
		}
		return sfAtoms;
	}

	@Override
	public boolean isRBVToBeCalculated(Atom a) {
		if (a.getGrain() == Atom.IGNORED_GRAIN) return false;
		
		int type = a.getType();
		if (type != FCC && type != HCP && type != 6) return true;
		return false;
	}
	
	@Override
	public int getNumberOfTypes() {
		return 8;
	}

	@Override
	public String getNameForType(int i) {
			switch (i) {
			case 0: return "bcc";
			case 1: return "fcc";
			case 2: return "hcp";
			case 3: return "12 neigh., unassigned";
			case 4: return "10-11 neighbors";
			case 5: return "13-14 neighbors";
			case 6: return "<10 neighbors";
			case 7: return ">14 neighbors";
			default: return "unknown";
		}
	}
	
	
	@Override
	public Vec3[] getPerfectNearestNeighborsUnrotated() {
		return neighPerfFCC.clone();
	}
	
	@Override
	public ArrayList<RBVToBVPattern> getBurgersVectorClassificationPattern() {
		return bvClassifcationPattern;
	}
	
	public List<SkeletonPreprocessor> getSkeletonizerPreProcessors(){
		Vector<SkeletonPreprocessor>  list = new Vector<SkeletonPreprocessor>();
		list.add(new MeshCleaningPreprocessor());
		list.add(new MeshLineSenseCenteringPreprocessor());
		
		return list;
	}
	
	@Override
	public int getDefaultType(){
		return 1;
	}
	
	@Override
	public int getSurfaceType() {
		return 6;
	}
	
	@Override
	public GrainDetectionCriteria getGrainDetectionCriteria() {
		return new FCCGrainDetectionCriteria(this);
	}
	
	private class FCCGrainDetectionCriteria implements GrainDetectionCriteria {

		private CrystalStructure cs;
		private int surfaceType;
		
		public FCCGrainDetectionCriteria(CrystalStructure cs){
			this.cs = cs;
			this.surfaceType = cs.getSurfaceType();
		}
		
		@Override
		public float getNeighborDistance() {
			return cs.getNearestNeighborSearchRadius();
		}
		
		@Override
		public boolean acceptAsFirstAtomInGrain(Atom atom, List<AtomToGrainObject> neighbors) {
			return neighbors.size()>9;
		}

		@Override
		public int getMinNumberOfAtoms() {
			return 20;
		}

		@Override
		public boolean includeAtom(Atom atom) {
			if (atom.getType() != surfaceType) return true;
			return false;
		}

		@Override
		public boolean includeAtom(AtomToGrainObject atom, List<AtomToGrainObject> neighbors) {
			int count = 0;
			for (int i=0; i<neighbors.size(); i++){
				Atom a = neighbors.get(i).getAtom(); 
				if (a.getType() == FCC || a.getType() == HCP) count++;
			}
			
			return count>9;
		}
	}

}
