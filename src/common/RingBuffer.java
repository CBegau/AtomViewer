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

/**
 * Takes elements in a collection and creates a ring buffer
 * @param <E>
 */
public class RingBuffer<E extends Object> {

	private Entry<E> currentEntry;
	private int count;
	
	public RingBuffer(Collection<E> elements) {
		this.count = elements.size();
		
		Entry<E> previous;
		Entry<E> first = null;
		
		for (E e : elements){
			previous = currentEntry;
			
			Entry<E> entry = new Entry<E>();
			entry.element = e;
			currentEntry = entry;
			if (previous != null){
				currentEntry.previous = previous;
				previous.next = currentEntry; 
			}
			if (first == null)
				first = currentEntry;
		}
		
		currentEntry.next = first;
		first.previous = currentEntry;
		
		currentEntry = first;
	}
	
	public E getCurrent(){
		return currentEntry.element;
	}
	
	public E getNext(){
		currentEntry = currentEntry.next;
		return currentEntry.element;
	}
	
	public E getPrevious(){
		currentEntry = currentEntry.previous;
		return currentEntry.element;
	}
	
	public int size(){
		return count;
	}
	
	private static class Entry<E extends Object>{
		E element;
		Entry<E> next, previous;
	}
}
