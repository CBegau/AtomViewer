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
import javax.swing.event.*;
import javax.swing.filechooser.FileFilter;

import crystalStructures.B2NiTi;

import model.io.MDFileLoader.InputFormat;
import model.Configuration;
import model.ImportStates;

public class JMDFileChooser extends JFileChooser{
	private static final String CONF_FILE = "crystal.conf";
	private Window owner;
	
	private File propertiesFile;
	private static final long serialVersionUID = 1L;
	
	private JOpenOptionComponent components;
		
	private boolean confFileFound = false;
	private InputFormat format = InputFormat.IMD;
	
	private FileFilter imdFileFilterBasic = new FileFilter() {
		@Override
		public String getDescription() {
			return "IMD files (*.chkpt, *.ada, *.ss)";
		}
		
		@Override
		public boolean accept(File f) {
			if (f.isDirectory()) return true;
			String name = f.getName();
			if (name.endsWith(".ada") || name.endsWith(".chkpt") || name.endsWith(".ss") 
					|| name.endsWith(".ada.gz") || name.endsWith(".chkpt.gz") || name.endsWith(".ss.gz")
					|| name.endsWith(".chkpt.head") || name.endsWith(".chkpt.head.gz") 
					|| name.endsWith(".ada.head") || name.endsWith(".ada.head.gz")
					|| name.endsWith(".ss.head") || name.endsWith(".ss.head.gz")){
				return true;
			}
			return false;
		}
	};
	
	private FileFilter lammpsFileFilterBasic = new FileFilter() {
		@Override
		public String getDescription() {
			return "Lammps file (*.dump)";
		}
		
		@Override
		public boolean accept(File f) {
			if (f.isDirectory()) return true;
			String name = f.getName();
			if (name.endsWith(".dump") || name.endsWith(".dump.gz")){
				return true;
			}
			return false;
		}
	};
	
	private FileFilter imdFileFilterSequence = new FileFilter() {
		@Override
		public String getDescription() {
			return "Sequence of IMD files (*.xxxxx.chkpt,*.xxxxx.ss, *.xxxxx.ada)";
		}
		
		@Override
		public boolean accept(File f) {
			if (f.isDirectory()) return true;
			String name = f.getName();
			if (name.endsWith(".ada") || name.endsWith(".chkpt") || name.endsWith(".ss")
					|| name.endsWith(".chkpt.head") || name.endsWith(".ss.head")){
				String[] parts = name.split("\\.");
				int multiFile = name.endsWith(".head") ? 1 : 0;
				if (parts.length < 3+multiFile) return false;
				try {
					Integer.parseInt(parts[parts.length-2-multiFile]);
				} catch (NumberFormatException e){
					return false;
				}
				return true;
			} else if (name.endsWith(".ada.gz") || name.endsWith(".chkpt.gz") || name.endsWith(".ss.gz")
					|| name.endsWith(".chkpt.head.gz") || name.endsWith(".ss.head.gz")){
				String[] parts = name.split("\\.");
				int multiFile = name.endsWith(".head.gz") ? 1 : 0;
				if (parts.length < 4+multiFile) return false;
				try {
					Integer.parseInt(parts[parts.length-3-multiFile]);
				} catch (NumberFormatException e){
					return false;
				}
				return true;
			}
			return false;
		}
	};
	
	public JMDFileChooser(InputFormat format){
		this.format = format;
		
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
			ImportStates.loadProperties(propertiesFile);
		} catch (IOException e){
			e.printStackTrace();
		}
		
		if (format == InputFormat.IMD) {
			if (ImportStates.isImportSequence()) this.setFileFilter(imdFileFilterSequence);
			else this.setFileFilter(imdFileFilterBasic);
			this.setMultiSelectionEnabled(!ImportStates.isImportSequence());
		}
		else if (format == InputFormat.LAMMPS) { 
			this.setFileFilter(lammpsFileFilterBasic);
			this.setMultiSelectionEnabled(true);
		}
		
