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


package common;

import java.util.Collection;
import java.util.Vector;

/**
 * Derived class from Vector<E> that supports faster remove(index) operations
 * The last element in the list is placed into the current position
 * @author Christoph Begau
 */
public class FastDeletableVector<E> extends Vector<E>{
	private static final long serialVersionUID = -1L;

	public FastDeletableVector(Collection<E> c){
		super(c);
	}
	
	public FastDeletableVector(){
		super();
	}
	
	@Override
	public E remove(int index) {
		synchronized (this) {
			E tmp = this.get(index);
			super.elementData[index] = super.lastElement();
			super.elementCount--;
			super.elementData[elementCount] = null;
			return tmp;
		}
	}
}