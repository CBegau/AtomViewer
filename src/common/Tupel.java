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

/**
 * Bundling two objects of chosen classes into one object. 
 * Very useful if a set of exactly two different values are to be returned from one method
 * @param <T>
 * @param <V>
 */
public class Tupel<T, V>{
	public T o1;
	public V o2;

	public Tupel(T o1, V o2) {
		this.o1 = o1;
		this.o2 = o2;
	}

	public T getO1() {
		return o1;
	}

	public void setO1(T object1) {
		this.o1 = object1;
	}

	public V getO2() {
		return o2;
	}

	public void setO2(V object2) {
		this.o2 = object2;
	}
}
