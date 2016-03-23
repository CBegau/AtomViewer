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

package processingModules;

import gui.RenderRange;
import gui.ViewerGLJPanel;

import com.jogamp.opengl.GL3;

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
	
	public abstract JDataPanel getDataControlPanel();
	
	public static class DefaultDataContainerProcessingResult extends ProcessingResult{
		private DataContainer dc;
		private String s;
		
		public DefaultDataContainerProcessingResult(DataContainer dc, String s) {
			this.dc = dc;
			this.s = s;
		}
		@Override
		public DataContainer getDataContainer() {
			return dc;
		}
		@Override
		public String getResultInfoString() {
			return s;
		}
	}
	
}
