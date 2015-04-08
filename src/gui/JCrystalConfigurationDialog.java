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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import model.Configuration;
import model.DataColumnInfo;
import common.CommonUtils;
import common.Vec3;
import crystalStructures.*;

public class JCrystalConfigurationDialog extends JDialog{
	private static final long serialVersionUID = 1L;

	private DecimalFormat decimalFormat = new DecimalFormat("0.###");
	private JFormattedTextField latticeConstTextField = new JFormattedTextField(decimalFormat);
	private JFormattedTextField[][] crystalOrientation = new JFormattedTextField[3][3];
	private JComboBox crystalStructureComboBox = new JComboBox();
	private CrystalStructure crystalStructure;
	private JButton okButton = new JButton("OK");
	private JButton cancelButton = new JButton("cancel");
	private Container tableContainer = new Container();
	private Container crystalPropertiesContainer = new Container();
	private CrystalConfContent t;
	
	private JTextField[] nameTextFields, idTextFields, unitTextFields;
	private JFormattedTextField[] scalingFactorFields, averagingRadiusFields;
	private JCheckBox[] activeCheckBoxes, averageCheckBoxes;
	private JComponentComboBox[] componentComboBoxes;
	
	private boolean isSavedSuccessfully = false;
	
	public JCrystalConfigurationDialog(final File folder, final File selectedFile, boolean showCancelButton){
		createDialog(folder, selectedFile, showCancelButton);
	}
	
	public JCrystalConfigurationDialog(Window owner, final File folder, final File selectedFile, boolean showCancelButton){
		super(owner);
		createDialog(folder, selectedFile, showCancelButton);
	}
	
