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

package crystalStructures;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Properties;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;


public class CrystalStructureProperties {
	
	public static JPanel createPropertyContainer(Collection<CrystalProperty> cp){
		JPanel configPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridx = 0; gbc.gridy = 0;
		gbc.weightx = 1;
		
		configPanel.setBorder(new TitledBorder(new EtchedBorder(1), "Crystal structure options"));
		for (final CrystalProperty c : cp){
			gbc.gridwidth = 2;
			JLabel label = new JLabel(c.label);
			label.setToolTipText(c.tooltip);
			configPanel.add(label, gbc); gbc.gridy++;
			gbc.gridwidth = GridBagConstraints.RELATIVE;
			gbc.weightx = 3;
			configPanel.add(c.getEditor(), gbc);  gbc.gridx++;
			JButton reset = new JButton("default");
			reset.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					c.setToDefault();
				}
			});
			gbc.weightx = 1;
			gbc.gridwidth = GridBagConstraints.REMAINDER;
			configPanel.add(reset, gbc);  gbc.gridx = 0;
			gbc.gridy++;
		}
		return configPanel;
	}
	
	public static void readProperties(Collection<CrystalProperty> cp, Reader r) throws IOException{
		Properties prop = new Properties();
		prop.load(r);
		for (CrystalProperty c : cp){
			c.load(prop);
		}
	}
	
	public static void storeProperties(Collection<CrystalProperty> cp, Writer w) throws IOException{
		Properties prop = new Properties();
		for (CrystalProperty c : cp){
			c.save(prop);
		}
		prop.store(w, "CrystalStructureOptions");
	}
	
	public static abstract class CrystalProperty{
		protected String id, label, tooltip;
		
		public CrystalProperty(String id, String label) {
			this(id, label, "");
		}
		
		public CrystalProperty(String id, String label, String tooltip) {
			this.id = id;
			this.label = label;
			this.tooltip = tooltip;
		}
		
		public abstract JComponent getEditor();
		
		public String getLabel(){
			return label;
		}
		
		protected abstract void setToDefault();
		protected abstract void save(Properties prop);
		protected abstract void load(Properties prop);
	}
	
	public static class IntegerCrystalProperty extends CrystalProperty{
		int value, defaultValue;
		int max, min;
		JSpinner valueSpinner;
		
		public IntegerCrystalProperty(String id, String label, int defaultValue, int min, int max) {
			this(id, label, "", defaultValue, min, max);
		}
		
		public IntegerCrystalProperty(String id, String label, String tooltip, int defaultValue, int min, int max) {
			super(id, label, tooltip);
			this.min = min;
			this.max = max;
			this.value = defaultValue;
			this.defaultValue = defaultValue;
			assert (min>value  || max<value);
		}
		
		@Override
		protected void save(Properties prop) {
			prop.setProperty(id, Integer.toString(value));
		}
		
		@Override
		protected void load(Properties prop) {
			String s = prop.getProperty(id, Integer.toString(value));
			value = Integer.parseInt(s);
		}
		
		@Override
		public JComponent getEditor() {
			valueSpinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
			valueSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent arg0) {
					value = ((Integer)(((JSpinner)arg0.getSource()).getValue())).intValue();
				}
			});
			return valueSpinner;
		}
		
		
		@Override
		protected void setToDefault() {
			this.value = defaultValue;
			if (valueSpinner!=null){
				valueSpinner.setValue(this.value);
			}
		}
		
		public void setDefaultValue(int defaultValue) {
			this.defaultValue = defaultValue;
		}
	}
	
	public static class FloatCrystalProperty extends CrystalProperty{
		float value, defaultValue;
		float max, min;
		JSpinner valueSpinner;
		
		public FloatCrystalProperty(String id, String label,float defaultValue, float min, float max) {
			this(id, label,"", defaultValue, min, max);
		}
		
		public FloatCrystalProperty(String id, String label, String tooltip, float defaultValue, float min, float max) {
			super(id, label, tooltip);
			this.min = min;
			this.max = max;
			this.value = defaultValue;
			this.defaultValue = defaultValue;
			assert (min<=value && max>=value);
		}
		
		public float getValue() {
			return value;
		}
		
		@Override
		protected void save(Properties prop) {
			prop.setProperty(id, Float.toString(value));
		}
		
		@Override
		protected void load(Properties prop) {
			String s = prop.getProperty(id, Float.toString(value));
			value = Float.parseFloat(s);
		}
		
		@Override
		public JComponent getEditor() {
			valueSpinner = new JSpinner(new SpinnerNumberModel(value, min, max, (max-min)/1000.));
			valueSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent arg0) {
					value = ((Double)(((JSpinner)arg0.getSource()).getValue())).floatValue();
				}
			});
			return valueSpinner;
		}
		
		@Override
		protected void setToDefault() {
			this.value = defaultValue;
			if (valueSpinner!=null){
				valueSpinner.setValue(new Double((double)this.value));
			}
		}
		
		public void setDefaultValue(float defaultValue) {
			this.defaultValue = defaultValue;
		}
	}
	
	public static class BooleanCrystalProperty extends CrystalProperty{
		boolean value, defaultValue;
		JCheckBox valueCheckbox;
		
		public BooleanCrystalProperty(String id, String label, boolean defaultValue) {
			this(id, label, "", defaultValue);
		}
		
		public BooleanCrystalProperty(String id, String label, String tooltip, boolean defaultValue) {
			super(id, label, tooltip);
			this.value = defaultValue;
			this.defaultValue = defaultValue;
		}
			
		public boolean getValue(){
			return value;
		}
		
		@Override
		protected void save(Properties prop) {
			prop.setProperty(id, Boolean.toString(value));
		}
		
		@Override
		protected void load(Properties prop) {
			String s = prop.getProperty(id, Boolean.toString(value));
			value = Boolean.parseBoolean(s);
		}
		
		@Override
		public JComponent getEditor() {
			valueCheckbox = new JCheckBox(); 
			valueCheckbox.setSelected(value);
			valueCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					value = ((JCheckBox)arg0.getSource()).isSelected();
				}
			});
			
			return valueCheckbox;
		}
		
		@Override
		protected void setToDefault() {
			this.value = this.defaultValue;
			valueCheckbox.setSelected(this.value);
		}
		
		public void setDefaultValue(boolean defaultValue) {
			this.defaultValue = defaultValue;
		}
	}
}
