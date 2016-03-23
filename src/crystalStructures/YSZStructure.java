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

import common.ColorTable;
import common.Tupel;
import common.Vec3;
import gui.PrimitiveProperty.BooleanProperty;
import model.Atom;
import model.Filter;
import model.NearestNeighborBuilder;

public class YSZStructure extends FCCStructure {
	
	protected BooleanProperty dontImportOxygen = 
			new BooleanProperty("dontImportOxygen", "Do not import oxygen",
					"Ignores all oxygen atoms during import",false);
	
	protected BooleanProperty hasArtificialPlaceHolderAtoms = 
			new BooleanProperty("hasArtificialPlaceholder", "Has artificial placeholder particles",
					"<html>The file contains artificial placeholders and thus<br>"
					+ "consists of four types, not three.</html>",
					false);
	
	public YSZStructure() {
		super();
		crystalProperties.add(dontImportOxygen);
		crystalProperties.add(hasArtificialPlaceHolderAtoms);
		
		crystalProperties.remove(super.highTempProperty);
	}
	
	@Override
	protected CrystalStructure deriveNewInstance() {
		return new YSZStructure();
	}
	
	@Override
	protected String getIDName() {
		return "YSZ";
	}
	
	@Override
	public int getNumberOfTypes() {
		return 9;
	}
	
	@Override
	public int getNumberOfElements() {
		return hasArtificialPlaceHolderAtoms.getValue() ? 4 : 3;
	}
	
	@Override
	public String[] getNamesOfElements(){
		if (hasArtificialPlaceHolderAtoms.getValue())
			return new String[]{"Zr", "O", "Y", "-"};
		return new String[]{"Zr", "O", "Y"};
	}
	
	@Override
	public float[][] getDefaultColors(){
		return ColorTable.createColorTable(getNumberOfTypes());
	}

	@Override
	public int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb) {
		if (atom.getElement() % getNumberOfElements() == 1) return 0;
		
		ArrayList<Tupel<Atom,Vec3>> neighAtoms = nnb.getNeighAndNeighVec(atom);
		ArrayList<Vec3> neigh = new ArrayList<Vec3>();
		for (int i=0; i<neighAtoms.size(); i++){
			int e = neighAtoms.get(i).o1.getElement()%getNumberOfElements();
			if (e == 0 || e == 2)
				neigh.add(neighAtoms.get(i).o2);
		}
		if (neigh.size()<10){
			if (neigh.size()<6) return 8;
			//Test if all atoms are located almost in a half-space of the center atom
			//Compute the sum of all neighbor vectors and negate 
			Vec3 con = new Vec3();
			for (Vec3 n : neigh)
				con.sub(n);
			//Normalize this vector --> this normal splits the volume into two half-spaces 
			con.normalize();
			boolean surface = true;
			//Test if all neighbors either on one side of the halfplane (dot product < 0) or only 
			//slightly off
			for (Vec3 n : neigh){
				if (con.dot(n.normalizeClone())>0.35f)
					surface = false;
			}
			if (surface) return 7;
		}
		if (neigh.size()>12) return 6;
		
		int co_x0 = 0;
		int co_x1 = 0;
		int co_x2 = 0;
		int co_x3 = 0;
		for (int i = 0; i < neigh.size(); i++) {
			Vec3 v = neigh.get(i);
			float v_length = v.getLength();
			for (int j = 0; j < i; j++) {
				Vec3 u = neigh.get(j);
				float u_length = u.getLength();
				float a = v.dot(u) / (v_length*u_length);
				
				if (a < -0.945f) co_x0++; // 0.945
				else if (a < -.915f) co_x1++;
				else if (a < -.775f) co_x2++; // 0.755
				else if (a < 0.573576436f) co_x3++;
				
			}
		}
		
		if (co_x0 == 6) return 1;
		else if (co_x0 == 5 && co_x3>=3) return 2;
		else if (co_x0 == 3 && co_x1 <= 1 && co_x2 > 2) return 3;
		else if (neigh.size()<12) return 5;
		else return 4;
	}

	@Override
	public String getNameForType(int i) {
		switch(i){
		case 0 : return "Oxygen";
		case 1 : return "Cubic YSZ";
		case 2 : return "Tetra. YSZ (?)";
		case 3 : return "HCP";
		case 4 : return "12 neigbors";
		case 5 : return "<12 neighbors";
		case 6 : return ">12 neighbors";
		case 7 : return "surface";
		case 8 : return "<6 neighbors";
		default: return "unknown";
		}
	}
	
	@Override
	public float[] getDefaultSphereSizeScalings(){
		return new float[]{1f, 0.49f, 1.095f, 0.1f};
	}

	@Override
	public float getDefaultNearestNeighborSearchScaleFactor() {
		return 0.7071067f*1.2f;
	}
	
	@Override
	public Filter<Atom> getIgnoreAtomsDuringImportFilter() {
		if (dontImportOxygen.getValue() == true){
			return new Filter<Atom>() {
				@Override
				public boolean accept(Atom a) {
					int e = a.getElement() % getNumberOfElements();
					return (e == 0 || e == 2);
				}
			};
		} else return new Filter<Atom>() {
			@Override
			public boolean accept(Atom a) {
				int e = a.getElement() % getNumberOfElements();
				return (e != 3);
			}
		}; 
	}

}
