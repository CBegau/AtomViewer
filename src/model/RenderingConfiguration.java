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

import gui.ViewerGLJPanel;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import common.ColorTable;
import common.ColorTable.ColorBarScheme;

public class RenderingConfiguration {
	
	private static File configFile;
	private static boolean headless = false;
	
	/**
	 * Global options for AtomViewer, accessible in the settings-menu
	 */
	public static enum Options {
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
	
	private static boolean filterMin = false;
	private static boolean filterMax = false;
	private static boolean filterInversed = false;
	private static boolean normalizeVectorData = false;
	private static float vectorDataThickness = 0.1f;
	private static float vectorDataScaling = 1f;
	
	private static DataColumnInfo selectedColumn = null;
	private static DataColumnInfo selectedVectorColumn = null;
	
	private static ViewerGLJPanel viewer;
	
	public static void setViewer(ViewerGLJPanel viewer){
		RenderingConfiguration.viewer = viewer; 
	}
	
	public static ViewerGLJPanel getViewer(){
		return RenderingConfiguration.viewer; 
	}
	
	public static boolean isFilterMin() {
		return filterMin;
	}
	
	public static boolean isFilterMax() {
		return filterMax;
	}
	
	public static boolean isFilterInversed() {
		return filterInversed;
	}
	
	public static void setFilterMin(boolean filterMin) {
		RenderingConfiguration.filterMin = filterMin;
	}
	
	public static void setFilterMax(boolean filterMax) {
		RenderingConfiguration.filterMax = filterMax;
	}
	
	public static void setFilterInversed(boolean filterInversed) {
		RenderingConfiguration.filterInversed = filterInversed;
	}
	
	public static void setNormalizedVectorData(boolean normalizeVectorData) {
		RenderingConfiguration.normalizeVectorData = normalizeVectorData;
	}
	
	public static boolean isNormalizedVectorData() {
		return RenderingConfiguration.normalizeVectorData;
	}
	
	public static float getVectorDataScaling() {
		return vectorDataScaling;
	}
	
	public static float getVectorDataThickness() {
		return vectorDataThickness;
	}
	
	public static void setVectorDataScaling(float vectorDataScaling) {
		RenderingConfiguration.vectorDataScaling = vectorDataScaling;
	}
	
	public static void setVectorDataThickness(float vectorDataThickness) {
		RenderingConfiguration.vectorDataThickness = vectorDataThickness;
	}
	
	public static DataColumnInfo getSelectedColumn() {
		return RenderingConfiguration.selectedColumn;
	}
	
	public static DataColumnInfo getSelectedVectorColumn() {
		return RenderingConfiguration.selectedVectorColumn;
	}
	
	public static void setSelectedColumn(DataColumnInfo selectedColumn) {
		RenderingConfiguration.selectedColumn = selectedColumn;
	}
	
	public static void setSelectedVectorColumn(DataColumnInfo selectedColumn) {
		assert (selectedColumn.isFirstVectorComponent());
		RenderingConfiguration.selectedVectorColumn = selectedColumn;
	}
	
	public static void setHeadless(boolean headless) {
		RenderingConfiguration.headless = headless;
	}
	
	public static boolean isHeadless() {
		return headless;
	}
}
