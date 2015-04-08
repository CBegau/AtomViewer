package gui;


import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JColorChooser;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

import model.Configuration;

import common.ColorTable;

public class JColorSelectPanel extends JPanel{		
	private static final long serialVersionUID = 1L;
	public enum ColorSelectPanelTypes{ELEMENTS, TYPES};
	private ViewerGLJPanel viewer;
	
	public JColorSelectPanel(final int number, Color c, final ColorSelectPanelTypes type, ViewerGLJPanel viewer){
		this.setSize(100, 20);
		this.setBackground(c);
		this.setBorder(new LineBorder(Color.BLACK, 1));
		this.viewer = viewer;
		this.addMouseListener(new MouseListener() {
			@Override
			public void mouseReleased(MouseEvent e) {}				
			@Override
			public void mousePressed(MouseEvent e) {}
			@Override
			public void mouseExited(MouseEvent e) {}
			@Override
			public void mouseEntered(MouseEvent e) {}
			
			@Override
			public void mouseClicked(MouseEvent e) {
				String message = "";
				if (type == ColorSelectPanelTypes.ELEMENTS){
					message = String.format("Choose color for element %d", number);
				} else if (type == ColorSelectPanelTypes.TYPES){
					message = String.format("Choose color for type %d (%s)", number, 
							Configuration.getCrystalStructure().getNameForType(number));
				}

				Color newColor = JColorChooser.showDialog(null, 
						message,  JColorSelectPanel.this.getBackground());
				if (newColor == null) return;
				JColorSelectPanel.this.setBackground(newColor);
				float[] color = new float[3];
				color[0] = newColor.getRed()/255f;
				color[1] = newColor.getGreen()/255f;
				color[2] = newColor.getBlue()/255f;
				if (type == ColorSelectPanelTypes.ELEMENTS){
					float[][] colors = ColorTable.getColorTableForElements(Configuration.getNumElements());
					colors[number][0] = color[0];
					colors[number][1] = color[1];
					colors[number][2] = color[2];
					ColorTable.saveColorScheme(colors, ColorTable.ELEMENT_COLORS_ID);
				}  else if (type == ColorSelectPanelTypes.TYPES){
					Configuration.getCrystalStructure().setGLColors(number, color);
					Configuration.getCrystalStructure().saveColorScheme();
				}
				JColorSelectPanel.this.viewer.updateAtoms();
				JColorSelectPanel.this.viewer.reDraw();
			}
		});
	}
	
	
	public JColorSelectPanel(final float[] colorArray, Color initialColor){
		this.setSize(100, 20);
		this.setBackground(initialColor);
		this.setBorder(new LineBorder(Color.BLACK, 1));
		this.addMouseListener(new MouseListener() {
			@Override
			public void mouseReleased(MouseEvent e) {}				
			@Override
			public void mousePressed(MouseEvent e) {}
			@Override
			public void mouseExited(MouseEvent e) {}
			@Override
			public void mouseEntered(MouseEvent e) {}
			
			@Override
			public void mouseClicked(MouseEvent e) {
				Color newColor = JColorChooser.showDialog(null, 
						"Select color",  JColorSelectPanel.this.getBackground());
				if (newColor == null) return;
				JColorSelectPanel.this.setBackground(newColor);
				
				colorArray[0] = newColor.getRed()/255f;
				colorArray[1] = newColor.getGreen()/255f;
				colorArray[2] = newColor.getBlue()/255f;
				
				JColorSelectPanel.this.viewer.updateAtoms();
				JColorSelectPanel.this.viewer.reDraw();
			}
		});
	}
	
	public void setViewer(ViewerGLJPanel viewer) {
		this.viewer = viewer;
	}
}
