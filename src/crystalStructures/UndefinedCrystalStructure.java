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
import common.Vec3;
import model.Atom;
import model.AtomData;
import model.NearestNeighborBuilder;
import processingModules.skeletonizer.processors.BurgersVectorAnalyzer.ClassificationPattern;

public class UndefinedCrystalStructure extends CrystalStructure {
	
	@Override
	protected CrystalStructure deriveNewInstance() {
		return new UndefinedCrystalStructure();
	}
	
	@Override
	protected String getIDName() {
		return "undefined";
	}
	
	@Override
	public int getNumberOfTypes() {
		return 1;
	}
	
	@Override
	public float[][] getDefaultColors(){
		return ColorTable.createColorTable(getNumberOfTypes(), 0.5f);
	}

	@Override
	public int identifyAtomType(Atom atom, NearestNeighborBuilder<Atom> nnb) {
		return 0;
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
		case 0 : return "atoms";
		default: return "unknown";
		}
	}

	@Override
	public int getDefaultType() {
		return 0;
	}

	@Override
	public int getSurfaceType() {
		return -1; //Undefined
	}

	@Override
	public float getPerfectBurgersVectorLength() {
		return 1f;
	}

	@Override
	public int getNumberOfNearestNeighbors() {
		return 1;
	}

	@Override
	public Vec3[] getPerfectNearestNeighborsUnrotated() {
		return new Vec3[]{new Vec3(1,0,0), new Vec3(0,1,0), new Vec3(0,0,1)};
	}

	@Override
	public ArrayList<ClassificationPattern> getBurgersVectorClassificationPattern() {
		return new ArrayList<ClassificationPattern>();
	}

	@Override
	public float getDefaultNearestNeighborSearchScaleFactor() {
		return 1f;
	}
}
