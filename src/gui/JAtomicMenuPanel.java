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

import gui.JColorSelectPanel.ColorSelectPanelTypes;
import gui.JMainWindow.KeyBoardAction;
import gui.ViewerGLJPanel.AtomRenderType;
import gui.ViewerGLJPanel.RenderOption;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import common.ColorTable;
import common.Vec3;
import model.*;
import model.dataContainer.DataContainer;

public class JAtomicMenuPanel extends JPanel{
	private static final long serialVersionUID = 1L;
	
	private ViewerGLJPanel viewer;
	
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
	private JRadioButton drawAsCustomButton = new JRadioButton("Data Values");
	private JCheckBox drawClusterCheckBox = new JCheckBox("Grain boundaries");
	
	private Container elementIgnoreContainer = new Container();	
	private Container grainIgnoreContainer = new Container();
	private JScrollPane elementScrollPane;
	private JScrollPane grainScrollPane;
	
	private JDataColumnControlPanel dataColumnPanel;
	
	private JPanel dataPanel = new JPanel();
	private GridBagConstraints dataPanelContraints = new GridBagConstraints();
 	
	private JDislocationMenuPanel dislocationMenu = new JDislocationMenuPanel();
	
	private AtomData atomData;
	private JFrame parentFrame;
	
	public JAtomicMenuPanel(KeyBoardAction kba, JFrame parent){
		this.parentFrame = parent;
		Container cont = new Container();
		cont.setLayout(new GridBagLayout());
		
		GridBagConstraints gbc = new GridBagConstraints();
		
		gbc.anchor = GridBagConstraints.WEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridwidth = 2;
		gbc.weightx = 0.5;
		
		gbc.gridy = 0; cont.add(totalAtomsLabel = new JLabel("Total number of atoms: "), gbc);
		gbc.gridy++; cont.add(timeStepLabel, gbc);
		gbc.gridy++; cont.add(boxSizeLabel[0] = new JLabel("Boxsize X: "), gbc);
		gbc.gridy++; cont.add(boxSizeLabel[1] = new JLabel("Boxsize Y: "), gbc);
		gbc.gridy++; cont.add(boxSizeLabel[2] = new JLabel("Boxsize Z: "), gbc);
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
						viewer.setTypeIgnored(i, true);
					}
					atomsVisibleToggleButton.setText("Show atoms");
				} else {
					for (int i=0; i<ignoreTypeCheckbox.length; i++){
						ignoreTypeCheckbox[i].setEnabled(true);
						viewer.setTypeIgnored(i, !ignoreTypeCheckbox[i].isSelected());
					}
					atomsVisibleToggleButton.setText("Hide atoms");
				}
				viewer.updateAtoms();
			}
		});
		
		rbvVisibleToogleButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				viewer.setRenderingAtomsAsRBV(JAtomicMenuPanel.this.rbvVisibleToogleButton.isSelected());
			}
		});
		
		colorShiftButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JColorShiftDialog d = new JColorShiftDialog(
						parentFrame,
						viewer.getColorShiftForElements().o1,
						viewer.getColorShiftForElements().o2,
						Configuration.getCrystalStructure());
				Vec3 hsv = d.getShift();
				viewer.setColorShiftForElements(hsv.x, hsv.y, hsv.z, d.isShiftForVTypes());
				viewer.updateAtoms();
			}
		});
		
		
		
		ButtonGroup bg = new ButtonGroup();
		bg.add(drawAsTypesButton);
		bg.add(drawAsElementsButton);
		bg.add(drawAsGrainsButton);
		bg.add(drawAsCustomButton);
		
		ActionListener al = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				elementScrollPane.setVisible(false);
				if (!drawClusterCheckBox.isVisible() || !drawClusterCheckBox.isSelected())
					grainScrollPane.setVisible(false);
				dataColumnPanel.setVisible(false);
				colorShiftButton.setVisible(false);
				
				if (arg0.getActionCommand().equals(drawAsTypesButton.getText())){
					if (viewer!=null) viewer.setAtomRenderMethod(AtomRenderType.TYPE);
					if (Configuration.getNumElements()>1) colorShiftButton.setVisible(true);
				} else if (arg0.getActionCommand().equals(drawAsCustomButton.getText())){
					if (viewer!=null) viewer.setAtomRenderMethod(AtomRenderType.DATA);
					dataColumnPanel.setVisible(true);
				}else if (arg0.getActionCommand().equals(drawAsElementsButton.getText())){
					if (viewer!=null) viewer.setAtomRenderMethod(AtomRenderType.ELEMENTS);
					elementScrollPane.setVisible(true);
				} else if (arg0.getActionCommand().equals(drawAsGrainsButton.getText())){
					if (viewer!=null) viewer.setAtomRenderMethod(AtomRenderType.GRAINS);
					grainScrollPane.setVisible(true);
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
		drawAsCustomButton.addActionListener(al);
		drawAsCustomButton.setActionCommand(drawAsCustomButton.getText());
		
		cont.add(drawAsTypesButton, gbc); gbc.gridy++;
		cont.add(drawAsElementsButton, gbc); gbc.gridy++;
		cont.add(drawAsGrainsButton, gbc); gbc.gridy++;
		cont.add(drawAsCustomButton, gbc); gbc.gridy++;

		drawAsTypesButton.setVisible(false);
		drawAsElementsButton.setVisible(false);
		drawAsGrainsButton.setVisible(false);
		drawAsCustomButton.setVisible(false);
		
		elementIgnoreContainer.setLayout(new GridBagLayout());
		elementScrollPane = new JScrollPane(elementIgnoreContainer, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		elementScrollPane.setBorder(new TitledBorder(new EtchedBorder(1), "Show Elements"));
		elementScrollPane.setPreferredSize(new Dimension(10, 120));
		elementScrollPane.setMinimumSize(new Dimension(10, 120));
		cont.add(elementScrollPane, gbc); gbc.gridy++;
		elementScrollPane.setVisible(false);
		
		dataColumnPanel = new JDataColumnControlPanel();
		cont.add(dataColumnPanel, gbc); gbc.gridy++;
		dataColumnPanel.setVisible(false);
		
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
		
		cont.add(dislocationMenu, gbc); gbc.gridy++;
		dislocationMenu.setVisible(false);

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
			p.setBackground(Configuration.getCrystalStructure().getColor(i));
		}
	}
	
	private void fillIgnoreBoxPanel(){
		typeIgnoreContainer.removeAll();
		if (Configuration.getCrystalStructure() == null){
			numberOftypeLabels = new JLabel[0];
			ignoreTypeCheckbox = new JIgnoreTypeCheckbox[0];
			return;
		} else 
			
		numberOftypeLabels = new JLabel[Configuration.getCrystalStructure().getNumberOfTypes()];
		ignoreTypeCheckbox = new JIgnoreTypeCheckbox[Configuration.getCrystalStructure().getNumberOfTypes()];
		typeColorPanel = new JColorSelectPanel[Configuration.getCrystalStructure().getNumberOfTypes()];
		
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.;
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.CENTER;
		c.gridwidth = 2;
		typeIgnoreContainer.add(colorShiftButton, c); c.gridy++;
		c.gridwidth = 1;
		for (int i=0; i<Configuration.getCrystalStructure().getNumberOfTypes();i++){
			typeColorPanel[i] = new JColorSelectPanel(i, 
					Configuration.getCrystalStructure().getColor(i), ColorSelectPanelTypes.TYPES, viewer);
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
	
	public void setAtomData(AtomData data, ViewerGLJPanel viewer, boolean init){
		this.dataPanel.removeAll();
		dataPanelContraints.gridy = 0;
		for (DataContainer dc: data.getAdditionalData()){
			dc.getDataControlPanel().update(dc);
			dataPanel.add(dc.getDataControlPanel(),dataPanelContraints);
			dc.getDataControlPanel().setViewer(viewer);
			dataPanelContraints.gridy++;
		}
		dataPanel.revalidate();
		
		this.viewer = viewer;
		this.atomData = data;
		
		if (init){
			fillIgnoreBoxPanel();
			int def = Configuration.getCrystalStructure().getDefaultType();
			atomsVisibleToggleButton.setSelected(false);
			viewer.setRenderingAtomsAsRBV(false);
			this.rbvVisibleToogleButton.setSelected(false);
			if (data.getNumberOfAtomsWithType(def)*4>data.getAtoms().size() && (data.getNumberOfAtomsWithType(def)!=data.getAtoms().size())){
				ignoreTypeCheckbox[def].setSelected(false);
				viewer.setTypeIgnored(def, true);
			}
		}
		
		
		this.rbvVisibleToogleButton.setVisible(atomData.isRbvAvailable());
		
		for (int i=0; i<ignoreTypeCheckbox.length;i++){
			viewer.setTypeIgnored(i, !(ignoreTypeCheckbox[i].isSelected() && ignoreTypeCheckbox[i].isEnabled()) );
		}
		
		if (atomData.getFileMetaData("timestep") != null){
			timeStepLabel.setText("Timestep: "+( (int)((float[])(atomData.getFileMetaData("timestep")))[0]) );
		} else timeStepLabel.setText(""); 
		
		totalAtomsLabel.setText("#Atoms: "+data.getAtoms().size());
		boxSizeLabel[0].setText("Size X: "+data.getBox().getHeight().x);
		boxSizeLabel[1].setText("Size Y: "+data.getBox().getHeight().y);
		boxSizeLabel[2].setText("Size Z: "+data.getBox().getHeight().z);
		crystalStructureLabel.setText("Structure: "+Configuration.getCrystalStructure().toString());
		
		for (int i=0; i<numberOftypeLabels.length;i++){
			numberOftypeLabels[i].setText(
					Configuration.getCrystalStructure().getNameForType(i) +" ("+data.getNumberOfAtomsWithType(i)+")");
		}
		
		dataColumnPanel.setViewer(viewer);
		
		elementIgnoreContainer.removeAll();
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.;
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.WEST;
		for (int i=0; i<Configuration.getNumElements(); i++){
			c.weightx = 0.3;
			c.gridx = 0;
			float[] col = ColorTable.getColorTableForElements(Configuration.getNumElements())[i];
			elementIgnoreContainer.add(
					new JColorSelectPanel(i, new Color(col[0], col[1], col[2]), ColorSelectPanelTypes.ELEMENTS, viewer),c);
			c.gridx++;
			c.weightx = 1.;
			elementIgnoreContainer.add(new JIgnoreElementCheckbox(i, atomData.getNumberOfAtomsWithElement(i)), c);
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
		
		
		if (Configuration.getNumElements() < 2){
			if (drawAsElementsButton.isSelected())
				drawAsTypesButton.doClick();
			drawAsElementsButton.setVisible(false);
		} else drawAsElementsButton.setVisible(true);
		
		if (!ImportStates.POLY_MATERIAL.isActive()){
			drawAsGrainsButton.setVisible(false);
			drawClusterCheckBox.setVisible(false);
			grainScrollPane.setVisible(false);
		} else {
			drawAsGrainsButton.setVisible(true);
			drawClusterCheckBox.setVisible(true);
			if (drawClusterCheckBox.isSelected())
				grainScrollPane.setVisible(true);
		}
		
		//Check if there are any none special columns that need to be displayed  
		boolean enableCustom = Configuration.getSizeDataColumns()>0;
		
		drawAsCustomButton.setVisible(enableCustom);
		if (drawAsCustomButton.isSelected() && !enableCustom){
			drawAsTypesButton.doClick();
		}
			
		if (init) {
			dataColumnPanel.resetDropDown();
			dataColumnPanel.resetValues();
		}
		
		this.dislocationMenu.setAtomData(viewer);
		this.dislocationMenu.setVisible(ImportStates.SKELETONIZE.isActive());
		
		drawAsTypesButton.setVisible(ImportStates.LATTICE_ROTATION.isActive() ||
		        Configuration.getNumElements() >= 2 ||
		        Configuration.getSizeDataColumns() > 0 ||
				ImportStates.POLY_MATERIAL.isActive());
		
		if (fastForwardButton.isEnabled() != (data.getNext() != null)) fastForwardButton.setEnabled(data.getNext() != null);
		if (forwardButton.isEnabled() != (data.getNext() != null)) forwardButton.setEnabled(data.getNext() != null);
		if (fastRewindButton.isEnabled() != (data.getPrevious() != null)) fastRewindButton.setEnabled(data.getPrevious() != null);
		if (rewindButton.isEnabled() != (data.getPrevious() != null))rewindButton.setEnabled(data.getPrevious() != null);
		
		this.colorShiftButton.setVisible(Configuration.getNumElements()>1);
		
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
						viewer.setTypeIgnored(number, !JIgnoreTypeCheckbox.this.isSelected());
						viewer.updateAtoms();
					}
				}
			});
		}
	}
		
	private class JIgnoreElementCheckbox extends JCheckBox{
		private static final long serialVersionUID = 1L;
		
		public JIgnoreElementCheckbox(final int number, final int numberOfAtoms){
			this.setText(Integer.toString(number)+ " ("+ Integer.toString(numberOfAtoms)+")");
			this.setSelected(!viewer.isElementIgnored(number));
			this.setSize(new Dimension(200, 20));
			this.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
					if (atomData!=null){
						viewer.setElementIgnored(number, !JIgnoreElementCheckbox.this.isSelected());
						viewer.updateAtoms();
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
			this.setSelected(!viewer.isGrainIgnored(number));
			float[] color = viewer.getGrainColor(number);
			this.setBackground(new Color(color[0], color[1], color[2]));
			this.setSize(new Dimension(200, 20));
			this.addActionListener(new ActionListener(){
				@Override
				public void actionPerformed(ActionEvent e) {
					if (atomData!=null){
						viewer.setGrainIgnored(number, !JIgnoreGrainCheckbox.this.isSelected());
						viewer.updateAtoms();
					}
				}
			});
		}
	}
	
	private static class JDataColumnControlPanel extends JPanel {
		private static final long serialVersionUID = 1L;
		
		private JSpinner lowerLimitSpinner = new JSpinner(new SpinnerNumberModel(0., -50., 50., 0.0001));
		private JSpinner upperLimitSpinner = new JSpinner(new SpinnerNumberModel(0., -50., 50., 0.0001));
		private JButton resetButton = new JButton("This file");
		private JButton resetAllButton = new JButton("All files");
		private JButton makeSymButton = new JButton("Adjust around 0");
		private JCheckBox filterCheckbox = new JCheckBox("Filter");
		private JCheckBox inverseFilterCheckbox = new JCheckBox("Inverse");
		private int selected = -1;
		private DataColumnInfo selectedColumn;
		private JComboBox valueComboBox = new JComboBox();
		
		private ViewerGLJPanel viewer;
		
		public JDataColumnControlPanel() {
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
			
			filterCheckbox.setSelected(false);
			this.add(new JLabel("Min."), gbc);gbc.gridx++;
			this.add(new JLabel("Max."), gbc);
			gbc.gridx = 0; gbc.gridy++;
			this.add(lowerLimitSpinner, gbc);gbc.gridx++;
			this.add(upperLimitSpinner, gbc);
			gbc.gridx = 0; gbc.gridy++;
			gbc.gridwidth = 2;
			this.add(new JLabel("Auto adjust min/max"), gbc);
			gbc.gridx = 0; gbc.gridy++;
			gbc.gridwidth = 1;
			this.add(resetButton, gbc); gbc.gridx++;
			this.add(resetAllButton, gbc);
			gbc.gridx = 0; gbc.gridy++;
			gbc.gridwidth = 2;
			this.add(makeSymButton, gbc);
			gbc.gridx = 0; gbc.gridy++;
			gbc.gridwidth = 1;
			this.add(filterCheckbox, gbc);
			gbc.gridx++;
			this.add(inverseFilterCheckbox, gbc);
			gbc.gridx = 0; gbc.gridy++;
			this.validate();
			
			valueComboBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					selected = valueComboBox.getSelectedIndex();
					selectedColumn = (DataColumnInfo)valueComboBox.getSelectedItem();
					setSpinner();
					Configuration.setSelectedColumn(selectedColumn);
					viewer.updateAtoms();
				}
			});
			
			lowerLimitSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					selectedColumn.setLowerLimit(((Number)lowerLimitSpinner.getValue()).floatValue());
					viewer.updateAtoms();
				}
			});
			
			upperLimitSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					selectedColumn.setUpperLimit(((Number)upperLimitSpinner.getValue()).floatValue());
					viewer.updateAtoms();
				}
			});
			
			filterCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					Configuration.setFilterRange(filterCheckbox.isSelected());
					viewer.updateAtoms();
					inverseFilterCheckbox.setEnabled(filterCheckbox.isSelected());
				}
			});
			
			inverseFilterCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					Configuration.setFilterInversed(inverseFilterCheckbox.isSelected());
					viewer.updateAtoms();
				}
			});
			
			resetAllButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					selectedColumn.findRange(true);
					lowerLimitSpinner.setValue(selectedColumn.getLowerLimit());
					upperLimitSpinner.setValue(selectedColumn.getUpperLimit());
					viewer.updateAtoms();
				}
			});
			
			resetButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					selectedColumn.findRange(false);
					lowerLimitSpinner.setValue(selectedColumn.getLowerLimit());
					upperLimitSpinner.setValue(selectedColumn.getUpperLimit());
					viewer.updateAtoms();
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
					viewer.updateAtoms();
				}
			});
		}
		
		@Override
		public void setVisible(boolean aFlag) {
			super.setVisible(aFlag);
			this.filterCheckbox.setSelected(Configuration.isFilterRange());
			this.inverseFilterCheckbox.setSelected(Configuration.isFilterInversed());
			this.inverseFilterCheckbox.setEnabled(this.filterCheckbox.isSelected());
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
			if (selectedColumn!= null && Configuration.getSizeDataColumns()!=0) selectedColumn.findRange(false);
		}
		
		public void resetDropDown(){
			valueComboBox.removeAllItems();
			for (int i = 0; i<Configuration.getSizeDataColumns(); i++)
				if (!Configuration.getDataColumnInfo(i).isSpecialColoumn())
					valueComboBox.addItem(Configuration.getDataColumnInfo(i));
			if (selected > Configuration.getSizeDataColumns()){
				selected = 0;
				selectedColumn = (DataColumnInfo)valueComboBox.getItemAt(0);
				setSpinner();
			}
			else if (Configuration.getSizeDataColumns()>0){
				valueComboBox.setSelectedIndex(selected);
				selectedColumn = (DataColumnInfo)valueComboBox.getItemAt(selected);
				setSpinner();
			}
			Configuration.setSelectedColumn(selectedColumn);
		}
		
		public void setViewer(ViewerGLJPanel viewer){
			if (this.viewer==null) this.viewer = viewer;
		}
	}
	
}