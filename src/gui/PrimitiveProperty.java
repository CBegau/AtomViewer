package gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public abstract class PrimitiveProperty{
	protected String id, label, tooltip;
	
	PrimitiveProperty(String id, String label, String tooltip) {
		this.id = id;
		this.label = label;
		this.tooltip = tooltip;
	}
	
	public abstract JComponent getEditor();
	
	public String getLabel(){
		return label;
	}
	
	protected abstract void setToDefault();
	
	public void setEnabled(boolean enabled){
		this.getEditor().setEnabled(enabled);
	}
	
	public static class StringProperty extends PrimitiveProperty{
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
		protected void setToDefault() {
			this.textField.setText(defaultText);
		}

		public String getValue() {
			return textField.getText();
		}
	}

	public static class IntegerProperty extends PrimitiveProperty{
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
		protected void setToDefault() {
			this.value = defaultValue;
			if (valueSpinner!=null){
				valueSpinner.setValue(this.value);
			}
		}
		
		public void setDefaultValue(int defaultValue) {
			this.defaultValue = defaultValue;
		}
		
		public int getValue() {
			return value;
		}
	}

	public static class FloatProperty extends PrimitiveProperty{
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
		
		public float getValue() {
			return value;
		}
		
		@Override
		public JComponent getEditor() {
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

	public static class BooleanProperty extends PrimitiveProperty{
		boolean value, defaultValue;
		JCheckBox valueCheckbox;
		
		ArrayList<PrimitiveProperty> dependentProperties = new ArrayList<PrimitiveProperty>();
		
		public BooleanProperty(String id, String label, String tooltip, boolean defaultValue, JPrimitiveVariablesPropertiesDialog dialog) {
			super(id, "", "");
			this.value = defaultValue;
			this.defaultValue = defaultValue;
			
			valueCheckbox = new JCheckBox();
			valueCheckbox.setText(dialog.getWordWrappedString(label, valueCheckbox));
			valueCheckbox.setToolTipText(tooltip);
			valueCheckbox.setSelected(value);
			valueCheckbox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					value = valueCheckbox.isSelected();
					
					for (PrimitiveProperty p: dependentProperties)
						p.setEnabled(value);
				}
			});
		}
		
		public boolean getValue(){
			return value;
		}
		
		@Override
		public JComponent getEditor() {
			return valueCheckbox;
		}
		
		@Override
		protected void setToDefault() {
			this.value = this.defaultValue;
			valueCheckbox.setSelected(this.value);
			for (PrimitiveProperty p: dependentProperties)
				p.setEnabled(this.value);
		}
		
		public void setDefaultValue(boolean defaultValue) {
			this.defaultValue = defaultValue;
		}
		
		public void addDependentProperty(PrimitiveProperty p){
			dependentProperties.add(p);
			p.setEnabled(valueCheckbox.isSelected());
		}
		
		public void setEnabled(boolean enabled){
			super.setEnabled(enabled);
			for (PrimitiveProperty p: dependentProperties)
				p.setEnabled(enabled);
		}
	}
}


