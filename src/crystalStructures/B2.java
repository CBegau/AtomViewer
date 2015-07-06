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

import model.BurgersVector.BurgersVectorType;
import processingModules.skeletonizer.processors.*;
import processingModules.skeletonizer.processors.BurgersVectorAnalyzer.ClassificationPattern;

public class B2 extends BCCStructure{
	
	private static final ArrayList<ClassificationPattern> bvClassifcationPattern = new ArrayList<ClassificationPattern>();
	static{
		//1/2<111>
		bvClassifcationPattern.add(new ClassificationPattern(111, 2, 4, 111, 2, BurgersVectorType.PARTIAL));
		//<100>
		bvClassifcationPattern.add(new ClassificationPattern(100, 1, 2, 100, 1, BurgersVectorType.PERFECT));
		//1/2<111> identified as 1/4<112>
		bvClassifcationPattern.add(new ClassificationPattern(112, 4, 4, 111, 2, BurgersVectorType.PARTIAL));
	}
	
	@Override
	protected CrystalStructure deriveNewInstance() {
		return new B2();
	}
	
	public B2() {
		super();
	}
	
	@Override
	protected String getIDName() {
		return "B2";
	}
	
	@Override
	public String getNameForType(int i) {
		switch (i) {
			case 0: return "B2";
			case 1: return "B1/FCC";
			case 2: return "HCP";
			case 3: return "14 neigh.";
			case 4: return "12-13 neighbors";
			case 5: return "15 neighbors";
			case 6: return "<11 neighbors";
			case 7: return ">15 neighbors";
			default: return "unknown";
		}
	}
	
	@Override
	public int getNumberOfElements(){
		return 2;
	}
	
	@Override
	public float getPerfectBurgersVectorLength(){
		return latticeConstant;
	}
	
	@Override
	public List<SkeletonPreprocessor> getSkeletonizerPreProcessors(){
		Vector<SkeletonPreprocessor> list = new Vector<SkeletonPreprocessor>();
		list.add(new MeshCleaningPreprocessor());
		list.add(new RBVAngleFilterPreprocessor(45));
		list.add(new MeshLineSenseCenteringPreprocessor());
		return list;
	}
	
	@Override
	public ArrayList<ClassificationPattern> getBurgersVectorClassificationPattern() {
		return bvClassifcationPattern;
	}
	
	public float getDefaultSkeletonizerMeshingThreshold(){
		return 1.28f;
	}
	
	public float getDefaultSkeletonizerRBVThreshold(){
		return 0.35f;
	}
}