	private void createDialog(final File folder, final File selectedFile, boolean showCancelButton){
		this.setTitle("Crystal configuration");
		this.setLayout(new GridBagLayout());
		this.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0; gbc.gridy = 0;
		gbc.weightx = 1;
		
		this.add(new JLabel("Crystal orientation"), gbc); gbc.gridy++;
		Container c = new Container();
		c.setLayout(new GridLayout(3,4));
		
		CrystalOrientationListener col = new CrystalOrientationListener();
		
		for (int i=0; i<3; i++){
			if (i == 0) c.add(new Label("X-Axis"));
			if (i == 1) c.add(new Label("Y-Axis"));
			if (i == 2) c.add(new Label("Z-Axis"));
			for (int j=0; j<3; j++){
				crystalOrientation[i][j] = new JFormattedTextField(decimalFormat);
				c.add(crystalOrientation[i][j]);
				if (i == j) crystalOrientation[i][j].setValue(1);
				else crystalOrientation[i][j].setValue(0);
				if (i == 2) crystalOrientation[i][j].setEditable(false); 
				else {
					crystalOrientation[i][j].addActionListener(col);
					crystalOrientation[i][j].addPropertyChangeListener(col);
				}
			}
		}
		
		gbc.gridwidth = 2;
		this.add(c, gbc); gbc.gridy++;
		gbc.gridwidth = 1;
		
		this.add(new JLabel(" "), gbc); gbc.gridy++;
		this.add(new JLabel("Crystal structure"), gbc); gbc.gridx++;
		this.add(crystalStructureComboBox, gbc); gbc.gridx = 0; gbc.gridy++;
		
		if (Configuration.Options.SIMPLE.isEnabled()){
			crystalStructureComboBox.addItem(new FCCStructure());
			crystalStructureComboBox.addItem(new BCCStructure());
			crystalStructureComboBox.addItem(new B2());
			crystalStructureComboBox.addItem(new DiamondCubicStructure());
			crystalStructureComboBox.addItem(new UndefinedCrystalStructure());
		} else {
			for (CrystalStructure cs : CrystalStructure.getCrystalStructures())
				crystalStructureComboBox.addItem(cs);
		}
		
		this.add(new JLabel("Lattice constant"), gbc); gbc.gridx++;
		this.add(latticeConstTextField, gbc); gbc.gridy++;
		latticeConstTextField.setValue(3);
		gbc.gridx = 0;
		
		crystalStructureComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				crystalStructure = (CrystalStructure)crystalStructureComboBox.getSelectedItem();
				createCrystalPropertiesContainer();
			}
		});
		crystalStructureComboBox.setSelectedIndex(0);
		
		gbc.gridx = 0;
		gbc.gridy++;
		
		okButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
					readRawValueTable();
					writeConfigurationFile(folder);
				} catch (IOException e1) {
					JOptionPane.showMessageDialog(null, "Error writing crystal.conf", "Error", JOptionPane.ERROR_MESSAGE);
					dispose();
				}
				isSavedSuccessfully = true;
				dispose();
			}
		});
		
		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				isSavedSuccessfully = false;
				dispose();
			}
		});
		
		//Test if crystal.conf exists
		try {
			File conf = new File(folder, "crystal.conf");
			if (conf.exists()){
				t = readConfigurationFile(conf);
				if (t != null) {
					CrystalStructure cs = t.cs;
					Vec3[] o = t.orientation;
					
					crystalStructureComboBox.setSelectedIndex(0);
					for (int i = 0; i<crystalStructureComboBox.getItemCount(); i++){
						if (cs.toString().equalsIgnoreCase(crystalStructureComboBox.getItemAt(i).toString())){
							crystalStructureComboBox.setSelectedIndex(i);
							break;
						}
					}
					
					crystalOrientation[0][0].setValue(o[0].x);
					crystalOrientation[0][1].setValue(o[0].y);
					crystalOrientation[0][2].setValue(o[0].z);
					
					crystalOrientation[1][0].setValue(o[1].x);
					crystalOrientation[1][1].setValue(o[1].y);
					crystalOrientation[1][2].setValue(o[1].z);
					
					crystalOrientation[2][0].setValue(o[2].x);
					crystalOrientation[2][1].setValue(o[2].y);
					crystalOrientation[2][2].setValue(o[2].z);
					
					latticeConstTextField.setValue(cs.getLatticeConstant());
				}
				crystalStructure = t.cs;
			}
			if (t == null) t = new CrystalConfContent(new Vec3[]{new Vec3(1f,0f,0f),new Vec3(1f,1f,0f),new Vec3(0f,0f,1f)}, 
					new FCCStructure(), new ArrayList<DataColumnInfo>());
		} catch (IOException ex){
			
		}
		
		
		String[] h;
		boolean headerReadable = false;
		if (selectedFile != null){
			try {
				h = Configuration.currentFileLoader.getColumnsNamesFromHeader(selectedFile);
				headerReadable = true;
			} catch (IOException e1) {
				h = new String[0];
			}
		} else {
			h = new String[0];
		}
		final String[] valuesInHeader = h;
		
		String warningMessage = "";
		if (!headerReadable){
			warningMessage = "Could not parse values that can be read from file: ";
			if (selectedFile == null) warningMessage += "No file selected";
			else warningMessage += "Header seems corrupted";
			this.add(new JLabel(warningMessage), gbc); gbc.gridy++;
		}
		final String warning = warningMessage; 
		
		createTable(valuesInHeader, warning);
		JScrollPane sp = new JScrollPane(tableContainer);
		sp.setMinimumSize(new Dimension(600,300));
		sp.setPreferredSize(new Dimension(600,300));
		sp.setBorder(new TitledBorder(new EtchedBorder(1), "Import Raw Values"));
		gbc.gridwidth = 2;
		this.add(sp, gbc); gbc.gridy++;
		
		if (!Configuration.Options.SIMPLE.isEnabled()){
			createCrystalPropertiesContainer();
			sp = new JScrollPane(crystalPropertiesContainer);
			sp.setMinimumSize(new Dimension(600,300));
			sp.setPreferredSize(new Dimension(600,300));
			gbc.gridwidth = 2;
			this.add(sp, gbc); gbc.gridy++;
		}
		
		gbc.gridwidth = 1;
		
		gbc.gridx = 0;
		gbc.gridy++;
		
		if (!showCancelButton){
			gbc.gridwidth = 2;
			this.add(okButton, gbc);
		} else {
			this.add(okButton, gbc); gbc.gridx++;
			this.add(cancelButton, gbc);
		}
		
		this.pack();
		
		GraphicsDevice gd = this.getOwner().getGraphicsConfiguration().getDevice();
		this.setLocation( (gd.getDisplayMode().getWidth()-this.getWidth())>>1, 
				(gd.getDisplayMode().getHeight()-this.getHeight())>>1);
		this.setVisible(true);
	}
	
	
	
	private void createTable(String[] valuesFromHeader, String warning){
		ArrayList<JTextField> nameTextFieldsList = new ArrayList<JTextField>();
		ArrayList<JTextField> idTextFieldsList =  new ArrayList<JTextField>();
		ArrayList<JTextField> unitTextFieldsList =  new ArrayList<JTextField>();
		ArrayList<JFormattedTextField> scalingFactorFieldsList =  new ArrayList<JFormattedTextField>();
		ArrayList<JCheckBox> activeCheckBoxesList = new ArrayList<JCheckBox>();
		ArrayList<JCheckBox> averageCheckBoxesList = new ArrayList<JCheckBox>();
		ArrayList<JFormattedTextField> averagingRadiusFieldsList = new ArrayList<JFormattedTextField>();
		ArrayList<JComponentComboBox> componentComboBox = new ArrayList<JComponentComboBox>();
		
		tableContainer.removeAll();
		tableContainer.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		
		gbc.fill = GridBagConstraints.BOTH;
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.gridx = 0; gbc.gridy = 0;
		gbc.weightx = 1;
		
		if (!warning.isEmpty()){
			gbc.gridwidth = 7;
			this.add(new JLabel(warning), gbc); gbc.gridy++;
			gbc.gridwidth = 1;
		}

		gbc.fill = GridBagConstraints.NONE;
		tableContainer.add(new JLabel("Import"), gbc); gbc.gridx++;
		tableContainer.add(new JLabel("ID in file"), gbc); gbc.gridx++;
		tableContainer.add(new JLabel("Name"), gbc); gbc.gridx++;
		tableContainer.add(new JLabel("Unit"), gbc); gbc.gridx++;
		tableContainer.add(new JLabel("<html>Scaling<br>factor</html>"), gbc); gbc.gridx++;
		tableContainer.add(new JLabel("Data"), gbc); gbc.gridx++;
		tableContainer.add(new JLabel("<html>Compute<br>spatial<br>averages</html>"), gbc); gbc.gridx++;
		tableContainer.add(new JLabel("<html>Spatial<br>averaging<br>radius</html>"), gbc);
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridy++; gbc.gridx = 0;
		
		List<String> values = new ArrayList<String>();
		if (valuesFromHeader.length != 0)
			values = Arrays.asList(valuesFromHeader);
		
		boolean[] inHeader = new boolean[values.size()];
		
		for (int i=0; i<t.rawColumns.size(); i++){
			int index = values.indexOf(t.rawColumns.get(i).getId());
			if (index != -1)
				inHeader[index] = true;
			
			nameTextFieldsList.add(new JTextField(t.rawColumns.get(i).getName()));
			JTextField id = new JTextField(t.rawColumns.get(i).getId());
			id.setEditable(false);
			idTextFieldsList.add(id);
			unitTextFieldsList.add(new JTextField(t.rawColumns.get(i).getUnit()));
			
			JFormattedTextField ftf = new JFormattedTextField(NumberFormat.getNumberInstance());
			ftf.setValue(t.rawColumns.get(i).getScalingFactor());
			scalingFactorFieldsList.add(ftf);
			activeCheckBoxesList.add(new JCheckBox("", true));
			
			float averageValue = t.rawColumns.get(i).getSpatiallyAveragingRadius();
			averageCheckBoxesList.add(new JCheckBox("", averageValue>0f));
			ftf = new JFormattedTextField(NumberFormat.getNumberInstance());
			ftf.setValue(averageValue);
			averagingRadiusFieldsList.add(ftf);
			JComponentComboBox box = new JComponentComboBox();
			box.setSelectedItem(t.rawColumns.get(i).getComponent());
			componentComboBox.add(box);
		}
		for (int i=0; i < values.size(); i++){
			if (inHeader[i] == true) continue;

			nameTextFieldsList.add(new JTextField(values.get(i)));
			JTextField id = new JTextField(values.get(i));
			id.setEditable(false);
			idTextFieldsList.add(id);
			unitTextFieldsList.add(new JTextField(""));
			JFormattedTextField ftf = new JFormattedTextField(NumberFormat.getNumberInstance());
			ftf.setValue(1);
			scalingFactorFieldsList.add(ftf);
			activeCheckBoxesList.add(new JCheckBox("", false));
			averageCheckBoxesList.add(new JCheckBox("", false));
			ftf = new JFormattedTextField(NumberFormat.getNumberInstance());
			ftf.setValue(0f);
			averagingRadiusFieldsList.add(ftf);
			componentComboBox.add(new JComponentComboBox());
		}
		
		int size = nameTextFieldsList.size();
		for (int i=0; i<size; i++){
			gbc.fill = GridBagConstraints.NONE;
			tableContainer.add(activeCheckBoxesList.get(i), gbc); gbc.gridx++;
			gbc.fill = GridBagConstraints.BOTH;
			tableContainer.add(idTextFieldsList.get(i), gbc); gbc.gridx++;
			tableContainer.add(nameTextFieldsList.get(i), gbc); gbc.gridx++;
			tableContainer.add(unitTextFieldsList.get(i), gbc); gbc.gridx++;
			tableContainer.add(scalingFactorFieldsList.get(i), gbc); gbc.gridx++;
			tableContainer.add(componentComboBox.get(i), gbc); gbc.gridx++;
			gbc.fill = GridBagConstraints.NONE;
			tableContainer.add(averageCheckBoxesList.get(i), gbc); gbc.gridx++;
			gbc.fill = GridBagConstraints.BOTH;
			tableContainer.add(averagingRadiusFieldsList.get(i), gbc);
			gbc.gridx = 0; gbc.gridy++;
		}
	
		gbc.gridwidth = 7;
		if (size==0)
			tableContainer.add(new JLabel("No extra columns in file"), gbc);
		tableContainer.invalidate();
		
		nameTextFields = nameTextFieldsList.toArray(new JTextField[size]);
		idTextFields = idTextFieldsList.toArray(new JTextField[size]);
		unitTextFields = unitTextFieldsList.toArray(new JTextField[size]);
		scalingFactorFields = scalingFactorFieldsList.toArray(new JFormattedTextField[size]);
		activeCheckBoxes = activeCheckBoxesList.toArray(new JCheckBox[size]);
		averageCheckBoxes = averageCheckBoxesList.toArray(new JCheckBox[size]);
		averagingRadiusFields = averagingRadiusFieldsList.toArray(new JFormattedTextField[size]);
		componentComboBoxes = componentComboBox.toArray(new JComponentComboBox[size]);
		
		this.validate();
	}
	
	private void createCrystalPropertiesContainer(){
		crystalPropertiesContainer.removeAll();
		crystalPropertiesContainer.setLayout(new GridLayout(1,1));
		crystalPropertiesContainer.add(CrystalStructureProperties.createPropertyContainer(crystalStructure.getCrystalProperties()));
		crystalPropertiesContainer.invalidate();
		this.validate();
	}
	
	private void readRawValueTable(){
		t.rawColumns.clear();
		
		for (int i=0; i<nameTextFields.length; i++){
			if (activeCheckBoxes[i].isSelected()){
				String name = nameTextFields[i].getText();
				String id = idTextFields[i].getText();
				String unit = unitTextFields[i].getText();
				float scaling = ((Number)scalingFactorFields[i].getValue()).floatValue();
				float averagingRadius = 0f;
				if (averageCheckBoxes[i].isSelected())
					averagingRadius = ((Number)averagingRadiusFields[i].getValue()).floatValue();
				
				DataColumnInfo cci = new DataColumnInfo(name, id, unit, scaling, averagingRadius);
				cci.setComponent((DataColumnInfo.Component)componentComboBoxes[i].getSelectedItem());
				t.rawColumns.add(cci);
			}
		}
	}
	
	public static CrystalConfContent readConfigurationFile(File confFile) throws IOException{
		CrystalStructure cs;
		Vec3[] crystalOrientation = new Vec3[3];
		ArrayList<DataColumnInfo> dataColumns = new ArrayList<DataColumnInfo>(); 
		
		LineNumberReader lnr = new LineNumberReader(new FileReader(confFile));
		
		float lattice = 0f;
		float nnd = 0f;
		String struct = "fcc";
		
		boolean structureFound = false, latticeConstFound = false;
		boolean orientationXfound = false, orientationYfound = false, orientationZfound = false;
		
		Pattern p = Pattern.compile("[ \t]+");
		String line = lnr.readLine();
		while (line!=null && !line.startsWith("#CrystalStructureOptions")){
			line = line.trim();
			if (!line.isEmpty() && !line.startsWith("#")){
				String[] parts = p.split(line);
				
				if (parts[0].toLowerCase().equals("structure")){
					struct = parts[1].toLowerCase();
					structureFound = true;
				} else if (parts[0].toLowerCase().equals("latticeconst")){
					if (parts.length != 1){
						lattice = Float.parseFloat(parts[1]);
						latticeConstFound = true;
					}
				} else if (parts[0].toLowerCase().equals("nearestneighcutoff")){
					if (parts.length != 1) nnd = Float.parseFloat(parts[1]);
				} else if (parts[0].toLowerCase().equals("orientation_x")){
					if (parts.length>=4){
						crystalOrientation[0] = new Vec3(Float.parseFloat(parts[1]), 
								Float.parseFloat(parts[2]), 
								Float.parseFloat(parts[3]));
						orientationXfound = true;
					}
				} else if (parts[0].toLowerCase().equals("orientation_y")){
					if (parts.length>=4){
						crystalOrientation[1] = new Vec3(Float.parseFloat(parts[1]), 
								Float.parseFloat(parts[2]), 
								Float.parseFloat(parts[3]));
						orientationYfound = true;
					}
				} else if (parts[0].toLowerCase().equals("orientation_z")){
					if (parts.length>=4){
						crystalOrientation[2] = new Vec3(Float.parseFloat(parts[1]), 
								Float.parseFloat(parts[2]), 
								Float.parseFloat(parts[3]));
						orientationZfound = true;
						
						
					}
				} else if (parts[0].toLowerCase().equals("raw")){
					if (parts.length>=3){
						DataColumnInfo cci;
						if (parts.length>=6){
							cci = new DataColumnInfo(parts[1], parts[2], parts[3],
									Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
							if (parts.length>=7)
								for (DataColumnInfo.Component c : DataColumnInfo.Component.values()){
									if (c.name().equals(parts[6]))
										cci.setComponent(c);
								}
						}
						else if (parts.length>=6)
							cci = new DataColumnInfo(parts[1], parts[2], parts[3],
									Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
						else if (parts.length>=5)
							cci = new DataColumnInfo(parts[1], parts[2], parts[3], Float.parseFloat(parts[4]));
						else if (parts.length>=4)
							cci = new DataColumnInfo(parts[1], parts[2], parts[3], 1f);
						else cci = new DataColumnInfo(parts[1], parts[2], "", 1f);
						dataColumns.add(cci);
					}
				}
			}			
			
			line = lnr.readLine();
		}
			
		if (structureFound != true || latticeConstFound != true || 
				orientationXfound != true || orientationYfound != true || orientationZfound != true){
			lnr.close();
			return null;
		}
			
		
		cs = CrystalStructure.createCrystalStructure(struct, lattice, nnd);
		
		CrystalStructureProperties.readProperties(cs.getCrystalProperties(), lnr);
		
		lnr.close();
		return new CrystalConfContent(crystalOrientation, cs, dataColumns);
	}
	
	public boolean isSavedSuccessfully() {
		return isSavedSuccessfully;
	}
	
	private class CrystalOrientationListener implements ActionListener, PropertyChangeListener{
		@Override
		public void actionPerformed(ActionEvent arg0) {
			update();
		}
		
		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			update();
		}
		
		
		private void update(){
			int[] x = new int[3];
			int[] y = new int[3];
			
			x[0] = ((Number) crystalOrientation[0][0].getValue()).intValue();
			x[1] = ((Number) crystalOrientation[0][1].getValue()).intValue();
			x[2] = ((Number) crystalOrientation[0][2].getValue()).intValue();
			
			y[0] = ((Number) crystalOrientation[1][0].getValue()).intValue();
			y[1] = ((Number) crystalOrientation[1][1].getValue()).intValue();
			y[2] = ((Number) crystalOrientation[1][2].getValue()).intValue();
			
			int[] z = new int[]{ x[1]*y[2]-x[2]*y[1],
					             x[2]*y[0]-x[0]*y[2],
					             x[0]*y[1]-x[1]*y[0]};
			
			int gcd = CommonUtils.gcd(z);
			if (gcd != 0){
				z[0] /= gcd; z[1] /= gcd; z[2] /= gcd;
			}
			
			crystalOrientation[2][0].setValue(z[0]);
			crystalOrientation[2][1].setValue(z[1]);
			crystalOrientation[2][2].setValue(z[2]);
		}
	}
	
	private void writeConfigurationFile(File folder) throws IOException {
		File f = new File(folder, "crystal.conf");
		if (!f.exists()){
			boolean created = f.createNewFile();
			if (!created) return;
		}
		if (!f.canWrite()) return;
		PrintWriter pw = new PrintWriter(f);
		
		int[] l = new int[3];
		l[0] = ((Number) crystalOrientation[0][0].getValue()).intValue();
		l[1] = ((Number) crystalOrientation[0][1].getValue()).intValue();
		l[2] = ((Number) crystalOrientation[0][2].getValue()).intValue();
		int gcd = CommonUtils.gcd(l);
		if (gcd != 0){
			l[0] /= gcd; l[1] /= gcd; l[2] /= gcd;
		}
		pw.println(String.format("orientation_x %d %d %d", l[0],l[1], l[2]));
		
		l[0] = ((Number) crystalOrientation[1][0].getValue()).intValue();
		l[1] = ((Number) crystalOrientation[1][1].getValue()).intValue();
		l[2] = ((Number) crystalOrientation[1][2].getValue()).intValue();
		gcd = CommonUtils.gcd(l);
		if (gcd != 0){
			l[0] /= gcd; l[1] /= gcd; l[2] /= gcd;
		}
		pw.println(String.format("orientation_y %d %d %d", l[0],l[1], l[2]));
		
		l[0] = ((Number) crystalOrientation[2][0].getValue()).intValue();
		l[1] = ((Number) crystalOrientation[2][1].getValue()).intValue();
		l[2] = ((Number) crystalOrientation[2][2].getValue()).intValue();
		gcd = CommonUtils.gcd(l);
		if (gcd != 0){
			l[0] /= gcd; l[1] /= gcd; l[2] /= gcd;
		}
		pw.println(String.format("orientation_z %d %d %d", l[0],l[1], l[2]));
		
		CrystalStructure cs = (CrystalStructure)crystalStructureComboBox.getSelectedItem();
		
		pw.println("structure\t"+cs.toString().toLowerCase());
		
		float latticeConst = ((Number) latticeConstTextField.getValue()).floatValue();
		if (latticeConst<0f) latticeConst = -latticeConst;
		
		float nnbDist = cs.getDefaultNearestNeighborSearchScaleFactor() * latticeConst;
		
		pw.println(String.format("latticeconst %.4f", latticeConst));
		pw.println(String.format("# Modify slighty if needed"));
		pw.println(String.format("nearestneighcutoff %.4f", nnbDist));
		
		for (int i=0; i<t.rawColumns.size(); i++){
			DataColumnInfo c = t.rawColumns.get(i);
			if (!c.isSpecialColoumn()){
				if (c.isValueToBeSpatiallyAveraged()){
					pw.println(String.format("raw %s %s %s %f %f %s", c.getName(), c.getId(), c.getUnit().isEmpty()?"-":c.getUnit(),
							c.getScalingFactor(), c.getSpatiallyAveragingRadius(), c.getComponent().name()));
				} else {
					pw.println(String.format("raw %s %s %s %f 0. %s", c.getName(), c.getId(), c.getUnit().isEmpty()?"-":c.getUnit(),
							c.getScalingFactor(), c.getComponent().name()));
				}
			}
		}
		
		CrystalStructureProperties.storeProperties(crystalStructure.getCrystalProperties(), pw);
		
		pw.close();	
	}
	
	public static class CrystalConfContent{
		Vec3[] orientation;
		CrystalStructure cs;
		ArrayList<DataColumnInfo> rawColumns;
		public CrystalConfContent(Vec3[] orientation, CrystalStructure cs, ArrayList<DataColumnInfo> rawColumns) {
			this.orientation = orientation;
			this.cs = cs;
			this.rawColumns = rawColumns;
		}
		
		public CrystalStructure getCrystalStructure() {
			return cs;
		}
		
		public Vec3[] getOrientation() {
			return orientation;
		}
		
		public ArrayList<DataColumnInfo> getRawColumns() {
			return rawColumns;
		}
	}
	
	private static class JComponentComboBox extends JComboBox{
		private static final long serialVersionUID = 1L;

		public JComponentComboBox() {
			for(DataColumnInfo.Component c : DataColumnInfo.Component.values())
				if (c.isVisibleOption())
					this.addItem(c);
			
			this.setSelectedItem(DataColumnInfo.Component.UNDEFINED);
		}
	}
}
