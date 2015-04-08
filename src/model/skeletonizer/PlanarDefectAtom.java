// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2014  ICAMS, Ruhr-Universität Bochum
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
// with AtomViewer. If not, see <http://www.gnu.org/licenses

package model.skeletonizer;

import java.util.ArrayList;

import common.UniqueIDCounter;
import common.Vec3;
import model.Atom;

class PlanarDefectAtom extends Vec3 implements Comparable<PlanarDefectAtom>{
	private ArrayList<PlanarDefectAtom> neigh;
	private Atom atom;
	
	private static UniqueIDCounter id_source = UniqueIDCounter.getNewUniqueIDCounter(true);
	//This value is guaranteed to be unique and identical if the same file is loaded twice
	private int id;
	
	
	public PlanarDefectAtom(Atom a){
		this.atom = a;
		this.setTo(a);
		
		this.id = id_source.getUniqueID();
	}
	
	public Atom getAtom() {
		return atom;
	}
	
	public ArrayList<PlanarDefectAtom> getNeigh() {
		return neigh;
	}
	
	public void setNeigh(ArrayList<PlanarDefectAtom> neigh) {
		this.neigh = neigh;
	}
	
	@Override
	public int compareTo(PlanarDefectAtom o) {
		return this.id - o.id;
	}
	
	@Override
	public boolean equals(Object obj) {
		return this == obj;
	}
}
