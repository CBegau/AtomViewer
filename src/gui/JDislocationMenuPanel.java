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

package gui;

import gui.ViewerGLJPanel.RenderOption;

import java.awt.GridLayout;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import model.Configuration;
import model.ImportStates;


public class JDislocationMenuPanel extends JPanel{
	private static final long serialVersionUID = 1L;
	
	private JCheckBox drawCoresButton = new JCheckBox("Dislocation");
	private JCheckBox drawStackingFaultsButton = new JCheckBox("Stacking Faults");
	private JCheckBox drawVectorsCheckBox = new JCheckBox("Burgers vectors");
	
	private ViewerGLJPanel viewer;
	
	public JDislocationMenuPanel(){
		this.setLayout(new GridLayout(3,1));
		this.setBorder(new TitledBorder(new EtchedBorder(1), "Dislocations"));

		RenderTypeButtonActionListener renderTypeButtonListener = new RenderTypeButtonActionListener();
		
		this.drawCoresButton.setSelected(RenderOption.DISLOCATIONS.isEnabled());
		this.add(drawCoresButton);
		drawCoresButton.addActionListener(renderTypeButtonListener);
		drawCoresButton.setActionCommand(RenderOption.DISLOCATIONS.toString());
		
		this.drawVectorsCheckBox.setSelected(RenderOption.BURGERS_VECTORS_ON_CORES.isEnabled());
		this.add(drawVectorsCheckBox);
		drawVectorsCheckBox.addActionListener(renderTypeButtonListener);
		drawVectorsCheckBox.setActionCommand(RenderOption.BURGERS_VECTORS_ON_CORES.toString());
		
		this.drawStackingFaultsButton.setSelected(RenderOption.BURGERS_VECTORS_ON_CORES.isEnabled());
		this.add(drawStackingFaultsButton);
		drawStackingFaultsButton.addActionListener(renderTypeButtonListener);
		drawStackingFaultsButton.setActionCommand(RenderOption.STACKING_FAULT.toString());
	}
	
	public void setAtomData(ViewerGLJPanel viewer){
		this.viewer = viewer;
		drawStackingFaultsButton.setEnabled(Configuration.getCrystalStructure().hasStackingFaults());
		drawVectorsCheckBox.setEnabled(ImportStates.BURGERS_VECTORS.isActive());
	}
	
	private class RenderTypeButtonActionListener implements ActionListener{
		@Override
		public void actionPerformed(ActionEvent e) {
			String action = e.getActionCommand();
			if (viewer!=null){
				JCheckBox checkbox = (JCheckBox)e.getSource();
				if (action == null) return;
				if (action == RenderOption.DISLOCATIONS.toString())
					RenderOption.DISLOCATIONS.setEnabled(checkbox.isSelected());
				else if (action == RenderOption.STACKING_FAULT.toString())
					RenderOption.STACKING_FAULT.setEnabled(checkbox.isSelected());
				else if (action == RenderOption.BURGERS_VECTORS_ON_CORES.toString())
					RenderOption.BURGERS_VECTORS_ON_CORES.setEnabled(checkbox.isSelected());
			}
		}
	}
}
