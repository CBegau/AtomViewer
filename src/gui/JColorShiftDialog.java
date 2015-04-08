// Part of AtomViewer: AtomViewer is a tool to display and analyse
// atomistic simulations
//
// Copyright (C) 2015  ICAMS, Ruhr-Universität Bochum
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

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import common.ColorUtils;
import common.Tupel;
import common.Vec3;
import crystalStructures.CrystalStructure;

public class JColorShiftDialog extends JDialog {
	private static final long serialVersionUID = 1L;

	private boolean shiftForVTypes;
	private Vec3 shift, origShift;
	
	private CrystalStructure cs;
	private JColorPreviewPanel colorPreview = null;

	public JColorShiftDialog(Window owner, Vec3 shift, boolean shiftForVTypes, CrystalStructure cs){
		super(owner);
			
		this.shiftForVTypes = shiftForVTypes;
		if (cs.getNumberOfElements() == 1)
			this.shiftForVTypes = true;	
		
		this.shift = shift.clone();
		this.origShift = shift.clone();
		
		this.cs = cs;
		createDialog();
	}

	
	public Vec3 getShift() {
		return shift;
	}
	
	public boolean isShiftForVTypes(){
		return shiftForVTypes;
	}
	
	private void createDialog(){
		this.setTitle("Element shading");
		this.setLayout(new BorderLayout());
		this.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
		this.colorPreview = new JColorPreviewPanel();
		this.add(colorPreview, BorderLayout.CENTER);
		this.add(new JHSVSliderPanel(), BorderLayout.EAST);
		this.add(new JLabel("<html>Shade atoms differently in color depending on their type and element<html>"), BorderLayout.NORTH);

		this.pack();
		
		GraphicsDevice gd = this.getOwner().getGraphicsConfiguration().getDevice();
		this.setLocation( (gd.getDisplayMode().getWidth()-this.getWidth())>>1, 
				(gd.getDisplayMode().getHeight()-this.getHeight())>>1);
		this.setVisible(true);
	}
	
	private class JHSVSliderPanel extends JPanel{
		private static final long serialVersionUID = 1L;
		JHSVSliderPanel(){
			final JSlider sliderH = new JSlider(-180, 180, (int)(shift.x*180));
			final JSlider sliderS = new JSlider(-100, 100, (int)(shift.y*100));
			final JSlider sliderV = new JSlider(-100, 100, (int)(shift.z*100));
			
			ChangeListener cl = new ChangeListener() {
				@Override
				public void stateChanged(ChangeEvent e) {
					if (e.getSource() == sliderH){
						int h = (int)sliderH.getValue();
					    shift.x = h/360f;
					} else if (e.getSource() == sliderS){
						 int s = (int)sliderS.getValue();
						 shift.y = s/100f;
					} else if (e.getSource() == sliderV){
						 int v = (int)sliderV.getValue();
						 shift.z = v/100f;
					}
					colorPreview.updateLabelContainer();
				}
			};
			
			sliderH.addChangeListener(cl);
			sliderS.addChangeListener(cl);
			sliderV.addChangeListener(cl);
			
			this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
			JLabel l = new JLabel("Hue"); l.setAlignmentX(CENTER_ALIGNMENT);
			
			this.add(l);
			this.add(sliderH);
			l = new JLabel("Saturation"); l.setAlignmentX(CENTER_ALIGNMENT);
			this.add(l);
			this.add(sliderS);
			l = new JLabel("Value"); l.setAlignmentX(CENTER_ALIGNMENT);
			this.add(l);
			this.add(sliderV);
			
			final JButton resetButton = new JButton("Reset");
			resetButton.setAlignmentX(Component.CENTER_ALIGNMENT);
			this.add(resetButton);
			
			if (cs.getNumberOfElements() > 1){
				final JRadioButton realEleButton = new JRadioButton("Real elements");
				final JRadioButton virtualEleButton = new JRadioButton("Virtual elements");
				realEleButton.setAlignmentX(CENTER_ALIGNMENT);
				virtualEleButton.setAlignmentX(CENTER_ALIGNMENT);
				
				realEleButton.setSelected(!shiftForVTypes);
				virtualEleButton.setSelected(shiftForVTypes);
				
				ButtonGroup bg = new ButtonGroup();
				ActionListener vTypeActionListener = new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						shiftForVTypes = !realEleButton.isSelected();
						colorPreview.updateLabelContainer();
					}
				};
				bg.add(realEleButton);
				bg.add(virtualEleButton);
				realEleButton.addActionListener(vTypeActionListener);
				virtualEleButton.addActionListener(vTypeActionListener);
				
