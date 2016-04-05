//Part of AtomViewer: AtomViewer is a tool to display and analyse
//atomistic simulations
//
//Copyright (C) 2016  ICAMS, Ruhr-Universität Bochum
//
//AtomViewer is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//AtomViewer is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License along
//with AtomViewer. If not, see <http://www.gnu.org/licenses/> 

package common;

import gnu.trove.list.array.TFloatArrayList;

public final class FastTFloatArrayList extends TFloatArrayList {
	
	/**
	 * Creates a FastTFloatArrayList with the given capacity
	 * If requested, all in the array are initialized  
	 * @param size
	 * @param assumeInitialized
	 */
	public FastTFloatArrayList(int capacity, boolean assumeInitialized) {
		super(capacity, 0f);
		if (assumeInitialized) this._pos = capacity;
	}
	
	public FastTFloatArrayList(){
		super();
	}
	
	/**
	 * Permits direct access on the raw data
	 * If the array is not trimmed it may contain uninitialized values 
	 * @return
	 */
	public final float[] getData(){
		return _data;
	}
	
	/**
	 * Removes the value at the given index by overwriting it by the
	 * last value in the array
	 * @param index
	 */
	public final void removeFast(int index) {
		assert(index >=0 && index<-_pos);
		_data[index] = _data[--_pos];
	}
}
