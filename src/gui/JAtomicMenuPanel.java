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

package gui;

import gui.JColorSelectPanel.ColorSelectPanelTypes;
import gui.JMainWindow.KeyBoardAction;
import gui.ViewerGLJPanel.AtomRenderType;
import gui.ViewerGLJPanel.RenderOption;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import common.ColorTable;
import common.Vec3;
import model.*;
import model.Configuration.AtomDataChangedEvent;
import model.Configuration.AtomDataChangedListener;
import model.dataContainer.DataContainer;

public class JAtomicMenuPanel extends JPanel implements AtomDataChangedListener{
	private static final long serialVersionUID = 1L;
	
	private JLabel totalAtomsLabel;
	private JLabel crystalStructureLabel;
	private JLabel[] boxSizeLabel = new JLabel[3];
	private JLabel[] numberOftypeLabels = new JLabel[0];
	private JIgnoreTypeCheckbox[] ignoreTypeCheckbox = new JIgnoreTypeCheckbox[0];
	private JColorSelectPanel[] typeColorPanel = new JColorSelectPanel[0];
	private Container typeIgnoreContainer = new Container(); 
	
	private JLabel timeStepLabel = new JLabel();
	
	private JToggleButton atomsVisibleToggleButton = new JToggleButton("Hide atoms");
	private JCheckBox rbvVisibleToogleButton = new JCheckBox("Show as RBVs", false);
	
	private JButton colorShiftButton = new JButton("Set element shading"); 
	
	private JButton forwardButton = new JButton(">");
	private JButton rewindButton = new JButton("<");
	private JButton fastForwardButton = new JButton(">>");
	private JButton fastRewindButton = new JButton("<<");
	
	private JRadioButton drawAsTypesButton = new JRadioButton("Atoms");
	private JRadioButton drawAsGrainsButton = new JRadioButton("Grains");
	private JRadioButton drawAsElementsButton = new JRadioButton("Elements");
	private JRadioButton drawAsDataButton = new JRadioButton("Data Values");
	private JRadioButton drawAsVectorDataButton = new JRadioButton("Vector Data");
	private JCheckBox drawClusterCheckBox = new JCheckBox("Grain boundaries");
	
	private Container elementIgnoreContainer = new Container();	
	private Container grainIgnoreContainer = new Container();
	private JScrollPane elementScrollPane;
	private JScrollPane grainScrollPane;
	
	private JDataColumnControlPanel dataColumnPanel;
	private JVectorDataColumnControlPanel vectorDataColumnPanel;
	
	private JPanel dataPanel = new JPanel();
	private GridBagConstraints dataPanelContraints = new GridBagConstraints();
	
	private AtomData atomData;
	private JFrame parentFrame;
	
