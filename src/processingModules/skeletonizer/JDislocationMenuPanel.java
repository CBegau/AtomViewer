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

package processingModules.skeletonizer;

import java.awt.GridLayout;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import gui.ViewerGLJPanel;
import model.RenderingConfiguration;
import processingModules.DataContainer;
import processingModules.JDataPanel;


public class JDislocationMenuPanel extends JDataPanel{
	private static final long serialVersionUID = 1L;
	
	private JCheckBox drawCoresButton = new JCheckBox("Dislocation");
	private JCheckBox drawStackingFaultsButton = new JCheckBox("Stacking Faults");
	private JCheckBox drawVectorsCheckBox = new JCheckBox("Burgers vectors");
	
	public enum Option {
		BURGERS_VECTORS_ON_CORES(false), DISLOCATIONS(false), STACKING_FAULT(false);
		
		private boolean enabled;
		
		private Option(boolean enabled){
			this.enabled = enabled;
		}
		
		public void setEnabled(boolean enabled){
			this.enabled = enabled;
		}
		
		public boolean isEnabled(){
			return enabled;
		}
	}
	
	
	public JDislocationMenuPanel(){
		this.setLayout(new GridLayout(3,1));
		this.setBorder(new TitledBorder(new EtchedBorder(1), "Dislocations"));

		RenderTypeButtonActionListener renderTypeButtonListener = new RenderTypeButtonActionListener();
		
		this.drawCoresButton.setSelected(Option.DISLOCATIONS.isEnabled());
		this.add(drawCoresButton);
		drawCoresButton.addActionListener(renderTypeButtonListener);
		drawCoresButton.setActionCommand(Option.DISLOCATIONS.toString());
		
		this.drawVectorsCheckBox.setSelected(Option.BURGERS_VECTORS_ON_CORES.isEnabled());
		this.add(drawVectorsCheckBox);
		drawVectorsCheckBox.addActionListener(renderTypeButtonListener);
		drawVectorsCheckBox.setActionCommand(Option.BURGERS_VECTORS_ON_CORES.toString());
		
		this.drawStackingFaultsButton.setSelected(Option.STACKING_FAULT.isEnabled());
		this.add(drawStackingFaultsButton);
		drawStackingFaultsButton.addActionListener(renderTypeButtonListener);
		drawStackingFaultsButton.setActionCommand(Option.STACKING_FAULT.toString());
	}
	
	private class RenderTypeButtonActionListener implements ActionListener{
		@Override
		public void actionPerformed(ActionEvent e) {
			String action = e.getActionCommand();
			if (RenderingConfiguration.getViewer()!=null){
				JCheckBox checkbox = (JCheckBox)e.getSource();
				if (action == null) return;
				if (action.equals(Option.DISLOCATIONS.toString()))
					Option.DISLOCATIONS.setEnabled(checkbox.isSelected());
				else if (action.equals(Option.STACKING_FAULT.toString()))
					Option.STACKING_FAULT.setEnabled(checkbox.isSelected());
				else if (action.equals(Option.BURGERS_VECTORS_ON_CORES.toString()))
					Option.BURGERS_VECTORS_ON_CORES.setEnabled(checkbox.isSelected());
					
				RenderingConfiguration.getViewer().reDraw();
			}
		}
	}
	
	@Override
	public void update(DataContainer dc) {}

	@Override
	public void setViewer(ViewerGLJPanel viewer) {}

	@Override
	public boolean isDataVisible() {
		return true;
	}
}
