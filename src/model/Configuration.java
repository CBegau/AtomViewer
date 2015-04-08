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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;

import model.io.MDFileLoader;
import processingModules.*;
import common.ColorTable;
import common.ColorTable.ColorBarScheme;
import common.Vec3;
import crystalStructures.CrystalStructure;
import crystalStructures.MonoclinicNiTi;
import gui.ViewerGLJPanel;

public class Configuration {	
	
	/**
	 * If compiled while this flag is true, all config-files are placed in the program directory
	 * otherwise the config files are stored in the users home-directory
	 */
	public static final boolean RUN_AS_STICKWARE = true;
	
	private static File configFile;
	
	private static File lastOpenedFolder = null;
	private static File lastOpenedExportFolder = null;
	private static boolean headless = false;
	public static MDFileLoader currentFileLoader = null;
	
	/**
	 * Global options for AtomViewer, accessible in the settings-menu
	 */
	public static enum Options {
		SIMPLE(false, "Basic features only", "AtomViewer must be restarted to apply all changes", 
				"Enables/Disables all implemented features. Non basic features may not work as expected, use at own risk!"),
		PERFECT_SPHERES(false, "Perfect spheres", "", "Renders perfect spheres, at the possible cost of performance."),
		FXAA(true, "Anti-aliasing", "", "Using FXAA to smooth visible edges"),
		ADS_SHADING(true, "Specular lighting", "", "Different lighting model"),
		SSAO(false, "Ambient occlusion", "", "Enable ambient occlusion (may improve depth perception)"),
		NO_SHADING(false, "Uniform atom color", "", "Each atom is uniformly colored and no lighting is applied");
		
		private Options(boolean enabled, String name, String message, String infoMessage){
			this.enabled = enabled;
			this.name = name;
			this.activateMessage = message;
			this.infoMessage = infoMessage;
		}
		
		private boolean enabled;
		private String activateMessage;
		private String infoMessage;
		private String name;
		private static ViewerGLJPanel viewer;
		
		public static void setViewerPanel(ViewerGLJPanel viewer){
			Options.viewer = viewer;
		}
		
		public void setEnabled(boolean enabled){
			this.enabled = enabled;
			if (viewer != null) {
				viewer.reDraw();
			}
		}
		
		public String getName() {
			return name;
		}
		
		public String getActivateMessage() {
			return activateMessage;
		}
		
		public String getInfoMessage() {
			return infoMessage;
		}
		
		public boolean isEnabled(){
			return enabled;
		}
		
		static{
			loadProperties();
		}
		
		private static void loadProperties() {
			if (headless) return;
			Properties prop = new Properties();
			
			try {
				if (Configuration.RUN_AS_STICKWARE){
					configFile = new File("viewerSettings.conf");
				} else {
					String userHome = System.getProperty("user.home");
					File dir = new File(userHome+"/.AtomViewer");
					if (!dir.exists()) dir.mkdir();
					configFile = new File(dir, "viewerSettings.conf");
				}
				if (!configFile.exists()) saveProperties();
				prop.load(new FileReader(configFile));
			} catch (IOException e){
				e.printStackTrace();
			}
		
			for (Options i: Options.values()){
				if (prop.getProperty(i.toString()) != null)
					i.setEnabled(Boolean.parseBoolean(prop.getProperty(i.toString())));
			}
			
			String scheme = prop.getProperty("ColorScheme");
			if (scheme != null){
				for (ColorBarScheme cbs : ColorBarScheme.values()){
					if (cbs.name().equals(scheme)){
						ColorTable.setColorBarScheme(cbs);
						break;
					}
				}
			}
			String schemeSwapped = prop.getProperty("ColorSchemeSwapped");
			if (schemeSwapped != null)
				ColorTable.setColorBarSwapped(Boolean.parseBoolean(schemeSwapped));
			
			
			prop.setProperty("ColorScheme", ColorTable.getColorBarScheme().name());
			prop.setProperty("ColorSchemeSwapped", Boolean.toString(ColorTable.isColorBarSwapped()));
		}
		
