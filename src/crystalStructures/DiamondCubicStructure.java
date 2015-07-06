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
import model.*;
import model.BurgersVector.BurgersVectorType;
import processingModules.skeletonizer.processors.BurgersVectorAnalyzer.ClassificationPattern;

/**
 * Not fully implemented! And not tested!
 */
public class DiamondCubicStructure extends CrystalStructure {

	private static Vec3[] neighPerfDC = new Vec3[]{
		new Vec3(-0.25f,  0.25f, -0.25f),
		new Vec3( 0.25f,  0.25f,  0.25f),
		new Vec3(-0.25f, -0.25f,  0.25f),
		new Vec3( 0.25f, -0.25f, -0.25f),
		
		
		new Vec3(-0.5f,  0.5f, 0.0f),
		new Vec3( 0.5f,  0.5f, 0.0f),
		new Vec3(-0.5f, -0.5f, 0.0f),
		new Vec3( 0.5f, -0.5f, 0.0f),
		
		new Vec3(-0.5f, 0.0f, -0.5f),
		new Vec3( 0.5f, 0.0f,  0.5f),
		new Vec3(-0.5f, 0.0f,  0.5f),
		new Vec3( 0.5f, 0.0f, -0.5f),
		
		new Vec3(0.0f,  0.5f, -0.5f),
		new Vec3(0.0f,  0.5f,  0.5f),
		new Vec3(0.0f, -0.5f,  0.5f),
		new Vec3(0.0f, -0.5f, -0.5f),
	};
	
	private static final ArrayList<ClassificationPattern> bvClassifcationPattern = new ArrayList<ClassificationPattern>();
	
	static{
		//1/2<110>
		bvClassifcationPattern.add(new ClassificationPattern(110, 2, 4, 110, 2, BurgersVectorType.PERFECT));
		//1/6<211>
		bvClassifcationPattern.add(new ClassificationPattern(211, 6, 6, 211, 6, BurgersVectorType.PARTIAL));
	}
	
	@Override
	protected CrystalStructure deriveNewInstance() {
		return new DiamondCubicStructure();
	}
	
	@Override
	protected String getIDName() {
		return "DiamondCubic";
	}
	
	public float getDefaultSkeletonizerRBVThreshold(){
		return 0.2f;
	}
	
	@Override
	public int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb) {
		ArrayList<Vec3> neighVec = nnb.getNeighVec(atom);
		if (neighVec.size()<=12) return 6; 
		ArrayList<Vec3> c = new ArrayList<Vec3>();
		for (Vec3 f : neighVec){
			if (f.getLengthSqr()<(0.25f*latticeConstant*latticeConstant))
				c.add(f);
		}
		neighVec = c;
		
		if (neighVec.size() == 5) return 3;
		if (neighVec.size() == 3) return 4;
		if (neighVec.size() <3) return 6;
		if (neighVec.size() >5) return 5;
		
		else {
			int co_x0 = 0;
			for (int i = 0; i < neighVec.size(); i++) {
				Vec3 v = neighVec.get(i);
				float v_length = v.getLength();
				
				for (int j = 0; j < i; j++) {
					Vec3 u = neighVec.get(j);
					float u_length = u.getLength();
					float a = v.dot(u) / (v_length*u_length);
					
					if (a < -0.173648178 && a > -0.5 ) co_x0++; 
				}
			}
		
			if (co_x0 == 6) return 0;
			return 1;
		}
	}
	
	@Override
	public String getNameForType(int i) {
		switch (i) {
			case 0: return "diamond cubic";
			case 1: return "4 neigh. (distort)";
			case 2: return "unused"; 
			case 3: return "5 neighbors"; 
			case 4: return "3 neighbors";
			case 5: return ">5 neighbors";
			case 6: return "free surface";
			case 7: return "unused";
			default: return "unknown";
		}
	}
	
	@Override
	public int getSurfaceType() {
		return 6;
	}

	@Override
	public int getNumberOfTypes() {
		return 7;
	};

	@Override
	public boolean isRBVToBeCalculated(Atom a) {
		return a.getType()!=0 && a.getType()!=6;
	}
	
	@Override
	public float getDefaultNearestNeighborSearchScaleFactor(){
		return 0.707f*1.1f;
	}
	
	@Override
	public float[] getSphereSizeScalings(){
		float[] size = {0.6f};
		return size;
	}
	
	@Override
	public float getPerfectBurgersVectorLength(){
		return latticeConstant*0.7071067f;
	}

	@Override
	public int getNumberOfNearestNeighbors() {
		return 16;
	}

	@Override
	public Vec3[] getPerfectNearestNeighborsUnrotated() {
		return neighPerfDC.clone();
	}

	@Override
	public float getRBVIntegrationRadius() {
		return 0.707107f*latticeConstant*0.9f;
	}
	
	@Override
	public ArrayList<ClassificationPattern> getBurgersVectorClassificationPattern() {
		return bvClassifcationPattern;
	}

	@Override
	public int getDefaultType() {
		return 0;
	}
}
