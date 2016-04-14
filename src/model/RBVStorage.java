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

/**
 * A thread safe implementation that maps Atoms to resultant Burgers vectors (RBVs)
 * @author Christoph Begau
 *
 */
public class RBVStorage {
	private Map<Atom, RBV> rbvData = Collections.synchronizedMap(new HashMap<Atom, RBV>());
	
	/**
	 * Sets the values for the resultant Burgers vector and the line direction to this atom
	 * @param a the atom associated with this RBV
	 * @param rbv The resultant Burgers vector, must not be null
	 * @param lineDirection the lineDirection, should be a unit vector, must not be null
	 */
	public void addRBV(Atom a, Vec3 rbv, Vec3 lineDirection ){
		assert (rbv != null && lineDirection != null); 		
		rbvData.put(a, new model.RBV(rbv, lineDirection));
	}
	
	public RBV getRBV(Atom a){
		return rbvData.get(a);
	}
	
	public boolean isEmpty(){
		return rbvData.isEmpty();
	}
	
	public boolean removeAtom(Atom a){
		return (rbvData.remove(a)!=null);
	}
	
	public void clear(){
		rbvData.clear();
	}
}
