package gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class JPrimitiveVariablesPropertiesDialog extends JDialog {

	private static final long serialVersionUID = 1L;
	private ArrayList<JComponent> components = new ArrayList<JComponent>();
	
	private JPanel currentGroup = null;
	
	private boolean ok = false;
	public JPrimitiveVariablesPropertiesDialog(JFrame frame, String title){
		super(frame, true);
		this.setTitle(title);
	}
	
	public boolean showDialog(){
		GraphicsDevice gd = this.getOwner().getGraphicsConfiguration().getDevice();
		JPanel propertyPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = 0; gbc.gridy = 0;
		gbc.weightx = 1;
		
		gbc.gridwidth = 2;
		for (JComponent c : components){
			propertyPanel.add(c, gbc);
			gbc.gridy++;
		}
		
		JButton okButton = new JButton("OK");
		JButton cancelButton = new JButton("cancel");
		cancelButton.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				ok = false;
				dispose();
			}
		});
		okButton.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				ok = true;
				dispose();
			}
		});
		
		gbc.gridwidth = 1;
		propertyPanel.add(okButton, gbc); gbc.gridx++;
		propertyPanel.add(cancelButton, gbc);

		JScrollPane scrollPane = new JScrollPane(propertyPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, 
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		this.add(scrollPane);
		this.pack();
		
		this.setLocation( (gd.getDisplayMode().getWidth()-this.getWidth())>>1, 
				(gd.getDisplayMode().getHeight()-this.getHeight())>>1);
		
		this.setVisible(true);
		
		return ok;
	}
	
	public void startGroup(String caption){
		currentGroup = new JPanel();
		currentGroup.setLayout(new BoxLayout(currentGroup, BoxLayout.Y_AXIS));
		
		currentGroup.setBorder(new TitledBorder(new EtchedBorder(1), caption));
		
		components.add(currentGroup);
	}
	
	public void endGroup(){
		currentGroup = null;
	}
	
	public IntegerProperty addInteger(String id, String label, String tooltip, int defaultValue, int min, int max){
		IntegerProperty ip = new IntegerProperty(id, label, tooltip, defaultValue, min, max);
		this.addComponent(getControlPanelForProperty(ip, false));
		return ip;
	}
	
	public FloatProperty addFloat(String id, String label, String tooltip, float defaultValue, float min, float max){
		FloatProperty fp = new FloatProperty(id, label, tooltip, defaultValue, min, max);
		this.addComponent(getControlPanelForProperty(fp, false));
		return fp;
	}

	public BooleanProperty addBoolean(String id, String label, String tooltip, boolean defaultValue){
		BooleanProperty bp = new BooleanProperty(id, label, tooltip, defaultValue, this);
		this.addComponent(getControlPanelForProperty(bp, true));
		return bp;
	}
	
	public void addLabel(String s){
		this.addComponent(getWordWrappedJLabel(s));
	}
	
	private JLabel getWordWrappedJLabel(String s){
		JLabel l = new JLabel();
		l.setText(getWordWrappedString(s, l));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}
	
	String getWordWrappedString(String s, JComponent c){
		GraphicsDevice gd = this.getOwner().getGraphicsConfiguration().getDevice();
		int maxSizeString = gd.getDisplayMode().getWidth()/3;
		if (c.getFontMetrics(c.getFont()).stringWidth(s) > maxSizeString)
			return "<html><table><tr><td width='"+maxSizeString+"'>"+ s +"</td></tr></table></html>";
		else return s;
	}
	
	public void addComponent(JComponent c){
		if (currentGroup != null)
			currentGroup.add(c);
		else 
			components.add(c);
	}
	
	private JPanel getControlPanelForProperty(final PrimitiveProperty p, boolean addGlue){		
		JPanel propertyPanel = new JPanel();
		
		JLabel label1 = null;
		if (p.label != null && !p.label.isEmpty()){
			label1 = getWordWrappedJLabel(p.label);
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
	
	static abstract class PrimitiveProperty{
		protected String id, label, tooltip;
		
		public PrimitiveProperty(String id, String label, String tooltip) {
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
	}
	
	public static class IntegerProperty extends PrimitiveProperty{
		int value, defaultValue;
		int max, min;
		JSpinner valueSpinner;
		
		IntegerProperty(String id, String label, String tooltip, int defaultValue, int min, int max) {
			super(id, label, tooltip);
			this.min = min;
			this.max = max;
			this.value = defaultValue;
			this.defaultValue = defaultValue;
			assert (min>value  || max<value);
			
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
		
		FloatProperty(String id, String label, String tooltip, float defaultValue, float min, float max) {
			super(id, label, tooltip);
			this.min = min;
			this.max = max;
			this.value = defaultValue;
			this.defaultValue = defaultValue;
			assert (min<=value && max>=value);
			
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
		
		ArrayList<PrimitiveProperty> dependentProperties = new ArrayList<JPrimitiveVariablesPropertiesDialog.PrimitiveProperty>();
		
		BooleanProperty(String id, String label, String tooltip, boolean defaultValue, JPrimitiveVariablesPropertiesDialog dialog) {
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
