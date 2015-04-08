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
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;

import javax.swing.*;

import model.io.ImdFileLoader;
import model.io.MDFileLoader;
import model.Configuration;
import model.ImportConfiguration;
import model.ImportConfiguration.ImportStates;

public class JMDFileChooser extends JFileChooser{
	private static final String CONF_FILE = "crystal.conf";
	private Window owner;
	
	private File propertiesFile;
	private static final long serialVersionUID = 1L;
	
	private JOpenOptionComponent components;
	
	protected ImportConfiguration importConfig; 
	
	private boolean confFileFound = false;
	
	public JMDFileChooser(MDFileLoader loader){
		importConfig = ImportConfiguration.getNewInstance();
		
		if (Configuration.RUN_AS_STICKWARE){
			propertiesFile = new File("viewer.conf");
		} else {
			String userHome = System.getProperty("user.home");
			File dir = new File(userHome+"/.AtomViewer");
			if (!dir.exists()) dir.mkdir();
			propertiesFile = new File(dir, "viewer.conf");
		}
		
		try {
			if (!propertiesFile.exists()) propertiesFile.createNewFile();
			importConfig.loadProperties(propertiesFile);
		} catch (IOException e){
			e.printStackTrace();
		}
		
		this.setFileFilter(loader.getDefaultFileFilter());
		
		this.setMultiSelectionEnabled(true);
		
		this.setFileHidingEnabled(true);
		
		//TODO Remove this ugly workaround in a future user interface
		this.components = new JOpenOptionComponent(loader instanceof ImdFileLoader);
		
		this.setAccessory(components);
		this.setPreferredSize(new Dimension(720,500));
		
		this.addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				if (JFileChooser.DIRECTORY_CHANGED_PROPERTY == evt.getPropertyName()){
					confFileFound = false;
					File confFile = new File(getCurrentDirectory(),CONF_FILE);
					if (confFile.exists()) {
						confFileFound = true;
						importConfig.readConfigurationFile(confFile);
					}
					
					components.revalidate();
				} else if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY == evt.getPropertyName()){
					components.editCrystalConfButton.setEnabled(getSelectedFile() != null);
				}
			}
		});
		
		this.firePropertyChange(JFileChooser.DIRECTORY_CHANGED_PROPERTY, null, this.getCurrentDirectory());
		
		this.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (arg0.getActionCommand().equals(JFileChooser.APPROVE_SELECTION)){
					if (!isConfFileFound()) {
						File selectedFile = JMDFileChooser.this.getSelectedFile();
						if (JMDFileChooser.this.getSelectedFiles().length >= 1){
							selectedFile = JMDFileChooser.this.getSelectedFiles()[0];
						}
						JCrystalConfigurationDialog ccd = 
								new JCrystalConfigurationDialog(JMDFileChooser.this.owner, 
										getCurrentDirectory(), selectedFile, false);
						if (ccd.isSavedSuccessfully()) {
							confFileFound = true;
							importConfig.readConfigurationFile(new File(getCurrentDirectory(),CONF_FILE));
						}
					}
				
					importConfig.saveProperties(propertiesFile);
				}
			}
		});

	}
	
	@Override
	protected JDialog createDialog(Component parent) throws HeadlessException {
		JDialog dialog = super.createDialog(parent);
		owner = dialog;
		return dialog;
	};
	
	public boolean createConfiguration(){
		if (!this.isConfFileFound()){
			JOptionPane.showMessageDialog(this.getParent(), "crystal.conf file not found or broken, aborting");
			return false;
		}
		
		return Configuration.create();
	}

	public boolean isConfFileFound() {
		return confFileFound;
	}
	
	private class JOpenOptionComponent extends JComponent {
		private static final long serialVersionUID = 1L;
		private final JButton editCrystalConfButton = new JButton("Edit crystal.conf");
		
		public JOpenOptionComponent(boolean showExtendedImportOptions) {
			JPanel p = new JPanel();
			JScrollPane sp = new JScrollPane(p);
			this.setLayout(new GridLayout(1,1));
			p.setLayout(new GridBagLayout());
			this.add(sp);
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 0.33;
			gbc.gridx = 0; gbc.gridy = 0;
			
			this.setPreferredSize(new Dimension(180, 80));
			
			final JCheckBox importedAtomTypeCheckbox = new JCheckBox("<html>Import atom types<br> from file</html>", ImportStates.IMPORT_ATOMTYPE.isActive());
			final JCheckBox disposeDefaultAtomsCheckBox = new JCheckBox("<html>Dispose perfect<br>lattice atoms</html>", ImportStates.DISPOSE_DEFAULT.isActive());
			final JCheckBox calculateRBVcheckBox = new JCheckBox("<html>Import<br>Burgers Vectors</html>", ImportStates.IMPORT_BURGERS_VECTORS.isActive());
			final JCheckBox identifyGrainsCheckBox = new JCheckBox("Import Grains", ImportStates.IMPORT_GRAINS.isActive());
			
			importedAtomTypeCheckbox.setToolTipText("If enable, atomic classification are read from file, if available.");
			disposeDefaultAtomsCheckBox.setToolTipText("Atoms at perfect lattice sites are not ignored to save memory.");
			calculateRBVcheckBox.setToolTipText("Import Burgers vectors from input file");
			editCrystalConfButton.setToolTipText("Configure the crystal structure and define imported values");
			
			editCrystalConfButton.setEnabled(false);
			
			JCheckBox xCheckBox= new JCheckBox("X");
			JCheckBox yCheckBox= new JCheckBox("Y");
			JCheckBox zCheckBox= new JCheckBox("Z");
			xCheckBox.setSelected(importConfig.getPeriodicBoundaryConditions()[0]); 
			yCheckBox.setSelected(importConfig.getPeriodicBoundaryConditions()[1]);
			zCheckBox.setSelected(importConfig.getPeriodicBoundaryConditions()[2]);
			
			gbc.gridwidth = 3;
			p.add(editCrystalConfButton, gbc);
			gbc.gridx = 0; gbc.gridy++;
			p.add(new JLabel("Periodic boundaries"), gbc); gbc.gridy++;
			p.add(new JLabel("<html><i>ignored if defined in file</i></html>"), gbc); gbc.gridy++;
			gbc.gridwidth = 1;
			p.add(xCheckBox, gbc); gbc.gridx = 1;
			p.add(yCheckBox, gbc); gbc.gridx = 2;
			p.add(zCheckBox, gbc); gbc.gridx = 0;
			gbc.gridy++;
			gbc.gridwidth = 3;
			p.add(new JSeparator(), gbc); gbc.gridy++;
			
			if (showExtendedImportOptions){
				p.add(importedAtomTypeCheckbox, gbc); gbc.gridy++;
				p.add(disposeDefaultAtomsCheckBox, gbc); gbc.gridy++;
				p.add(calculateRBVcheckBox, gbc); gbc.gridy++;
				p.add(identifyGrainsCheckBox, gbc); gbc.gridy++;
			}
			
			ActionListener simpleCheckBoxListener = new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					String command = e.getActionCommand();
					
					if (command.equals("importType"))
						ImportStates.IMPORT_ATOMTYPE.setState(((JCheckBox)e.getSource()).isSelected());
					else if (command.equals("importGrains"))
						ImportStates.IMPORT_GRAINS.setState(((JCheckBox)e.getSource()).isSelected());
					else if (command.equals("pbc_x"))
						importConfig.getPeriodicBoundaryConditions()[0] = ((JCheckBox)(e.getSource())).isSelected();
					else if (command.equals("pbc_y"))
						importConfig.getPeriodicBoundaryConditions()[1] = ((JCheckBox)(e.getSource())).isSelected();
					else if (command.equals("pbc_z"))
						importConfig.getPeriodicBoundaryConditions()[2] = ((JCheckBox)(e.getSource())).isSelected();
					else if (command.equals("disposeDefaultAtoms")){
						ImportStates.DISPOSE_DEFAULT.setState(((JCheckBox)e.getSource()).isSelected());
					} else if (command.equals("importRBV"))
						ImportStates.IMPORT_BURGERS_VECTORS.setState(((JCheckBox)e.getSource()).isSelected());
				}
			};
			
			disposeDefaultAtomsCheckBox.setActionCommand("disposeDefaultAtoms");
			disposeDefaultAtomsCheckBox.addActionListener(simpleCheckBoxListener);
			
			calculateRBVcheckBox.setActionCommand("importRBV");
			calculateRBVcheckBox.addActionListener(simpleCheckBoxListener);
			
			importedAtomTypeCheckbox.setActionCommand("importType");
			importedAtomTypeCheckbox.addActionListener(simpleCheckBoxListener);
			
			identifyGrainsCheckBox.setActionCommand("importGrains");
			identifyGrainsCheckBox.addActionListener(simpleCheckBoxListener);
			
			xCheckBox.setActionCommand("pbc_x");
			xCheckBox.addActionListener(simpleCheckBoxListener);
			yCheckBox.setActionCommand("pbc_y");
			yCheckBox.addActionListener(simpleCheckBoxListener);
			zCheckBox.setActionCommand("pbc_z");
			zCheckBox.addActionListener(simpleCheckBoxListener);
			
			editCrystalConfButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					File selectedFile = JMDFileChooser.this.getSelectedFile();
					if (JMDFileChooser.this.getSelectedFiles().length >= 1)
						selectedFile = JMDFileChooser.this.getSelectedFiles()[0];
					
					JCrystalConfigurationDialog ccd = 
							new JCrystalConfigurationDialog(JMDFileChooser.this.owner, 
									getCurrentDirectory(), selectedFile, true);
					if (ccd.isSavedSuccessfully()) {
						confFileFound = true;
						importConfig.readConfigurationFile(new File(getCurrentDirectory(),CONF_FILE));
					}
				}
			});
		}
	}
}
