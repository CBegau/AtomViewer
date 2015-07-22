package gui;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Properties;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import common.CommonUtils;

public abstract class PrimitiveProperty<T>{
	protected String id, label, tooltip;
	
	public static JPanel getControlPanelForProperty(final PrimitiveProperty<?> p, boolean addGlue, Window w){		
		JPanel propertyPanel = new JPanel();
		
		JLabel label1 = null;
		if (p.label != null && !p.label.isEmpty()){
			label1 = CommonUtils.getWordWrappedJLabel(p.label, w);
			label1.setToolTipText(p.tooltip);
			propertyPanel.setLayout(new GridLayout(2, 1));
			propertyPanel.add(label1);
		} else {
			propertyPanel.setLayout(new GridLayout(1, 1));
		}
		
		final JLabel label = label1;
		
		JPanel editorPanel = new JPanel();
		editorPanel.setLayout(new BoxLayout(editorPanel, BoxLayout.LINE_AXIS));
		
		editorPanel.add(p.getEditor());
		if (addGlue) editorPanel.add(Box.createHorizontalGlue());
		
		final JButton reset = new JButton("default");
		reset.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				p.setToDefault();
			}
		});
		
		reset.setAlignmentX(Component.RIGHT_ALIGNMENT);
		
		editorPanel.add(reset);
		propertyPanel.add(editorPanel);
		
		p.getEditor().addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				boolean e = p.getEditor().isEnabled();
				reset.setEnabled(e);
				if (label!=null) label.setEnabled(e);
			}
		});
		
		return propertyPanel;
	}
		
	protected PrimitiveProperty(String id, String label, String tooltip) {
		this.id = id;
		this.label = label;
		this.tooltip = tooltip;
	}
	
	public abstract JComponent getEditor();
	
	public abstract void save(Properties prop);
	public abstract void load(Properties prop);
	
	public String getLabel(){
		return label;
	}
	
	public abstract void setToDefault();
	
	public abstract T getValue();
	public abstract void setValue(T t);
	
	public void setEnabled(boolean enabled){
		this.getEditor().setEnabled(enabled);
	}
	
	public String getTooltip() {
		return tooltip;
	}
	
	public static class StringProperty extends PrimitiveProperty<String>{
		JTextField textField;
		String defaultText;
		
		public StringProperty(String id, String label, String tooltip, String defaultString) {
			super(id, label, tooltip);
			textField.setText(defaultString);
			textField.setEditable(true);
			this.defaultText = defaultString;
		}

		@Override
		public JComponent getEditor() {
			return textField;
		}

		@Override
		public void setToDefault() {
			this.textField.setText(defaultText);
		}

		public String getValue() {
			return textField.getText();
		}
		
		@Override
		public void setValue(String t) {
			textField.setText(t);
		}
		
		@Override
		public void save(Properties prop) {
			prop.setProperty(id, this.textField.getText());
		}
		
		public void setDefaultValue(String defaultValue) {
			this.defaultText = defaultValue;
		}
		
		@Override
		public void load(Properties prop) {
			String s = prop.getProperty(id, this.textField.getText());
			this.setDefaultValue(s);
			this.setToDefault();
		}
	}

	public static class IntegerProperty extends PrimitiveProperty<Integer>{
		int value, defaultValue;
		int max, min;
		JSpinner valueSpinner;
		
		public IntegerProperty(String id, String label, String tooltip, int defaultValue, int min, int max) {
			super(id, label, tooltip);
			this.min = min;
			this.max = max;
			this.value = defaultValue;
			this.defaultValue = defaultValue;
			assert (value>=min && value<=max);
			
			valueSpinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
			valueSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent arg0) {
					value = ((Integer)(((JSpinner)arg0.getSource()).getValue())).intValue();
				}
			});
		}
		
		@Override
		public JComponent getEditor() {
			return valueSpinner;
		}
		
		
		@Override
		public void setToDefault() {
			this.value = defaultValue;
			if (valueSpinner!=null){
				valueSpinner.setValue(this.value);
			}
		}
		
		public void setDefaultValue(int defaultValue) {
			this.defaultValue = defaultValue;
		}
		
		public Integer getValue() {
			return value;
		}
		
		@Override
		public void setValue(Integer t) {
			value = t;
			valueSpinner.setValue(t);
		}
		
		@Override
		public void save(Properties prop) {
			prop.setProperty(id, Integer.toString(this.getValue()));
		}
		
		@Override
		public void load(Properties prop) {
			String s = prop.getProperty(id, Integer.toString(this.getValue()));
			this.setDefaultValue(Integer.parseInt(s));
			this.setToDefault();
		}
	}

	public static class FloatProperty extends PrimitiveProperty<Float>{
		float value, defaultValue;
		float max, min;
		JSpinner valueSpinner;
		
		public FloatProperty(String id, String label, String tooltip, float defaultValue, float min, float max) {
			super(id, label, tooltip);
			this.min = min;
			this.max = max;
			this.value = defaultValue;
			this.defaultValue = defaultValue;
			assert (value>=min && value<=max);
			
			valueSpinner = new JSpinner(new SpinnerNumberModel(value, min, max, (max-min)/1000.));
			valueSpinner.addChangeListener(new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent arg0) {
					value = ((Double)(((JSpinner)arg0.getSource()).getValue())).floatValue();
				}
			});
		}
		
		@Override
		public Float getValue() {
			return value;
		}
		
		@Override
		public void setValue(Float t) {
			value = t;
			valueSpinner.setValue(t);
		}
		
		@Override
		public JComponent getEditor() {
			return valueSpinner;
		}
		
		@Override
		public  void setToDefault() {
			this.value = defaultValue;
			if (valueSpinner!=null){
				valueSpinner.setValue(new Double((double)this.value));
			}
		}
		
		public void setDefaultValue(float defaultValue) {
			this.defaultValue = defaultValue;
		}
		
		@Override
		public void save(Properties prop) {
			prop.setProperty(id, Float.toString(getValue()));
		}
		
		@Override
		public void load(Properties prop) {
			String s = prop.getProperty(id, Float.toString(this.getValue()));
			this.setDefaultValue(Float.parseFloat(s));
			this.setToDefault();
		}
	}

	public static class BooleanProperty extends PrimitiveProperty<Boolean>{
		boolean value, defaultValue;
		JCheckBox valueCheckbox;
		
		ArrayList<PrimitiveProperty<?>> dependentProperties = new ArrayList<PrimitiveProperty<?>>();
		
		public BooleanProperty(String id, String label, String tooltip, boolean defaultValue) {
			super(id, "", "");
			this.value = defaultValue;
			this.defaultValue = defaultValue;
			
			valueCheckbox = new JCheckBox();
			valueCheckbox.setText(label);
			valueCheckbox.setToolTipText(tooltip);
			valueCheckbox.setSelected(value);
			valueCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					value = valueCheckbox.isSelected();
					
					for (PrimitiveProperty<?> p: dependentProperties)
						p.setEnabled(value);
				}
			});
		}
		
		@Override
		public Boolean getValue(){
			return value;
		}
		
		@Override
		public void setValue(Boolean t) {
			value = t;
			valueCheckbox.setSelected(t);
		}
		
		@Override
		public JComponent getEditor() {
			return valueCheckbox;
		}
		
		@Override
		public  void setToDefault() {
			this.value = this.defaultValue;
			valueCheckbox.setSelected(this.value);
			for (PrimitiveProperty<?> p: dependentProperties)
				p.setEnabled(this.value);
		}
		
		public void setDefaultValue(boolean defaultValue) {
			this.defaultValue = defaultValue;
		}
		
		public void addDependentProperty(PrimitiveProperty<?> p){
			dependentProperties.add(p);
			p.setEnabled(valueCheckbox.isSelected());
		}
		
		public void setEnabled(boolean enabled){
			super.setEnabled(enabled);
			for (PrimitiveProperty<?> p: dependentProperties)
				p.setEnabled(enabled);
		}
		
		@Override
		public void save(Properties prop) {
			prop.setProperty(id, Boolean.toString(this.getValue()));
		}
		
		@Override
		public void load(Properties prop) {
			String s = prop.getProperty(id, Boolean.toString(this.getValue()));
			this.setDefaultValue(Boolean.parseBoolean(s));
			this.setToDefault();
		}
	}
}


