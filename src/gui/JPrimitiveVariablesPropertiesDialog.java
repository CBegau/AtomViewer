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
	
	public StringProperty addString(String id, String label, String tooltip, String defaultValue){
		StringProperty sp = new StringProperty(id, label, tooltip, defaultValue);
		this.addComponent(getControlPanelForProperty(sp, false));
		return sp;
	}
	
	public void addProperty(PrimitiveProperty property, boolean addGlue){
		this.addComponent(getControlPanelForProperty(property, addGlue));
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
	
	
}
