package gui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import common.CommonUtils;
import gui.PrimitiveProperty.*;

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
		GridBagConstraints gbc = CommonUtils.getBasicGridBagConstraint();
		
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
		this.addComponent(PrimitiveProperty.getControlPanelForProperty(ip, this));
		return ip;
	}
	
	public FloatProperty addFloat(String id, String label, String tooltip, float defaultValue, float min, float max){
		FloatProperty fp = new FloatProperty(id, label, tooltip, defaultValue, min, max);
		this.addComponent(PrimitiveProperty.getControlPanelForProperty(fp, this));
		return fp;
	}

	public BooleanProperty addBoolean(String id, String label, String tooltip, boolean defaultValue){
		String wrappedLabel = CommonUtils.getWordWrappedString(label, new JCheckBox(), this);
		
		BooleanProperty bp = new BooleanProperty(id, wrappedLabel, tooltip, defaultValue);
		this.addComponent(PrimitiveProperty.getControlPanelForProperty(bp, this));
		return bp;
	}
	
	public StringProperty addString(String id, String label, String tooltip, String defaultValue){
		StringProperty sp = new StringProperty(id, label, tooltip, defaultValue);
		this.addComponent(PrimitiveProperty.getControlPanelForProperty(sp, this));
		return sp;
	}
	
	public void addProperty(PrimitiveProperty<?> property){
		this.addComponent(PrimitiveProperty.getControlPanelForProperty(property, this));
	}
	
	public void addLabel(String s){
		this.addComponent(CommonUtils.getWordWrappedJLabel(s, this));
	}
	
	public void addComponent(JComponent c){
		if (currentGroup != null)
			currentGroup.add(c);
		else 
			components.add(c);
	}
}
