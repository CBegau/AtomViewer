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
// with AtomViewer. If not, see <http://www.gnu.org/licenses/> 

package common;

import java.util.ArrayList;

/**
 * A simple counter that serves as a source for unique IDs.
 * The methods are thread safe.
 */
public class UniqueIDCounter {
	
	private static ArrayList<UniqueIDCounter> allCounter = new ArrayList<UniqueIDCounter>();
	
	private int uniqueNumber = 0;
	
	/**
	 * Creates a new instance of UniqueIDCounter
	 * @param createAsStatic if true, the counter is reinitialized if resetStaticCounters() is called
	 * @return a new instance of a counter
	 */
	public static UniqueIDCounter getNewUniqueIDCounter(boolean createAsStatic){
		UniqueIDCounter counter = new UniqueIDCounter();
		counter.reinit();
		if (createAsStatic) allCounter.add(counter);
		return counter;
	}
	
	/**
	 * Reinitializes all counter that have been in a static content 
	 */
	public static void resetStaticCounters(){
		for (UniqueIDCounter c : allCounter)
			c.reinit();
	}
	
	/**
	 * Removes all static counter
	 */
	public static void removeStaticCounter(){
		allCounter.clear();
	}
	
	private UniqueIDCounter(){}
	
	/**
	 * Reinitializes the counter back to 0
	 */
	public synchronized void reinit() {
		uniqueNumber = 0;
	}
	
	/**
	 * Provides a integer that has not been returned since the last 
	 * (re-) initalization.
	 * @return
	 */
	public synchronized int getUniqueID(){
		return uniqueNumber++;
	}	
	
}
