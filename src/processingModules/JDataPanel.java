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

package processingModules;

import gui.ViewerGLJPanel;

import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;


public abstract class JDataPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	/**
	 * Adds a titled border around the panel with the given label
	 * @param label
	 */
	protected JDataPanel(String label){
		this.setBorder(new TitledBorder(new EtchedBorder(1), label));
	}
	
	public abstract void setViewer(ViewerGLJPanel viewer);

	/**
	 * Must pass a compatible container that belongs to the DataPanel
	 * @param dc
	 */
	public abstract void update(DataContainer dc);
	
	public abstract boolean isDataVisible();
}
