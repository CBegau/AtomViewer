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

package model.dataContainer;

import gui.RenderRange;
import gui.ViewerGLJPanel;

import javax.media.opengl.GL3;

import model.AtomData;
import model.BoxParameter;

public abstract class DataContainer {
	
	public abstract boolean isTransparenceRenderingRequired();
	
	/**
	 * Renders the solid objects of this container
	 * Should use a deferred shader
	 * @param viewer
	 * @param gl
	 * @param renderRange
	 * @param picking
	 */
	public abstract void drawSolidObjects(ViewerGLJPanel viewer, GL3 gl, 
			RenderRange renderRange, boolean picking, BoxParameter box);
	public abstract void drawTransparentObjects(ViewerGLJPanel viewer, GL3 gl, 
			RenderRange renderRange, boolean picking, BoxParameter box);
	
	public abstract boolean processData(AtomData atomData) throws Exception;
	public abstract JDataPanel getDataControlPanel();
	
	public abstract String getDescription();
	
	/**
	 * A user friendly name for the data container
	 * @return
	 */
	public abstract String getName();
	
	/**
	 * Show an dialog with options for this DataContainer
	 * If there are no options, nothing happens
	 * @returns true if all parameters are valid
	 */
	public abstract boolean showOptionsDialog();
	
	/**
	 * Creates a new instance of the DataContainer
	 * State variables of the instance are copied as well if needed
	 * @return
	 */
	public abstract DataContainer deriveNewInstance();
	
	/**
	 * Test if the module can be applied in an instance of AtomData
	 * @return
	 */
	public abstract boolean isApplicable(final AtomData data);
	
	/**
	 * Describes what the processing module requires to work
	 * @return
	 */
	public abstract String getRequirementDescription();
}
