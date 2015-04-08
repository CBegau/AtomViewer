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
import java.util.HashSet;

import model.io.MDFileLoader;

public class Configuration {	
	
	/**
	 * If compiled while this flag is true, all config-files are placed in the program directory
	 * otherwise the config files are stored in the users home-directory
	 */
	public static final boolean RUN_AS_STICKWARE = true;
	
	private static File lastOpenedFolder = null;
	private static File lastOpenedExportFolder = null;
	public static MDFileLoader currentFileLoader = null;
	
	private static AtomData currentAtomData;
	
	//TODO Find better solution for handling poly-crystals
	private static HashSet<Integer> grainIndices = new HashSet<Integer>();
	
	
	private static ArrayList<AtomDataChangedListener> atomDataListeners = new ArrayList<AtomDataChangedListener>();
	
	public static void addAtomDataListener(AtomDataChangedListener l){
		if (!atomDataListeners.contains(l)) atomDataListeners.add(l);
	}
	
	public static void removeAtomDataListener(AtomDataChangedListener l){
		atomDataListeners.remove(l);
	}
	
	private static void fireAtomDataChangedEvent(AtomData atomData, boolean updateGUI, boolean resetGUI){
		AtomDataChangedEvent e = new AtomDataChangedEvent();
		e.newAtomData = atomData;
		e.resetGUI = resetGUI;
		e.updateGUI = updateGUI;
		for (AtomDataChangedListener l : atomDataListeners)
			l.atomDataChanged(e);
	}
	
	
	public static boolean create(){
		grainIndices.clear();
		grainIndices.add(Atom.IGNORED_GRAIN);
		grainIndices.add(Atom.DEFAULT_GRAIN);
		
		ImportConfiguration.getInstance().createVectorDataColumn();
		
		return true;
	}
	
	public static File getLastOpenedExportFolder() {
		return lastOpenedExportFolder;
	}
	
	public static File getLastOpenedFolder() {
		return lastOpenedFolder;
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
	
	public static boolean addGrainIndex(int index){
		return grainIndices.add(index);
	}
	
	/**
	 * Returns a copy of the grainIndices Set 
	 * @return
	 */
	public static HashSet<Integer> getGrainIndices(){
		return new HashSet<Integer>(grainIndices);
	}
	
	public static void setCurrentAtomData(AtomData currentAtomData, boolean updateGUI, boolean resetGUI) {
		Configuration.currentAtomData = currentAtomData;
		if (updateGUI)
			fireAtomDataChangedEvent(currentAtomData, updateGUI, resetGUI);
	}
	
	public interface AtomDataChangedListener{
		public void atomDataChanged(AtomDataChangedEvent e);
	}
	
	public static class AtomDataChangedEvent {
		AtomData newAtomData;
		boolean resetGUI;
		boolean updateGUI;
		
		public AtomData getNewAtomData() {
			return newAtomData;
		}
		
		public boolean isUpdateGUI(){
			return updateGUI;
		}
		
		public boolean isResetGUI(){
			return resetGUI;
		}
	}
}