		public static void saveProperties() {
			if (headless) return;
			Properties prop = new Properties();
		
			for (Options i: Options.values()){
				prop.setProperty(i.toString(), Boolean.toString(i.isEnabled()));
			}
			
			prop.setProperty("ColorScheme", ColorTable.getColorBarScheme().name());
			prop.setProperty("ColorSchemeSwapped", Boolean.toString(ColorTable.isColorBarSwapped()));
			
			try {
				if (!configFile.exists()) configFile.createNewFile();
				if (configFile.canWrite()){
					prop.store(new FileWriter(configFile), "Viewer settings config file");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
	}
	
	private static boolean[] pbc;
	private static CrystalStructure crystalStructure;
	private static CrystalRotationTools crystalRotationTools;
	private static Vec3[] crystalOrientation;
	private static AtomData currentAtomData;
	//Caching values for faster access, are often needed
	private static Vec3 currentAtomDataBound_x, currentAtomDataBound_y, currentAtomDataBound_z;
	private static float currentAtomDataHalfBound_x, currentAtomDataHalfBound_y, currentAtomDataHalfBound_z;
	
	private static DataColumnInfo selectedColumn = null;
	private static boolean filterRange = false;
	private static boolean filterInversed = false;
	private static ArrayList<DataColumnInfo> dataColumns = new ArrayList<DataColumnInfo>();
	private static byte numElements = 1;
	private static HashSet<Integer> grainIndices = new HashSet<Integer>();
	
	/**
	 * Post processing routines to be applied on the data
	 */
	private static ArrayList<ProcessingModule> processingModule = new ArrayList<ProcessingModule>();
	
	public static boolean create(){
		Configuration.pbc = ImportStates.getPeriodicBoundaryConditions();
		Configuration.crystalStructure = ImportStates.getCrystalStructure();
		Configuration.crystalOrientation = ImportStates.getCrystalOrientation();
		
		Configuration.crystalRotationTools = new CrystalRotationTools(crystalStructure, crystalOrientation);
		
		//Disable data columns if no values are found in the crystal.conf file
		dataColumns.clear();
		
		grainIndices.clear();
		grainIndices.add(Atom.IGNORED_GRAIN);
		grainIndices.add(Atom.DEFAULT_GRAIN);
		
		if (ImportStates.getDataColumns().size() != 0){
			for (int i=0; i<ImportStates.getDataColumns().size(); i++){
				DataColumnInfo c = ImportStates.getDataColumns().get(i);
				c.setColumn(dataColumns.size());
				dataColumns.add(c);
			}
		}
		
		processingModule.clear();
		
		if (ImportStates.DETECT_MARTENSITE_VARIANTS.isActive()){
			MonoclinicNiTi niti = new MonoclinicNiTi();
			if (niti.isApplicable())
				processingModule.add(niti);
        }
		
		ProcessingModule procMod = new ComputeTemperatureModule();
		if (procMod.isApplicable()){
			processingModule.add(procMod);
		}
		
		
		//Add processors for all values that are to be spatially averaged
		for (DataColumnInfo cci : dataColumns){
			if (cci.isValueToBeSpatiallyAveraged())
				processingModule.add(new SpatialAveragingModule(cci));
		}
		
		if (ImportStates.LATTICE_ROTATION.isActive())
			processingModule.add(new LatticeRotationModule());
		
		if (ImportStates.ENERGY_GND_ANALYSIS.isActive())
			processingModule.add(new EnergyAndGNDModule());
		
		for (int i=0; i<processingModule.size(); i++){
			ProcessingModule pm = processingModule.get(i);
			if (pm.isApplicable()){
				DataColumnInfo[] dcia = pm.getDataColumnsInfo();
				if (dcia != null){
					for (DataColumnInfo dci : dcia){
						if (dci.isValueToBeSpatiallyAveraged())
							processingModule.add(new SpatialAveragingModule(dci));
						dci.setColumn(dataColumns.size());
						dataColumns.add(dci);
					}
				}
			}
		}
		
		return true;
	}
	
	/**
	 * Test if a data column with the given name exists. If it exists, the index of the column is returned
	 * @param name the name of the data column to be searched 
	 * @return the index of the data column if existing, or -1 if it does not exist
	 */
	public static int getIndexForDataColumnName(String name){
		for (int i=0; i<Configuration.getSizeDataColumns(); i++){
			DataColumnInfo cci = Configuration.getDataColumnInfo(i);
			if (cci.getName().equals(name))
				return i;
		}
		
		return -1;
	}
	
	/**
	 * Test if a data column with the given ID exists. If it exists, the index of the column is returned
	 * @param ID the ID of the data column to be searched 
	 * @return the index of the data column if existing, or -1 if it does not exist
	 */
	public static int getIndexForDataColumnID(String ID){
		for (int i=0; i<Configuration.getSizeDataColumns(); i++){
			DataColumnInfo cci = Configuration.getDataColumnInfo(i);
			if (cci.getId().equals(ID))
				return i;
		}
		
		return -1;
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
	
	public static boolean[] getPbc() {
		return pbc;
	}
	
	public static AtomData getCurrentAtomData() {
		return currentAtomData;
	}
	
	public static void setPBC(boolean[] pbc){
		assert (pbc.length == 3);
		Configuration.pbc = pbc;
	}
	
	public static boolean isFilterRange() {
		return filterRange;
	}
	
	public static boolean isFilterInversed() {
		return filterInversed;
	}
	
	public static void setNumElements(byte numElements) {
		Configuration.numElements = numElements;
	}
	
	public static byte getNumElements() {
		return numElements;
	}
	
	public static DataColumnInfo getSelectedColumn() {
		return selectedColumn;
	}
	
	public static boolean addGrainIndex(int index){
		return grainIndices.add(index);
	}
	
	public static boolean isHeadless() {
		return headless;
	}
	
	public static void setHeadless(boolean headless) {
		Configuration.headless = headless;
		Configuration.Options.SIMPLE.setEnabled(false);
	}
	
	/**
	 * Returns a copy of the grainIndices Set 
	 * @return
	 */
	public static HashSet<Integer> getGrainIndices(){
		return new HashSet<Integer>(grainIndices);
	}
	
	public static void setSelectedColumn(DataColumnInfo selectedColumn) {
		Configuration.selectedColumn = selectedColumn;
	}
	
	public static void setFilterRange(boolean filterRange) {
		Configuration.filterRange = filterRange;
	}
	
	public static void setFilterInversed(boolean filterInversed) {
		Configuration.filterInversed = filterInversed;
	}
	
	public static void setCurrentAtomData(AtomData currentAtomData) {
		Configuration.currentAtomData = currentAtomData;
		if (currentAtomData == null) return;
		Configuration.currentAtomDataBound_x = currentAtomData.getBox().getBoxSize()[0];
		Configuration.currentAtomDataBound_y = currentAtomData.getBox().getBoxSize()[1];
		Configuration.currentAtomDataBound_z = currentAtomData.getBox().getBoxSize()[2];
		
		Configuration.currentAtomDataHalfBound_x = currentAtomData.getBox().getHeight().x * 0.5f;
		Configuration.currentAtomDataHalfBound_y = currentAtomData.getBox().getHeight().y * 0.5f;
		Configuration.currentAtomDataHalfBound_z = currentAtomData.getBox().getHeight().z * 0.5f;
	}
	
	public static CrystalStructure getCrystalStructure() {
		return crystalStructure;
	}
	
	public static Vec3[] getCrystalOrientation() {
		return crystalOrientation;
	}
	
	public static CrystalRotationTools getCrystalRotationTools() {
		return crystalRotationTools;
	}
	
	public static DataColumnInfo getDataColumnInfo(int i){
		return dataColumns.get(i);
	}
	
	public static int getSizeDataColumns(){
		return dataColumns.size();
	}
	
	public static ArrayList<ProcessingModule> getProcessingModules() {
		return processingModule;
	}
	
	public static Vec3 pbcCorrectedDirection(Vec3 c1, Vec3 c2){
		Vec3 dir = c1.subClone(c2);
		if (pbc[0]){
			if (dir.x > currentAtomDataHalfBound_x) dir.sub(currentAtomDataBound_x);
			else if (dir.x < -currentAtomDataHalfBound_x) dir.add(currentAtomDataBound_x);
		}
		if (pbc[1]){
			if (dir.y > currentAtomDataHalfBound_y) dir.sub(currentAtomDataBound_y);
			else if (dir.y < -currentAtomDataHalfBound_y) dir.add(currentAtomDataBound_y);
		}
		if (pbc[2]){
			if (dir.z > currentAtomDataHalfBound_z) dir.sub(currentAtomDataBound_z);
			else if (dir.z < -currentAtomDataHalfBound_z) dir.add(currentAtomDataBound_z);
		}
		return dir;
	}
}
