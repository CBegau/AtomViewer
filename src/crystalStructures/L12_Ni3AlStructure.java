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
import java.util.concurrent.CyclicBarrier;

import common.Tupel;
import common.Vec3;
import model.Atom;
import model.AtomData;
import model.NearestNeighborBuilder;

public class L12_Ni3AlStructure extends FCCStructure{
	
	private static final float[][] bondsAngleClasses;
	
	static{
		float[] tol_l = new float[]{0f, 7f, 6f, 5f, 4f, 3f};
		float[] tol_u = new float[]{13f, 10f, 6f, 5f, 4f, 3f};
		float[] angles = new float[]{180f, 146.5f, 120f, 109.5f, 90f, 60f};
		
		
		
		bondsAngleClasses = new float[6][2];
		
		// Lower bonds
		bondsAngleClasses[0][0] = -1.001f;		//Should be exactly -1, but there might be rounding errors
		bondsAngleClasses[1][0] = (float)Math.cos((angles[1]+tol_l[1])*Math.PI/180.);
		bondsAngleClasses[2][0] = (float)Math.cos((angles[2]+tol_l[2])*Math.PI/180.);
		bondsAngleClasses[3][0] = (float)Math.cos((angles[3]+tol_l[3])*Math.PI/180.);
		bondsAngleClasses[4][0] = (float)Math.cos((angles[4]+tol_l[4])*Math.PI/180.);
		bondsAngleClasses[5][0] = (float)Math.cos((angles[5]+tol_l[5])*Math.PI/180.);
		
		
		//Upper bonds
		bondsAngleClasses[0][1] = (float)Math.cos((angles[0]-tol_u[0])*Math.PI/180.);
		bondsAngleClasses[1][1] = (float)Math.cos((angles[1]-tol_u[1])*Math.PI/180.);
		bondsAngleClasses[2][1] = (float)Math.cos((angles[2]-tol_u[2])*Math.PI/180.);
		bondsAngleClasses[3][1] = (float)Math.cos((angles[3]-tol_u[3])*Math.PI/180.);
		bondsAngleClasses[4][1] = (float)Math.cos((angles[4]-tol_u[4])*Math.PI/180.);
		bondsAngleClasses[5][1] = (float)Math.cos((angles[5]-tol_u[5])*Math.PI/180.);
		
	}
	
	public L12_Ni3AlStructure() {
		super();
	}
	
	@Override
	protected CrystalStructure deriveNewInstance() {
		return new L12_Ni3AlStructure();
	}
	
	@Override
	protected String getIDName() {
		return "Ni3Al";
	}
	
	public float getDefaultSkeletonizerRBVThreshold(){
		return 0.25f;
	}
	
	@Override
	public void identifyDefectAtoms(List<Atom> atoms, NearestNeighborBuilder<Atom> nnb, 
			int start, int end, CyclicBarrier barrier) {
		
		for (int i=start; i<end; i++){
			if (Thread.interrupted()) return;
			Atom a = atoms.get(i);
			a.setType(identifyAtomType(a, nnb));
		}
		try {
			barrier.await();
		} catch (Exception e) {
			if (Thread.interrupted()) return;
		}
			
		float tol = (float)(Math.cos(167)*Math.PI/180.);	
		
		for (int i=start; i<end; i++){
			if (Thread.interrupted()) return;
			Atom a = atoms.get(i);
			
			if (a.getType() == 0){
				int countPseudoTwin = 0;
				int pairs = 0;
				ArrayList<Atom> nei = nnb.getNeigh(a);
				for (int j=0; j<nei.size(); j++){
					if (nei.get(j).getType() == 8){
						countPseudoTwin++;
						Vec3 u = nei.get(j).subClone(a);
						boolean pairFound = false;
						for (int k=0; k<nei.size(); k++){
							if (nei.get(k).getType() == 8){
								Vec3 v = nei.get(j).subClone(a);
								if (u.getAngle(v)>tol){
									pairFound = true;
									break;
								}
							}
						}
						if (pairFound) pairs++;
					}
				}
				
				if (pairs>=2 && countPseudoTwin >= 4) a.setType(8);
			}
		}
	}
	