		this.setFileHidingEnabled(true);
		this.components = new JOpenOptionComponent();
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
						ImportStates.readConfigurationFile(confFile);
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
							ImportStates.readConfigurationFile(new File(getCurrentDirectory(),CONF_FILE));
						}
					}
				
					ImportStates.saveProperties(propertiesFile);
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
		private final JCheckBox detectVariantsCheckBox = new JCheckBox("Martensite variants", ImportStates.DETECT_MARTENSITE_VARIANTS.isActive());
		private final JButton   editCrystalConfButton = new JButton("Edit crystal.conf");
		
		public JOpenOptionComponent() {
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
			
			final JCheckBox overrideImportedCheckbox = new JCheckBox("<html>Override values<br> from file</html>", ImportStates.OVERRIDE.isActive());
			final JCheckBox killAllCheckBox = new JCheckBox("Remove all atoms", ImportStates.KILL_ALL_ATOMS.isActive());
			final JCheckBox disposeDefaultAtomsCheckBox = new JCheckBox("<html>Dispose perfect<br>lattice atoms</html>", ImportStates.DISPOSE_DEFAULT.isActive());
			final JCheckBox calculateRBVcheckBox = new JCheckBox("Burgers Vectors", ImportStates.BURGERS_VECTORS.isActive());
			final JCheckBox filterSurfaceCheckBox = new JCheckBox("Filter Surface", ImportStates.FILTER_SURFACE.isActive());
			final JCheckBox autoSkeletonizeCheckBox = new JCheckBox("Dislocation Network", ImportStates.SKELETONIZE.isActive());
			final JCheckBox calculateLatticeRotationCheckBox = new JCheckBox("Lattice rotation", ImportStates.LATTICE_ROTATION.isActive());
			final JCheckBox identifyGrainsCheckBox = new JCheckBox("Identify Grains", ImportStates.POLY_MATERIAL.isActive());
			final JCheckBox energyDislocationCheckBox = new JCheckBox("Energy/GND analysis", ImportStates.ENERGY_GND_ANALYSIS.isActive());
			
			//Tooltips
			overrideImportedCheckbox.setToolTipText("If enable, values like atomic classification are not read from file, but are recomputed.");
			overrideImportedCheckbox.setBorderPainted(true);
			
			disposeDefaultAtomsCheckBox.setToolTipText("Atoms at perfect lattice sites are not ignored to save memory.");
			if (Configuration.Options.SIMPLE.isEnabled()){
				calculateRBVcheckBox.setToolTipText("Perform Burgers vector analysis & create dislocation networks");
			}
			filterSurfaceCheckBox.setToolTipText("Atoms neighboring the free surface are removed." +
					" May improve dislocation networks by reducing artefacts at the surface.");
			calculateLatticeRotationCheckBox.setToolTipText("Calculate local lattice rotations." +
					" At free surfaces and in severely distorted regions, no values can be calculated.");
			editCrystalConfButton.setToolTipText("Configure the crystal structure and define imported values");
			editCrystalConfButton.setEnabled(false);
			
			JCheckBox sequenceCheckBox = new JCheckBox("Import sequence", ImportStates.isImportSequence());
			final JSpinner filesInSequenceSpinner = new JSpinner(new SpinnerNumberModel(ImportStates.getFilesInSequence(), 1, 2000, 5));
			JCheckBox xCheckBox= new JCheckBox("X");
			JCheckBox yCheckBox= new JCheckBox("Y");
			JCheckBox zCheckBox= new JCheckBox("Z");
			xCheckBox.setSelected(ImportStates.getPeriodicBoundaryConditions()[0]); 
			yCheckBox.setSelected(ImportStates.getPeriodicBoundaryConditions()[1]);
			zCheckBox.setSelected(ImportStates.getPeriodicBoundaryConditions()[2]);
			filesInSequenceSpinner.setEnabled(ImportStates.isImportSequence());
			filesInSequenceSpinner.setMaximumSize(new Dimension(170, 16));
			
			gbc.gridwidth = 3;
			p.add(editCrystalConfButton, gbc);
			gbc.gridx = 0; gbc.gridy++;
			if (format == InputFormat.IMD){
				p.add(new JSeparator(), gbc); gbc.gridy++;
				p.add(sequenceCheckBox, gbc); gbc.gridy++;
				p.add(filesInSequenceSpinner, gbc); gbc.gridy++;
				p.add(new JLabel("Periodic boundaries"), gbc); gbc.gridy++;
			} else if (format == InputFormat.LAMMPS){
				p.add(new JLabel("Periodic boundaries"), gbc); gbc.gridy++;
				p.add(new JLabel("<html><i>ignored if defined <br> in file</i></html>"), gbc); gbc.gridy++;
			}
			
			gbc.gridwidth = 1;
			p.add(xCheckBox, gbc); gbc.gridx = 1;
			p.add(yCheckBox, gbc); gbc.gridx = 2;
			p.add(zCheckBox, gbc); gbc.gridx = 0;
			gbc.gridy++;
			gbc.gridwidth = 3;
			p.add(new JSeparator(), gbc); gbc.gridy++;
			
			p.add(overrideImportedCheckbox, gbc); gbc.gridy++;
			p.add(disposeDefaultAtomsCheckBox, gbc); gbc.gridy++;
			p.add(killAllCheckBox, gbc); gbc.gridy++;
			p.add(calculateRBVcheckBox, gbc); gbc.gridy++;
			if (!Configuration.Options.SIMPLE.isEnabled()) p.add(autoSkeletonizeCheckBox, gbc); gbc.gridy++;
			if (!Configuration.Options.SIMPLE.isEnabled()) p.add(identifyGrainsCheckBox, gbc); gbc.gridy++;
			p.add(filterSurfaceCheckBox, gbc); gbc.gridy++;
			p.add(calculateLatticeRotationCheckBox, gbc); gbc.gridy++;
			if (!Configuration.Options.SIMPLE.isEnabled()) 
				p.add(energyDislocationCheckBox, gbc); gbc.gridy++;
			if (!Configuration.Options.SIMPLE.isEnabled()) p.add(detectVariantsCheckBox, gbc); gbc.gridy++;
			detectVariantsCheckBox.setVisible(false);
			
			ActionListener simpleCheckBoxListener = new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					String command = e.getActionCommand();
					
					if (command.equals("filterSurface"))
						ImportStates.FILTER_SURFACE.setState(((JCheckBox)e.getSource()).isSelected());
					else if (command.equals("override"))
						ImportStates.OVERRIDE.setState(((JCheckBox)e.getSource()).isSelected());
					else if (command.equals("killAllAtoms"))
						ImportStates.KILL_ALL_ATOMS.setState(((JCheckBox)e.getSource()).isSelected());
					else if (command.equals("identifyGrains"))
						ImportStates.POLY_MATERIAL.setState(((JCheckBox)e.getSource()).isSelected());
					else if (command.equals("detectVariants"))
						ImportStates.DETECT_MARTENSITE_VARIANTS.setState(((JCheckBox)e.getSource()).isSelected());
					else if (command.equals("pbc_x"))
						ImportStates.getPeriodicBoundaryConditions()[0] = ((JCheckBox)(e.getSource())).isSelected();
					else if (command.equals("pbc_y"))
						ImportStates.getPeriodicBoundaryConditions()[1] = ((JCheckBox)(e.getSource())).isSelected();
					else if (command.equals("pbc_z"))
						ImportStates.getPeriodicBoundaryConditions()[2] = ((JCheckBox)(e.getSource())).isSelected();
					else if (command.equals("calculateRBV")){
						//Enable skeletonization together with Burgers vectors
						if (Configuration.Options.SIMPLE.isEnabled()) 
							ImportStates.SKELETONIZE.setState(((JCheckBox)e.getSource()).isSelected());
						ImportStates.BURGERS_VECTORS.setState(((JCheckBox)e.getSource()).isSelected());
					}
					else if (command.equals("disposeDefaultAtoms")){
						boolean state = ((JCheckBox)e.getSource()).isSelected();
						ImportStates.DISPOSE_DEFAULT.setState(state);
						if (state){
							calculateLatticeRotationCheckBox.setSelected(false);
							ImportStates.LATTICE_ROTATION.setState(false);
						}
						
						ImportStates.DISPOSE_DEFAULT.setState(((JCheckBox)e.getSource()).isSelected());
					}
					else if (command.equals("importSequence")){
						ImportStates.setImportSequence(((JCheckBox)(e.getSource())).isSelected());
						JMDFileChooser.this.setMultiSelectionEnabled(!ImportStates.isImportSequence());
						if (ImportStates.isImportSequence()){
							JMDFileChooser.this.setSelectedFile(new File(""));
						}
						filesInSequenceSpinner.setEnabled(ImportStates.isImportSequence());
						JMDFileChooser.this.removeChoosableFileFilter(imdFileFilterBasic);
						JMDFileChooser.this.removeChoosableFileFilter(imdFileFilterSequence);
						
						if (ImportStates.isImportSequence()) JMDFileChooser.this.setFileFilter(imdFileFilterSequence);
						else JMDFileChooser.this.setFileFilter(imdFileFilterBasic);
					}
					else if (command.equals("autoSkeletonize"))
						ImportStates.SKELETONIZE.setState(((JCheckBox)e.getSource()).isSelected());
					else if (command.equals("calculateLatticeRotation")){
						boolean state = ((JCheckBox)e.getSource()).isSelected();
						ImportStates.LATTICE_ROTATION.setState(state);
						if (state){
							ImportStates.DISPOSE_DEFAULT.setState(false);
							disposeDefaultAtomsCheckBox.setSelected(false);
						}
					}
					else if (command.equals("energyGND"))
						ImportStates.ENERGY_GND_ANALYSIS.setState(((JCheckBox)e.getSource()).isSelected());
				}
			};

			calculateLatticeRotationCheckBox.setActionCommand("calculateLatticeRotation");
			calculateLatticeRotationCheckBox.addActionListener(simpleCheckBoxListener);
			
			autoSkeletonizeCheckBox.setActionCommand("autoSkeletonize");
			autoSkeletonizeCheckBox.addActionListener(simpleCheckBoxListener);
			
			sequenceCheckBox.setActionCommand("importSequence");
			sequenceCheckBox.addActionListener(simpleCheckBoxListener);
			
			disposeDefaultAtomsCheckBox.setActionCommand("disposeDefaultAtoms");
			disposeDefaultAtomsCheckBox.addActionListener(simpleCheckBoxListener);
			
			calculateRBVcheckBox.setActionCommand("calculateRBV");
			calculateRBVcheckBox.addActionListener(simpleCheckBoxListener);
			
			filterSurfaceCheckBox.setActionCommand("filterSurface");
			filterSurfaceCheckBox.addActionListener(simpleCheckBoxListener);
			
			overrideImportedCheckbox.setActionCommand("override");
			overrideImportedCheckbox.addActionListener(simpleCheckBoxListener);
			
			killAllCheckBox.setActionCommand("killAllAtoms");
			killAllCheckBox.addActionListener(simpleCheckBoxListener);
			
			identifyGrainsCheckBox.setActionCommand("identifyGrains");
			identifyGrainsCheckBox.addActionListener(simpleCheckBoxListener);
			
			detectVariantsCheckBox.setActionCommand("detectVariants");
			detectVariantsCheckBox.addActionListener(simpleCheckBoxListener);
			
			energyDislocationCheckBox.setActionCommand("energyGND");
			energyDislocationCheckBox.addActionListener(simpleCheckBoxListener);
			
			xCheckBox.setActionCommand("pbc_x");
			xCheckBox.addActionListener(simpleCheckBoxListener);
			yCheckBox.setActionCommand("pbc_y");
			yCheckBox.addActionListener(simpleCheckBoxListener);
			zCheckBox.setActionCommand("pbc_z");
			zCheckBox.addActionListener(simpleCheckBoxListener);
			
			filesInSequenceSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent arg0) {
					ImportStates.setFilesInSequence((Integer)(((JSpinner)arg0.getSource()).getValue()));
				}
			});
		
			
			if (Configuration.Options.SIMPLE.isEnabled()){
				ImportStates.DETECT_MARTENSITE_VARIANTS.setState(false);
				ImportStates.POLY_MATERIAL.setState(false);
				ImportStates.KILL_ALL_ATOMS.setState(false);
			}
			
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
						ImportStates.readConfigurationFile(new File(getCurrentDirectory(),CONF_FILE));
					}
				}
			});
		}
		
		@Override
		public void revalidate() {
			//TODO: Nasty workaround
			if (ImportStates.getCrystalStructure() != null && 
					ImportStates.getCrystalStructure() instanceof B2NiTi)
				detectVariantsCheckBox.setVisible(true);
			else ImportStates.DETECT_MARTENSITE_VARIANTS.setState(false);
			super.revalidate();
		}
	}
}
