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

import javax.xml.stream.XMLStreamException;

import model.io.MDFileLoader;
import processingModules.Toolchain;

public class Configuration {	
	
	/**
	 * If compiled while this flag is true, all config-files are placed in the program directory
	 * otherwise the config files are stored in the users home-directory
	 */
	public static final boolean RUN_AS_STICKWARE = true;
	
	private static File lastOpenedFolder = null;
	private static File lastOpenedExportFolder = null;
	private static MDFileLoader currentFileLoader = null;
	
	private static Toolchain currentToolchain = null;
	
	private static AtomData currentAtomData;
	
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
	
	public static Toolchain getCurrentToolchain() {
		return currentToolchain;
	}
	
	public static void setCurrentToolchain(Toolchain currentToolchain) {
		if(!Configuration.currentToolchain.isClosed()) try {
			Configuration.currentToolchain.closeToolChain();
		} catch (XMLStreamException e) {
			e.printStackTrace();
		}
		Configuration.currentToolchain = currentToolchain;
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