				this.add(realEleButton);
				this.add(virtualEleButton);
			}
			
			this.add(Box.createVerticalGlue());
			
			final JButton okButton = new JButton("Apply");
			okButton.setAlignmentX(CENTER_ALIGNMENT);
			final JButton cancelButton = new JButton("Cancel");
			cancelButton.setAlignmentX(CENTER_ALIGNMENT);
			final JButton disableButton = new JButton("Disable");
			disableButton.setAlignmentX(CENTER_ALIGNMENT);
			
			ActionListener buttonsListener = new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					if (e.getSource()==resetButton){
						sliderH.setValue(0);
						sliderS.setValue(0);
						sliderV.setValue(0);
						return;
					} else {
						//cancel, disable, apply
						if (e.getSource() == disableButton){
							shift.setTo(0f, 0f, 0f);
						} else if (e.getSource() == cancelButton){
							shift.setTo(origShift);
						} 
						dispose();
					}
				}
			};
			
			resetButton.addActionListener(buttonsListener);
			okButton.addActionListener(buttonsListener);
			cancelButton.addActionListener(buttonsListener);
			disableButton.addActionListener(buttonsListener);
			
			Container c = new Container();
			c.setLayout(new GridLayout(1, 3));
			c.add(cancelButton);
			c.add(disableButton);
			c.add(okButton);
			
			this.add(c);
		}
	}
	
	private class JColorPreviewPanel extends JPanel{
		private static final long serialVersionUID = 1L;
		
		int rows = cs.getNumberOfTypes();
		int cols;
		boolean vTypes;
		JPanel labelContainer = new JPanel();
		JLabel[][] labels = null;
		
		JColorPreviewPanel() {
			Tupel<float[][], Integer> colors = ColorUtils.getColorShift(shiftForVTypes, cs, shift);
			cols = colors.o2;
			this.vTypes = shiftForVTypes;
			this.setLayout(new BorderLayout());
			
			updateLabelContainer();
			this.add(labelContainer, BorderLayout.CENTER);
			this.add(new JLabel("Element"), BorderLayout.NORTH);
		}
		
		void updateLabelContainer() {
			Tupel<float[][], Integer> a = ColorUtils.getColorShift(shiftForVTypes, cs, shift);
			float[][] colors = a.o1;
			cols = a.o2;
			
			if (labels == null || shiftForVTypes!=vTypes){
				labelContainer.removeAll();
				
				labelContainer.setLayout(new GridLayout(rows, cols));
				labels = new JLabel[rows][cols];
				for (int i=0; i<rows; i++){
					for (int j=0; j<cols; j++){
						labels[i][j] = new JLabel(String.format("%d", j));
						labelContainer.add(labels[i][j]);
						labels[i][j].setPreferredSize(new Dimension(30,16));
						labels[i][j].setOpaque(true);
					}
				}
				vTypes = shiftForVTypes;
				labelContainer.revalidate();
			}
			
			for (int i=0; i<rows; i++){
				for (int j=0; j<cols; j++){
					float[] c = colors[i*cols+j]; 
					labels[i][j].setBackground(new Color(c[0], c[1], c[2]));
				}
			}
		}
	}
}
