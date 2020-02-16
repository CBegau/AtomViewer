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
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import model.Configuration;
import model.DataColumnInfo;
import model.DataColumnInfo.Component;
import common.CommonUtils;
import common.Vec3;
import crystalStructures.*;

public class JCrystalConfigurationDialog extends JDialog{
	private static final long serialVersionUID = 1L;

	private DecimalFormat decimalFormat = new DecimalFormat();
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
	private JFormattedTextField[] scalingFactorFields;
	private JCheckBox[] activeCheckBoxes;
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
		
		for (CrystalStructure cs : CrystalStructure.getCrystalStructures())
			crystalStructureComboBox.addItem(cs);
		
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
					readDataValueTable();
					writeConfigurationFile(folder);
					isSavedSuccessfully = true;
				} catch (IOException e1) {
					JOptionPane.showMessageDialog(null, "Error updating crystal.conf", "Error", JOptionPane.ERROR_MESSAGE);
				} finally {
					dispose();
				}
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
		
		
		String[][] h;
		boolean headerReadable = false;
		if (selectedFile != null){
			try {
				h = Configuration.getCurrentFileLoader().getColumnNamesUnitsFromHeader(selectedFile);
				headerReadable = true;
			} catch (IOException e1) {
				h = new String[0][];
			}
		} else {
			h = new String[0][];
		}
		final String[][] valuesUnitsInHeader = h;
		
		String warningMessage = "";
		if (!headerReadable){
			warningMessage = "Could not parse values that can be read from file: ";
			if (selectedFile == null) warningMessage += "No file selected";
			else warningMessage += "Header seems corrupted";
			this.add(new JLabel(warningMessage), gbc); gbc.gridy++;
		}
		final String warning = warningMessage; 
		
		createTable(valuesUnitsInHeader, warning);
		JScrollPane sp = new JScrollPane(tableContainer);
		sp.setMinimumSize(new Dimension(600,300));
		sp.setPreferredSize(new Dimension(600,300));
		sp.setBorder(new TitledBorder(new EtchedBorder(1), "Import Raw Values"));
		gbc.gridwidth = 2;
		this.add(sp, gbc); gbc.gridy++;
		
		createCrystalPropertiesContainer();
		sp = new JScrollPane(crystalPropertiesContainer);
		sp.setMinimumSize(new Dimension(600,300));
		sp.setPreferredSize(new Dimension(600,300));
		gbc.gridwidth = 2;
		this.add(sp, gbc); gbc.gridy++;	
		
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
	
	private void createTable(String[][] valuesUnitsFromHeader, String warning){
		ArrayList<JTextField> nameTextFieldsList = new ArrayList<JTextField>();
		ArrayList<JTextField> idTextFieldsList =  new ArrayList<JTextField>();
		ArrayList<JTextField> unitTextFieldsList =  new ArrayList<JTextField>();
		ArrayList<JFormattedTextField> scalingFactorFieldsList =  new ArrayList<JFormattedTextField>();
		ArrayList<JCheckBox> activeCheckBoxesList = new ArrayList<JCheckBox>();
		ArrayList<JComponentComboBox> componentComboBox = new ArrayList<JComponentComboBox>();
		
		tableContainer.removeAll();
		tableContainer.setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		
		gbc.fill = GridBagConstraints.BOTH;
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.gridx = 0; gbc.gridy = 0;
		gbc.weightx = 1;
		
		if (!warning.isEmpty()){
			gbc.gridwidth = 5;
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
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridy++; gbc.gridx = 0;
		
		List<String> values = new ArrayList<String>();
		List<String> units = new ArrayList<String>();
		if (valuesUnitsFromHeader.length != 0){
			for (String[] vu : valuesUnitsFromHeader){
				values.add(vu[0]);
				units.add(vu[1]);
			}
		}
		
		boolean[] inHeader = new boolean[values.size()];
		
		Map<String, Component> nameToComponentMap = Configuration.getCurrentFileLoader().getDefaultNamesForComponents();
		
		for (int i=0; i<t.dataColumns.size(); i++){
			int index = values.indexOf(t.dataColumns.get(i).getId());
			if (index != -1)
				inHeader[index] = true;
			
			nameTextFieldsList.add(new JTextField(t.dataColumns.get(i).getName()));
			JTextField id = new JTextField(t.dataColumns.get(i).getId());
			id.setEditable(false);
			idTextFieldsList.add(id);
			unitTextFieldsList.add(new JTextField(t.dataColumns.get(i).getUnit()));
			
			JFormattedTextField ftf = new JFormattedTextField("#.########");
			ftf.setValue(t.dataColumns.get(i).getScalingFactor());
			scalingFactorFieldsList.add(ftf);
			activeCheckBoxesList.add(new JCheckBox("", true));

			JComponentComboBox box = new JComponentComboBox();
			Component selectedComponent = t.dataColumns.get(i).getComponent(); 
			if (selectedComponent == Component.OTHER){
				if (nameToComponentMap.containsKey(t.dataColumns.get(i).getId()))
					selectedComponent = nameToComponentMap.get(t.dataColumns.get(i).getId());
			}
			box.setSelectedItem(selectedComponent);
			
			componentComboBox.add(box);
		}
		for (int i=0; i < values.size(); i++){
			if (inHeader[i] == true) continue;

			nameTextFieldsList.add(new JTextField(values.get(i)));
			JTextField id = new JTextField(values.get(i));
			id.setEditable(false);
			idTextFieldsList.add(id);
			unitTextFieldsList.add(new JTextField(units.get(i)));
			JFormattedTextField ftf = new JFormattedTextField(new DecimalFormat("#.########"));
			ftf.setValue(1);
			scalingFactorFieldsList.add(ftf);
			activeCheckBoxesList.add(new JCheckBox("", false));
			JComponentComboBox box = new JComponentComboBox();
			Component selectedComponent = Component.OTHER; 
			if (nameToComponentMap.containsKey(values.get(i)))
				selectedComponent = nameToComponentMap.get(values.get(i));
			box.setSelectedItem(selectedComponent);
			
			componentComboBox.add(box);
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
			gbc.gridx = 0; gbc.gridy++;
		}
	
		gbc.gridwidth = 5;
		if (size==0)
			tableContainer.add(new JLabel("No extra columns in file"), gbc);
		tableContainer.invalidate();
		
		nameTextFields = nameTextFieldsList.toArray(new JTextField[size]);
		idTextFields = idTextFieldsList.toArray(new JTextField[size]);
		unitTextFields = unitTextFieldsList.toArray(new JTextField[size]);
		scalingFactorFields = scalingFactorFieldsList.toArray(new JFormattedTextField[size]);
		activeCheckBoxes = activeCheckBoxesList.toArray(new JCheckBox[size]);
		componentComboBoxes = componentComboBox.toArray(new JComponentComboBox[size]);
		
		this.validate();
	}
	
	private void createCrystalPropertiesContainer(){
		crystalPropertiesContainer.removeAll();
		crystalPropertiesContainer.setLayout(new GridLayout(1,1));
		crystalPropertiesContainer.add(
				CrystalStructureProperties.createPropertyContainer(crystalStructure.getCrystalProperties()));
		crystalPropertiesContainer.invalidate();
		this.validate();
	}
	
	private void readDataValueTable(){
		t.dataColumns.clear();
		
		for (int i=0; i<nameTextFields.length; i++){
			if (activeCheckBoxes[i].isSelected()){
				String name = nameTextFields[i].getText();
				String id = idTextFields[i].getText();
				String unit = unitTextFields[i].getText();
				float scaling = ((Number)scalingFactorFields[i].getValue()).floatValue();

				DataColumnInfo cci = new DataColumnInfo(name, id, unit);
				cci.setScalingFactor(scaling);
				cci.setComponent((DataColumnInfo.Component)componentComboBoxes[i].getSelectedItem());
				t.dataColumns.add(cci);
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
				} else if (parts[0].toLowerCase().equals("import_column")){
					if (parts.length>=6){
						DataColumnInfo cci = new DataColumnInfo(parts[1], parts[2], parts[3]);
						
						for (DataColumnInfo.Component c : DataColumnInfo.Component.values()){
							if (c.name().equals(parts[5]))
								cci.setComponent(c);
						}
						cci.setScalingFactor(Float.parseFloat(parts[4]));
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
			float[] x = new float[3];
			float[] y = new float[3];
			
			x[0] = ((Number) crystalOrientation[0][0].getValue()).floatValue();
			x[1] = ((Number) crystalOrientation[0][1].getValue()).floatValue();
			x[2] = ((Number) crystalOrientation[0][2].getValue()).floatValue();
			
			y[0] = ((Number) crystalOrientation[1][0].getValue()).floatValue();
			y[1] = ((Number) crystalOrientation[1][1].getValue()).floatValue();
			y[2] = ((Number) crystalOrientation[1][2].getValue()).floatValue();
			
			float[] z = new float[]{ x[1]*y[2]-x[2]*y[1],
					             x[2]*y[0]-x[0]*y[2],
					             x[0]*y[1]-x[1]*y[0]};
			
			//Handle the special case of even numbers, here values can be reduced
			boolean evenNumbers = true;
			for (int i=0; i<3; i++){
				float xi = (int)Math.round(x[i]);
				if (Math.abs(xi-x[i]) > 1e-5f) evenNumbers = false;
				float yi = (int)Math.round(y[i]);
				if (Math.abs(yi-y[i]) > 1e-5f) evenNumbers = false;
			}
			if (evenNumbers){
				int[] zint = {(int)Math.round(z[0]), (int)Math.round(z[1]), (int)Math.round(z[2])};
				int gcd = CommonUtils.greatestCommonDivider(zint);
				if (gcd != 0){
					zint[0] /= gcd; zint[1] /= gcd; zint[2] /= gcd;
				}
				z[0] = zint[0];
				z[1] = zint[1];
				z[2] = zint[2];
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
		if (!f.canWrite()) 
			throw new IOException("crystal.conf could not be written");
		
		PrintWriter pw = new PrintWriter(f);
		
		Vec3 l = new Vec3();
		l.x = ((Number) crystalOrientation[0][0].getValue()).floatValue();
		l.y = ((Number) crystalOrientation[0][1].getValue()).floatValue();
		l.z = ((Number) crystalOrientation[0][2].getValue()).floatValue();
		pw.println(String.format("orientation_x %f %f %f", l.x,l.y, l.z));
		
		l.x = ((Number) crystalOrientation[1][0].getValue()).floatValue();
		l.y = ((Number) crystalOrientation[1][1].getValue()).floatValue();
		l.z = ((Number) crystalOrientation[1][2].getValue()).floatValue();
		pw.println(String.format("orientation_y %f %f %f", l.x,l.y, l.z));
		
		l.x = ((Number) crystalOrientation[2][0].getValue()).floatValue();
		l.y = ((Number) crystalOrientation[2][1].getValue()).floatValue();
		l.z = ((Number) crystalOrientation[2][2].getValue()).floatValue();
		pw.println(String.format("orientation_z %f %f %f", l.x,l.y, l.z));
		
		CrystalStructure cs = (CrystalStructure)crystalStructureComboBox.getSelectedItem();
		
		pw.println("structure\t"+cs.toString().toLowerCase());
		
		float latticeConst = ((Number) latticeConstTextField.getValue()).floatValue();
		if (latticeConst<0f) latticeConst = -latticeConst;
		
		float nnbDist = cs.getDefaultNearestNeighborSearchScaleFactor() * latticeConst;
		
		pw.println(String.format("latticeconst %.4f", latticeConst));
		pw.println(String.format("# Modify slighty if needed"));
		pw.println(String.format("nearestneighcutoff %.4f", nnbDist));
		
		for (int i=0; i<t.dataColumns.size(); i++){
			DataColumnInfo c = t.dataColumns.get(i);
			pw.println(String.format("import_column %s %s %s %e %s", c.getName(), c.getId(), c.getUnit().isEmpty()?"-":c.getUnit(),
					c.getScalingFactor(), c.getComponent().name()));
		}
		
		CrystalStructureProperties.storeProperties(crystalStructure.getCrystalProperties(), pw);
		
		pw.close();	
	}
	
	public static class CrystalConfContent{
		Vec3[] orientation;
		CrystalStructure cs;
		ArrayList<DataColumnInfo> dataColumns;
		public CrystalConfContent(Vec3[] orientation, CrystalStructure cs, ArrayList<DataColumnInfo> rawColumns) {
			this.orientation = orientation;
			this.cs = cs;
			this.dataColumns = rawColumns;
		}
		
		public CrystalStructure getCrystalStructure() {
			return cs;
		}
		
		public Vec3[] getOrientation() {
			return orientation;
		}
		
		public ArrayList<DataColumnInfo> getRawColumns() {
			return dataColumns;
		}
	}
	
	private static class JComponentComboBox extends JComboBox{
		private static final long serialVersionUID = 1L;

		public JComponentComboBox() {
			for(DataColumnInfo.Component c : DataColumnInfo.Component.values())
				if (c.isVisibleOption())
					this.addItem(c);
			
			this.setSelectedItem(DataColumnInfo.Component.OTHER);
		}
	}
}
