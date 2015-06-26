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
import crystalStructures.CrystalStructureProperties.BooleanCrystalProperty;
import model.Atom;
import model.AtomData;
import model.Filter;
import model.NearestNeighborBuilder;

public class YSZStructure extends CrystalStructure {
	
	protected BooleanCrystalProperty dontImportOxygen = 
			new BooleanCrystalProperty("dontImportOxygen", "Do not import oxygen",
					"Ignores all oxygen atoms during import",false);
	
	protected BooleanCrystalProperty hasArtificialPlaceHolderAtoms = 
			new BooleanCrystalProperty("hasArtificialPlaceholder", "Has artificial placeholder particles",
					"<html>The file contains artificial placeholders and thus<br>"
					+ "consists of four types, not three.</html>",
					true);
	
	public YSZStructure() {
		super();
		crystalProperties.add(dontImportOxygen);
		crystalProperties.add(hasArtificialPlaceHolderAtoms);
	}
	
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
		return 8;
	}
	
	@Override
	public int getNumberOfElements() {
		return hasArtificialPlaceHolderAtoms.value ? 4 : 3;
	}
	
	@Override
	public String[] getNamesOfElements(){
		if (hasArtificialPlaceHolderAtoms.value)
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
			if (neigh.size()<6) return 7;
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
			if (surface) return 6;
		}
		if (neigh.size()<12) return 4;
		if (neigh.size()>12) return 5;
		
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
				
				if (a < -0.945f) co_x0++; // 0.945
				else if (a < -.915f) co_x1++;
				else if (a < -.775f) co_x2++; // 0.755
			}
		}
		
		if (co_x0 == 6) return 1;
		else if (co_x0 == 3 && co_x1 <= 1 && co_x2 > 2) return 2;
		else return 3;
	}

	@Override
	public List<Atom> getStackingFaultAtoms(AtomData data) {
		return new ArrayList<Atom>();
	}

	@Override
	public boolean isRBVToBeCalculated(Atom a) {
		return false;
	}

	@Override
	public String getNameForType(int i) {
		switch(i){
		case 0 : return "Oxygen";
		case 1 : return "FCC";
		case 2 : return "HCP";
		case 3 : return "12 neigbors";
		case 4 : return "<12 neighbors";
		case 5 : return ">12 neighbors";
		case 6 : return "surface";
		case 7 : return "<6 neighbors";
		default: return "unknown";
		}
	}
	
	@Override
	public float[] getSphereSizeScalings() {
		if (hasArtificialPlaceHolderAtoms.value)
			return new float[]{1f, 0.49f, 1.095f, 0.1f};
		else return new float[]{1f, 0.49f, 1.095f};
	}
	
	@Override
	public int getDefaultType() {
		return 1;
	}

	@Override
	public int getSurfaceType() {
		return 6; //Undefined
	}

	@Override
	public float getPerfectBurgersVectorLength() {
		return 1f;
	}

	@Override
	public int getNumberOfNearestNeighbors() {
		return 12;
	}

	@Override
	public Vec3[] getPerfectNearestNeighborsUnrotated() {
		return neighPerfFCC.clone();
	}

	@Override
	public float getDefaultNearestNeighborSearchScaleFactor() {
		return 1f;
	}
	
	@Override
	public Filter<Atom> getIgnoreAtomsDuringImportFilter() {
		if (dontImportOxygen.value == true){
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
