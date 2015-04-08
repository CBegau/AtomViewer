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

import gui.JCrystalConfigurationDialog;
import gui.JCrystalConfigurationDialog.CrystalConfContent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import common.Vec3;
import crystalStructures.CrystalStructure;

//TODO remove DETECT_MARTENSITE_VARIANTS and ENERGY_GND_ANALYSIS and LATTICE_ROTATION
public enum ImportStates {
	DISPOSE_DEFAULT,
	BURGERS_VECTORS, FILTER_SURFACE,
	POLY_MATERIAL, SKELETONIZE, KILL_ALL_ATOMS,
	LATTICE_ROTATION, DETECT_MARTENSITE_VARIANTS,
	OVERRIDE, ENERGY_GND_ANALYSIS;
	
	private boolean state;
	private ImportStates(){
		this.state = false;
	}
	
	public boolean isActive(){
		return state;
	}
	
	public void setState(boolean state){
		this.state = state;
	}
	
	private static ArrayList<DataColumnInfo> dataColumns = new ArrayList<DataColumnInfo>();
	private static boolean[] pbc = new boolean[3];
	private static int filesInSequence = 100;
	private static boolean importSequence;
	private static CrystalStructure cs = null;
	
	private static Vec3[] crystalOrientation = new Vec3[]{
		new Vec3(1f,0f,0f),
		new Vec3(0f,1f,0f),
		new Vec3(0f,0f,1f)
	};
	
	public static void setImportSequence(boolean importSequence) {
		ImportStates.importSequence = importSequence;
	}
	
	public static void setFilesInSequence(int filesInSequence) {
		ImportStates.filesInSequence = filesInSequence;
	}
	
	public static boolean[] getPeriodicBoundaryConditions(){
		return pbc;
	}
	
	public static ArrayList<DataColumnInfo> getDataColumns() {
		return dataColumns;
	}
	
	public static boolean isImportSequence() {
		return importSequence;
	}
	
	public static int getFilesInSequence() {
		return filesInSequence;
	}
	
	public static CrystalStructure getCrystalStructure() {
		return cs;
	}
	
	public static Vec3[] getCrystalOrientation(){
		return crystalOrientation;
	}
	
	public static boolean readConfigurationFile(File confFile){
		try {
			CrystalConfContent crystalData = JCrystalConfigurationDialog.readConfigurationFile(confFile);
			if (crystalData==null) 
				return false;
			
			cs = crystalData.getCrystalStructure();
			crystalOrientation = crystalData.getOrientation();
			dataColumns = crystalData.getRawColumns();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	public static void loadProperties(File propertiesFile) throws IOException{
		Properties prop = new Properties();
		prop.load(new FileReader(propertiesFile));
		
		for (ImportStates i: ImportStates.values()){
			i.setState(Boolean.parseBoolean(prop.getProperty(i.toString(), "false")));
		}
		
		ImportStates.getPeriodicBoundaryConditions()[0] = Boolean.parseBoolean(prop.getProperty("pbc_x", "false"));
		ImportStates.getPeriodicBoundaryConditions()[1] = Boolean.parseBoolean(prop.getProperty("pbc_y", "false"));
		ImportStates.getPeriodicBoundaryConditions()[2] = Boolean.parseBoolean(prop.getProperty("pbc_z", "false"));
		
		importSequence = Boolean.parseBoolean(prop.getProperty("importSequence", "false"));
		filesInSequence = Integer.parseInt(prop.getProperty("filesInSequence", "100"));
	}
	
	public static void saveProperties(File propertiesFile){
		Properties prop = new Properties();
	
		for (ImportStates i: ImportStates.values()){
			prop.setProperty(i.toString(), Boolean.toString(i.isActive()));
		}
		
		prop.setProperty("importSequence", Boolean.toString(importSequence));
		prop.setProperty("pbc_x", Boolean.toString(pbc[0]));
		prop.setProperty("pbc_y", Boolean.toString(pbc[1]));
		prop.setProperty("pbc_z", Boolean.toString(pbc[2]));
		prop.setProperty("filesInSequence", Integer.toString(filesInSequence));

		try {
			if (propertiesFile.canWrite()){
				if (!propertiesFile.exists()) propertiesFile.createNewFile();
				prop.store(new FileWriter(propertiesFile), "Viewer config file");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}
