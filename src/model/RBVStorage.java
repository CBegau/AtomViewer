// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2016  ICAMS, Ruhr-Universität Bochum
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

package model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import common.Vec3;

public class RBVStorage {
	private Map<Atom, RBV> rbvData = Collections.synchronizedMap(new HashMap<Atom, RBV>());
	
	/**
	 * Sets the values for the resultant Burgers vector and the line direction to this atom
	 * If one of these values is null the existing reference is nulled
	 * @param a the atom associated with this RBV
	 * @param rbv The resultant Burgers vector
	 * @param lineDirection the lineDirection, should be a unit vector.
	 * If it is the null-vector, no reference to a RBV is created 
	 */
	public void addRBV(Atom a, Vec3 rbv, Vec3 lineDirection ){
		if (rbv != null && lineDirection != null && lineDirection.dot(lineDirection)>0.)
			rbvData.put(a, new model.RBV(rbv, lineDirection));
		else rbvData.remove(a);
	}
	
	public RBV getRBV(Atom a){
		return rbvData.get(a);
	}
	
	public boolean isEmpty(){
		return rbvData.isEmpty();
	}
	
	boolean removeAtom(Atom a){
		return (rbvData.remove(a)!=null);
	}
	
	public void clear(){
		rbvData.clear();
	}
}
