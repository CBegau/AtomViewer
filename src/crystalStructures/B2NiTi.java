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

import common.Tupel;
import common.Vec3;
import model.*;
import model.BurgersVector.BurgersVectorType;
import model.polygrain.grainDetection.*;
import processingModules.skeletonizer.processors.*;
import processingModules.skeletonizer.processors.BurgersVectorAnalyzer.RBVToBVPattern;

public class B2NiTi extends BCCStructure{

	private static final ArrayList<RBVToBVPattern> bvClassifcationPattern = new ArrayList<RBVToBVPattern>();
	
	static{
		//1/2<111>
		bvClassifcationPattern.add(new RBVToBVPattern(111, 2, 4, 111, 2, BurgersVectorType.PARTIAL));
		//<100>
		bvClassifcationPattern.add(new RBVToBVPattern(100, 1, 2, 100, 1, BurgersVectorType.PERFECT));
		//1/2<111> identified as 1/4<112>
		bvClassifcationPattern.add(new RBVToBVPattern(112, 4, 4, 111, 2, BurgersVectorType.PARTIAL));
	}
	
	public B2NiTi() {
		super();
	}
	
	@Override
	protected CrystalStructure deriveNewInstance() {
		return new B2NiTi();
	}
	
	@Override
	protected String getIDName() {
		return "NiTi";
	}
	
	@Override
	public int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb) {
		ArrayList<Tupel<Atom, Vec3>> d_cont  = nnb.getNeighAndNeighVec(atom);
		if (d_cont.size()<=10) return 6;
				
		int co_x0_same = 0;
		int co_x0_other = 0;
//		int co_x1_other = 0;
//		int co_x2_other = 0;
		int co_x3_other = 0;
		int co_x4_other = 0;
		int co_x5_other = 0;
		int co_x6_other = 0;
		int co_x7_other = 0;
		int num_same = 0;
		int num_other = 0;
		
		for (int i = 0; i < d_cont.size(); i++) {
			int type_i = d_cont.get(i).o1.getElement()%2;
			Vec3 v = d_cont.get(i).o2;
			float v_length = v.getLength();
						
			if (type_i == (atom.getElement()%2)) num_same++;
			else num_other++;
			
			for (int j = 0; j < i; j++) {
				int type_j = d_cont.get(j).o1.getElement()%2;
				if (type_i == type_j){
					Vec3 u = d_cont.get(j).o2;
					float u_length = u.getLength();
					float a = v.dot(u) / (v_length*u_length);
					
					boolean same = type_i == atom.getElement()%2;
					
					if (same){
						if (a < -.95) co_x0_same++;
					} else {
						if (a < -.95) co_x0_other++;
						else if (a < -.90){}// co_x1_other++;
						else if (a < -.60){}// co_x2_other++;
						else if (a < -.30) co_x3_other++;
						else if (a < -.20) co_x4_other++;
						else if (a < .20) co_x5_other++;
						else if (a < 0.45) co_x6_other++;
						else if (a < 0.7) co_x7_other++;
					}
				}
			}
		}
		
		if (num_same==6 && num_other == 8 && co_x0_other == 2 && co_x0_same == 1 && co_x3_other+co_x4_other>=10) return 5;
		if ( atom.getElement()%2 == 0 && num_same >= 5 && num_other>=7 && co_x5_other+co_x7_other > 3 && co_x3_other<12 && co_x6_other<12) return 3;  //MartTi
		if ( atom.getElement()%2 == 1 && num_same >= 5 && num_other>=7 && co_x3_other <= co_x4_other+co_x5_other && co_x6_other<12) return 3;  //MartNi
		if (num_same == 6 && num_other>=num_same && (co_x3_other >= 7 || (co_x3_other == 6 && co_x6_other==12)) && co_x6_other > 9 && co_x0_other >= 3 && co_x0_same >= 2) return 0;  //B2
		
		return 4;
	}
	
	@Override
	public float[] getDefaultSphereSizeScalings(){
		return new float[]{1f, 0.85f};
	}
	
	@Override
	public String getNameForType(int i) {
		switch (i) {
			case 0: return "B2";
			case 1: return "Unused";
			case 2: return "Unused";
			case 3: return "Possible martensite";
			case 4: return "Defect";
			case 5: return "APB";
			case 6: return "free surface";
			case 7: return "Unused";
			default: return "unknown";
		}
	}
	
	@Override
	public ArrayList<RBVToBVPattern> getBurgersVectorClassificationPattern() {
		return bvClassifcationPattern;
	}
	
	@Override
	public int getNumberOfElements(){
		return 2;
	}
	
	@Override
	public String[] getNamesOfElements(){
		return new String[]{"Ni", "Ti"};
	}
	
	@Override
	public List<SkeletonPreprocessor> getSkeletonizerPreProcessors(){
		Vector<SkeletonPreprocessor>  list = new Vector<SkeletonPreprocessor>();
		
		list.add(new RBVAngleFilterPreprocessor(45));
		
		list.add(new MeshLineSenseCenteringPreprocessor());
		list.add(new ReMeshingPreprocessor());
		list.add(new RBVAngleFilterPreprocessor(45));
		
		list.add(new MeshCleaningPreprocessor());
		
		return list;
	}
	
	public float getDefaultSkeletonizerMeshingThreshold(){
		return 1.28f;
	}
	
	public float getDefaultSkeletonizerRBVThreshold(){
		return 0.35f;
	}
	
	private class MartensiteGrainDetectionCriteria implements GrainDetectionCriteria {

		private CrystalStructure cs;
		
		public MartensiteGrainDetectionCriteria(CrystalStructure cs){
			this.cs = cs;
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
			return atom.getType() == 3;
		}

		@Override
		public boolean includeAtom(AtomToGrainObject atom, List<AtomToGrainObject> neighbors) {		
			return neighbors.size()>9;
		}
	}
	
	public GrainDetectionCriteria getGrainDetectionCriteria(){
		return new MartensiteGrainDetectionCriteria(this);
	}
	
	public CrystalStructure getCrystalStructureOfDetectedGrains(){
		return new MonoclinicNiTi(this.getLatticeConstant(), this.getNearestNeighborSearchRadius());
	}

}