	public JAtomicMenuPanel(KeyBoardAction kba, JFrame parent){
		this.parentFrame = parent;
		Configuration.addAtomDataListener(this);
		
		Container cont = new Container();
		cont.setLayout(new GridBagLayout());
		
		GridBagConstraints gbc = new GridBagConstraints();
		
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridwidth = 2;
		gbc.weightx = 0.5;
		
		gbc.gridy = 0; cont.add(totalAtomsLabel = new JLabel(), gbc);
		gbc.gridy++; cont.add(timeStepLabel, gbc);
		gbc.gridy++; cont.add(boxSizeLabel[0] = new JLabel(), gbc);
		gbc.gridy++; cont.add(boxSizeLabel[1] = new JLabel(), gbc);
		gbc.gridy++; cont.add(boxSizeLabel[2] = new JLabel(), gbc);
		gbc.gridy++;
		
		JPanel infoPanel = new JPanel();
		infoPanel.setLayout(new GridBagLayout());
		GridBagConstraints gbcCont = new GridBagConstraints();
		infoPanel.setBorder(new TitledBorder(new EtchedBorder(1), "Atoms"));
		gbcCont.anchor = GridBagConstraints.WEST;
		gbcCont.fill = GridBagConstraints.HORIZONTAL;
		gbcCont.gridwidth = 3;
		gbcCont.weightx = 1;
		gbcCont.gridx = 0;
		gbcCont.gridy = 0;
		infoPanel.add(crystalStructureLabel = new JLabel(), gbcCont); gbcCont.gridy++;
		
		typeIgnoreContainer.setLayout(new GridBagLayout());
		fillIgnoreBoxPanel();
		infoPanel.add(typeIgnoreContainer, gbcCont); gbcCont.gridx++;

		rbvVisibleToogleButton.setVisible(false);
		cont.add(infoPanel, gbc); gbc.gridy++;
		
		atomsVisibleToggleButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (atomData == null) return;
				if (atomsVisibleToggleButton.isSelected()){
					for (int i=0; i<ignoreTypeCheckbox.length; i++){
						ignoreTypeCheckbox[i].setEnabled(false);
						RenderingConfiguration.getViewer().setTypeIgnored(i, true);
					}
					atomsVisibleToggleButton.setText("Show atoms");
				} else {
					for (int i=0; i<ignoreTypeCheckbox.length; i++){
						ignoreTypeCheckbox[i].setEnabled(true);
						RenderingConfiguration.getViewer().setTypeIgnored(i, !ignoreTypeCheckbox[i].isSelected());
					}
					atomsVisibleToggleButton.setText("Hide atoms");
				}
				RenderingConfiguration.getViewer().updateAtoms();
			}
		});
		
		rbvVisibleToogleButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				RenderingConfiguration.getViewer().setRenderingAtomsAsRBV(JAtomicMenuPanel.this.rbvVisibleToogleButton.isSelected());
			}
		});
		
		colorShiftButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JColorShiftDialog d = new JColorShiftDialog(
						parentFrame,
						RenderingConfiguration.getViewer().getColorShiftForElements().o1,
						RenderingConfiguration.getViewer().getColorShiftForElements().o2,
						atomData.getCrystalStructure());
				Vec3 hsv = d.getShift();
				RenderingConfiguration.getViewer().setColorShiftForElements(hsv.x, hsv.y, hsv.z, d.isShiftForVTypes());
			}
		});
		
		
		
		ButtonGroup bg = new ButtonGroup();
		bg.add(drawAsTypesButton);
		bg.add(drawAsElementsButton);
		bg.add(drawAsGrainsButton);
		bg.add(drawAsDataButton);
		bg.add(drawAsVectorDataButton);
		
		ActionListener al = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				ViewerGLJPanel viewer = RenderingConfiguration.getViewer();
				elementScrollPane.setVisible(false);
				if (!drawClusterCheckBox.isVisible() || !drawClusterCheckBox.isSelected())
					grainScrollPane.setVisible(false);
				dataColumnPanel.setVisible(false);
				vectorDataColumnPanel.setVisible(false);
				colorShiftButton.setVisible(false);
				
				if (arg0.getActionCommand().equals(drawAsTypesButton.getText())){
					if (viewer!=null) viewer.setAtomRenderMethod(AtomRenderType.TYPE);
					if (atomData!=null && atomData.getNumberOfElements()>1) colorShiftButton.setVisible(true);
				} else if (arg0.getActionCommand().equals(drawAsDataButton.getText())){
					if (viewer!=null) viewer.setAtomRenderMethod(AtomRenderType.DATA);
					dataColumnPanel.setVisible(true);
				}else if (arg0.getActionCommand().equals(drawAsElementsButton.getText())){
					if (viewer!=null) viewer.setAtomRenderMethod(AtomRenderType.ELEMENTS);
					elementScrollPane.setVisible(true);
				} else if (arg0.getActionCommand().equals(drawAsGrainsButton.getText())){
					if (viewer!=null) viewer.setAtomRenderMethod(AtomRenderType.GRAINS);
					grainScrollPane.setVisible(true);
				} else if (arg0.getActionCommand().equals(drawAsVectorDataButton.getText())){
					if (viewer!=null) viewer.setAtomRenderMethod(AtomRenderType.VECTOR_DATA);
					vectorDataColumnPanel.setVisible(true);
				}
				JAtomicMenuPanel.this.revalidate();	
			}
		};
		
		drawAsTypesButton.addActionListener(al);
		drawAsTypesButton.setActionCommand(drawAsTypesButton.getText());
		drawAsGrainsButton.addActionListener(al);
		drawAsGrainsButton.setActionCommand(drawAsGrainsButton.getText());
		drawAsElementsButton.addActionListener(al);
		drawAsElementsButton.setActionCommand(drawAsElementsButton.getText());
		drawAsDataButton.addActionListener(al);
		drawAsDataButton.setActionCommand(drawAsDataButton.getText());
		drawAsVectorDataButton.addActionListener(al);
		drawAsVectorDataButton.setActionCommand(drawAsVectorDataButton.getText());
		
		cont.add(drawAsTypesButton, gbc); gbc.gridy++;
		cont.add(drawAsElementsButton, gbc); gbc.gridy++;
		cont.add(drawAsGrainsButton, gbc); gbc.gridy++;
		cont.add(drawAsDataButton, gbc); gbc.gridy++;
		cont.add(drawAsVectorDataButton, gbc); gbc.gridy++;

		drawAsTypesButton.setVisible(false);
		drawAsElementsButton.setVisible(false);
		drawAsGrainsButton.setVisible(false);
		drawAsDataButton.setVisible(false);
		drawAsVectorDataButton.setVisible(false);
		
		elementIgnoreContainer.setLayout(new GridBagLayout());
		elementScrollPane = new JScrollPane(elementIgnoreContainer, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		elementScrollPane.setBorder(new TitledBorder(new EtchedBorder(1), "Show Elements"));
		elementScrollPane.setPreferredSize(new Dimension(10, 120));
		elementScrollPane.setMinimumSize(new Dimension(10, 120));
		cont.add(elementScrollPane, gbc); gbc.gridy++;
		elementScrollPane.setVisible(false);
		
		dataColumnPanel = new JDataColumnControlPanel(this);
		cont.add(dataColumnPanel, gbc); gbc.gridy++;
		dataColumnPanel.setVisible(false);
		
		vectorDataColumnPanel = new JVectorDataColumnControlPanel(this);
		cont.add(vectorDataColumnPanel, gbc); gbc.gridy++;
		vectorDataColumnPanel.setVisible(false);
		
		drawClusterCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				RenderOption.GRAINS.setEnabled(drawClusterCheckBox.isSelected());
				if (drawClusterCheckBox.isSelected()){
					grainScrollPane.setVisible(true);
				} else {
					grainScrollPane.setVisible(drawAsGrainsButton.isSelected());
				}
				JAtomicMenuPanel.this.revalidate();	
			}
		});
		cont.add(drawClusterCheckBox, gbc); gbc.gridy++;
		drawClusterCheckBox.setVisible(false);
		
		grainIgnoreContainer.setLayout(new GridBagLayout());
		grainScrollPane = new JScrollPane(grainIgnoreContainer, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		grainScrollPane.setBorder(new TitledBorder(new EtchedBorder(1), "Show Grains"));
		grainScrollPane.setPreferredSize(new Dimension(10, 120));
		grainScrollPane.setMinimumSize(new Dimension(10, 120));
		cont.add(grainScrollPane, gbc); gbc.gridy++;
		grainScrollPane.setVisible(false);

		cont.add(dataPanel, gbc); gbc.gridy++;
		dataPanel.setVisible(true);
		dataPanel.setLayout(new GridBagLayout());
		dataPanelContraints.fill = GridBagConstraints.HORIZONTAL;
		dataPanelContraints.weightx = 1;
		dataPanelContraints.gridx = 0;
		dataPanelContraints.gridy = 0;
		
		gbc.weighty = 1.;
		cont.add(new JLabel(), gbc); gbc.gridy++;
		
		gbc.gridheight = GridBagConstraints.REMAINDER;
		gbc.anchor = GridBagConstraints.SOUTH;
		JPanel p = new JPanel();
		p.setLayout(new GridLayout(1,4));
		
		p.add(fastRewindButton);
		fastRewindButton.setToolTipText("First file in sequence (f)");
		fastRewindButton.addActionListener(kba);
		fastRewindButton.setActionCommand("f");
		fastRewindButton.setEnabled(false);
		p.add(rewindButton); 
		rewindButton.setToolTipText("Previous file in sequence (y or z)");
		rewindButton.addActionListener(kba);
		rewindButton.setActionCommand("z");
		rewindButton.setEnabled(false);
		p.add(forwardButton);
		forwardButton.setToolTipText("Next file in sequence (x)");
		forwardButton.addActionListener(kba);
		forwardButton.setActionCommand("x");
		forwardButton.setEnabled(false);
		p.add(fastForwardButton);
		fastForwardButton.setToolTipText("Last file in sequence (l)");
		fastForwardButton.addActionListener(kba);
		fastForwardButton.setActionCommand("l");
		fastForwardButton.setEnabled(false);
		cont.add(p, gbc);
		
		drawAsTypesButton.doClick(); //Set selected
		
		JScrollPane scrollPane = new JScrollPane(cont, 
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		this.setLayout(new GridLayout(1,1));
		this.add(scrollPane);
	}
	
	/**
	 * updates values like colors if they have modified
	 */
	public void updateValues(){
		for (int i=0; i<typeColorPanel.length; i++){
			JColorSelectPanel p = typeColorPanel[i];
			p.setBackground(atomData.getCrystalStructure().getColor(i));
		}
	}
	
	private void fillIgnoreBoxPanel(){
		typeIgnoreContainer.removeAll();
		if (atomData == null || atomData.getCrystalStructure() == null){
			numberOftypeLabels = new JLabel[0];
			ignoreTypeCheckbox = new JIgnoreTypeCheckbox[0];
			return;
		} else 
			
		numberOftypeLabels = new JLabel[atomData.getCrystalStructure().getNumberOfTypes()];
		ignoreTypeCheckbox = new JIgnoreTypeCheckbox[atomData.getCrystalStructure().getNumberOfTypes()];
		typeColorPanel = new JColorSelectPanel[atomData.getCrystalStructure().getNumberOfTypes()];
		
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.;
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.CENTER;
		c.gridwidth = 2;
		typeIgnoreContainer.add(colorShiftButton, c); c.gridy++;
		c.gridwidth = 1;
		for (int i=0; i<atomData.getCrystalStructure().getNumberOfTypes();i++){
			typeColorPanel[i] = new JColorSelectPanel(i, 
					atomData.getCrystalStructure().getColor(i), ColorSelectPanelTypes.TYPES);
			c.fill = GridBagConstraints.HORIZONTAL;
			c.gridwidth = 2;
			typeIgnoreContainer.add(numberOftypeLabels[i] = new JLabel(), c); c.gridy++;
			c.weightx = 1;
			c.gridwidth = 1;
			typeIgnoreContainer.add(typeColorPanel[i], c); c.gridx++;
			c.weightx = 1;
			c.fill = GridBagConstraints.NONE;
			typeIgnoreContainer.add(ignoreTypeCheckbox[i] = new JIgnoreTypeCheckbox(i), c);
			ignoreTypeCheckbox[i].setText("visible");
			c.gridy++;
			c.gridx = 0;
		}
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridwidth = 2;
		typeIgnoreContainer.add(atomsVisibleToggleButton, c); c.gridy++;
		typeIgnoreContainer.add(rbvVisibleToogleButton, c); c.gridy++;
	}
	
	@Override
	public void atomDataChanged(AtomDataChangedEvent e) {
		this.dataPanel.removeAll();
		dataPanelContraints.gridy = 0;
		this.atomData = e.getNewAtomData();
		
		if (this.atomData == null) {
			this.revalidate();
			this.repaint();
			fillIgnoreBoxPanel();
			totalAtomsLabel.setText("");
			boxSizeLabel[0].setText("");
			boxSizeLabel[1].setText("");
			boxSizeLabel[2].setText("");
			crystalStructureLabel.setText("");
			return;
		}
		
		for (DataContainer dc: atomData.getAdditionalData()){
			dc.getDataControlPanel().update(dc);
			dataPanel.add(dc.getDataControlPanel(),dataPanelContraints);
			dc.getDataControlPanel().setViewer(RenderingConfiguration.getViewer());
			dataPanelContraints.gridy++;
		}
		dataPanel.revalidate();
		
		if (e.isResetGUI()){
			fillIgnoreBoxPanel();
			int def = atomData.getCrystalStructure().getDefaultType();
			atomsVisibleToggleButton.setSelected(false);
			RenderingConfiguration.getViewer().setRenderingAtomsAsRBV(false);
			this.rbvVisibleToogleButton.setSelected(false);
			if (atomData.getNumberOfAtomsWithType(def)*4>atomData.getAtoms().size() 
					&& (atomData.getNumberOfAtomsWithType(def)!=atomData.getAtoms().size())){
				ignoreTypeCheckbox[def].setSelected(false);
				RenderingConfiguration.getViewer().setTypeIgnored(def, true);
			}
		}
		
		
		this.rbvVisibleToogleButton.setVisible(atomData.isRbvAvailable());
		
		for (int i=0; i<ignoreTypeCheckbox.length;i++){
			RenderingConfiguration.getViewer().setTypeIgnored(i, !(ignoreTypeCheckbox[i].isSelected() && ignoreTypeCheckbox[i].isEnabled()) );
		}
		
		if (atomData.getFileMetaData("timestep") != null){
			timeStepLabel.setText("Timestep: "+( (int)((float[])(atomData.getFileMetaData("timestep")))[0]) );
		} else timeStepLabel.setText(""); 
		
		totalAtomsLabel.setText("#Atoms: "+atomData.getAtoms().size());
		boxSizeLabel[0].setText("Size X: "+atomData.getBox().getHeight().x);
		boxSizeLabel[1].setText("Size Y: "+atomData.getBox().getHeight().y);
		boxSizeLabel[2].setText("Size Z: "+atomData.getBox().getHeight().z);
		crystalStructureLabel.setText("Structure: "+atomData.getCrystalStructure().toString());
		
		for (int i=0; i<numberOftypeLabels.length;i++){
			numberOftypeLabels[i].setText(
					atomData.getCrystalStructure().getNameForType(i) +" ("+atomData.getNumberOfAtomsWithType(i)+")");
		}
		
		elementIgnoreContainer.removeAll();
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.;
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		for (int i=0; i<atomData.getNumberOfElements(); i++){
			c.weightx = 0.3;
			c.gridx = 0;
			float[] col = ColorTable.getColorTableForElements(atomData.getNumberOfElements())[i];
			elementIgnoreContainer.add(
					new JColorSelectPanel(i, new Color(col[0], col[1], col[2]), ColorSelectPanelTypes.ELEMENTS),c);
			c.gridx++;
			c.weightx = 1.;
			String label = atomData.getNameOfElement(i)+" ";
			label += "("+Integer.toString(atomData.getNumberOfAtomsOfElement(i))+")";
			elementIgnoreContainer.add(new JIgnoreElementCheckbox(i, label.trim()), c);
			c.gridy++;
		}
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weighty = 1.;
		elementIgnoreContainer.add(new JLabel(""),c);
		
		grainIgnoreContainer.removeAll();
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.;
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.NORTHWEST;
		ArrayList<Integer> sortedList = new ArrayList<Integer>(Configuration.getGrainIndices());
		Collections.sort(sortedList);
		for (int i : sortedList){
			grainIgnoreContainer.add(new JIgnoreGrainCheckbox(i), c);
			c.gridy++;
		}
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weighty = 1.;
		grainIgnoreContainer.add(new JLabel(""),c);
		
		
		if (atomData.getNumberOfElements() < 2){
			if (drawAsElementsButton.isSelected())
				drawAsTypesButton.doClick();
			drawAsElementsButton.setVisible(false);
		} else drawAsElementsButton.setVisible(true);
		
		if (!atomData.isPolyCrystalline()){
			drawAsGrainsButton.setVisible(false);
			drawClusterCheckBox.setVisible(false);
			grainScrollPane.setVisible(false);
		} else {
			drawAsGrainsButton.setVisible(true);
			drawClusterCheckBox.setVisible(true);
			if (drawClusterCheckBox.isSelected())
				grainScrollPane.setVisible(true);
		}
		
		//Check if there are any data columns that need to be displayed  
		boolean enableDataPanel = atomData.getDataColumnInfos().size()>0;
		
		drawAsDataButton.setVisible(enableDataPanel);
		if (drawAsDataButton.isSelected() && !enableDataPanel){
			drawAsTypesButton.doClick();
		}
			
		dataColumnPanel.resetDropDown();
		if (e.isResetGUI()) dataColumnPanel.resetValues();
		
		//Check if there are any vector columns that need to be displayed  
		boolean enableVectorDataPanel = false;
		for (DataColumnInfo dci : atomData.getDataColumnInfos())
			if (dci.isFirstVectorComponent()) enableVectorDataPanel = true;
		
		drawAsVectorDataButton.setVisible(enableVectorDataPanel);
		
		if (drawAsVectorDataButton.isSelected() && !enableVectorDataPanel){
			drawAsTypesButton.doClick();
		}
		vectorDataColumnPanel.resetDropDown();
		if (e.isResetGUI()) vectorDataColumnPanel.resetValues();
		
		
		this.drawAsTypesButton.setVisible(atomData.getNumberOfElements() >= 2 ||
				atomData.getDataColumnInfos().size() > 0 ||
				atomData.isPolyCrystalline());
		
		if (fastForwardButton.isEnabled() != (atomData.getNext() != null)) fastForwardButton.setEnabled(atomData.getNext() != null);
		if (forwardButton.isEnabled() != (atomData.getNext() != null)) forwardButton.setEnabled(atomData.getNext() != null);
		if (fastRewindButton.isEnabled() != (atomData.getPrevious() != null)) fastRewindButton.setEnabled(atomData.getPrevious() != null);
		if (rewindButton.isEnabled() != (atomData.getPrevious() != null))rewindButton.setEnabled(atomData.getPrevious() != null);
		
		this.colorShiftButton.setVisible(atomData.getNumberOfElements()>1);
		
		if (e.isResetGUI())
			drawAsTypesButton.doClick();
		
		this.revalidate();
		this.repaint();
	}
	
	private class JIgnoreTypeCheckbox extends JCheckBox{
		private static final long serialVersionUID = 1L;		
		
		public JIgnoreTypeCheckbox(final int number){
			this.setSelected(true);
			this.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
					if (atomData != null){
						RenderingConfiguration.getViewer().setTypeIgnored(number, !JIgnoreTypeCheckbox.this.isSelected());
						RenderingConfiguration.getViewer().updateAtoms();
					}
				}
			});
		}
	}
		
	private class JIgnoreElementCheckbox extends JCheckBox{
		private static final long serialVersionUID = 1L;
		
		public JIgnoreElementCheckbox(final int number, String label){
			this.setText(Integer.toString(number)+" "+label);
			this.setSelected(!RenderingConfiguration.getViewer().isElementIgnored(number));
			this.setSize(new Dimension(200, (int)(20*RenderingConfiguration.getGUIScalingFactor())));
			this.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
					if (atomData!=null){
						RenderingConfiguration.getViewer().setElementIgnored(number, !JIgnoreElementCheckbox.this.isSelected());
						RenderingConfiguration.getViewer().updateAtoms();
					}
				}
			});
		}
	}
	
	private class JIgnoreGrainCheckbox extends JCheckBox{
		private static final long serialVersionUID = 1L;
		
		public JIgnoreGrainCheckbox(final int number){
			this.setText(Integer.toString(number));
			if (number == Atom.IGNORED_GRAIN) this.setText("none"); 
			if (number == Atom.DEFAULT_GRAIN) this.setText("default");
			this.setSelected(!RenderingConfiguration.getViewer().isGrainIgnored(number));
			float[] color = RenderingConfiguration.getViewer().getGrainColor(number);
			this.setBackground(new Color(color[0], color[1], color[2]));
			this.setSize(new Dimension(200, (int)(20*RenderingConfiguration.getGUIScalingFactor())));
			this.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
					if (atomData!=null){
						RenderingConfiguration.getViewer().setGrainIgnored(number, !JIgnoreGrainCheckbox.this.isSelected());
						RenderingConfiguration.getViewer().updateAtoms();
					}
				}
			});
		}
	}
	
	private class JDataColumnControlPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		
		private JSpinner lowerLimitSpinner = new JSpinner(new SpinnerNumberModel(0., -50., 50., 0.0001));
		private JSpinner upperLimitSpinner = new JSpinner(new SpinnerNumberModel(0., -50., 50., 0.0001));
		private JButton resetButton = new JButton("This file");
		private JButton resetAllButton = new JButton("All files");
		private JButton makeSymButton = new JButton("Adjust around 0");
		private JButton deleteButton = new JButton("Delete values");
		private JCheckBox filterCheckboxMin = new JCheckBox("Filter <min");
		private JCheckBox filterCheckboxMax = new JCheckBox("Filter >max");
		private JCheckBox inverseFilterCheckbox = new JCheckBox("Inverse filtering");
		private DataColumnInfo selectedColumn;
		private JComboBox valueComboBox = new JComboBox();
		
		private boolean isResetActive = false;
		
		private JAtomicMenuPanel parentPanel;
		
		public JDataColumnControlPanel(JAtomicMenuPanel parentPanel) {
			this.parentPanel = parentPanel;
			makeSymButton.setToolTipText("if min<0 and max>0, the values will be set symmetrical.\n "
					+ "If both are positive, the lower value is set to 0.\n"
					+ "If both are negative, the upper value is set to 0.");
			
			((JSpinner.NumberEditor)lowerLimitSpinner.getEditor()).getFormat().setMaximumFractionDigits(4);
			((JSpinner.NumberEditor)upperLimitSpinner.getEditor()).getFormat().setMaximumFractionDigits(4);
			 
			((SpinnerNumberModel)(lowerLimitSpinner.getModel())).setMinimum(null);
			((SpinnerNumberModel)(upperLimitSpinner.getModel())).setMinimum(null);
			((SpinnerNumberModel)(upperLimitSpinner.getModel())).setMaximum(null);
			((SpinnerNumberModel)(lowerLimitSpinner.getModel())).setMaximum(null);
			
			this.setBorder(new TitledBorder(new EtchedBorder(1), "Values"));
			
			this.setLayout(new GridBagLayout());
			
			GridBagConstraints gbc = new GridBagConstraints();
			
			gbc.anchor = GridBagConstraints.WEST;
			
			gbc.fill = GridBagConstraints.BOTH;
			gbc.weightx = 1;
			gbc.gridy = 0; gbc.gridx = 0;
			gbc.gridwidth = 2;
			gbc.gridx = 0; gbc.gridy++;
			this.add(valueComboBox, gbc); gbc.gridy++;
			gbc.gridwidth = 1;
			
			filterCheckboxMin.setSelected(RenderingConfiguration.isFilterMin());
			filterCheckboxMax.setSelected(RenderingConfiguration.isFilterMax());
			this.add(new JLabel("Min."), gbc);gbc.gridx++;
			this.add(new JLabel("Max."), gbc);
			gbc.gridx = 0; gbc.gridy++;
			this.add(lowerLimitSpinner, gbc);gbc.gridx++;
			this.add(upperLimitSpinner, gbc);
			gbc.gridx = 0; gbc.gridy++;
			
			this.add(filterCheckboxMin, gbc); gbc.gridx++;
			this.add(filterCheckboxMax, gbc);
			gbc.gridx = 0; gbc.gridy++;
			this.add(inverseFilterCheckbox, gbc);
			gbc.gridx = 0; gbc.gridy++;
			
			gbc.gridwidth = 2;
			this.add(new JLabel("Auto adjust min/max"), gbc);
			gbc.gridx = 0; gbc.gridy++;
			
			gbc.gridwidth = 1;
			this.add(resetButton, gbc); gbc.gridx++;
			this.add(resetAllButton, gbc);
			gbc.gridx = 0; gbc.gridy++;
			gbc.gridwidth = 2;
			this.add(makeSymButton, gbc); gbc.gridy++;
			this.add(deleteButton, gbc);
			gbc.gridx = 0; gbc.gridy++;
			gbc.gridwidth = 1;
			
			this.validate();
			
			valueComboBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if (isResetActive) return;
					selectedColumn = (DataColumnInfo)valueComboBox.getSelectedItem();
					setSpinner();
					RenderingConfiguration.setSelectedColumn(selectedColumn);
					RenderingConfiguration.getViewer().updateAtoms();
				}
			});
			
			lowerLimitSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					selectedColumn.setLowerLimit(((Number)lowerLimitSpinner.getValue()).floatValue());
					RenderingConfiguration.getViewer().updateAtoms();
				}
			});
			
			upperLimitSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					selectedColumn.setUpperLimit(((Number)upperLimitSpinner.getValue()).floatValue());
					RenderingConfiguration.getViewer().updateAtoms();
				}
			});
			
			filterCheckboxMin.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					RenderingConfiguration.setFilterMin(filterCheckboxMin.isSelected());
					RenderingConfiguration.getViewer().updateAtoms();
					inverseFilterCheckbox.setEnabled(filterCheckboxMin.isSelected() || filterCheckboxMax.isSelected());
				}
			});
			
			filterCheckboxMax.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					RenderingConfiguration.setFilterMax(filterCheckboxMax.isSelected());
					RenderingConfiguration.getViewer().updateAtoms();
					inverseFilterCheckbox.setEnabled(filterCheckboxMin.isSelected() || filterCheckboxMax.isSelected());
				}
			});
			
			inverseFilterCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					RenderingConfiguration.setFilterInversed(inverseFilterCheckbox.isSelected());
					RenderingConfiguration.getViewer().updateAtoms();
				}
			});
			
			resetAllButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					selectedColumn.findRange(atomData, true);
					lowerLimitSpinner.setValue(selectedColumn.getLowerLimit());
					upperLimitSpinner.setValue(selectedColumn.getUpperLimit());
					RenderingConfiguration.getViewer().updateAtoms();
				}
			});
			
			resetButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					selectedColumn.findRange(atomData, false);
					lowerLimitSpinner.setValue(selectedColumn.getLowerLimit());
					upperLimitSpinner.setValue(selectedColumn.getUpperLimit());
					RenderingConfiguration.getViewer().updateAtoms();
				}
			});
			
			makeSymButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					float min = ((Number)lowerLimitSpinner.getValue()).floatValue();
					float max = ((Number)upperLimitSpinner.getValue()).floatValue();
					
					if (min<0f && max>0f){
						float symValue = Math.max(Math.abs(min), Math.abs(max));
						lowerLimitSpinner.setValue(-symValue);
						upperLimitSpinner.setValue(symValue);
					} else if (min>0f && max>0f){
						lowerLimitSpinner.setValue(0f);
					} else if (min<0f && max<0f){
						upperLimitSpinner.setValue(0f);
					}
					RenderingConfiguration.getViewer().updateAtoms();
				}
			});
			
			deleteButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					String text = "Are you sure to delete"+ (selectedColumn.isVectorComponent()?" the vector data of ": " ")+
							selectedColumn.getName();
					int result = JOptionPane.showConfirmDialog(JDataColumnControlPanel.this.parentPanel,
							text, "Delete "+selectedColumn.getName(), JOptionPane.OK_CANCEL_OPTION);
					if (result == JOptionPane.OK_OPTION){
						atomData.removeDataColumnInfo(selectedColumn);
						Configuration.setCurrentAtomData(atomData, true, false);
					}
				}
			});
		}
		
		@Override
		public void setVisible(boolean aFlag) {
			super.setVisible(aFlag);
			this.filterCheckboxMin.setSelected(RenderingConfiguration.isFilterMin());
			this.filterCheckboxMax.setSelected(RenderingConfiguration.isFilterMax());
			this.inverseFilterCheckbox.setSelected(RenderingConfiguration.isFilterInversed());
			this.inverseFilterCheckbox.setEnabled(this.filterCheckboxMin.isSelected() || this.filterCheckboxMax.isSelected());
		}
		
		private void setSpinner(){
			if (selectedColumn == null) return;
			
			lowerLimitSpinner.setValue(selectedColumn.getLowerLimit());
			upperLimitSpinner.setValue(selectedColumn.getUpperLimit());
			
			lowerLimitSpinner.setEnabled(!selectedColumn.isFixedRange());
			upperLimitSpinner.setEnabled(!selectedColumn.isFixedRange());
			resetAllButton.setEnabled(!selectedColumn.isFixedRange());
			resetButton.setEnabled(!selectedColumn.isFixedRange());
		}
		
		public void resetValues(){
			if (selectedColumn!= null && atomData.getDataColumnInfos().size() != 0)
				selectedColumn.findRange(atomData, false);
		}
		
		public void resetDropDown(){
			this.isResetActive = true;
			DataColumnInfo s = selectedColumn; //Save from overwriting during switching which triggers actionListeners
			valueComboBox.removeAllItems();
			List<DataColumnInfo> dci = atomData.getDataColumnInfos();
			for (int i = 0; i<dci.size(); i++)
				valueComboBox.addItem(dci.get(i));
			if (s == null || !dci.contains(s)){
				selectedColumn = (DataColumnInfo)valueComboBox.getItemAt(0);
				setSpinner();
			}
			else if (dci.size()>0){
				valueComboBox.setSelectedItem(s);
				setSpinner();
			}
			this.isResetActive = false;
			RenderingConfiguration.setSelectedColumn(selectedColumn);
			
		}
	}
	
	
	private class JVectorDataColumnControlPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		
		private JSpinner lowerLimitSpinner = new JSpinner(new SpinnerNumberModel(0., -50., 50., 0.0001));
		private JSpinner upperLimitSpinner = new JSpinner(new SpinnerNumberModel(0., -50., 50., 0.0001));
		private JButton resetButton = new JButton("This file");
		private JButton resetAllButton = new JButton("All files");
		private JCheckBox filterCheckboxMin = new JCheckBox("Filter <min");
		private JCheckBox filterCheckboxMax = new JCheckBox("Filter >max");
		private JCheckBox inverseFilterCheckbox = new JCheckBox("Inverse filtering");
		private JCheckBox normalizeCheckbox = new JCheckBox("Normalize");
		private DataColumnInfo selectedColumn;
		private DataColumnInfo selectedColumnAbs;
		private JComboBox valueComboBox = new JComboBox();
		
		private boolean isResetActive = false;
		
		private JSpinner vectorThicknessSpinner = new JSpinner(new SpinnerNumberModel(1, 0.01, 5., 0.001));
		private JSpinner vectorScalingSpinner = new JSpinner(new SpinnerNumberModel(1., 0.001, 1000., 0.001));
		
		public JVectorDataColumnControlPanel(JAtomicMenuPanel parentPanel) {
			((JSpinner.NumberEditor)lowerLimitSpinner.getEditor()).getFormat().setMaximumFractionDigits(4);
			((JSpinner.NumberEditor)upperLimitSpinner.getEditor()).getFormat().setMaximumFractionDigits(4);
			 
			((SpinnerNumberModel)(lowerLimitSpinner.getModel())).setMinimum(null);
			((SpinnerNumberModel)(upperLimitSpinner.getModel())).setMinimum(null);
			((SpinnerNumberModel)(upperLimitSpinner.getModel())).setMaximum(null);
			((SpinnerNumberModel)(lowerLimitSpinner.getModel())).setMaximum(null);
			
			this.setBorder(new TitledBorder(new EtchedBorder(1), "Values"));
			
			this.setLayout(new GridBagLayout());
			
			GridBagConstraints gbc = new GridBagConstraints();
			
			gbc.anchor = GridBagConstraints.WEST;
			
			gbc.fill = GridBagConstraints.BOTH;
			gbc.weightx = 1;
			gbc.gridy = 0; gbc.gridx = 0;
			gbc.gridwidth = 2;
			gbc.gridx = 0; gbc.gridy++;
			this.add(valueComboBox, gbc); gbc.gridy++;
			gbc.gridwidth = 1;
			
			filterCheckboxMin.setSelected(RenderingConfiguration.isFilterMin());
			filterCheckboxMax.setSelected(RenderingConfiguration.isFilterMax());
			this.add(new JLabel("Min."), gbc);gbc.gridx++;
			this.add(new JLabel("Max."), gbc);
			gbc.gridx = 0; gbc.gridy++;
			this.add(lowerLimitSpinner, gbc);gbc.gridx++;
			this.add(upperLimitSpinner, gbc);
			gbc.gridx = 0; gbc.gridy++;
			
			this.add(filterCheckboxMin, gbc); gbc.gridx++;
			this.add(filterCheckboxMax, gbc);
			gbc.gridx = 0; gbc.gridy++;
			this.add(inverseFilterCheckbox, gbc);
			gbc.gridx = 0; gbc.gridy++;
			
			gbc.gridwidth = 2;
			this.add(new JLabel("Auto adjust min/max"), gbc);
			gbc.gridx = 0; gbc.gridy++;
			
			gbc.gridwidth = 1;
			this.add(resetButton, gbc); gbc.gridx++;
			this.add(resetAllButton, gbc);
			gbc.gridx = 0; gbc.gridy++;
			gbc.gridwidth = 2;
			this.add(normalizeCheckbox, gbc); gbc.gridy++;
			gbc.gridx = 0; gbc.gridy++;
			gbc.gridwidth = 1;
			this.add(new JLabel("Scale length"), gbc);gbc.gridx++;
			this.add(new JLabel("Thickness"), gbc);
			gbc.gridx = 0; gbc.gridy++;
			this.add(vectorScalingSpinner, gbc);gbc.gridx++;
			this.add(vectorThicknessSpinner, gbc);
			
			this.validate();
			
			valueComboBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if (valueComboBox.getSelectedItem() != null && !isResetActive){
						selectedColumn = ((DataColumnInfo.VectorDataColumnInfo)valueComboBox.getSelectedItem()).getDci();
						setSpinner();
						RenderingConfiguration.setSelectedVectorColumn(selectedColumn);
						RenderingConfiguration.getViewer().updateAtoms();
					}
				}
			});
			
			lowerLimitSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					selectedColumnAbs.setLowerLimit(((Number)lowerLimitSpinner.getValue()).floatValue());
					RenderingConfiguration.getViewer().updateAtoms();
				}
			});
			
			upperLimitSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					selectedColumnAbs.setUpperLimit(((Number)upperLimitSpinner.getValue()).floatValue());
					RenderingConfiguration.getViewer().updateAtoms();
				}
			});
			
			filterCheckboxMin.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					RenderingConfiguration.setFilterMin(filterCheckboxMin.isSelected());
					RenderingConfiguration.getViewer().updateAtoms();
					inverseFilterCheckbox.setEnabled(filterCheckboxMin.isSelected() || filterCheckboxMax.isSelected());
				}
			});
			
			filterCheckboxMax.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					RenderingConfiguration.setFilterMax(filterCheckboxMax.isSelected());
					RenderingConfiguration.getViewer().updateAtoms();
					inverseFilterCheckbox.setEnabled(filterCheckboxMin.isSelected() || filterCheckboxMax.isSelected());
				}
			});
			
			inverseFilterCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					RenderingConfiguration.setFilterInversed(inverseFilterCheckbox.isSelected());
					RenderingConfiguration.getViewer().updateAtoms();
				}
			});
			
			resetAllButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					selectedColumnAbs.findRange(atomData, true);
					lowerLimitSpinner.setValue(selectedColumnAbs.getLowerLimit());
					upperLimitSpinner.setValue(selectedColumnAbs.getUpperLimit());
					RenderingConfiguration.getViewer().updateAtoms();
				}
			});
			
			resetButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					selectedColumnAbs.findRange(atomData, false);
					lowerLimitSpinner.setValue(selectedColumnAbs.getLowerLimit());
					upperLimitSpinner.setValue(selectedColumnAbs.getUpperLimit());
					RenderingConfiguration.getViewer().updateAtoms();
				}
			});
			
			normalizeCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					RenderingConfiguration.setNormalizedVectorData(normalizeCheckbox.isSelected());
					RenderingConfiguration.getViewer().reDraw();
				}
			});
			
			vectorScalingSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					RenderingConfiguration.setVectorDataScaling(((Number)vectorScalingSpinner.getValue()).floatValue());
					RenderingConfiguration.getViewer().reDraw();
				}
			});
		
			vectorThicknessSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					RenderingConfiguration.setVectorDataThickness(((Number)vectorThicknessSpinner.getValue()).floatValue()*0.1f);
					RenderingConfiguration.getViewer().reDraw();
				}
			});
		}
		
		@Override
		public void setVisible(boolean aFlag) {
			super.setVisible(aFlag);
			this.filterCheckboxMin.setSelected(RenderingConfiguration.isFilterMin());
			this.filterCheckboxMax.setSelected(RenderingConfiguration.isFilterMax());
			this.inverseFilterCheckbox.setSelected(RenderingConfiguration.isFilterInversed());
			this.inverseFilterCheckbox.setEnabled(this.filterCheckboxMin.isSelected() || this.filterCheckboxMax.isSelected());
			if (aFlag){
				RenderingConfiguration.setSelectedVectorColumn(selectedColumn);
				setSpinner();
			}
		}
		
		private void setSpinner(){
			if (selectedColumn == null) return;
			selectedColumnAbs = selectedColumn.getVectorComponents()[3];
			
			lowerLimitSpinner.setValue(selectedColumnAbs.getLowerLimit());
			upperLimitSpinner.setValue(selectedColumnAbs.getUpperLimit());
			
			lowerLimitSpinner.setEnabled(!selectedColumnAbs.isFixedRange());
			upperLimitSpinner.setEnabled(!selectedColumnAbs.isFixedRange());
			resetAllButton.setEnabled(!selectedColumnAbs.isFixedRange());
			resetButton.setEnabled(!selectedColumnAbs.isFixedRange());
		}
		
		public void resetValues(){
			if (selectedColumnAbs!= null && atomData.getDataColumnInfos().size() != 0)
				selectedColumnAbs.findRange(atomData, false);
		}
		
		public void resetDropDown(){
			DataColumnInfo s = selectedColumn; //Save from overwriting during switching which triggers actionListeners
			this.isResetActive = true;
			valueComboBox.removeAllItems();
			List<DataColumnInfo> dci = atomData.getDataColumnInfos();
			for (int i = 0; i<dci.size(); i++)
				if (dci.get(i).isFirstVectorComponent())
					valueComboBox.addItem(new DataColumnInfo.VectorDataColumnInfo(dci.get(i)));
			
			if (valueComboBox.getModel().getSize() == 0) return;
			
			if (s == null || !dci.contains(s)){
				selectedColumn = ((DataColumnInfo.VectorDataColumnInfo)valueComboBox.getItemAt(0)).getDci();
				setSpinner();
			}
			else if (dci.size()>0){
				valueComboBox.setSelectedItem(s);
				setSpinner();
			}
			this.isResetActive = false;
			
			RenderingConfiguration.setSelectedVectorColumn(selectedColumn);
		}
	}
	
}
