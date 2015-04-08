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

package model.dataContainer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gui.RenderRange;
import gui.ViewerGLJPanel;

import javax.media.opengl.GL3;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import model.AtomData;
import model.Configuration;
import model.dataContainer.dislocationDensity.DislocationDensityTensorData;

public abstract class DataContainer {
	
	private final static List<DataContainer> dataContainer;
	
	static {
		//Creating the list of all available dataContainer
		dataContainer = new ArrayList<DataContainer>();
		if (!Configuration.Options.SIMPLE.isEnabled()) dataContainer.add(new StressData());
		if (!Configuration.Options.SIMPLE.isEnabled()) dataContainer.add(new LoadBalancingData());
		dataContainer.add(new DislocationDensityTensorData());
		if (!Configuration.Options.SIMPLE.isEnabled()) dataContainer.add(new VacancyDataContainer());
	}

	public File selectFile(File folder){
		JFileChooser chooser = new JFileChooser(folder);
		chooser.setFileFilter(fileFilter);
		
		int result = chooser.showOpenDialog(null);
		if (result == JFileChooser.APPROVE_OPTION)
			return chooser.getSelectedFile();
		else return null;
	}
	
	public abstract boolean isTransparenceRenderingRequired();
	
	/**
	 * Renders the solid objects of this container
	 * Should use a deferred shader
	 * @param viewer
	 * @param gl
	 * @param renderRange
	 * @param picking
	 */
	public abstract void drawSolidObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking);
	public abstract void drawTransparentObjects(ViewerGLJPanel viewer, GL3 gl, RenderRange renderRange, boolean picking);
	
	public abstract boolean processData(File dataFile, AtomData atomData) throws IOException;
	public abstract JDataPanel getDataControlPanel();
	
	public abstract String[] getFileExtensions();
	public abstract boolean isExternalFileRequired();
	
	public abstract String getDescription();
	public abstract String getName();
	
	/**
	 * Show an dialog with options for this DataContainer
	 * If there are no options, nothing happens
	 * @returns true if all parameters are valid
	 */
	public boolean showOptionsDialog(){
		return true;
	}
	
	
	public static List<DataContainer> getDataContainer() {
		return Collections.unmodifiableList(dataContainer);
	}
	
	public abstract DataContainer deriveNewInstance();
	
	
	private FileFilter fileFilter = new FileFilter() {
		@Override
		public String getDescription() {
			StringBuilder sb = new StringBuilder();
			if (getFileExtensions() == null || getFileExtensions().length == 0)
				return "Files (*.*)";
			sb.append("Supported formats (");
			for (String e : getFileExtensions()){
				sb.append("*.");
				sb.append(e);
				sb.append(", ");
			}
			sb.delete(sb.length()-2, sb.length());
			sb.append(")");
			return sb.toString();
		}
		
		@Override
		public boolean accept(File f) {
			if (f.isDirectory()) return true;
			if (getFileExtensions() == null || getFileExtensions().length == 0) return true;
			
			String name = f.getName();
			for (String e : getFileExtensions()){
				if (name.endsWith("."+e)) return true;
			}
			return false;
		}
	};
}
