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
import processingModules.skeletonizer.processors.*;
import processingModules.skeletonizer.processors.BurgersVectorAnalyzer.RBVToBVPattern;

public class BCCStructure extends CrystalStructure {

	private static final ArrayList<RBVToBVPattern> bvClassifcationPattern = new ArrayList<RBVToBVPattern>();
	static{
		//1/2<111>
		bvClassifcationPattern.add(new RBVToBVPattern(111, 2, 4, 111, 2, BurgersVectorType.PERFECT));
		//<100>
		bvClassifcationPattern.add(new RBVToBVPattern(100, 1, 2, 100, 1, BurgersVectorType.SUPER));
	}
	
	protected BooleanProperty highTempProperty = 
			new BooleanProperty("highTempADA", "optimize defect classification for >150K",
					"<html>Modifies the thresholds to classify atoms.<br>"
					+ "Typically reduces the number of false classifications.</html>",
					false);
	
	private static Vec3[] neighPerfBCC = new Vec3[]{
			new Vec3( 0.5f,  0.5f,  0.5f),
			new Vec3( 0.5f,  0.5f, -0.5f),
			new Vec3( 0.5f, -0.5f,  0.5f),
			new Vec3( 0.5f, -0.5f, -0.5f),
			new Vec3(-0.5f,  0.5f,  0.5f),
			new Vec3(-0.5f,  0.5f, -0.5f),
			new Vec3(-0.5f, -0.5f,  0.5f),
			new Vec3(-0.5f, -0.5f, -0.5f),

			new Vec3( 1f, 0f, 0f),
			new Vec3(-1f, 0f, 0f),
			new Vec3( 0f, 1f, 0f),
			new Vec3( 0f,-1f, 0f),
			new Vec3( 0f, 0f, 1f),
			new Vec3( 0f, 0f,-1f),
	};
	
	public BCCStructure() {
		super();
		crystalProperties.add(highTempProperty);
	}
	
	@Override
	protected CrystalStructure deriveNewInstance() {
		return new BCCStructure();
	}
	
	@Override
	protected String getIDName() {
		return "BCC";
	}
	
	public float getDefaultNearestNeighborSearchScaleFactor(){
		return 1.2f;
	}
	
	public float getDefaultSkeletonizerRBVThreshold(){
		return 0.25f;
	}
	
	public float getPerfectBurgersVectorLength(){
		return latticeConstant*0.866025f;
	}
	
	@Override
	public int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb) {
		int threshold = highTempProperty.getValue() ? 3 : 2;
		float t1 = highTempProperty.getValue() ? -.77f : -.75f;
		float t2 = highTempProperty.getValue() ? -.69f : -0.67f;
		ArrayList<Vec3> neigh = nnb.getNeighVec(atom);
		/*
		 * type=0: bcc
		 * type=1: fcc
		 * type=2: hcp
		 * type=3: 14 neighbors
		 * type=4: 11-13 neighbors
		 * type=5: 15 neighbors
		 * type=6: less than 11 neighbors
		 * type=7: unknown
		 */
		if (neigh.size() < 11) return 6;
		else if (neigh.size() == 11) return 4;
		else if (neigh.size() == 13) return 4;
		else if (neigh.size() == 15) return 5;
		else if (neigh.size() > 15) return 7;
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
					
					if (a < -.945)
						co_x0++;
					else if (a < -.915)
						co_x1++;
					else if (a > t1 && a< t2)
						co_x2++;
				}
			}
			
			if (co_x0 > 5 && co_x0+co_x1==7 && co_x2<=threshold && neigh.size()==14) return 0;
			else if (co_x0 == 6 && neigh.size() == 12) return 1;
			else if (co_x0 == 3 && neigh.size() == 12) return 2;
			else if (neigh.size() == 12) return 4;
			else return 3;
		}
	}
	
	@Override
	public int getSurfaceType() {
		return 6;
	}
	
	@Override
	public boolean isRBVToBeCalculated(Atom a) {
		if (a.getGrain() == Atom.IGNORED_GRAIN) return false;
		int type = a.getType();		
		if (type != 0 && type != 6) {			
			return true;
		}
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
			case 3: return "14 neighbors";
			case 4: return "11-13 neighbors";
			case 5: return "15 neighbors";
			case 6: return "<11 neighbors";
			case 7: return ">15 neighbors";
			default: return "unknown";
		}
	}
	
	
	
	@Override
	public Vec3[] getPerfectNearestNeighborsUnrotated() {
		return neighPerfBCC.clone();
	}
	
	@Override
	public int getNumberOfNearestNeighbors() {
		return 14;
	}
	
	@Override
	public ArrayList<RBVToBVPattern> getBurgersVectorClassificationPattern() {
		return bvClassifcationPattern;
	}
	
	@Override
	public int getDefaultType(){
		return 0;
	}
	
	@Override
	public List<SkeletonPreprocessor> getSkeletonizerPreProcessors(){
		Vector<SkeletonPreprocessor> list = new Vector<SkeletonPreprocessor>();
		list.add(new MeshCleaningPreprocessor());
		list.add(new RBVAngleFilterPreprocessor(38));
		list.add(new MeshLineSenseCenteringPreprocessor());
		return list;
	}
}