	@Override
	public int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb) {
		ArrayList<Tupel<Atom, Vec3>> d_cont  = nnb.getNeighAndNeighVec(atom);
		
		/*
		 * type=0: L12
		 * type=1: APB
		 * type=2: CSF
		 * type=3: SISF
		 * type=4: 12 neighbors, defect
		 * type=5: 10-11 neighbors
		 * type=6: >=13 neighbors
		 * type=7: <10 neighbors (surface)
		 * type=8: pseudoTwin
		 */
		if (d_cont.size() < 10) return 7;
		else if (d_cont.size() < 11) return 5;
		else if (d_cont.size() > 13) return 6;
		else {
			//There are three type of bond within the binary system with an atom of type A in the middle of the bond
			//type 0: B-A-B (both neighbor the same, but other type)
			//type 1: A-A-B (one neighbor of each element)
			//type 2: A-A-A (bond consists of only one type)
			int bonds[][] = new int[bondsAngleClasses.length][3];
			
			//Element of the central atom in the bond pair
			int type_atom = atom.getElement()%2;
			
			
			for (int i = 0; i < d_cont.size(); i++) {
				//Element of the first bond atom
				int type_i = d_cont.get(i).o1.getElement()%2;
				Vec3 v = d_cont.get(i).o2;
				float v_length = v.getLength();
				
				for (int j = 0; j < i; j++) {
					//Element of the second bond atom
					int type_j = d_cont.get(j).o1.getElement()%2;
					
					int bondType = 0;
					//Identify the type of bond 
					if (type_j!=type_i) bondType = 1;
					else {
						if (type_i == type_atom) bondType = 2;
						else bondType = 0;
					}
					Vec3 u = d_cont.get(j).o2;
					float u_length = u.getLength();
					float a = v.dot(u) / (v_length*u_length);
					
					for (int k=0; k<bondsAngleClasses.length; k++){
						if (a>=bondsAngleClasses[k][0] && a<=bondsAngleClasses[k][1]) bonds[k][bondType]++;
					}
				}
			}
			
			//type 0: B-A-B (both neighbor the same, but other type)
			//type 1: A-A-B (one neighbor of each element)
			//type 2: A-A-A (bond consists of only one type)
			
			//Nickel
			if (type_atom == 0){
				if (bonds[0][0] == 2 && bonds[0][2] == 4) return 0; //L12
				if (bonds[0][2] == 3 && bonds[0][1] == 2 && bonds[0][0] == 1) return 1; //APB
				if (bonds[0][2] == 4 && bonds[0][1] == 1 && bonds[0][0] == 1) return 1; //APB
				if (bonds[0][2] == 2 && bonds[0][0] == 1 && bonds[1][2] == 3 && bonds[1][1] == 2 && bonds[1][0] == 1) return 2; //CSF
				if (bonds[0][2] == 2 && bonds[0][0] == 1 && bonds[1][2] == 4 && bonds[1][1] == 2) return 2; //CSF
				if (bonds[0][2] == 2 && bonds[0][0] == 1 && bonds[1][2] == 2 && bonds[1][1] == 4) return 3; //SISF
				
				if (bonds[0][2] == 2 && bonds[0][0] == 1 && bonds[1][2]+bonds[1][1]+bonds[1][0] >= 3) return 2; //CSF
				
				if (bonds[0][0] == 1 && bonds[0][2] == 5  && bonds[1][0] == 0) return 8; //Pseudo-twin
				
			} else {	//Aluminum	
				if (bonds[0][0] == 6) return 0; //L12
				if (bonds[0][0] == 5 && bonds[0][1] == 1) return 1; //APB
				if (bonds[0][0] == 3 && bonds[1][1] == 2 && bonds[1][0] == 4) return 2; //CSF
				if (bonds[0][0] == 3 && bonds[1][0] == 6) return 3; //SISF
				
				if (bonds[0][0] == 3 && bonds[1][2]+bonds[1][1]+bonds[1][0] >=3 ) return 2; //CSF
				if (bonds[0][2] == 1 && bonds[0][0] == 5  && bonds[1][2] == 0) return 8; //Pseudo-twin
			}
			
			if (d_cont.size() < 12) return 5;
			else if (d_cont.size() > 12) return 6;
			else return 4;
		}
	}
	
	@Override
	public String getNameForType(int i) {
			switch (i) {
			case 0: return "L12";
			case 1: return "APB";
			case 2: return "CSF";
			case 3: return "SISF";
			case 4: return "12 neigbors, defect";
			case 5: return "10-11 neighbors";
			case 6: return ">=13 neighbors";
			case 7: return "<10 neighbors";
			case 8: return "PseudoTwin";
			
			default: return "unknown";
		}
	}
	
	@Override
	public boolean hasMultipleStackingFaultTypes(){
		return true;
	}
	
	@Override
	public List<Atom> getStackingFaultAtoms(AtomData data){
		List<Atom> sfAtoms = new ArrayList<Atom>();
		for (int i=0; i<data.getAtoms().size(); i++){
			Atom a = data.getAtoms().get(i);
			if (a.getType() >= 1 && a.getType() <= 3 && a.getGrain() != Atom.IGNORED_GRAIN)
				sfAtoms.add(a);
		}
		return sfAtoms;
	}
	
	@Override
	public int getSurfaceType() {
		return 7;
	}
	
	@Override
	public int getDefaultType(){
		return 0;
	}
	
	@Override
	public int getNumberOfTypes() {
		return 9;
	}
	
	@Override
	public int getNumberOfElements(){
		return 2;
	}
	
	@Override
	public float[] getDefaultSphereSizeScalings(){
		return new float[]{1f, 0.79f};
	}
	
	@Override
	public float getRBVIntegrationRadius() {
		return 0.707107f*latticeConstant*1.1f;
	}
	
	@Override
	public boolean isRBVToBeCalculated(Atom a) {
		return (a.getType() != 0 && a.getType() != 7);
	}
	
}
