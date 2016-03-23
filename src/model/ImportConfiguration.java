// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2015  ICAMS, Ruhr-Universität Bochum
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
import java.util.HashMap;
import java.util.Properties;

import model.DataColumnInfo.Component;
import common.Vec3;
import crystalStructures.CrystalStructure;

public class ImportConfiguration {

	public enum ImportStates {
		DISPOSE_DEFAULT,
		APPEND_FILES;
		
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
	}
		
	private ArrayList<DataColumnInfo> dataColumns;
	private boolean[] pbc;
	private CrystalStructure cs = null;
	private Vec3[] crystalOrientation;
	
	private static ImportConfiguration importConfiguration;
	
	public static ImportConfiguration getInstance(){
		return importConfiguration;
	}
	
	public static ImportConfiguration getNewInstance(){
		importConfiguration = new ImportConfiguration();
		return importConfiguration;
	}
	
	private ImportConfiguration(){
		this.dataColumns = new ArrayList<DataColumnInfo>();
		this.pbc = new boolean[3];
		this.crystalOrientation = new Vec3[]{
				new Vec3(1f,0f,0f),
				new Vec3(0f,1f,0f),
				new Vec3(0f,0f,1f)};
	}
	
	public boolean[] getPeriodicBoundaryConditions(){
		return this.pbc;
	}
	
	public ArrayList<DataColumnInfo> getDataColumns() {
		return this.dataColumns;
	}
	
	public CrystalStructure getCrystalStructure() {
		return this.cs;
	}
	
	public Vec3[] getCrystalOrientation(){
		return this.crystalOrientation;
	}
	
	public boolean readConfigurationFile(File confFile){
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
	
	public void loadProperties(File propertiesFile) throws IOException{
		Properties prop = new Properties();
		prop.load(new FileReader(propertiesFile));
		
		this.getPeriodicBoundaryConditions()[0] = Boolean.parseBoolean(prop.getProperty("pbc_x", "false"));
		this.getPeriodicBoundaryConditions()[1] = Boolean.parseBoolean(prop.getProperty("pbc_y", "false"));
		this.getPeriodicBoundaryConditions()[2] = Boolean.parseBoolean(prop.getProperty("pbc_z", "false"));
	}
	
	public void saveProperties(File propertiesFile){
		Properties prop = new Properties();
		
		prop.setProperty("pbc_x", Boolean.toString(pbc[0]));
		prop.setProperty("pbc_y", Boolean.toString(pbc[1]));
		prop.setProperty("pbc_z", Boolean.toString(pbc[2]));

		try {
			if (propertiesFile.canWrite()){
				if (!propertiesFile.exists()) propertiesFile.createNewFile();
				prop.store(new FileWriter(propertiesFile), "Viewer config file");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	public void createVectorDataColumn(){
		if (dataColumns.size() != 0){
			//Identify vector components
			HashMap<DataColumnInfo.Component, DataColumnInfo> map = new HashMap<DataColumnInfo.Component, DataColumnInfo>();
			for (DataColumnInfo dci : dataColumns)
				map.put(dci.getComponent(), dci);
			
			if (map.get(Component.FORCE_X) != null && map.get(Component.FORCE_Y) != null && map.get(Component.FORCE_Z) != null){
				DataColumnInfo f_abs = new DataColumnInfo("", "force_abs", map.get(Component.FORCE_X).getUnit());
				int fzPos = dataColumns.indexOf(map.get(Component.FORCE_Z));
				if (fzPos==dataColumns.size()-1) dataColumns.add(f_abs);
				else dataColumns.add(fzPos+1, f_abs);
				map.get(Component.FORCE_X).setAsFirstVectorComponent(map.get(Component.FORCE_Y), map.get(Component.FORCE_Z), f_abs, "Force");
			}
			if (map.get(Component.VELOCITY_X) != null && map.get(Component.VELOCITY_Y) != null && map.get(Component.VELOCITY_Z) != null){
				DataColumnInfo v_abs = new DataColumnInfo("", "v_abs", map.get(Component.VELOCITY_X).getUnit());
				int vzPos = dataColumns.indexOf(map.get(Component.VELOCITY_Z));
				if (vzPos==dataColumns.size()-1) dataColumns.add(v_abs);
				else dataColumns.add(vzPos+1, v_abs);
				map.get(Component.VELOCITY_X).setAsFirstVectorComponent(
						map.get(Component.VELOCITY_Y), map.get(Component.VELOCITY_Z), v_abs, "Veclocity");
			}
		}
	}
	
}
