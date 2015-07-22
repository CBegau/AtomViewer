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

package model;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import model.io.MDFileLoader;

public class Configuration {	
	
	/**
	 * If compiled while this flag is true, all config-files are placed in the program directory
	 * otherwise the config files are stored in the users home-directory
	 */
	public static final boolean RUN_AS_STICKWARE = true;
	
	private static File lastOpenedFolder = null;
	private static File lastOpenedExportFolder = null;
	private static MDFileLoader currentFileLoader = null;
	
	private static AtomData currentAtomData;
	
	private static ArrayList<AtomDataChangedListener> atomDataListeners = new ArrayList<AtomDataChangedListener>();
	
	public static void addAtomDataListener(AtomDataChangedListener l){
		if (!atomDataListeners.contains(l)) atomDataListeners.add(l);
	}
	
	public static void removeAtomDataListener(AtomDataChangedListener l){
		atomDataListeners.remove(l);
	}
	
	private static void fireAtomDataChangedEvent(AtomData newAtomData, AtomData oldAtomData, boolean updateGUI, boolean resetGUI){
		AtomDataChangedEvent e = new AtomDataChangedEvent();
		e.newAtomData = newAtomData;
		e.oldAtomData = oldAtomData;
		e.resetGUI = resetGUI;
		e.updateGUI = updateGUI;
		for (AtomDataChangedListener l : atomDataListeners)
			l.atomDataChanged(e);
	}
	
	public static boolean create(){
		ImportConfiguration.getInstance().createVectorDataColumn();
		
		return true;
	}
	
	public static File getLastOpenedExportFolder() {
		return lastOpenedExportFolder;
	}
	
	public static File getLastOpenedFolder() {
		return lastOpenedFolder;
	}
	
	public static MDFileLoader getCurrentFileLoader() {
		return currentFileLoader;
	}
	
	public static void setCurrentFileLoader(MDFileLoader currentFileLoader) {
		Configuration.currentFileLoader = currentFileLoader;
	}
	
	public static void setLastOpenedExportFolder(File lastOpenedExportFolder) {
		Configuration.lastOpenedExportFolder = lastOpenedExportFolder;
	}
	
	public static void setLastOpenedFolder(File lastOpenedFolder) {
		Configuration.lastOpenedFolder = lastOpenedFolder;
	}
	
	public static AtomData getCurrentAtomData() {
		return currentAtomData;
	}
	
	public static Iterable<AtomData> getAtomDataIterable(){
		return new Iterable<AtomData>() {
			
			@Override
			public Iterator<AtomData> iterator() {
				return new Iterator<AtomData>() {
					private AtomData current = null;
					private boolean done = false;
					
					private void getFirst(){
						current = getCurrentAtomData();
						if (current == null) return;
						while (current.getPrevious() != null)
							current = current.getPrevious();
					}
					
					@Override
					public boolean hasNext() {
						if (current == null && !done)
							getFirst();
						
						return current != null;
					}
					
					@Override
					public AtomData next() {
						AtomData r = current;
						if (r!=null){
							current = current.getNext();
							if(current == null) done = true;
						}
						return r;
					}

					@Override
					public void remove() {
						throw new RuntimeException("Remove is not supported");
						
					}
				};
			}
		};
	}
	
	public static void setCurrentAtomData(AtomData currentAtomData, boolean updateGUI, boolean resetGUI) {
		AtomData old = Configuration.currentAtomData; 
		Configuration.currentAtomData = currentAtomData;
		if (updateGUI)
			fireAtomDataChangedEvent(currentAtomData, old, updateGUI, resetGUI);
	}
	
	public interface AtomDataChangedListener{
		public void atomDataChanged(AtomDataChangedEvent e);
	}
	
	public static class AtomDataChangedEvent {
		AtomData newAtomData;
		AtomData oldAtomData;
		boolean resetGUI;
		boolean updateGUI;
		
		public AtomData getNewAtomData() {
			return newAtomData;
		}
		
		public AtomData getOldAtomData() {
			return oldAtomData;
		}
		
		public boolean isUpdateGUI(){
			return updateGUI;
		}
		
		public boolean isResetGUI(){
			return resetGUI;
		}
	}
}
