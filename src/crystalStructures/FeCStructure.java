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

import java.util.ArrayList;

import common.Vec3;
import model.Atom;
import model.Filter;
import model.NearestNeighborBuilder;

public class FeCStructure extends BCCStructure {
	
	@Override
	protected CrystalStructure deriveNewInstance() {
		return new FeCStructure();
	}
	
	@Override
	protected String getIDName() {
		return "FeC";
	}
	
	@Override
	public String getNameForType(int i) {
		switch (i) {
			case 0: return "bcc";
			case 1: return "unused";
			case 2: return "unused";
			case 3: return "14 neighbors";
			case 4: return "11-13 neighbors";
			case 5: return ">14 neighbors";
			case 6: return "<11 neighbors";
			case 7: return "Carbon";
			default: return "unknown";
		}
	}
	
	@Override
	public int getNumberOfElements(){
		return 2;
	}
	
	@Override
	public String[] getNamesOfElements(){
		return new String[]{"Fe", "C"};
	}
	
	@Override
	public float[] getDefaultSphereSizeScalings(){
		return new float[]{1f, 0.43f};
	}
	
	public Filter<Atom> getFilterForAtomsNotNeedingClassificationByNeighbors(){
		return new Filter<Atom>(){
			@Override
			public boolean accept(Atom a) {
				return a.getElement() % getNumberOfElements() == 0;	//Accept only Fe
			}
		};
	}
	
	@Override
	public int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb) {
		/*
		 * type=0: bcc
		 * type=1: unused
		 * type=2: unused
		 * type=3: 14 neighbors
		 * type=4: 11-13 neighbors
		 * type=5: >14 neighbors
		 * type=6: less than 11 neighbors
		 * type=7: carbon
		 */
		int numTypes = getNumberOfElements();
		//carbon
		if (atom.getElement()%numTypes == 1) return 7;
		
		int threshold = highTempProperty.getValue() ? 3 : 2;
		float t1 = highTempProperty.getValue() ? -.77f : -.75f;
		float t2 = highTempProperty.getValue() ? -.69f : -0.67f;
		ArrayList<Vec3> neigh = nnb.getNeighVec(atom);
		
		//count Fe neighbors for Fe atoms
		int count = neigh.size();
		
		if (count < 11) return 6;
		else if (count == 11) return 4;
		else if (count >= 15) return 5;
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
			
			if (co_x0 > 5 && co_x0+co_x1==7 && co_x2<=threshold && count==14) return 0;
			else if (count==13 && neigh.size()==14 && co_x0==6) return 0;
			else if (count == 13) return 4;
			else if (count == 12) return 4;
			else return 3;
		}
	}
	
	@Override
	public boolean isRBVToBeCalculated(Atom a) {
		int type = a.getType();		
		if (type != 0 && type < 6) {			
			return true;
		}
		return false;		
	}

	@Override
	/**
	 * Ignore carbon
	 */
	public boolean considerAtomAsNeighborDuringRBVCalculation(Atom a){
		return (a.getType() != 7);
	}
}
