package gui;

import java.awt.Component;
import java.awt.GridLayout;
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
import common.Tupel;

public abstract class PrimitiveProperty<T> extends JPanel{
	private static final long serialVersionUID = 1L;
	protected String id, label, tooltip;
	
	protected void initControlPanel(boolean addDefaultButton){
		JLabel label1 = null;
		if (this.label != null && !this.label.isEmpty()){
			label1 = CommonUtils.getWordWrappedJLabel(this.label);
			label1.setToolTipText(this.tooltip);
			this.setLayout(new GridLayout(2, 1));
			this.add(label1);
		} else {
			this.setLayout(new GridLayout(1, 1));
		}
		
		final JLabel label = label1;
		
		JPanel editorPanel = new JPanel();
		editorPanel.setLayout(new BoxLayout(editorPanel, BoxLayout.LINE_AXIS));
		
		editorPanel.add(this.getEditor());
		if (this.editorNeedsGlue()) editorPanel.add(Box.createHorizontalGlue());
		
		final JButton reset = new JButton("default");
		reset.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				setToDefault();
			}
		});
		
		reset.setAlignmentX(Component.RIGHT_ALIGNMENT);
		
		if (addDefaultButton) editorPanel.add(reset);
		this.add(editorPanel);
		
		this.getEditor().addPropertyChangeListener(new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				boolean e = getEditor().isEnabled();
				reset.setEnabled(e);
				if (label!=null) label.setEnabled(e);
			}
		});
	}
		
	protected PrimitiveProperty(String id, String label, String tooltip) {
		this.id = id;
		this.label = label;
		this.tooltip = tooltip;
	}
	
	public abstract JComponent getEditor();
	
	public abstract void save(Properties prop);
	public abstract void load(Properties prop);
	
	boolean editorNeedsGlue(){
		return false;
	}
	
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
		private static final long serialVersionUID = 1L;
		JTextField textField;
		String defaultText;
		
		public StringProperty(String id, String label, String tooltip, String defaultString){
			this(id, label, tooltip, defaultString, false);
		}
		
		public StringProperty(String id, String label, String tooltip, String defaultString, boolean addDefaultButton) {
			super(id, label, tooltip);
			textField.setText(defaultString);
			textField.setEditable(true);
			this.defaultText = defaultString;
			super.initControlPanel(addDefaultButton);
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
		private static final long serialVersionUID = 1L;
		int value, defaultValue;
		int max, min;
		JSpinner valueSpinner;
		
		public IntegerProperty(String id, String label, String tooltip, int defaultValue, int min, int max){
			this(id, label, tooltip, defaultValue, min, max, false);
		}
		
		public IntegerProperty(String id, String label, String tooltip, int defaultValue, 
				int min, int max, boolean addDefaultButton) {
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
			super.initControlPanel(addDefaultButton);
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
		private static final long serialVersionUID = 1L;
		float value, defaultValue;
		float max, min;
		JSpinner valueSpinner;
		
		public FloatProperty(String id, String label, String tooltip, float defaultValue, float min, float max){
			this(id, label, tooltip, defaultValue, min, max, false);
		}
		
		public FloatProperty(String id, String label, String tooltip, float defaultValue, 
				float min, float max, boolean addDefaultButton) {
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
			super.initControlPanel(addDefaultButton);
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
		private static final long serialVersionUID = 1L;
		boolean value, defaultValue;
		JCheckBox valueCheckbox;
		
		ArrayList<Tupel<JComponent, Boolean>> dependentComponents = new ArrayList<Tupel<JComponent, Boolean>>();
		
		public BooleanProperty(String id, String label, String tooltip, boolean defaultValue) {
			this(id, label, tooltip, defaultValue, false);
		}
		
		public BooleanProperty(String id, String label, String tooltip, boolean defaultValue, boolean addDefaultButton) {
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
					
					for (Tupel<JComponent, Boolean> c: dependentComponents)
						c.o1.setEnabled(value ^ c.o2);
				}
			});
			super.initControlPanel(addDefaultButton);
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
		
		boolean editorNeedsGlue(){
			return true;
		}
		
		@Override
		public  void setToDefault() {
			this.value = this.defaultValue;
			valueCheckbox.setSelected(this.value);
			for (Tupel<JComponent, Boolean> c: dependentComponents)
				c.o1.setEnabled(this.value ^ c.o2);
		}
		
		public void setDefaultValue(boolean defaultValue) {
			this.defaultValue = defaultValue;
		}
		
		public void addDependentComponent(JComponent p){
			dependentComponents.add(new Tupel<JComponent, Boolean>(p, false));
			p.setEnabled(valueCheckbox.isSelected());
		}
		
		public void addDependentComponent(JComponent p, boolean invert){
			dependentComponents.add(new Tupel<JComponent, Boolean>(p, invert));
			p.setEnabled(valueCheckbox.isSelected());
		}
		
		public void setEnabled(boolean enabled){
			super.setEnabled(enabled);
			for (Tupel<JComponent, Boolean> c: dependentComponents)
				c.o1.setEnabled(this.value ^ c.o2);
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


