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

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

public class JRenderedIntervalEditorDialog extends JDialog{
	private static final long serialVersionUID = 1L;
	private final RenderRange interval;
	
	public JRenderedIntervalEditorDialog(Frame owner, final RenderRange interval){
		super(owner);
		this.interval = interval;
		this.setTitle("Render interval");
		this.setLayout(new GridBagLayout());
		
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.gridx = 0; gbc.gridy = 0;
		gbc.weightx = 1;
		
		this.add(Box.createHorizontalGlue(),gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.NONE;
		this.add(new JLabel("Lower Limit"),gbc); gbc.gridx++;
		this.add(Box.createHorizontalGlue(),gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.NONE;
		this.add(new JLabel("Upper Limit"),gbc); gbc.gridx++;
		this.add(Box.createHorizontalGlue(),gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.BOTH;
		
		final JActivateCheckbox[] limitActiveCheckboxes = new JActivateCheckbox[6];
		final JFormattedTextField[] limits = new JFormattedTextField[6];
		for (int i=0; i<6; i++){
			limits[i] = new JFormattedTextField(NumberFormat.getInstance());
			limits[i].setValue(interval.getCurrentLimit(i));
			limitActiveCheckboxes[i] = new JActivateCheckbox(i, limits[i]);
			
			final int j=i;
			limits[j].addPropertyChangeListener(new PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					if (evt.getPropertyName().equals("value"))
						limitActiveCheckboxes[j].setSelected(true);
				}
			});
			limitActiveCheckboxes[i].setSelected(interval.getLimitActive(i));
		}
		
		gbc.gridx = 0; gbc.gridy++;
		gbc.fill = GridBagConstraints.NONE;
		this.add(limitActiveCheckboxes[0],gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.BOTH;
		this.add(limits[0],gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.NONE;
		this.add(new JLabel("x"),gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.BOTH;
		this.add(limits[3],gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.NONE;
		this.add(limitActiveCheckboxes[3],gbc);
		
		gbc.gridx = 0; gbc.gridy++;
		gbc.fill = GridBagConstraints.NONE;
		this.add(limitActiveCheckboxes[1],gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.BOTH;
		this.add(limits[1],gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.NONE;
		this.add(new JLabel("y"),gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.BOTH;
		this.add(limits[4],gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.NONE;
		this.add(limitActiveCheckboxes[4],gbc);
		
		gbc.gridx = 0; gbc.gridy++;
		gbc.fill = GridBagConstraints.NONE;
		this.add(limitActiveCheckboxes[2],gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.BOTH;
		this.add(limits[2],gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.NONE;
		this.add(new JLabel("z"),gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.BOTH;
		this.add(limits[5],gbc); gbc.gridx++;
		gbc.fill = GridBagConstraints.NONE;
		this.add(limitActiveCheckboxes[5],gbc);
		
		gbc.gridx = 0; gbc.gridy++;
		gbc.gridwidth = 5;
		gbc.fill = GridBagConstraints.BOTH;
		this.add(new JSeparator(SwingConstants.HORIZONTAL), gbc);
		gbc.gridy++;
		JPanel customPlanePane = new JPanel();
		customPlanePane.setLayout(new GridBagLayout());
		
		GridBagConstraints gbc2 = new GridBagConstraints();
		gbc2.anchor = GridBagConstraints.CENTER;
		gbc2.gridx = 0; gbc2.gridy = 0;
		gbc2.weightx = 1;
		gbc2.gridwidth = 5;
		
		customPlanePane.add(new JLabel("Custom clipping planes"), gbc2);
		gbc2.gridy++;
		gbc2.gridwidth = 1;
		gbc2.fill = GridBagConstraints.NONE;
		customPlanePane.add(new JLabel("Active"), gbc2); gbc2.gridx++;
		customPlanePane.add(new JLabel("x"), gbc2); gbc2.gridx++;
		customPlanePane.add(new JLabel("y"), gbc2); gbc2.gridx++;
		customPlanePane.add(new JLabel("z"), gbc2); gbc2.gridx++;
		customPlanePane.add(new JLabel("normal_x"), gbc2); gbc2.gridx++;
		customPlanePane.add(new JLabel("normal_y"), gbc2); gbc2.gridx++;
		customPlanePane.add(new JLabel("normal_z"), gbc2); gbc2.gridx++;
		
		
		final JCheckBox[] customPlaneCheckBoxes = new JCheckBox[6];
		final JFormattedTextField[][] customPlaneTextField = new JFormattedTextField[6][6];
		List<float[]> customPlanes = interval.getCustomClippingPlanes();
		
		for (int i=0; i<6;i++){
			gbc2.gridx = 0; gbc2.gridy++;
			gbc2.fill = GridBagConstraints.NONE;
			
			customPlaneCheckBoxes[i] = new JCheckBox();
			for (int j=0 ;j < customPlaneTextField.length; j++){
				customPlaneTextField[i][j] = new JFormattedTextField(NumberFormat.getInstance());
				customPlaneTextField[i][j].setPreferredSize(new java.awt.Dimension(80,20));
			}
			
			if (customPlanes != null && customPlanes.size()>i){
				customPlaneCheckBoxes[i].setSelected(true);
				for (int j=0 ;j < customPlaneTextField.length; j++)
					customPlaneTextField[i][j].setValue(customPlanes.get(i)[j]);
			} else {
				customPlaneCheckBoxes[i].setSelected(false);
				for (int j=0 ;j < customPlaneTextField.length; j++)
					customPlaneTextField[i][j].setValue(0f);
			}
			
			gbc2.weightx = 1;
			customPlanePane.add(customPlaneCheckBoxes[i], gbc2); gbc2.gridx++;
			gbc2.fill = GridBagConstraints.BOTH;
			for (int j=0 ;j < customPlaneTextField.length; j++){
				customPlanePane.add(customPlaneTextField[i][j], gbc2); gbc2.gridx++;
			}
		}
		
		this.add(customPlanePane, gbc);
		gbc.gridwidth = 1;
		
		JButton okButton = new JButton("OK");
		JButton resetButton = new JButton("Reset");
		JButton cancelButton = new JButton("Cancel");
		
		gbc.gridx = 0; gbc.gridy++;
		this.add(Box.createHorizontalGlue(),gbc); gbc.gridx++;
		this.add(okButton,gbc); gbc.gridx++;
		this.add(resetButton,gbc); gbc.gridx++;
		this.add(cancelButton,gbc);
		
		okButton.addActionListener(l->{
			try {
				for (int i=0; i<6; i++){
					if (limitActiveCheckboxes[i].isSelected())
						interval.setCurrentLimit(i, ((Number)(limits[i].getValue())).floatValue());
				}
				ArrayList<float[]> custPlanes = new ArrayList<float[]>();
				for (int i=0; i<6; i++){
					if (customPlaneCheckBoxes[i].isSelected()){
						float[] p = new float[customPlaneTextField.length];
						for (int j=0 ;j < customPlaneTextField.length; j++)
							p[j] = ((Number)(customPlaneTextField[i][j].getValue())).floatValue();
						custPlanes.add(p);
					}
				}
				interval.setCustomClippingPlanes(custPlanes);
				
				dispose();
			} catch (NumberFormatException ex){
				JOptionPane.showMessageDialog(null, "Please enter only numbers");
			}
		});
		
		resetButton.addActionListener(new ActionListener(){
			@Override
			public void actionPerformed(ActionEvent e) {
				interval.reset();
				for (int i=0; i<6; i++){
					limits[i].setText(Float.toString(interval.getGlobalLimit(i)));
					limitActiveCheckboxes[i].setSelected(false);
				}
			}
		});
		
		resetButton.addActionListener(l->{
			interval.reset();
			for (int i=0; i<6; i++){
				limits[i].setText(Float.toString(interval.getGlobalLimit(i)));
				limitActiveCheckboxes[i].setSelected(false);
			}
		});
		
		cancelButton.addActionListener(l->dispose());
		
		this.setModalityType(Dialog.DEFAULT_MODALITY_TYPE);
		this.pack();
		GraphicsDevice gd = owner.getGraphicsConfiguration().getDevice();
		this.setLocation( (gd.getDisplayMode().getWidth()-this.getWidth())>>1, 
				(gd.getDisplayMode().getHeight()-this.getHeight())>>1);
		this.setVisible(true);
	}
	
	private class JActivateCheckbox extends JCheckBox{
		private static final long serialVersionUID = 1L;

		public JActivateCheckbox(final int i, final JTextField textfield) {
			this.addActionListener(l->
				JRenderedIntervalEditorDialog.this.interval.setLimitActive(i, JActivateCheckbox.this.isSelected())
			);
		}
	}
	
	//Dialog is disposed when hitting ESC 
	protected JRootPane createRootPane() {
		JRootPane rootPane = super.createRootPane();
		KeyStroke stroke =  KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
		Action actionListener = new AbstractAction() {
			private static final long serialVersionUID = 1L;
			public void actionPerformed(ActionEvent actionEvent) {
				JRenderedIntervalEditorDialog.this.dispatchEvent(new WindowEvent( 
						JRenderedIntervalEditorDialog.this, WindowEvent.WINDOW_CLOSING 
		            )); 
			}
		};
		InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		inputMap.put(stroke, "ESCAPE");
		rootPane.getActionMap().put("ESCAPE", actionListener);

		return rootPane;
	}
}
